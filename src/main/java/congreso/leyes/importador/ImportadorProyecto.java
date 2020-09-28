package congreso.leyes.importador;

import static java.lang.Thread.sleep;

import com.typesafe.config.ConfigFactory;
import congreso.leyes.Proyecto.ProyectoLey;
import congreso.leyes.Proyecto.ProyectoLey.Enlaces;
import congreso.leyes.Proyecto.ProyectoLey.Id;
import congreso.leyes.internal.ProyectoIdSerde;
import congreso.leyes.internal.ProyectoLeySerde;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology.AutoOffsetReset;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.Stores;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportadorProyecto {

  static final Logger LOG = LoggerFactory.getLogger(ImportadorProyecto.class);

  final String baseUrl;

  public ImportadorProyecto(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    var config = ConfigFactory.load();

    var kafkaBootstrapServers = config.getString("kafka.bootstrap-servers");
    var topic = config.getString("kafka.topics.proyecto-importado");

    LOG.info("Cargando proyectos importados");

    var builder = new StreamsBuilder();
    builder.globalTable(topic,
        Consumed.with(new ProyectoIdSerde(), new ProyectoLeySerde())
            .withOffsetResetPolicy(AutoOffsetReset.EARLIEST),
        Materialized.as(Stores.persistentKeyValueStore("proyectos")));

    var streamsConfig = new Properties();
    streamsConfig.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
    streamsConfig.put(StreamsConfig.APPLICATION_ID_CONFIG,
        config.getString("kafka.consumer-groups.importador-proyecto"));
    var streamsOverrides = config.getConfig("kafka.streams").entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().unwrapped()));
    streamsConfig.putAll(streamsOverrides);

    var kafkaStreams = new KafkaStreams(builder.build(), streamsConfig);
    kafkaStreams.start();

    while (!kafkaStreams.state().isRunningOrRebalancing()) {
      LOG.info("Esperando por streams a cargar...");
      sleep(Duration.ofSeconds(1).toMillis());
    }

    var proyectoRepositorio = kafkaStreams.store(
        StoreQueryParameters.fromNameAndType("proyectos", QueryableStoreTypes.keyValueStore()));

    var baseUrl = config.getString("importador.base-url");
    var proyectosUrl = config.getString("importador.proyectos-url");

    var importador = new ImportadorProyecto(baseUrl);

    LOG.info("Iniciando importacion de proyectos");

    var proyectos = importador.importarProyectos(proyectosUrl);

    LOG.info("Proyectos importados {}", proyectos.size());

    var producerConfig = new Properties();
    producerConfig.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
    var overrides = config.getConfig("kafka.producer").entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().unwrapped()));
    producerConfig.putAll(overrides);

    var keySerializer = new ProyectoIdSerde().serializer();
    var valueSerializer = new ProyectoLeySerde().serializer();
    var producer = new KafkaProducer<>(producerConfig, keySerializer, valueSerializer);

    LOG.info("Guardando proyectos");

    int changes = 0;
    for (var proyecto : proyectos) {
      var importado = (ProyectoLey) proyectoRepositorio.get(proyecto.getId());
      if (!proyecto.equals(importado)) {
        changes ++;
        var record = new ProducerRecord<>(topic, proyecto.getId(), proyecto);
        producer.send(record, (recordMetadata, e) -> {
          if (e != null) {
            LOG.error("Error enviando proyecto", e);
            throw new IllegalStateException("Error enviando proyecto", e);
          }
        });
      }
    }

    LOG.info("Proyectos guardados {}", changes);

    producer.close();
    kafkaStreams.close();
  }

  List<ProyectoLey> importarProyectos(String proyectosUrl) throws IOException {
    var proyectos = new ArrayList<ProyectoLey>();

    var index = 1;
    var batchSize = 0;

    do {
      var proyectosPorPagina = importarPagina(proyectosUrl, index);
      proyectos.addAll(proyectosPorPagina);

      batchSize = proyectosPorPagina.size();
      index = index + batchSize;
    } while (batchSize == 100);

    return proyectos;
  }

  List<ProyectoLey> importarPagina(String proyectosUrl, int index) throws IOException {
    var url = baseUrl + proyectosUrl + index;
    var doc = Jsoup.connect(url).get();
    var tablas = doc.body().getElementsByTag("table");
    if (tablas.size() != 3) {
      LOG.error("Numero de tablas inesperado: {}, url={}", tablas.size(), url);
      throw new IllegalStateException("Unexpected number of tables");
    }
    var proyectos = new ArrayList<ProyectoLey>();
    var tablaProyectos = tablas.get(1);
    var filas = tablaProyectos.getElementsByTag("tr");
    for (int i = 1; i < filas.size(); i++) {
      proyectos.add(leerProyecto(filas.get(i)));
    }
    return proyectos;
  }

  private ProyectoLey leerProyecto(Element row) {
    var campos = row.getElementsByTag("td");
    if (campos.size() != 5) {
      LOG.error("Numero inesperado de campos: {}, fila: {}", campos.size(), row.html());
      throw new IllegalStateException("Numero inesperado de campos");
    }
    var numero = campos.get(0).text();
    var fechaActualizacion = campos.get(1).text().isBlank() ?
        Optional.<Long>empty() :
        Optional.of(leerFecha(campos.get(1)));
    var fechaPresentacion = leerFecha(campos.get(2));
    var estado = campos.get(3).text();
    var enlaceSeguimiento = baseUrl + campos.get(0).getElementsByTag("a").attr("href");
    var builder = ProyectoLey.newBuilder()
        .setId(Id.newBuilder()
            .setNumeroPeriodo(numero)
            .setPeriodo("2016-2021")
            .build())
        .setEstado(estado)
        .setFechaPublicacion(fechaPresentacion)
        .setEnlaces(Enlaces.newBuilder().setSeguimiento(enlaceSeguimiento).build());
    fechaActualizacion.ifPresent(builder::setFechaActualizacion);
    return builder.build();
  }

  private Long leerFecha(Element td) {
    return LocalDate.parse(td.text(), DateTimeFormatter.ofPattern("MM/dd/yyyy"))
        .atStartOfDay()
        .toInstant(ZoneOffset.ofHours(-5))
        .toEpochMilli();
  }
}
