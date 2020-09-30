package congreso.leyes.exportador;

import com.typesafe.config.ConfigFactory;
import congreso.leyes.Proyecto.ProyectoLey;
import congreso.leyes.Proyecto.ProyectoLey.Id;
import congreso.leyes.Proyecto.ProyectoLey.Seguimiento;
import congreso.leyes.Proyecto.Tuit;
import congreso.leyes.Proyecto.Tuits;
import congreso.leyes.importador.ImportadorExpediente;
import congreso.leyes.internal.ProyectoIdSerde;
import congreso.leyes.internal.ProyectoLeySerde;
import congreso.leyes.internal.ProyectoTuitsSerde;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.ValueTransformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.StatusUpdate;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;

public class ExportadorTwitter {

  static final Logger LOG = LoggerFactory.getLogger(ImportadorExpediente.class);

  public static void main(String[] args) {
    var config = ConfigFactory.load();

    var kafkaBootstrapServers = config.getString("kafka.bootstrap-servers");
    var inputTopic = config.getString("kafka.topics.expediente-importado");
    var outputTopic = config.getString("kafka.topics.exportador-twitter");

    var streamsBuilder = new StreamsBuilder();
    streamsBuilder.addStateStore(Stores.keyValueStoreBuilder(
        Stores.persistentKeyValueStore("tuits"),
        new ProyectoIdSerde(),
        new ProyectoLeySerde()
    ));

    streamsBuilder
        .stream(inputTopic, Consumed.with(new ProyectoIdSerde(), new ProyectoLeySerde()))
        .transformValues(() -> new ValueTransformer<ProyectoLey, Tuits>() {
          KeyValueStore<Id, Tuits> store;

          @Override
          public void init(ProcessorContext context) {
            store = (KeyValueStore<Id, Tuits>) context.getStateStore("tuits");
          }

          @Override
          public Tuits transform(ProyectoLey proyectoLey) {
            var found = store.get(proyectoLey.getId());
            if (found == null) {
              var idPrincipal = tuitPrincipal(proyectoLey);
              var tuits = Tuits.newBuilder()
                  .setPrincipal(Tuit.newBuilder().setId(idPrincipal).build());
              store.put(proyectoLey.getId(), tuits.build());
              for (var seguimiento : proyectoLey.getSeguimientoList()) {
                var idSeguimiento = tuitSeguimiento(proyectoLey, seguimiento, idPrincipal);
                tuits.addSeguimientos(Tuit.newBuilder().setId(idSeguimiento).build());
                store.put(proyectoLey.getId(), tuits.build());
              }
              return tuits.build();
            } else {
              if (found.getSeguimientosCount() == proyectoLey.getSeguimientoCount()) {
                return null; //nada nuevo
              } else {
                var tuits = found.toBuilder();
                for (int i = found.getSeguimientosCount();
                    i < proyectoLey.getSeguimientoCount(); i++) {
                  var seguimiento = proyectoLey.getSeguimiento(i);
                  var urlSeguimiento = tuitSeguimiento(proyectoLey, seguimiento,
                      found.getPrincipal().getId());
                  tuits.addSeguimientos(Tuit.newBuilder().setId(urlSeguimiento).build());
                  store.put(proyectoLey.getId(), tuits.build());
                }
                return tuits.build();
              }
            }
          }

          @Override
          public void close() {
          }
        }, "tuits")
        .filterNot((id, tuits) -> Objects.isNull(tuits))
        .to(outputTopic, Produced.with(new ProyectoIdSerde(), new ProyectoTuitsSerde()));

    var streamsConfig = new Properties();
    streamsConfig.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
    var groupId = config.getString("kafka.consumer-groups.exportador-twitter");
    streamsConfig.put(StreamsConfig.APPLICATION_ID_CONFIG, groupId);
    var overrides = config.getConfig("kafka.streams").entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().unwrapped()));
    streamsConfig.putAll(overrides);
    var kafkaStreams = new KafkaStreams(streamsBuilder.build(), streamsConfig);

    Runtime.getRuntime().addShutdownHook(new Thread(kafkaStreams::close));

    LOG.info("Iniciando exportacion de tuits");

    kafkaStreams.start();
  }

  private static long tuitSeguimiento(
      ProyectoLey proyectoLey,
      Seguimiento seguimiento,
      long idPrincipal) {
    try {
      var factory = TwitterFactory.getSingleton();
      var statusUpdate = new StatusUpdate(
          String.format("""
              %s: %s
              
              %s
              """,
              fecha(seguimiento.getFecha()),
              seguimiento.getTexto(),
              urlHugo(proyectoLey)));
      statusUpdate.setInReplyToStatusId(idPrincipal);
      var status = factory.updateStatus(statusUpdate);
      return status.getId();
    } catch (TwitterException e) {
      LOG.error("Error tuiteando seguimiento {}", proyectoLey, e);
      throw new RuntimeException(e);
    }
  }

  private static long tuitPrincipal(ProyectoLey proyectoLey) {
    try {
      var factory = TwitterFactory.getSingleton();
      var status = factory.updateStatus(new StatusUpdate(
          String.format("""
                  Proyecto %s: "%s"
                  publicado el %s
                  
                  %s
                  """,
              proyectoLey.getDetalle().getNumeroUnico(),
              titulo(proyectoLey.getDetalle().getPeriodoTexto()),
              fecha(proyectoLey.getFechaPublicacion()),
              urlHugo(proyectoLey))
      ));
      return status.getId();
    } catch (TwitterException e) {
      LOG.error("Error tuiteando {}", proyectoLey, e);
      throw new RuntimeException(e);
    }
  }

  private static String titulo(String texto) {
    return texto.length() > 180 ? texto.substring(0, 180) : texto + "...";
  }

  private static Object urlHugo(ProyectoLey proyectoLey) {
    return String.format("https://jeqo.github.io/peru-congreso/proyectos-ley/%s/%s/%s/",
        proyectoLey.getId().getPeriodo(),
        proyectoLey.getId().getNumeroGrupo(),
        proyectoLey.getId().getNumeroPeriodo());
  }

  static String fecha(long fecha) {
    return OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(fecha),
        ZoneOffset.ofHours(-5))
        .toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
  }
}
