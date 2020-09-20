package congreso.leyes.importador;

import com.typesafe.config.ConfigFactory;
import congreso.leyes.Congresista;
import congreso.leyes.Proyecto;
import congreso.leyes.Seguimiento;
import congreso.leyes.internal.ProyectoSerde;
import congreso.leyes.internal.SeguimientoSerde;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.stream.Collectors;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportadorSeguimiento {

  static final Logger LOG = LoggerFactory.getLogger(ImportadorSeguimiento.class);

  final String baseUrl;

  public ImportadorSeguimiento(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public static void main(String[] args) throws IOException {
    var config = ConfigFactory.load();

    var consumerConfig = new Properties();
    var kafkaBootstrapServers = config.getString("kafka.bootstrap-servers");
    consumerConfig.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
    var groupId = config.getString("kafka.consumer-groups.importador-seguimiento");
    consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    var overrides = config.getConfig("kafka.consumer").entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().unwrapped()));
    consumerConfig.putAll(overrides);
    var keyDeserializer = new StringDeserializer();
    var valueDeserializer = new ProyectoSerde().deserializer();
    var consumer = new KafkaConsumer<>(consumerConfig, keyDeserializer, valueDeserializer);
    consumer.subscribe(List.of(config.getString("kafka.topics.proyecto-importado")));

    var producerConfig = new Properties();
    producerConfig.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
    var producerOverrides = config.getConfig("kafka.producer").entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().unwrapped()));
    producerConfig.putAll(producerOverrides);
    var keySerializer = new StringSerializer();
    var valueSerializer = new SeguimientoSerde().serializer();
    var producer = new KafkaProducer<>(producerConfig, keySerializer, valueSerializer);

    var baseUrl = config.getString("importador.base-url");

    var importador = new ImportadorSeguimiento(baseUrl);

    LOG.info("Iniciando importacion de seguimientos");

    var topic = config.getString("kafka.topics.seguimiento-importado");

    while (!Thread.interrupted()) {
      var records = consumer.poll(Duration.ofSeconds(5));

      LOG.info("Proyectos recibidos {}", records.count());

      if (records.isEmpty()) {
        LOG.info("Cerrando importador ya que no hay mas proyectos disponibles");
        producer.close();
        consumer.close();
        Runtime.getRuntime().exit(0);
      }

      for (var record : records) {
        var seguimiento = importador.getSeguimiento(record.value());
        var recordSeguimiento = new ProducerRecord<>(topic, seguimiento.getNumero(), seguimiento);
        producer.send(recordSeguimiento, (recordMetadata, e) -> {
          if (e != null) {
            LOG.error("Error guardando seguimiento {}", seguimiento, e);
            throw new IllegalStateException(e);
          }
        });
      }
      consumer.commitAsync((map, e) -> {
        if (e != null) {
          LOG.error("Error guardando progreso de importador {}", map, e);
          throw new IllegalStateException(e);
        }
        LOG.info("Progreso de importador {}", map);
      });
    }
  }

  Seguimiento getSeguimiento(Proyecto proyecto) throws IOException {
    var url = baseUrl + proyecto.getReferencia();
    try {
      var seguimiento = new Seguimiento();
      var doc = Jsoup.connect(url).get();
      var scripts = doc.head().getElementsByTag("script");
      if (scripts.size() != 2) {
        LOG.error("Numero inesperado de scripts {}, url={}", scripts.size(), url);
        throw new IllegalStateException("Unexpected number of tables");
      }
      var enlace = Arrays.stream(scripts.get(1).html().split("\\n"))
          .filter(s -> s.strip().startsWith("var url="))
          .map(s -> s.substring(s.indexOf("\"") + 1, s.lastIndexOf("\"")))
          .findFirst();
      var tablas = doc.body().getElementsByTag("table");
      if (tablas.size() != 2) {
        LOG.error("Unexpected number of tables url={}", url);
        throw new IllegalStateException("Unexpected number of tables");
      }
      var a = tablas.get(0).getElementsByTag("a").first();
      if (a != null) {
        var onclick = a.attr("onclick");
        var param = onclick.substring(onclick.indexOf("'"), onclick.lastIndexOf("'"));
        enlace.ifPresent(s -> seguimiento.setEnlaceExpedienteDigital(s + param));
      }

      var contenidoTabla = tablas.get(1);
      contenidoTabla.getElementsByTag("tr")
          .forEach(tr -> {
            var tds = tr.getElementsByTag("td");
            var field = tds.get(0).text();
            switch (field) {
              case "Período:" -> seguimiento.setPeriodo(tds.get(1).text());
              case "Legislatura:" -> seguimiento.setLegislatura(tds.get(1).text());
              case "Fecha Presentación:" -> seguimiento.setPresentacionLocalDate(
                  parsearFecha(tds.get(1)));
              case "Número:" -> seguimiento.setNumero(tds.get(1).text());
              case "Proponente:" -> seguimiento.setProponente(tds.get(1).text());
              case "Grupo Parlamentario:" -> seguimiento
                  .setGrupoParlamentario(tds.get(1).text());
              case "Título:" -> seguimiento.setTitulo(tds.get(1).text());
              case "Sumilla:" -> seguimiento.setSumilla(tds.get(1).text());
              case "Autores (*):", "Autores (***)" -> seguimiento
                  .setAutores(leerCongresistasAutores(tds.get(1)));
              case "Adherentes(**):" -> seguimiento
                  .setAdherentes(leerCongresistasAdherentes(tds.get(1)));
              case "Seguimiento:" -> seguimiento.setSeguimiento(tds.get(1).text());
              case "Iniciativas Agrupadas:" -> seguimiento
                  .setIniciativasAgrupadas(tds.get(1).text());
              case "Número de Ley:" -> seguimiento.setLeyNumero(tds.get(1).text());
              case "Título de la Ley:" -> seguimiento.setLeyTitulo(tds.get(1).text());
              case "Sumilla de la Ley" -> seguimiento.setLeySumilla(tds.get(1).text());
              default -> LOG.error("Campo no mapeado: " + field);
            }
          });
      return seguimiento;
    } catch (Throwable e) {
      LOG.error("Error procesando proyecto {} referencia {}", proyecto.getNumero(), url);
      throw e;
    }
  }

  private List<Congresista> leerCongresistasAutores(Element element) {
    return
        element.getElementsByTag("a").stream()
            .map(a -> {
              String email = a.attr("href");
              String nombreCompleto = a.text();
              return new Congresista(email, nombreCompleto);
            })
            .collect(Collectors.toList());
  }

  private List<String> leerCongresistasAdherentes(Element element) {
    return Arrays.asList(element.text().split(","));
  }

  private LocalDate parsearFecha(Element td) {
    return LocalDate.parse(td.text(), DateTimeFormatter.ofPattern("MM/dd/yyyy"));
  }
}
