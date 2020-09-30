package congreso.leyes.exportador;

import static java.lang.Thread.sleep;

import com.typesafe.config.ConfigFactory;
import congreso.leyes.Proyecto.ProyectoLey;
import congreso.leyes.internal.ProyectoIdSerde;
import congreso.leyes.internal.ProyectoLeySerde;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.stream.Collectors;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology.AutoOffsetReset;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.Stores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExportadorHugo {

  static final Logger LOG = LoggerFactory.getLogger(ExportadorCsv.class);

  public static void main(String[] args) throws InterruptedException {

    var config = ConfigFactory.load();

    var kafkaBootstrapServers = config.getString("kafka.bootstrap-servers");
    var topic = config.getString("kafka.topics.expediente-importado");

    LOG.info("Cargando proyectos importados");

    var builder = new StreamsBuilder();
    builder.globalTable(topic,
        Consumed.with(new ProyectoIdSerde(), new ProyectoLeySerde())
            .withOffsetResetPolicy(AutoOffsetReset.EARLIEST),
        Materialized.as(Stores.persistentKeyValueStore("proyectos")));

    var streamsConfig = new Properties();
    streamsConfig.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
    streamsConfig.put(StreamsConfig.APPLICATION_ID_CONFIG,
        config.getString("kafka.consumer-groups.exportador-hugo"));
    var streamsOverrides = config.getConfig("kafka.streams").entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().unwrapped()));
    streamsConfig.putAll(streamsOverrides);

    var kafkaStreams = new KafkaStreams(builder.build(), streamsConfig);
    kafkaStreams.start();

    while (!kafkaStreams.state().isRunningOrRebalancing()) {
      LOG.info("Esperando por streams a cargar...");
      sleep(Duration.ofSeconds(1).toMillis());
    }

    var proyectoRepositorio = kafkaStreams.store(
        StoreQueryParameters.fromNameAndType("proyectos", QueryableStoreTypes.keyValueStore()));

    var baseDir = "content/proyectos-ley";
    try (var iter = proyectoRepositorio.all()) {
      while (iter.hasNext()) {
        var keyValue = iter.next();
        var proyectoLey = (ProyectoLey) keyValue.value;
        var numeroPeriodo = proyectoLey.getId().getNumeroPeriodo();
        var grupo = proyectoLey.getId().getNumeroGrupo();
        var dir =
            baseDir + "/" + proyectoLey.getId().getPeriodo() + "/" + grupo;
        Files.createDirectories(Paths.get(dir));
        var rutaTexto = dir + "/" + numeroPeriodo + ".md";
        Path ruta = Paths.get(rutaTexto);
        Files.deleteIfExists(ruta);
        Files.createFile(ruta);
        var pagina = crearPagina(proyectoLey);
        Files.writeString(ruta, pagina);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    kafkaStreams.close();
  }


  static String crearPagina(ProyectoLey proyectoLey) {
    var titulo = proyectoLey.getDetalle().getTitulo();
    var quote = "\"";
    var header = "---" + "\n"
        + "title: " + quote + titulo + quote + "\n"
        + "date: " + fecha(proyectoLey.getFechaPublicacion()) + "\n"
        + (proyectoLey.hasFechaActualizacion() ? "lastmod: " + fecha(
        proyectoLey.getFechaActualizacion().getValue()) + "\n" : "")
        + "estados: \n  - " + proyectoLey.getEstado() + "\n"
        + "proponentes: \n  - " + proyectoLey.getDetalle().getProponente() + "\n"
        + "grupos: \n  - " + proyectoLey.getDetalle().getGrupoParlamentario() + "\n"
        + "autores: \n  - " + String.join("\n  - ", proyectoLey.getDetalle().getAutorList()) + "\n"
        + "adherentes: \n  - " + String.join("\n  - ", proyectoLey.getDetalle().getAdherenteList())
        + "\n"
        + "sectores: \n  - " + String.join("\n  - ", proyectoLey.getDetalle().getSectorList())
        + "\n"
        + "periodos: \n  - " + proyectoLey.getId().getPeriodo() + "\n"
        + "---" + "\n\n";
    var body = new StringBuilder();
    // Metadata
    body.append("- **Periodo**: ").append(proyectoLey.getId().getPeriodo()).append("\n");
    body.append("- **Legislatura**: ").append(proyectoLey.getDetalle().getLegislatura())
        .append("\n");
    body.append("- **Número**: ").append(proyectoLey.getDetalle().getNumeroUnico()).append("\n");
    if (proyectoLey.getDetalle().getIniciativaAgrupadaCount() > 0) {
      body.append("- **Iniciativas agrupadas**: ")
          .append(proyectoLey.getDetalle().getIniciativaAgrupadaList()
              .stream()
              .map(num -> "[" + num + "](../../" + grupo(num) + "/" + num + ")")
              .collect(Collectors.joining(", ")))
          .append("\n");
    }
    body.append("- **Estado**: ").append(proyectoLey.getEstado()).append("\n");
    if (proyectoLey.getDetalle().hasSumilla()) {
      body.append("\n").append("> ").append(proyectoLey.getDetalle().getSumilla().getValue())
          .append("\n\n");
    }

    // Congresistas
    body.append("""

        ## Actores

        """);
    body.append("### Proponente\n\n")
        .append("**").append(proyectoLey.getDetalle().getProponente()).append("**")
        .append("\n\n");
    if (proyectoLey.getDetalle().getGrupoParlamentario() != null &&
        !proyectoLey.getDetalle().getGrupoParlamentario().isBlank()) {
      body.append("### Grupo Parlamentario\n\n")
          .append("**").append(proyectoLey.getDetalle().getGrupoParlamentario()).append("**")
          .append("\n\n");
    }
    if (!proyectoLey.getDetalle().getAutorList().isEmpty()) {
      body.append("### Autores\n\n")
          .append(String.join("; ", proyectoLey.getDetalle().getAutorList()))
          .append("\n\n");
    }
    if (!proyectoLey.getDetalle().getAdherenteList().isEmpty()) {
      body.append("### Adherentes\n\n")
          .append(String.join("; ", proyectoLey.getDetalle().getAdherenteList()))
          .append("\n\n");
    }

    // Opiniones
    body.append("""

        ## Opiniones

        """);

    if (proyectoLey.getEnlaces().getOpinionesPublicadas() != null) {
      body.append("### Opiniones publicadas").append("\n\n");
      body.append("{{<iframe \"").append(proyectoLey.getEnlaces().getOpinionesPublicadas())
          .append("\" \"Opiniones publicadas\" >}}\n");
      body.append("\n[Enlace](")
          .append(proyectoLey.getEnlaces().getOpinionesPublicadas()).append(")\n");
    }
    if (proyectoLey.getEnlaces().hasPublicarOpinion()) {
      body.append("### Publicar opinión").append("\n\n");
      body.append("{{< iframe \"").append(proyectoLey.getEnlaces().getPublicarOpinion().getValue())
          .append("\" \"Brindar opinión\" >}}\n");
      body.append("\n[Enlace](").append(proyectoLey.getEnlaces().getPublicarOpinion().getValue())
          .append(")\n");
    }

    // Seguimiento
    body.append("""

        ## Seguimiento

        | Fecha | Evento |
        |------:|--------|
        """);
    for (var seguimiento : proyectoLey.getSeguimientoList()) {
      body.append("| **").append(fecha(seguimiento.getFecha())).append("** | ")
          .append(seguimiento.getTexto()).append("|\n");
    }
    body.append("\n");

    // Ley
    if (proyectoLey.hasLey() && !proyectoLey.getLey().getNumero().isBlank()) {
      body.append("## ").append(proyectoLey.getLey().getNumero())
          .append("\n\n")
          .append("**\"").append(proyectoLey.getLey().getTitulo()).append("\"**")
          .append("\n\n")
          .append("> ").append(proyectoLey.getLey().getSumilla().getValue())
          .append("\n\n")
      ;
    }

    // Expediente
    if (proyectoLey.hasExpediente()) {
      body.append("\n")
          .append("## Expediente")
          .append("\n\n")
//          .append("**").append(proyectoLey.getExpediente().getTitulo()).append("**")
//          .append("\n\n")
      ;
//      if (proyectoLey.getExpediente().hasSubtitulo()) {
//        body
//          .append("> ").append(proyectoLey.getExpediente().getSubtitulo().getValue())
//            .append("\n\n");
//      }
      if (proyectoLey.getExpediente().getResultadoCount() > 0) {
        body.append("""
                        
            ### Documentos resultado
                        
            | Fecha | Documento |
            |------:|--------|
            """);

        for (var doc : proyectoLey.getExpediente().getResultadoList()) {
          body.append("| **").append(doc.hasFecha() ? fecha(doc.getFecha().getValue()) : "")
              .append("** | [")
              .append(doc.getTitulo()).append("](")
              .append(doc.getUrl()).append(") |")
              .append("\n");
        }
      }

      if (proyectoLey.getExpediente().getProyectoCount() > 0) {
        body.append("""
                        
            ### Documentos del Proyecto de Ley
                        
            | Fecha | Documento |
            |------:|--------|
            """);

        for (var doc : proyectoLey.getExpediente().getProyectoList()) {
          body.append("| **").append(doc.hasFecha() ? fecha(doc.getFecha().getValue()) : "")
              .append("** | [")
              .append(doc.getTitulo()).append("](")
              .append(doc.getUrl()).append(") |")
              .append("\n");
        }
      }
      if (proyectoLey.getExpediente().getAnexoCount() > 0) {
        body.append("""
                        
            ### Documentos de Anexo 
                        
            | Fecha | Documento |
            |------:|--------|
            """);

        for (var doc : proyectoLey.getExpediente().getAnexoList()) {
          body.append("| **").append(doc.hasFecha() ? fecha(doc.getFecha().getValue()) : "")
              .append("** | [")
              .append(doc.getTitulo()).append("](")
              .append(doc.getUrl()).append(") |")
              .append("\n");
        }
      }

      // Enlaces
      body.append("\n## Enlaces ")
          .append("\n\n");
      if (proyectoLey.getEnlaces().getSeguimiento() != null) {
        body.append("- [Seguimiento](")
            .append(proyectoLey.getEnlaces().getSeguimiento())
            .append(")\n");
      }
      if (proyectoLey.getEnlaces().getExpediente() != null) {
        body.append("- [Expediente Digital](")
            .append(proyectoLey.getEnlaces().getExpediente().getValue())
            .append(")\n");
      }
    }
    return header + body;
  }

  private static String grupo(String num) {
    var i = (Integer.parseInt(num) / 100) * 100;
    return String.format("%05d", i);
  }

  static String fecha(long fecha) {
    return OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(fecha),
        ZoneOffset.ofHours(-5))
        .toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
  }
}
