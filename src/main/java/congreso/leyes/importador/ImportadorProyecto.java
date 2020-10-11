package congreso.leyes.importador;

import static java.lang.Thread.sleep;
import static org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG;
import static org.apache.kafka.streams.StreamsConfig.*;

import com.google.protobuf.Int64Value;
import com.typesafe.config.Config;
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
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology.AutoOffsetReset;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.apache.kafka.streams.state.Stores;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportadorProyecto {

  static final Logger LOG = LoggerFactory.getLogger(ImportadorProyecto.class);

  final String baseUrl;
  final KafkaProducer<Id, ProyectoLey> producer;
  final String topic;

  public ImportadorProyecto(String baseUrl, KafkaProducer<Id, ProyectoLey> producer, String topic) {
    this.baseUrl = baseUrl;
    this.producer = producer;
    this.topic = topic;
  }

  public static void main(String[] args) throws InterruptedException, IOException {
    var config = ConfigFactory.load();
    run(config);
  }

  public static void run(Config config) throws InterruptedException, IOException {
    var kafkaBootstrapServers = config.getString("kafka.bootstrap-servers");
    var topic = config.getString("kafka.topics.proyecto-importado");

    LOG.info("Cargando proyectos importados");

    var builder = new StreamsBuilder();
    builder.globalTable(
        topic,
        Consumed.with(new ProyectoIdSerde(), new ProyectoLeySerde())
            .withOffsetResetPolicy(AutoOffsetReset.EARLIEST),
        Materialized.as(Stores.persistentKeyValueStore("proyectos")));

    var streamsConfig = new Properties();
    streamsConfig.put(BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
    final var groupId = config.getString("kafka.consumer-groups.importador-proyecto");
    streamsConfig.put(APPLICATION_ID_CONFIG, groupId);
    var streamsOverrides = config.getConfig("kafka.streams").entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().unwrapped()));
    streamsConfig.putAll(streamsOverrides);

    var kafkaStreams = new KafkaStreams(builder.build(), streamsConfig);

    kafkaStreams.start();

    while (!kafkaStreams.state().isRunningOrRebalancing()) {
      LOG.info("Esperando por streams a cargar...");
      sleep(Duration.ofSeconds(1).toMillis());
    }

    var repositorio = kafkaStreams.store(
        StoreQueryParameters.fromNameAndType("proyectos", QueryableStoreTypes.keyValueStore()));

    var producerConfig = new Properties();
    producerConfig.put(BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
    var overrides = config.getConfig("kafka.producer").entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().unwrapped()));
    producerConfig.putAll(overrides);

    var keySerializer = new ProyectoIdSerde().serializer();
    var valueSerializer = new ProyectoLeySerde().serializer();

    var producer = new KafkaProducer<>(producerConfig, keySerializer, valueSerializer);

    var baseUrl = config.getString("importador.base-url");
    var proyectosUrl = config.getString("importador.proyectos-url");

    var importador = new ImportadorProyecto(baseUrl, producer, topic);

    LOG.info("Iniciando importación de proyectos");

    var index = 1;
    var batchSize = 0;
    int cambios = 0;

    do {
      var proyectosPorPagina = importador.importarPagina(proyectosUrl, index);

      for (var proyecto : proyectosPorPagina) {
        int cambio = importador.guardarProyecto(repositorio, proyecto);
        cambios = cambios + cambio;
      }

      batchSize = proyectosPorPagina.size();
      index = index + batchSize;
    } while (batchSize == 100);

    LOG.info("Proyectos guardados {}", cambios);

    producer.close();
    kafkaStreams.close();
  }

  int guardarProyecto(ReadOnlyKeyValueStore<Object, Object> repositorio, ProyectoLey proyecto) {
    var importado = (ProyectoLey) repositorio.get(proyecto.getId());
    int cambio = 0;
    if (!proyecto.equals(importado)) {
      var record = new ProducerRecord<>(topic, proyecto.getId(), proyecto);
      producer.send(record, (recordMetadata, e) -> {
        if (e != null) {
          LOG.error("Error enviando proyecto {}", proyecto, e);
          throw new IllegalStateException(e);
        }
      });
      cambio = 1;
    }
    return cambio;
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
    var filas = tablas.get(1).getElementsByTag("tr");
    for (int i = 1; i < filas.size(); i++) {
      proyectos.add(proyecto(filas.get(i)));
    }
    return proyectos;
  }

  private ProyectoLey proyecto(Element row) {
    var campos = row.getElementsByTag("td");
    if (campos.size() != 5) {
      LOG.error("Numero inesperado de campos: {}, fila: {}", campos.size(), row.html());
      throw new IllegalStateException("Numero inesperado de campos");
    }
    var numero = campos.get(0).text();
    var fechaActualizacion = campos.get(1).text().isBlank() ?
        Optional.<Long>empty() :
        Optional.of(fecha(campos.get(1).text().trim()));
    var fechaPresentacion = fecha(campos.get(2).text().trim());
    var estado = campos.get(3).text();
    var titulo = campos.get(4).text();
    var enlaceSeguimiento = baseUrl + campos.get(0).getElementsByTag("a").attr("href");
    var builder = ProyectoLey.newBuilder()
        .setId(Id.newBuilder()
            .setNumeroPeriodo(numero)
            .setNumeroGrupo(grupo(numero))
            .setPeriodo("2016-2021")
            .build())
        .setEstado(estado)
        .setFechaPublicacion(fechaPresentacion)
        .setTitulo(titulo
            .replaceAll("\"\"", "\"")
            .replaceAll("\"", "'")
            .replaceAll(",,", ",")
            .replaceAll(":", ".-"))
        .setEnlaces(Enlaces.newBuilder().setSeguimiento(enlaceSeguimiento).build());
    fechaActualizacion.map(Int64Value::of).ifPresent(builder::setFechaActualizacion);
    return builder.build();
  }

  private Long fecha(String texto) {
    return LocalDate.parse(texto, DateTimeFormatter.ofPattern("MM/dd/yyyy"))
        .atStartOfDay()
        .toInstant(ZoneOffset.ofHours(-5))
        .toEpochMilli();
  }

  private static String grupo(String numeroPeriodo) {
    var i = (Integer.parseInt(numeroPeriodo) / 100) * 100;
    return String.format("%05d", i);
  }
}
