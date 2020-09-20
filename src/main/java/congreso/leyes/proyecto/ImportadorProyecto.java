package congreso.leyes.proyecto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.ConfigFactory;
import java.io.IOException;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

public class ImportadorProyecto {

  public static void main(String[] args) throws IOException {
    var config = ConfigFactory.load();
    var baseUrl = config.getString("importador.base-url");
    var proyectosUrl = config.getString("importador.proyectos-url");
    var importador = new Importador(baseUrl);
    var proyectos = importador.getAll(proyectosUrl);

    var objectMapper = new ObjectMapper();

    var producerConfig = new Properties();
    producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    var producer = new KafkaProducer<>(producerConfig, new StringSerializer(), new StringSerializer());

    var topic = "congreso.leyes.proyecto-importado-v1";

    for (var proyecto : proyectos) {
      var value = objectMapper.writeValueAsString(proyecto);
      var record = new ProducerRecord<>(topic, proyecto.getNumero(), value);
      producer.send(record, (recordMetadata, e) -> {
        if (e != null) e.printStackTrace();
      });
    }

    producer.close();
  }
}
