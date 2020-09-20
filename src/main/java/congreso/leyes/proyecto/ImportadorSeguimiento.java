package congreso.leyes.proyecto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.ConfigFactory;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

public class ImportadorSeguimiento {

  public static void main(String[] args) throws IOException {
    var consumerConfig = new Properties();
    consumerConfig.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, "congreso.leyes.proyecto-seguimiento-v1");
    consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    consumerConfig.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    var consumer = new KafkaConsumer<>(consumerConfig, new StringDeserializer(),
        new StringDeserializer());
    consumer.subscribe(List.of("congreso.leyes.proyecto-importado-v1"));

    var objectMapper = new ObjectMapper();

    var producerConfig = new Properties();
    producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    var producer = new KafkaProducer<>(producerConfig, new StringSerializer(), new StringSerializer());

    var config = ConfigFactory.load();
    var baseUrl = config.getString("importador.base-url");

    var importador = new Importador(baseUrl);

    var topic = "congreso.leyes.seguimiento-importado-v1";

    while(!Thread.interrupted()) {
      var records = consumer.poll(Duration.ofSeconds(5));
      for (var record : records) {
        String value = record.value();
        var proyecto = objectMapper.readValue(value, ProyectoImportado.class);
        var proyectoSeguimiento = importador.getProyectoSeguimiento(proyecto);

        var valueSeguimiento = objectMapper.writeValueAsString(proyectoSeguimiento);
        var recordSeguimiento = new ProducerRecord<>(topic, proyectoSeguimiento.getNumero(), valueSeguimiento);
        producer.send(recordSeguimiento, (recordMetadata, e) -> {
          if (e != null) e.printStackTrace();
        });
      }
    }
  }
}
