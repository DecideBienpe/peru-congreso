package congreso.leyes.proyecto.importador;

import com.typesafe.config.ConfigFactory;
import congreso.leyes.proyecto.Proyecto;
import congreso.leyes.proyecto.internal.ProyectoSerde;
import java.io.IOException;
import java.time.LocalDate;
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
import org.apache.kafka.common.serialization.StringSerializer;
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

  public static void main(String[] args) throws IOException {
    var config = ConfigFactory.load();
    var baseUrl = config.getString("importador.base-url");
    var proyectosUrl = config.getString("importador.proyectos-url");

    var importador = new ImportadorProyecto(baseUrl);

    LOG.info("Iniciando proyecto");

    var proyectos = importador.leerProyectos(proyectosUrl);

    LOG.info("Proyectos importados {}", proyectos.size());

    var producerConfig = new Properties();
    var kafkaBootstrapServers = config.getString("kafka.bootstrap-servers");
    producerConfig.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
    var overrides = config.getConfig("kafka.producer").entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().unwrapped()));
    producerConfig.putAll(overrides);

    var keySerializer = new StringSerializer();
    var valueSerializer = new ProyectoSerde().serializer();
    var producer = new KafkaProducer<>(producerConfig, keySerializer, valueSerializer);

    var topic = config.getString("kafka.topics.proyecto-importado");

    LOG.info("Guardando proyectos");

    for (var proyecto : proyectos) {
      var record = new ProducerRecord<>(topic, proyecto.getNumero(), proyecto);
      producer.send(record, (recordMetadata, e) -> {
        if (e != null) {
          LOG.error("Error enviando proyecto", e);
          throw new IllegalStateException("Error enviando proyecto", e);
        }
      });
    }

    LOG.info("Proyectos guardados");

    producer.close();
  }

  List<Proyecto> leerProyectos(String proyectosUrl) throws IOException {
    var proyectos = new ArrayList<Proyecto>();

    var index = 1;
    var batchSize = 0;

    do {
      var proyectosPorPagina = leerPaginaProyectos(proyectosUrl, index);
      proyectos.addAll(proyectosPorPagina);

      batchSize = proyectosPorPagina.size();
      index = index + batchSize;
    } while (batchSize == 100);

    return proyectos;
  }

  List<Proyecto> leerPaginaProyectos(String proyectosUrl, int index) throws IOException {
    var url = baseUrl + proyectosUrl + index;
    var doc = Jsoup.connect(url).get();
    var tablas = doc.body().getElementsByTag("table");
    if (tablas.size() != 3) {
      LOG.error("Numero de tablas inesperado: {}, url={}", tablas.size(), url);
      throw new IllegalStateException("Unexpected number of tables");
    }
    var proyectos = new ArrayList<Proyecto>();
    var tablaProyectos = tablas.get(1);
    var filas = tablaProyectos.getElementsByTag("tr");
    for (int i = 1; i < filas.size(); i++) {
      proyectos.add(leerProyecto(filas.get(i)));
    }
    return proyectos;
  }

  private Proyecto leerProyecto(Element row) {
    var campos = row.getElementsByTag("td");
    if (campos.size() != 5) {
      LOG.error("Numero inesperado de campos: {}, fila: {}", campos.size(), row.html());
      throw new IllegalStateException("Numero inesperado de campos");
    }
    var numero = campos.get(0).text();
    var fechaActualizacion = campos.get(1).text().isBlank() ?
        Optional.<LocalDate>empty() :
        Optional.of(parseDate(campos.get(1)));
    var fechaPresentacion = parseDate(campos.get(2));
    var estado = campos.get(3).text();
    var titulo = campos.get(4).text();
    var enlaceSeguimiento = campos.get(0).getElementsByTag("a").attr("href");
    return new Proyecto(
        numero,
        fechaActualizacion,
        fechaPresentacion,
        estado,
        titulo,
        enlaceSeguimiento);
  }

  private LocalDate parseDate(Element td) {
    return LocalDate.parse(td.text(), DateTimeFormatter.ofPattern("MM/dd/yyyy"));
  }
}
