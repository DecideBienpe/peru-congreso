package congreso.leyes.exportador.csv;

import static java.lang.Thread.sleep;

import com.typesafe.config.ConfigFactory;
import congreso.leyes.Proyecto.ProyectoLey;
import congreso.leyes.internal.ProyectoIdSerde;
import congreso.leyes.internal.ProyectoLeySerde;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.stream.Collectors;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology.AutoOffsetReset;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.Stores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExportadorCsv {

  static final Logger LOG = LoggerFactory.getLogger(ExportadorCsv.class);

  public static void main(String[] args) throws InterruptedException {
    var config = ConfigFactory.load();

    var kafkaBootstrapServers = config.getString("kafka.bootstrap-servers");
    var topic = config.getString("kafka.topics.seguimiento-importado");

    LOG.info("Cargando proyectos importados");

    var builder = new StreamsBuilder();
    builder.globalTable(topic,
        Consumed.with(new ProyectoIdSerde(), new ProyectoLeySerde())
            .withOffsetResetPolicy(AutoOffsetReset.EARLIEST),
        Materialized.as(Stores.persistentKeyValueStore("proyectos")));

    var streamsConfig = new Properties();
    streamsConfig.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
    streamsConfig.put(StreamsConfig.APPLICATION_ID_CONFIG,
        config.getString("kafka.consumer-groups.exportador-csv"));
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

    var csvList = new ArrayList<ProyectoCsv>();
    try (var iter = proyectoRepositorio.all()) {
      while (iter.hasNext()) {
        var keyValue = iter.next();
        var proyectoLey = (ProyectoLey) keyValue.value;
        ProyectoCsv csv = new ProyectoCsv();
        csv.periodo = proyectoLey.getId().getPeriodo();
        csv.numeroPeriodo = proyectoLey.getId().getNumeroPeriodo();
        csv.numeroUnico = proyectoLey.getDetalle().getNumeroUnico();
        csv.estado = proyectoLey.getEstado();
        csv.fechaPublicacion = fecha(proyectoLey.getFechaPublicacion());
        csv.fechaActualizacion = fecha(proyectoLey.getFechaActualizacion());
        csv.titulo = proyectoLey.getDetalle().getTitulo();
        csv.sumilla = proyectoLey.getDetalle().getSumilla();
        csv.legislatura = proyectoLey.getDetalle().getLegislatura();
        csv.proponente = proyectoLey.getDetalle().getProponente();
        csv.grupoParlamentario = proyectoLey.getDetalle().getGrupoParlamentario();
        csv.iniciativasAgrupadas = proyectoLey.getDetalle().getIniciativasAgrupadas();
        csv.autores = String.join(";", proyectoLey.getDetalle().getAutorList());
        csv.adherentes = String.join(";", proyectoLey.getDetalle().getAdherenteList());
        csv.sectores = String.join(";", proyectoLey.getDetalle().getSectorList());
        csv.ley = Boolean.toString(proyectoLey.hasLey());
        csvList.add(csv);
      }
    }

    csvList.sort(Comparator.comparing(o -> o.numeroUnico));

    var path = Paths.get("data/exportacion/csv/proyecto.csv");
    try {
      Files.writeString(path, ProyectoCsv.header() + "\n", StandardOpenOption.CREATE);
      for (ProyectoCsv csv : csvList) {
        Files.writeString(path, csv.toCsvLine() + "\n", StandardOpenOption.APPEND);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    kafkaStreams.close();
  }

  static String fecha(long fecha) {
    return OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(fecha),
        ZoneOffset.ofHours(-5))
        .toLocalDate().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
  }
}
