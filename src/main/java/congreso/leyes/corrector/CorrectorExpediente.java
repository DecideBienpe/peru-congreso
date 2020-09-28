package congreso.leyes.corrector;

import com.typesafe.config.ConfigFactory;
import congreso.leyes.Proyecto.ProyectoLey;
import congreso.leyes.Proyecto.ProyectoLey.Id;
import congreso.leyes.internal.ProyectoIdSerde;
import congreso.leyes.internal.ProyectoLeySerde;
import java.util.ArrayList;
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

public class CorrectorExpediente {

  static final Logger LOG = LoggerFactory.getLogger(CorrectorExpediente.class);

  public static void main(String[] args) {
    var config = ConfigFactory.load();

    var kafkaBootstrapServers = config.getString("kafka.bootstrap-servers");
    var inputTopic = config.getString("kafka.topics.expediente-importado");
    var outputTopic = config.getString("kafka.topics.expediente-importado");

    var streamsBuilder = new StreamsBuilder();
    streamsBuilder.addStateStore(Stores.keyValueStoreBuilder(
        Stores.persistentKeyValueStore("proyectos"),
        new ProyectoIdSerde(),
        new ProyectoLeySerde()
    ));

    streamsBuilder
        .stream(inputTopic, Consumed.with(new ProyectoIdSerde(), new ProyectoLeySerde()))
        .mapValues(value -> {
          var builder = value.toBuilder();
          var sectores = new ArrayList<String>();
          for (var sector : builder.getDetalle().getSectorList()) {
            if (sector.contains("-")) {
              LOG.info("Sector con guion encontrado: {}", sector);
              var corregido = sector.substring(0, sector.indexOf("-"));
              sectores.add(corregido);
            } else {
              sectores.add(sector);
            }
          }
          builder.setDetalle(builder.getDetalleBuilder().clearSector().addAllSector(sectores));
          return builder.build();
        })
        .filterNot((id, proyectoLey) -> Objects.isNull(proyectoLey))
        .transformValues(() -> new ValueTransformer<ProyectoLey, ProyectoLey>() {
          KeyValueStore<Id, ProyectoLey> store;

          @Override
          public void init(ProcessorContext context) {
            store = (KeyValueStore<Id, ProyectoLey>) context.getStateStore("proyectos");
          }

          @Override
          public ProyectoLey transform(ProyectoLey proyectoLey) {
            if (proyectoLey.equals(store.get(proyectoLey.getId()))) {
              return null;
            } else {
              store.put(proyectoLey.getId(), proyectoLey);
              return proyectoLey;
            }
          }

          @Override
          public void close() {
          }
        }, "proyectos")
        .filterNot((id, proyectoLey) -> Objects.isNull(proyectoLey))
        .to(outputTopic, Produced.with(new ProyectoIdSerde(), new ProyectoLeySerde()));

    var streamsConfig = new Properties();
    streamsConfig.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
    var groupId = config.getString("kafka.consumer-groups.corrector-expediente");
    streamsConfig.put(StreamsConfig.APPLICATION_ID_CONFIG, groupId);
    var overrides = config.getConfig("kafka.streams").entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().unwrapped()));
    streamsConfig.putAll(overrides);

    var kafkaStreams = new KafkaStreams(streamsBuilder.build(), streamsConfig);

    Runtime.getRuntime().addShutdownHook(new Thread(kafkaStreams::close));

    LOG.info("Iniciando corrector de expedientes");

    kafkaStreams.start();
  }
}
