package congreso.leyes.importador;

import com.typesafe.config.ConfigFactory;
import congreso.leyes.Proyecto;
import congreso.leyes.Proyecto.ProyectoLey;
import congreso.leyes.Proyecto.ProyectoLey.Expediente.Documento;
import congreso.leyes.Proyecto.ProyectoLey.Id;
import congreso.leyes.internal.ProyectoIdSerde;
import congreso.leyes.internal.ProyectoLeySerde;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

    var baseUrl = config.getString("importador.base-url");

    var importador = new ImportadorExpediente(baseUrl);

    var kafkaBootstrapServers = config.getString("kafka.bootstrap-servers");
    var inputTopic = config.getString("kafka.topics.seguimiento-importado");
    var outputTopic = config.getString("kafka.topics.expediente-importado");

    var streamsBuilder = new StreamsBuilder();
    streamsBuilder.addStateStore(Stores.keyValueStoreBuilder(
        Stores.persistentKeyValueStore("proyectos"),
        new ProyectoIdSerde(),
        new ProyectoLeySerde()
    ));

    streamsBuilder
        .stream(inputTopic, Consumed.with(new ProyectoIdSerde(), new ProyectoLeySerde()))
        .mapValues(importador::importarExpediente)
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
              LOG.info("Proyecto actualizado: {}", proyectoLey);
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
    var groupId = config.getString("kafka.consumer-groups.importador-expediente");
    streamsConfig.put(StreamsConfig.APPLICATION_ID_CONFIG, groupId);
    var overrides = config.getConfig("kafka.streams").entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().unwrapped()));
    streamsConfig.putAll(overrides);
    var kafkaStreams = new KafkaStreams(streamsBuilder.build(), streamsConfig);

    Runtime.getRuntime().addShutdownHook(new Thread(kafkaStreams::close));

    LOG.info("Iniciando importacion de expedientes");

    kafkaStreams.start();
  }

  ProyectoLey importarExpediente(ProyectoLey seguimiento) {
    if (seguimiento.getEnlaces().getExpediente() == null) {
      LOG.info("Seguimiento {}-{} no tiene enlace para expediente",
          seguimiento.getDetalle().getNumeroUnico(),
          seguimiento.getDetalle().getTitulo());
      return null;
    }
    var url = baseUrl + seguimiento.getEnlaces().getExpediente();
    try {
      var expediente = seguimiento.toBuilder();
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
        return null;
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
      var builder = expediente.getExpediente().toBuilder();
      if (!headers.isEmpty()) {
        builder.addTitulo(headers.get(0).text());
      }
      if (headers.size() > 1) {
        var titulo = headers.get(1).text();
        builder.addTitulo(titulo);
      }
      //extrayendo documentos
      var expedienteTablas = contenidoExperiente.first().getElementsByTag("table");
      if (expedienteTablas.size() == 3) { //cuando contiene docs de ley
        var leyTable = expedienteTablas.first();
        var docsLey = leerDocumentos(leyTable);
        builder.addAllResultado(docsLey);

        var proyectoLeyTable = expedienteTablas.get(1);
        var docsProyecto = leerDocumentos(proyectoLeyTable);
        builder.addAllProyecto(docsProyecto);

        var anexosTable = expedienteTablas.get(2);
        var anexos = leerDocumentos(anexosTable);
        builder.addAllAnexo(anexos);
      }

      if (expedienteTablas.size() == 2) { //cuando solo contiene proyecto y anexos
        var proyectoLeyTable = expedienteTablas.get(0);
        var docsProyecto = leerDocumentos(proyectoLeyTable);
        builder.addAllProyecto(docsProyecto);

        var anexosTable = expedienteTablas.get(1);
        var anexos = leerDocumentos(anexosTable);
        builder.addAllAnexo(anexos);
      }

      if (expedienteTablas.size() == 1) { //cuando solo contiene docs de proyecto
        var proyectoLeyTable = expedienteTablas.get(0);
        var docsProyecto = leerDocumentos(proyectoLeyTable);
        builder.addAllProyecto(docsProyecto);
      }
      //extrayendo opiniones
      var expedienteOpiniones = contenido.get(1).select("table[width=100]");
      if (expedienteOpiniones.size() == 2) {
        var presentarOpinionUrl = leerEnlacePresentarOpinion(doc, expedienteOpiniones.get(0));
        expediente.getEnlacesBuilder().setPublicarOpinion(presentarOpinionUrl);
        var opinionesUrl = leerEnlaceOpinionesPresentadas(doc);
        expediente.getEnlacesBuilder().setOpinionesPublicadas(opinionesUrl);
      }
      if (expedienteOpiniones.size() == 1) {
        var opinionesUrl = leerEnlaceOpinionesPresentadas(doc);
        expediente.getEnlacesBuilder().setOpinionesPublicadas(opinionesUrl);
      }

      return expediente.build();
    } catch (Throwable e) {
      LOG.error("Error procesando expediente {}", url, e);
      throw new IllegalStateException("Error procesando expediente", e);
    }
  }

  private String leerEnlaceOpinionesPresentadas(Document doc) {
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

  private String leerEnlacePresentarOpinion(Document doc, Element opinionTable) {
    var onclick = opinionTable.getElementsByTag("a").attr("onclick");
    var variableRuta = onclick.indexOf("ruta3 =") + 7;
    var urlPrefix = onclick.substring(variableRuta, onclick.indexOf(";", variableRuta));
    var urlPattern = urlPrefix
        .substring(urlPrefix.indexOf("\"") + 1, urlPrefix.lastIndexOf("\""));
    var idElement = doc.select("input[name=IdO]");
    var variable = idElement.first().attr("value");
    return urlPattern.replace("\"+ids+\"", variable);
  }

  private List<Proyecto.ProyectoLey.Expediente.Documento> leerDocumentos(Element table) {
    try {
      var rows = table.getElementsByTag("tr");
      var th = rows.first().getElementsByTag("th");
      var td = rows.first().getElementsByTag("td");
      var headers = rows.first().getElementsByTag("b");
      if (th.size() == 3 || headers.size() == 5 || td.size() == 3) { //extraer documentos de ley
        var docs = new ArrayList<Documento>();
        for (int i = 1; i < rows.size(); i++) {
          var row = rows.get(i);
          var values = row.getElementsByTag("td");
          if (values.size() == 3) {
            var numeroProyecto = values.get(0).text();
            var element = values.get(2);
            var nombreDocumento = element.text();
            var referenciaDocumento = element.getElementsByTag("a").attr("href");
            var doc = Documento.newBuilder()
                .setFecha(parseDate(values.get(1)))
                .setTitulo(nombreDocumento)
                .setProyecto(numeroProyecto)
                .setUrl(referenciaDocumento)
                .build();
            docs.add(doc);
          } else if (values.size() == 1) {
            var element = values.get(0);
            var referenciaDocumento = element.getElementsByTag("a").attr("href");
            var doc = Documento.newBuilder()
                .setUrl(referenciaDocumento)
                .build();
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
          var doc = Documento.newBuilder()
              .setFecha(parseDate(values.get(0)))
              .setTitulo(nombreDocumento)
              .setUrl(referenciaDocumento)
              .build();
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
          var doc = Documento.newBuilder()
              .setFecha(parseDate(values.get(0)))
              .setTitulo(nombreDocumento)
              .setUrl(referenciaDocumento)
              .build();
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
      throw new IllegalStateException("Error obteniendo documentos", e);
    }
  }

  private Long parseDate(Element td) {
    if (td.text().isBlank()) {
      LOG.error("Fecha vacia! {}", td.html());
      return null;
    }
    //agregar cualquier condicion para arreglar inconsistencias en fechas
    if (td.text().length() == 10) {
      return LocalDate.parse(td.text(),
          DateTimeFormatter.ofPattern("dd/MM/yyyy"))
          .atStartOfDay()
          .toInstant(ZoneOffset.ofHours(-5))
          .toEpochMilli();
    } else {
      if (td.text().length() == 8) {
        return LocalDate.parse(td.text()
                .replaceAll("\\s+", "")
                .replaceAll("-", "")
                .replaceAll("\\+", "")
                .replaceAll("//", "/")
                .replaceAll("02/15/19", "15/02/19")
                .replaceAll("20/0708", "20/07/18")
            ,
            DateTimeFormatter.ofPattern("dd/MM/yy"))
            .atStartOfDay()
            .toInstant(ZoneOffset.ofHours(-5))
            .toEpochMilli();
      } else {
        return LocalDate.parse(td.text()
                .replaceAll("\\s+", "")
                .replaceAll("-", "")
                .replaceAll("\\+", "")
                .replaceAll("//", "/")
                .replaceAll("011", "11")
                .replaceAll("119", "19")
                .replaceAll("240", "24")
                .replaceAll("178", "18")
                .replaceAll("187", "18")
                .replaceAll("182", "18")
                .replaceAll("0520", "05/20")
                .replaceAll("5/04/19", "05/04/19")
                .replaceAll("0719", "07/19")
                .replaceAll("0617", "06/17")
                .replaceAll("1710", "17/10")
                .replaceAll("1018", "10/18")
                .replaceAll("0208", "02/08")
                .replaceAll("1907", "19/07")
                .replaceAll("23/03/18/", "23/03/18")
                .replaceAll("02/15/19", "15/02/19")
                .replaceAll("21/5/20", "21/05/20")
                .replaceAll("20/0708", "20/07/18")
            ,
            DateTimeFormatter.ofPattern("dd/MM/yy"))
            .atStartOfDay()
            .toInstant(ZoneOffset.ofHours(-5))
            .toEpochMilli();
      }
    }
  }
}
