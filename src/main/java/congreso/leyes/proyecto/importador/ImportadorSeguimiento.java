package congreso.leyes.proyecto.importador;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.ConfigFactory;
import congreso.leyes.proyecto.Congresista;
import congreso.leyes.proyecto.Proyecto;
import congreso.leyes.proyecto.Seguimiento;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
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
    var producer = new KafkaProducer<>(producerConfig, new StringSerializer(),
        new StringSerializer());

    var config = ConfigFactory.load();
    var baseUrl = config.getString("importador.base-url");

    var importador = new ImportadorSeguimiento(baseUrl);

    var topic = "congreso.leyes.seguimiento-importado-v1";

    while (!Thread.interrupted()) {
      var records = consumer.poll(Duration.ofSeconds(5));
      for (var record : records) {
        String value = record.value();
        var proyecto = objectMapper.readValue(value, Proyecto.class);
        var proyectoSeguimiento = importador.getSeguimiento(proyecto);

        var valueSeguimiento = objectMapper.writeValueAsString(proyectoSeguimiento);
        var recordSeguimiento = new ProducerRecord<>(topic, proyectoSeguimiento.getNumero(),
            valueSeguimiento);
        producer.send(recordSeguimiento, (recordMetadata, e) -> {
          if (e != null) {
            e.printStackTrace();
          }
        });
      }
    }
  }

  public Seguimiento getSeguimiento(Proyecto proyecto)
      throws IOException {
    var url = baseUrl + proyecto.getReferencia();
    try {
      Seguimiento seguimiento = new Seguimiento();
      var doc = Jsoup.connect(url).get();
      var scripts = doc.head().getElementsByTag("script");
      if (scripts.size() != 2) {
        LOG.error("Unexpected number of tables url={}", url);
        throw new IllegalStateException("Unexpected number of tables");
      }
      var linkScript = Arrays.stream(scripts.get(1).html().split("\\n"))
          .filter(s -> s.strip().startsWith("var url="))
          .findFirst();
      var expedienteBaseUrl = linkScript
          .map(s -> s.substring(s.indexOf("\"") + 1, s.lastIndexOf("\"")));
      var tables = doc.body().getElementsByTag("table");
      if (tables.size() != 2) {
        LOG.error("Unexpected number of tables url={}", url);
        throw new IllegalStateException("Unexpected number of tables");
      }
      var a = tables.get(0).getElementsByTag("a").first();
      if (a != null) {
        var onclick = a.attr("onclick");
        var expedienteParam = onclick.substring(onclick.indexOf("'"), onclick.lastIndexOf("'"));
        expedienteBaseUrl
            .ifPresent(s -> seguimiento.setEnlaceExpedienteDigital(s + expedienteParam));
      }
      var payload = tables.get(1);
      payload.getElementsByTag("tr")
          .forEach(tr -> {
            var tds = tr.getElementsByTag("td");
            var field = tds.get(0).text();
            switch (field) {
              case "Período:" -> seguimiento.setPeriodo(tds.get(1).text());
              case "Legislatura:" -> seguimiento.setLegislatura(tds.get(1).text());
              case "Fecha Presentación:" -> seguimiento.setPresentacionLocalDate(
                  parseDate(tds.get(1)));
              case "Número:" -> seguimiento.setNumero(tds.get(1).text());
              case "Proponente:" -> seguimiento.setProponente(tds.get(1).text());
              case "Grupo Parlamentario:" -> seguimiento
                  .setGrupoParlamentario(tds.get(1).text());
              case "Título:" -> seguimiento.setTitulo(tds.get(1).text());
              case "Sumilla:" -> seguimiento.setSumilla(tds.get(1).text());
              case "Autores (*):", "Autores (***)" -> seguimiento
                  .setAutores(parseCongresistasAutores(tds.get(1)));
              case "Adherentes(**):" -> seguimiento
                  .setAdherentes(parseCongresistasAdherentes(tds.get(1)));
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
      LOG.error(
          "Error procesando proyecto {} referencia {}",
          proyecto.getNumero(), url);
      throw e;
    }
  }

  private List<Congresista> parseCongresistasAutores(Element element) {
    return
        element.getElementsByTag("a").stream()
            .map(a -> {
              String email = a.attr("href");
              String nombreCompleto = a.text();
              return new Congresista(email, nombreCompleto);
            })
            .collect(Collectors.toList());
  }

  private List<String> parseCongresistasAdherentes(Element element) {
    return Arrays.asList(element.text().split(","));
  }

  private LocalDate parseDate(Element td) {
    return LocalDate.parse(td.text(), DateTimeFormatter.ofPattern("MM/dd/yyyy"));
  }
}
