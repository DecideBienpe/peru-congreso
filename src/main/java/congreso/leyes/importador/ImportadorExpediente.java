package congreso.leyes.importador;

import com.typesafe.config.ConfigFactory;
import congreso.leyes.Documento;
import congreso.leyes.Expediente;
import congreso.leyes.Seguimiento;
import congreso.leyes.internal.ExpedienteSerde;
import congreso.leyes.internal.SeguimientoSerde;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

  public static void main(String[] args) {
    var config = ConfigFactory.load();

    var consumerConfig = new Properties();
    var kafkaBootstrapServers = config.getString("kafka.bootstrap-servers");
    consumerConfig.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
    var groupId = config.getString("kafka.consumer-groups.importador-expediente");
    consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    var overrides = config.getConfig("kafka.consumer").entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().unwrapped()));
    consumerConfig.putAll(overrides);
    var keyDeserializer = new StringDeserializer();
    var valueDeserializer = new SeguimientoSerde().deserializer();
    var consumer = new KafkaConsumer<>(consumerConfig, keyDeserializer, valueDeserializer);
    consumer.subscribe(List.of(config.getString("kafka.topics.seguimiento-importado")));

    var producerConfig = new Properties();
    producerConfig.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
    var producerOverrides = config.getConfig("kafka.producer").entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().unwrapped()));
    producerConfig.putAll(producerOverrides);
    var keySerializer = new StringSerializer();
    var valueSerializer = new ExpedienteSerde().serializer();
    var producer = new KafkaProducer<>(producerConfig, keySerializer, valueSerializer);

    var baseUrl = config.getString("importador.base-url");

    var importador = new ImportadorExpediente(baseUrl);

    var topic = config.getString("kafka.topics.expediente-importado");

    while (!Thread.interrupted()) {
      var records = consumer.poll(Duration.ofSeconds(5));

      LOG.info("Seguimientos recibidos {}", records.count());

      if (records.isEmpty()) {
        LOG.info("Cerrando importador ya que no hay mas seguimientos disponibles");
        producer.close();
        consumer.close();
        Runtime.getRuntime().exit(0);
      }

      for (var record : records) {
        var expediente = importador.getExpediente(record.value());

        var recordExpediente = new ProducerRecord<>(topic, record.key(), expediente);
        producer.send(recordExpediente, (recordMetadata, e) -> {
          if (e != null) {
            LOG.error("Error guardando expediente {}", expediente, e);
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

  Expediente getExpediente(Seguimiento seguimiento) {
    if (seguimiento.getEnlaceExpedienteDigital() == null) {
      LOG.info("Seguimiento {}-{} no tiene enlace para expediente",
          seguimiento.getNumero(),
          seguimiento.getTitulo());
      return null;
    }
    var url = baseUrl + seguimiento.getEnlaceExpedienteDigital();
    try {
      var expediente = new Expediente();
      var doc = Jsoup.connect(url).get();
      var scripts = doc.head().getElementsByTag("script");
      if (scripts.size() != 2) {
        LOG.error("Numero inesperado de scripts {}, url={}, html={}",
            scripts.size(), url, doc.html());
        throw new IllegalStateException("Numero inesperado de scripts");
      }
      var tablas = doc.body().select("table[width=500]");
      if (tablas.size() != 1) {
        LOG.error("Numero inesperado de tablas {}, url={}", tablas.size(), url);
        throw new IllegalStateException("Numero inesperado de tablas");
      }
      //Ubicar contenido
      var contenido = tablas
          .first().children()
          .first().children()
          .get(1).children();
      var contenidoExperiente = contenido
          .first().children()
          .first().children()
          .first().children()
          .first().children();
      //extrayendo titulos
      var headers = contenidoExperiente.first().getElementsByTag("div")
          .first().children()
          .first().getElementsByTag("b");
      if (!headers.isEmpty()) {
        expediente.setTitulo1(headers.get(0).text());
      }
      if (headers.size() > 1) {
        var titulo = headers.get(1).text();
        expediente.setTitulo2(titulo);
      }
      //extrayendo documentos
      var expedienteTablas = contenidoExperiente.first().getElementsByTag("table");
      if (expedienteTablas.size() == 3) { //cuando contiene docs de ley
        var leyTable = expedienteTablas.first();
        var docsLey = getDocumentos(leyTable);
        expediente.setDocumentosLey(docsLey);

        var proyectoLeyTable = expedienteTablas.get(1);
        var docsProyecto = getDocumentos(proyectoLeyTable);
        expediente.setDocumentosProyectosLey(docsProyecto);

        var anexosTable = expedienteTablas.get(2);
        var anexos = getDocumentos(anexosTable);
        expediente.setDocumentosAnexos(anexos);
      }

      if (expedienteTablas.size() == 2) { //cuando solo contiene proyecto y anexos
        var proyectoLeyTable = expedienteTablas.get(0);
        var docsProyecto = getDocumentos(proyectoLeyTable);
        expediente.setDocumentosProyectosLey(docsProyecto);

        var anexosTable = expedienteTablas.get(1);
        var anexos = getDocumentos(anexosTable);
        expediente.setDocumentosAnexos(anexos);
      }

      if (expedienteTablas.size() == 1) { //cuando solo contiene docs de proyecto
        var proyectoLeyTable = expedienteTablas.get(0);
        var docsProyecto = getDocumentos(proyectoLeyTable);
        expediente.setDocumentosProyectosLey(docsProyecto);
      }
      //extrayendo opiniones
      var expedienteOpiniones = contenido.get(1).select("table[width=100]");
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
      LOG.error("Error procesando expediente {}", url, e);
      throw new IllegalStateException("Error procesando expediente");
    }
  }

  private String getEnlaceOpinionesPresentadas(Document doc) {
    var scripts = doc.head().getElementsByTag("script");
    var html = scripts.get(0).html();
    var enlace = Arrays.stream(html.split("\\r"))
        .filter(s -> s.strip().startsWith("window.open"))
        .findFirst()
        .map(l -> l.substring(l.indexOf("(") + 1, l.lastIndexOf(")")))
        .map(l -> l.split(",")[0])
        .map(urlPrefix -> {
          var urlPattern = urlPrefix
              .substring(urlPrefix.indexOf("\"") + 1, urlPrefix.lastIndexOf("\""));
          var idElement = doc.select("input[name=IdO]");
          var variable = idElement.first().attr("value");
          return urlPattern.replace("\" + num + \"", variable);
        });
    if (enlace.isEmpty()) {
      LOG.warn("Enlace de opiniones presentadas no ha sido encontrado {}", html);
      return null;
    } else {
      return enlace.get();
    }
  }

  private String getEnlacePresentarOpinion(Document doc, Element opinionTable) {
    var onclick = opinionTable.getElementsByTag("a").attr("onclick");
    var variableRuta = onclick.indexOf("ruta3 =") + 7;
    var urlPrefix = onclick.substring(variableRuta, onclick.indexOf(";", variableRuta));
    var urlPattern = urlPrefix
        .substring(urlPrefix.indexOf("\"") + 1, urlPrefix.lastIndexOf("\""));
    var idElement = doc.select("input[name=IdO]");
    var variable = idElement.first().attr("value");
    return urlPattern.replace("\"+ids+\"", variable);
  }

  private List<Documento> getDocumentos(Element table) {
    try {
      var rows = table.getElementsByTag("tr");
      var th = rows.first().getElementsByTag("th");
      var headers = rows.first().getElementsByTag("b");
      if (th.size() == 3 || headers.size() == 5) { //extraer documentos de ley
        var docs = new ArrayList<Documento>();
        for (int i = 1; i < rows.size(); i++) {
          var row = rows.get(i);
          var values = row.getElementsByTag("td");
          if (values.size() == 3) {
            var numeroProyecto = values.get(0).text();
            var element = values.get(2);
            var nombreDocumento = element.text();
            var referenciaDocumento = element.getElementsByTag("a").attr("href");
            var doc = new Documento(parseDate(values.get(1)), nombreDocumento, numeroProyecto,
                referenciaDocumento);
            docs.add(doc);
          } else if (values.size() == 1) {
            var element = values.get(0);
            var referenciaDocumento = element.getElementsByTag("a").attr("href");
            var doc = new Documento(null, null, null, referenciaDocumento);
            docs.add(doc);
          } else {
            LOG.warn("Numero de columnas no esperado {}", values.size());
          }
        }
        return docs;
      } else if (th.size() == 2 || headers.size() == 2) { //extraer documentos de proyecto
        var docs = new ArrayList<Documento>();
        for (int i = 1; i < rows.size(); i++) {
          var row = rows.get(i);
          var values = row.getElementsByTag("td");
          var element = values.get(1);
          var nombreDocumento = element.text();
          var referenciaDocumento = element.getElementsByTag("a").attr("href");
          var doc = new Documento(parseDate(values.get(0)), nombreDocumento, referenciaDocumento);
          docs.add(doc);
        }
        return docs;
      } else if (th.size() == 0) { //extraer documentos de anexos
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
          var doc = new Documento(parseDate(values.get(0)), nombreDocumento, referenciaDocumento);
          docs.add(doc);
        }
        return docs;
      } else {
        LOG.error("Numero de columnas {} y cabeceras {} no es esperado. \n {}",
            th.size(), headers.size(), table.html());
        throw new IllegalStateException("Numero de cabeceras de documentos inespeado");
      }
    } catch (Throwable e) {
      LOG.error("Error obteniendo documentos {}", table.html(), e);
      throw new IllegalStateException("Error obteniendo documentos");
    }
  }

  private LocalDate parseDate(Element td) {
    if (td.text().isBlank()) {
      return null;
    }
    //agregar cualquier condicion para arreglar inconsistencias en fechas
    if (td.text().length() == 10) {
      return LocalDate.parse(td.text(),
          DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    } else {
      return LocalDate.parse(td.text()
              .replaceAll("\\s+", "")
              .replaceAll("011", "11")
              .replaceAll("119", "19")
              .replaceAll("240", "24")
              .replaceAll("178", "18")
              .replaceAll("187", "18")
              .replaceAll("182", "18")
              .replaceAll("0708", "07/18")
              .replaceAll("0617", "06/17")
              .replaceAll("1710", "17/10")
              .replaceAll("1018", "10/18")
              .replaceAll("0208", "02/08")
              .replaceAll("1907", "19/07")
              .replaceAll("23/03/18/", "23/03/18")
              .replaceAll("-", "")
              .replaceAll("\\+", "")
              .replaceAll("//", "/"),
          DateTimeFormatter.ofPattern("dd/MM/yy"));
    }
  }
}
