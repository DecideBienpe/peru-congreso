package congreso.leyes.exportador;

import static java.lang.Thread.sleep;

import com.typesafe.config.ConfigFactory;
import congreso.leyes.Proyecto.Congresista;
import congreso.leyes.Proyecto.ProyectoLey;
import congreso.leyes.Proyecto.ProyectoLey.Expediente.Documento;
import congreso.leyes.internal.ProyectoIdSerde;
import congreso.leyes.internal.ProyectoLeySerde;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
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
    var topicExpedientes = config.getString("kafka.topics.expediente-importado");

    LOG.info("Cargando proyectos importados");

    var builder = new StreamsBuilder();
    builder.globalTable(topicExpedientes,
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

    var proyectos = kafkaStreams.store(
        StoreQueryParameters.fromNameAndType("proyectos", QueryableStoreTypes.keyValueStore()));

    var congresistas = new HashMap<String, Set<Congresista>>();
    try (var iter = proyectos.all()) {
      while (iter.hasNext()) {
        var proyectoLey = (ProyectoLey) iter.next().value;
        var numeroPeriodo = proyectoLey.getId().getNumeroPeriodo();
        var grupo = proyectoLey.getId().getNumeroGrupo();
        var periodo = proyectoLey.getId().getPeriodo();
        var dir = "content/proyectos-ley" + "/" + periodo + "/" + grupo;
        Files.createDirectories(Paths.get(dir));
        var rutaTexto = dir + "/" + numeroPeriodo + ".md";
        var ruta = Paths.get(rutaTexto);
        Files.deleteIfExists(ruta);
        Files.createFile(ruta);
        var pagina = crearPagina(proyectoLey);
        Files.writeString(ruta, pagina);
        //actualizar lista de congresistas
        var congresistasPeriodo = congresistas.get(periodo);
        if (congresistasPeriodo == null) {
          congresistasPeriodo = new HashSet<>();
        }
        congresistasPeriodo.addAll(proyectoLey.getDetalle().getCongresistaList());
        congresistas.put(periodo, congresistasPeriodo);
      }
      congresistas.forEach((periodo, congresistasPeriodo) -> {
        try {
          var dir = "content/congresistas";
          Files.createDirectories(Paths.get(dir));
          var rutaTexto = dir + "/" + periodo + ".md";
          var ruta = Paths.get(rutaTexto);
          Files.deleteIfExists(ruta);
          Files.createFile(ruta);
          var pagina = paginaCongresistas(periodo, congresistasPeriodo);
          Files.writeString(ruta, pagina);
        } catch (IOException e) {
          LOG.error("Error procesando proyectos", e);
        }
      });
    } catch (IOException e) {
      LOG.error("Error procesando proyectos", e);
    }

    kafkaStreams.close();
  }

  private static String paginaCongresistas(String periodo, Set<Congresista> congresistasPeriodo) {
    var list = new ArrayList<>(congresistasPeriodo);
    list.sort(Comparator.comparing(Congresista::getNombreCompleto));
    var listaCongresistas = list.stream()
        .map(s -> String.format("- [%s](mailto:%s)", s.getNombreCompleto(), s.getEmail()))
        .collect(Collectors.joining("\n"));
    return String.format("""
            ---
            title: Directorio de Congresistas %s
            ---
            %s
            """,
        periodo,
        listaCongresistas);
  }


  static String crearPagina(ProyectoLey proyectoLey) {
    var header =
        String.format("""
                ---
                title: "%s"
                date: %s
                %s
                estados:
                - %s
                periodos:
                - %s
                proponentes:
                - %s
                grupos:
                - %s
                autores:
                %s
                sectores:
                %s
                ---
                """,
            proyectoLey.getDetalle().getTitulo().isBlank() ?
                proyectoLey.getExpediente().getSubtitulo().getValue().toUpperCase() :
                proyectoLey.getDetalle().getTitulo(),
            fecha(proyectoLey.getFechaPublicacion()),
            (proyectoLey.hasFechaActualizacion() ?
                "lastmod: " + fecha(proyectoLey.getFechaActualizacion().getValue()) :
                ""),
            proyectoLey.getEstado(),
            proyectoLey.getId().getPeriodo(),
            proyectoLey.getDetalle().getProponente(),
            proyectoLey.getDetalle().getGrupoParlamentario().getValue(),
            proyectoLey.getDetalle().getAutorList().stream()
                .map(s -> "- " + s)
                .collect(Collectors.joining("\n")),
            proyectoLey.getDetalle().getSectorList().stream()
                .map(s -> "- " + s)
                .collect(Collectors.joining("\n")));

    var body = new StringBuilder();
    // Metadata
    var metadata = String.format("""
            - **Periodo**: %s
            - **Legislatura**: %s
            - **Número**: %s
            - **Iniciativas agrupadas**: %s
            - **Estado**: %s
                    
            """,
        proyectoLey.getId().getPeriodo(),
        proyectoLey.getDetalle().getLegislatura(),
        proyectoLey.getDetalle().getNumeroUnico(),
        proyectoLey.getDetalle().getIniciativaAgrupadaList()
            .stream()
            .map(num -> "[" + num + "](../../" + grupo(num) + "/" + num + ")")
            .collect(Collectors.joining(", ")),
        proyectoLey.getEstado()
    );
    body.append(metadata);
    if (proyectoLey.getDetalle().hasSumilla()) {
      var sumilla = String.format("""
              > %s
                        
              """,
          proyectoLey.getDetalle().getSumilla().getValue());
      body.append(sumilla);
    }

    // Congresistas
    var emails = new HashSet<>(proyectoLey.getDetalle().getCongresistaList())
        .stream()
        .collect(Collectors.toMap(Congresista::getNombreCompleto, Congresista::getEmail));
    var actores = String.format("""

            ## Actores
                    
            ### Proponente
                    
            **%s**
                    
            """,
        proyectoLey.getDetalle().getProponente());
    body.append(actores);
    if (proyectoLey.getDetalle().hasGrupoParlamentario()) {
      var grupo = String.format("""
              ### Grupo Parlamentario
                        
              **%s**
                        
              """,
          proyectoLey.getDetalle().getGrupoParlamentario().getValue());
      body.append(grupo);
    }
    if (!proyectoLey.getDetalle().getAutorList().isEmpty()) {
      var autores = String.format("""
              ### Autores
                        
              %s
                        
              """,
          proyectoLey.getDetalle().getAutorList()
              .stream()
              .map(s -> "[" + s + "](mailto:" + emails.get(s) + ")")
              .collect(Collectors.joining("; ")));
      body.append(autores);
    }
    if (!proyectoLey.getDetalle().getAdherenteList().isEmpty()) {
      var adherentes = String.format("""
              ### Adherentes
                        
              %s
                        
              """,
          String.join("; ", proyectoLey.getDetalle().getAdherenteList()));
      body.append(adherentes);
    }

    // Opiniones
    var opinionesTitulo = String.format("""
            ## Opiniones
                    
            ### Opiniones publicadas
                    
            {{<iframe "%1$s" "Opiniones publicadas" >}}
            [Enlace](%1$s)
                    
            """,
        proyectoLey.getEnlaces().getOpinionesPublicadas());
    body.append(opinionesTitulo);

    if (proyectoLey.getEnlaces().hasPublicarOpinion()) {
      var publicarOpinion = String.format("""
              ### Publicar opinión
                      
              {{<iframe "%1$s" "Brindar opinión" >}}
              [Enlace](%1$s)
                      
              """,
          proyectoLey.getEnlaces().getPublicarOpinion().getValue());
      body.append(publicarOpinion);
    }

    // Seguimiento
    var seguimiento = String.format("""

            ## Seguimiento

            | Fecha | Evento |
            |------:|--------|
            %s
                        
            """,
        proyectoLey.getSeguimientoList().stream()
            .map(s -> String.format("| **%s** | %s |", fecha(s.getFecha()), s.getTexto()))
            .collect(Collectors.joining("\n")));
    body.append(seguimiento);

    // Ley
    if (proyectoLey.hasLey() && !proyectoLey.getLey().getNumero().isBlank()) {
      var ley = String.format("""
              ## %s
                        
              **%s**
                        
              > %s
                            
              """,
          proyectoLey.getLey().getNumero(),
          proyectoLey.getLey().getTitulo(),
          proyectoLey.getLey().getSumilla());
      body.append(ley);
    }

    // Expediente
    if (proyectoLey.hasExpediente()) {
      body.append("""
          ## Expediente
                    
          """);
      if (proyectoLey.getExpediente().getResultadoCount() > 0) {
        body.append(tablaDocumentos("resultado", proyectoLey.getExpediente().getResultadoList()));
      }

      if (proyectoLey.getExpediente().getProyectoCount() > 0) {
        body.append(
            tablaDocumentos("proyecto de ley", proyectoLey.getExpediente().getProyectoList()));
      }
      if (proyectoLey.getExpediente().getAnexoCount() > 0) {
        body.append(tablaDocumentos("anexos", proyectoLey.getExpediente().getAnexoList()));
      }

      // Enlaces
      var enlaces = String.format("""
              ## Enlaces 
                        
              - [Seguimiento](%s)
              %s
                        
              """,
          proyectoLey.getEnlaces().getSeguimiento(),
          proyectoLey.getEnlaces().hasExpediente() ? String.format("- [Expediente Digital](%s)",
              proyectoLey.getEnlaces().getExpediente().getValue()) : "");
      body.append(enlaces);
    }
    return header + body;
  }

  private static String tablaDocumentos(String titulo, List<Documento> docs) {
    return String.format("""
            ### Documentos %s
                        
            | Fecha | Documento |
            |------:|-----------|
            %s
                            
            """,
        titulo,
        docs.stream()
            .map(d -> String.format("| **%s** | [%s](%s) |",
                d.hasFecha() ? fecha(d.getFecha().getValue()) : "",
                d.getTitulo(),
                d.getUrl()))
            .collect(Collectors.joining("\n")));
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
