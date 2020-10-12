package congreso.leyes;

import static java.util.stream.Collectors.toMap;

import com.typesafe.config.ConfigFactory;
import congreso.leyes.exportador.ExportadorCsv;
import congreso.leyes.exportador.ExportadorHugo;
import congreso.leyes.importador.ImportadorExpediente;
import congreso.leyes.importador.ImportadorProyecto;
import congreso.leyes.importador.ImportadorSeguimiento;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

  static final Logger LOG = LoggerFactory.getLogger(Main.class);

  final AdminClient adminClient;

  public Main(AdminClient adminClient) {
    this.adminClient = adminClient;
  }

  public static void main(String[] args)
      throws IOException, InterruptedException, ExecutionException {
    var config = ConfigFactory.load();

    var kafkaBootstrapServers = config.getString("kafka.bootstrap-servers");

    var adminProperties = new Properties();
    adminProperties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);

    var adminClient = AdminClient.create(adminProperties);

    var main = new Main(adminClient);

    LOG.info("Iniciando importacion de proyectos");

    ImportadorProyecto.run(config);

    LOG.info("Proyectos importados");

    var grupoImportadorSeguimiento = config
        .getString("kafka.consumer-groups.importador-seguimiento");
    var grupoImportadorExpediente = config
        .getString("kafka.consumer-groups.importador-expediente");
    var topicProyectos = config.getString("kafka.topics.proyecto-importado");
    var topicSeguimientos = config.getString("kafka.topics.seguimiento-importado");

    if (!main.procesamientoCompleto(grupoImportadorSeguimiento, topicProyectos)) {
      main.resetToEarliest(grupoImportadorSeguimiento, topicProyectos);
      ImportadorSeguimiento.run(config);
      main.resetToEarliest(grupoImportadorExpediente, topicSeguimientos);
      ImportadorExpediente.run(config);
    }

    LOG.info("Importacion de Seguimiento y Expediente reseteados al inicio de la historia");

    int n = 0;
    int timeoutMinutes = 15;

    while (!main.procesamientoCompleto(grupoImportadorSeguimiento, topicProyectos) ||
        !main.procesamientoCompleto(grupoImportadorExpediente, topicSeguimientos)) {
      LOG.info("Esperando que importaciones finalicen procesamiento");
      Thread.sleep(Duration.ofMinutes(1).toMillis());
      n++;
      if (n == timeoutMinutes) {
        LOG.warn("Reiniciando importadores ya que tomo mas de {} minutos en procesar", n);
        ImportadorSeguimiento.close();
        ImportadorExpediente.close();
        ImportadorSeguimiento.run(config);
        ImportadorExpediente.run(config);
        n = 0;
      }
    }
    LOG.info("Importaciones finalizadas");

    LOG.info("Iniciando exportaciones");
    ExportadorCsv.run(config);
    ExportadorHugo.run(config);

    LOG.info("Exportaciones completadas");
    System.exit(0);
  }

  void resetToEarliest(String grupo, String topic) throws ExecutionException, InterruptedException {
    var results = adminClient
        .listConsumerGroupOffsets(grupo)
        .partitionsToOffsetAndMetadata().get();
    var newOffsets = new HashMap<TopicPartition, OffsetAndMetadata>();
    results.entrySet().stream().filter(entry -> entry.getKey().topic().equals(topic))
        .forEach(entry -> newOffsets.put(entry.getKey(), new OffsetAndMetadata(0)));
    adminClient.alterConsumerGroupOffsets(grupo, newOffsets).all()
        .get();
  }

  boolean procesamientoCompleto(String grupo, String topic)
      throws ExecutionException, InterruptedException {
    var results = adminClient
        .listConsumerGroupOffsets(grupo)
        .partitionsToOffsetAndMetadata().get();
    var topicNames = results.keySet().stream().collect(toMap(tp -> tp, tp -> OffsetSpec.latest()));
    var topicOffsets = adminClient.listOffsets(topicNames).all().get();
    var done = true;
    for (var entry : topicOffsets.entrySet()) {
      if (entry.getKey().topic().equals(topic)) {
        var consumerOffset = results.get(entry.getKey());
        if (consumerOffset.offset() < entry.getValue().offset()) {
          done = false;
          LOG.info("Grupo: {} at {} TopicPartition: {} at {} Lag: {}", grupo,
              consumerOffset.offset(), entry.getKey(), entry.getValue().offset(),
              entry.getValue().offset() - consumerOffset.offset());
        }
      }
    }
    return done;
  }

}
