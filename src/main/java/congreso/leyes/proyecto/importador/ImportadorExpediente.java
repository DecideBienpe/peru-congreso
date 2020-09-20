package congreso.leyes.proyecto.importador;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.ConfigFactory;
import congreso.leyes.proyecto.Documento;
import congreso.leyes.proyecto.Expediente;
import congreso.leyes.proyecto.Seguimiento;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportadorExpediente {

  static final Logger LOG = LoggerFactory.getLogger(ImportadorExpediente.class);

  final String baseUrl;

  public ImportadorExpediente(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public static void main(String[] args) throws IOException {
    var consumerConfig = new Properties();
    consumerConfig.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, "congreso.leyes.proyecto-expediente-v1");
    consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    consumerConfig.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    var consumer = new KafkaConsumer<>(consumerConfig, new StringDeserializer(),
        new StringDeserializer());
    consumer.subscribe(List.of("congreso.leyes.seguimiento-importado-v1"));

    var objectMapper = new ObjectMapper();

    var producerConfig = new Properties();
    producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    var producer = new KafkaProducer<>(producerConfig, new StringSerializer(),
        new StringSerializer());

    var config = ConfigFactory.load();
    var baseUrl = config.getString("importador.base-url");

    var importador = new ImportadorExpediente(baseUrl);

    var topic = "congreso.leyes.expediente-importado-v1";

    while (!Thread.interrupted()) {
      var records = consumer.poll(Duration.ofSeconds(5));
      for (var record : records) {
        String value = record.value();
        var seguimiento = objectMapper.readValue(value, Seguimiento.class);
        var expediente = importador.getExpediente(seguimiento);

        var valueSeguimiento = objectMapper.writeValueAsString(expediente);
        var recordSeguimiento = new ProducerRecord<>(topic, seguimiento.getNumero(),
            valueSeguimiento);
        producer.send(recordSeguimiento, (recordMetadata, e) -> {
          if (e != null) {
            e.printStackTrace();
          }
        });
      }
    }
  }

  Expediente getExpediente(Seguimiento seguimiento) {
    if (seguimiento.getEnlaceExpedienteDigital() == null) {
      return null;
    }
    var url = baseUrl + seguimiento.getEnlaceExpedienteDigital();
    try {
      var expediente = new Expediente();
      var doc = Jsoup.connect(url).get();
      var scripts = doc.head().getElementsByTag("script");
      if (scripts.size() != 2) {
        throw new IllegalStateException("Unexpected number of tables");
      }
      var tables = doc.body().select("table[width=500]");
      if (tables.size() != 1) {
        LOG.error("Unexpected number of tables url={}", url);
        throw new IllegalStateException("Unexpected number of tables");
      }
      var payload = tables.first().children().first().children().get(1).children();
      var expedienteContent = payload.first().children().first().children().first().children()
          .first().children();

      var headers = expedienteContent.first().getElementsByTag("div").first().children().first()
          .getElementsByTag("b");
      var numeroTexto = headers.get(0).text();
      expediente.setTitulo1(numeroTexto);
      if (headers.size() > 1) {
        var titulo = headers.get(1).text();
        expediente.setTitulo2(titulo);
      }
      var expedienteTables = expedienteContent.first().getElementsByTag("table");

      if (expedienteTables.size() == 3) { //contiene docs de ley
        var leyTable = expedienteTables.first();
        var docsLey = getDocumentos(leyTable);
        expediente.setDocumentosLey(docsLey);

        var proyectoLeyTable = expedienteTables.get(1);
        var docsProyecto = getDocumentos(proyectoLeyTable);
        expediente.setDocumentosProyectosLey(docsProyecto);

        var anexosTable = expedienteTables.get(2);
        var anexos = getDocumentos(anexosTable);
        expediente.setDocumentosAnexos(anexos);
      }

      if (expedienteTables.size() == 2) {
        var proyectoLeyTable = expedienteTables.get(0);
        var docsProyecto = getDocumentos(proyectoLeyTable);
        expediente.setDocumentosProyectosLey(docsProyecto);

        var anexosTable = expedienteTables.get(1);
        var anexos = getDocumentos(anexosTable);
        expediente.setDocumentosAnexos(anexos);
      }

      if (expedienteTables.size() == 1) {
        var proyectoLeyTable = expedienteTables.get(0);
        var docsProyecto = getDocumentos(proyectoLeyTable);
        expediente.setDocumentosProyectosLey(docsProyecto);
      }

      var expedienteOpiniones = payload.get(1).select("table[width=100]");

      if (expedienteOpiniones.size() == 2) {
        var presentarOpinionUrl = getEnlacePresentarOpinion(doc, expedienteOpiniones.get(0));
        expediente.setEnlacePresentarOpinion(presentarOpinionUrl);
        var opinionesUrl = getEnlaceOpinionesPresentadas(doc);
        expediente.setEnlaceOpinionesRecibidos(opinionesUrl);
      }
      if (expedienteOpiniones.size() == 1) {
        var opinionesUrl = getEnlaceOpinionesPresentadas(doc);
        expediente.setEnlaceOpinionesRecibidos(opinionesUrl);
      }

      return expediente;
    } catch (Throwable e) {
      e.printStackTrace();
      return null;
    }
  }


  private LocalDate parseDate2(Element td) {
    if (td.text().isBlank()) {
      return null;
    }
    return LocalDate.parse(td.text()
            .replaceAll("\\s+", "")
            .replaceAll("\\+", "")
            .replaceAll("//", "/"),
        DateTimeFormatter.ofPattern("dd/MM/yy"));
  }

  private String getEnlaceOpinionesPresentadas(Document doc) {
    var scripts = doc.head().getElementsByTag("script");
    var html = scripts.get(0).html();
    var linkScript = Arrays.stream(html.split("\\r"))
        .filter(s -> s.strip().startsWith("window.open"))
        .findFirst();
    var urlPatternPre = linkScript.map(l -> l.substring(l.indexOf("(") + 1, l.lastIndexOf(")")))
        .map(l -> l.split(",")[0]).get();
    var urlPattern = urlPatternPre
        .substring(urlPatternPre.indexOf("\"") + 1, urlPatternPre.lastIndexOf("\""));
    var idElement = doc.select("input[name=IdO]");
    var variable = idElement.first().attr("value");
    return urlPattern.replace("\" + num + \"", variable);
  }

  private String getEnlacePresentarOpinion(Document doc, Element opinionTable) {
    var onclick = opinionTable.getElementsByTag("a").attr("onclick");
    var i = onclick.indexOf("ruta3 =") + 7;
    var urlPatternPre = onclick.substring(i, onclick.indexOf(";", i));
    var urlPattern = urlPatternPre
        .substring(urlPatternPre.indexOf("\"") + 1, urlPatternPre.lastIndexOf("\""));
    var idElement = doc.select("input[name=IdO]");
    var variable = idElement.first().attr("value");
    return urlPattern.replace("\"+ids+\"", variable);
  }

  private List<Documento> getDocumentos(Element table) {
    try {
      var rows = table.getElementsByTag("tr");
      var th = rows.first().getElementsByTag("th");
      var headers = rows.first().getElementsByTag("b");
      if (th.size() == 3 || headers.size() == 5) {
        var docs = new ArrayList<Documento>();
        for (int i = 1; i < rows.size(); i++) {
          var row = rows.get(i);
          var values = row.getElementsByTag("td");
          var numeroProyecto = values.get(0).text();
          var element = values.get(2);
          var nombreDocumento = element.text();
          var referenciaDocumento = element.getElementsByTag("a").attr("href");
          var doc = new Documento(parseDate2(values.get(1)), nombreDocumento, numeroProyecto,
              referenciaDocumento);
          docs.add(doc);
        }
        return docs;
      } else if (th.size() == 2 || headers.size() == 2) {
        var docs = new ArrayList<Documento>();
        for (int i = 1; i < rows.size(); i++) {
          var row = rows.get(i);
          var values = row.getElementsByTag("td");
          var element = values.get(1);
          var nombreDocumento = element.text();
          var referenciaDocumento = element.getElementsByTag("a").attr("href");
          var doc = new Documento(parseDate2(values.get(0)), nombreDocumento, referenciaDocumento);
          docs.add(doc);
        }
        return docs;
      } else if (th.size() == 0) {
        var docs = new ArrayList<Documento>();
        var start = 0;
        if (headers.size() > 0) {
          start = 1;
        }
        for (int i = start; i < rows.size(); i++) {
          var row = rows.get(i);
          var values = row.getElementsByTag("td");
          var element = values.get(1);
          var nombreDocumento = element.text();
          var referenciaDocumento = element.getElementsByTag("a").attr("href");
          var doc = new Documento(parseDate2(values.get(0)), nombreDocumento, referenciaDocumento);
          docs.add(doc);
        }
        return docs;
      } else {
        LOG.error("Unexpected number of columns {}", table.html());
        return new ArrayList<>();
      }
    } catch (Throwable e) {
      LOG.error("Error getting docs {}", table.html());
      return new ArrayList<>();
    }
  }
}
