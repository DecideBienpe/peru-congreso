package congreso.leyes.importador;

import com.google.protobuf.StringValue;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import congreso.leyes.Proyecto;
import congreso.leyes.Proyecto.ProyectoLey;
import congreso.leyes.Proyecto.ProyectoLey.Detalle;
import congreso.leyes.Proyecto.ProyectoLey.Id;
import congreso.leyes.Proyecto.ProyectoLey.Ley;
import congreso.leyes.Proyecto.ProyectoLey.Seguimiento;
import congreso.leyes.internal.ProyectoIdSerde;
import congreso.leyes.internal.ProyectoLeySerde;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.ValueTransformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportadorSeguimiento {

  static final Logger LOG = LoggerFactory.getLogger(ImportadorSeguimiento.class);

  static final Pattern datePattern = Pattern.compile("\\d{2}/\\d{2}/\\d{4}");

  final String baseUrl;
  final String expedienteUrl;

  static KafkaStreams kafkaStreams;

  public ImportadorSeguimiento(String baseUrl, String expedienteUrl) {
    this.baseUrl = baseUrl;
    this.expedienteUrl = expedienteUrl;
  }

  public static void main(String[] args) {
    var config = ConfigFactory.load();
    run(config);
  }

  public static void run(Config config) {
    var baseUrl = config.getString("importador.base-url");
    var expedienteUrl = config.getString("importador.expedientes-url");

    var importador = new ImportadorSeguimiento(baseUrl, expedienteUrl);

    var kafkaBootstrapServers = config.getString("kafka.bootstrap-servers");
    var inputTopic = config.getString("kafka.topics.proyecto-importado");
    var outputTopic = config.getString("kafka.topics.seguimiento-importado");
    var congresistaTopic = config.getString("kafka.topics.congresista-importado");

    var streamsBuilder = new StreamsBuilder();
    streamsBuilder.addStateStore(Stores.keyValueStoreBuilder(
        Stores.persistentKeyValueStore("proyectos"),
        new ProyectoIdSerde(),
        new ProyectoLeySerde()
    ));

    var proyectoStream = streamsBuilder
        .stream(inputTopic, Consumed.with(new ProyectoIdSerde(), new ProyectoLeySerde()))
        .mapValues(importador::importarSeguimiento)
        .filterNot((k, v) -> Objects.isNull(v));
    proyectoStream
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

    proyectoStream
        .flatMapValues(proyectoLey -> proyectoLey.getDetalle().getCongresistaList())
        .map((id, congresista) -> KeyValue
            .pair(congresista.getNombreCompleto(), congresista.getEmail()))
        .to(congresistaTopic, Produced.with(Serdes.String(), Serdes.String()));

    var streamsConfig = new Properties();
    streamsConfig.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
    var groupId = config.getString("kafka.consumer-groups.importador-seguimiento");
    streamsConfig.put(StreamsConfig.APPLICATION_ID_CONFIG, groupId);
    var overrides = config.getConfig("kafka.streams").entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().unwrapped()));
    streamsConfig.putAll(overrides);
    kafkaStreams = new KafkaStreams(streamsBuilder.build(), streamsConfig);

    Runtime.getRuntime().addShutdownHook(new Thread(kafkaStreams::close));

    LOG.info("Iniciando importacion de seguimientos");

    kafkaStreams.start();
  }

  public static void close() {
    if (kafkaStreams != null) kafkaStreams.close(Duration.ofSeconds(10));
  }

  ProyectoLey importarSeguimiento(ProyectoLey proyecto) {
    var url = proyecto.getEnlaces().getSeguimiento();
    try {
      var builder = proyecto.toBuilder();
      var doc = Jsoup.connect(url).get();
      var scripts = doc.head().getElementsByTag("script");
      if (scripts.size() != 2) {
        LOG.error("Numero inesperado de scripts {}, url={}", scripts.size(), url);
        throw new IllegalStateException("Unexpected number of tables");
      }
      var tablas = doc.body().getElementsByTag("table");
      if (tablas.size() != 2) {
        LOG.error("Unexpected number of tables url={}", url);
        throw new IllegalStateException("Unexpected number of tables");
      }

      builder.getEnlacesBuilder().setExpediente(
          String.format(baseUrl + expedienteUrl, proyecto.getId().getNumeroPeriodo()));

      var detalle = Detalle.newBuilder();
      var ley = Ley.newBuilder();

      var contenidoTabla = tablas.get(1);
      contenidoTabla.getElementsByTag("tr")
          .forEach(tr -> {
            var tds = tr.getElementsByTag("td");
            var field = tds.get(0).text();
            var autores = autores(tds.get(1));
            detalle.addAllCongresista(autores);
            var texto = tds.get(1).text().trim();
            switch (field) {
              case "Período:" -> detalle.setPeriodoTexto(texto);
              case "Legislatura:" -> detalle.setLegislatura(texto);
              case "Número:" -> detalle.setNumeroUnico(texto);
              case "Fecha Presentación:" -> {
              }
              case "Proponente:" -> detalle.setProponente(texto);
              case "Grupo Parlamentario:" -> {
                if (!texto.isBlank()) {
                  detalle.setGrupoParlamentario(StringValue.of(texto));
                }
              }
              case "Título:" -> detalle.setTitulo(texto
                  .replaceAll("\"\"", "\"")
                  .replaceAll("\"", "'")
                  .replaceAll(",,", ",")
                  .replaceAll(":", ".-"));
              case "Sumilla:" -> {
                if (!texto.isBlank()) {
                  detalle.setSumilla(StringValue.of(texto));
                }
              }
              case "Autores (*):" -> detalle.addAllAutor(
                  autores.stream()
                      .map(Proyecto.Congresista::getNombreCompleto)
                      .collect(Collectors.toList()));
              case "Adherentes(**):" -> detalle.addAllAdherente(adherentes(tds.get(1)));
              case "Seguimiento:" -> detalle.setSeguimientoTexto(texto);
              case "Iniciativas Agrupadas:" -> {
                if (!texto.isBlank()) {
                  var values = Arrays.stream(texto.split(","))
                      .map(String::trim)
                      .collect(Collectors.toList());
                  detalle.addAllIniciativaAgrupada(values);
                }
              }
              case "Número de Ley:" -> ley.setNumero(texto);
              case "Título de la Ley:" -> ley.setTitulo(texto);
              case "Sumilla de la Ley" -> {
                if (!texto.isBlank()) {
                  ley.setSumilla(StringValue.of(texto));
                }
              }
              default -> LOG.error("Campo no mapeado: " + field);
            }
          });

      var seguimientos = new ArrayList<Seguimiento>();
      if (!detalle.getSeguimientoTexto().isBlank()) {
        var matcher = datePattern.matcher(detalle.getSeguimientoTexto());
        var textos = Arrays.stream(detalle.getSeguimientoTexto().split(datePattern.pattern()))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .collect(Collectors.toList());
        for (String texto : textos) {
          if (matcher.find()) {
            var fecha = matcher.group();
            seguimientos.add(Seguimiento.newBuilder()
                .setTexto(texto)
                .setFecha(fecha(fecha))
                .build());
          }
        }
      }

      var prefix = "Decretado a...";
      for (Seguimiento seguimiento : seguimientos) {
        if (seguimiento.getTexto().startsWith(prefix)) {
          final var sector = seguimiento.getTexto().substring(prefix.length() + 1).strip();
          if (sector.contains("-")) {
            var corregido = sector.substring(0, sector.indexOf("-"));
            detalle.addSector(corregido);
          } else {
            detalle.addSector(sector);
          }
        }
      }

      builder.addAllSeguimiento(seguimientos);
      builder.setLey(ley);
      builder.setDetalle(detalle);
      return builder.build();
    } catch (HttpStatusException e) {
      if (e.getStatusCode() == 404) {
        LOG.error("Error procesando proyecto {} referencia {}. Pagina no existe!!!", proyecto.getId(), url);
        return null;
      }
      LOG.error("Error procesando proyecto {} referencia {}", proyecto.getId(), url);
      throw new RuntimeException(e);
    } catch (Throwable e) {
      LOG.error("Error procesando proyecto {} referencia {}", proyecto.getId(), url);
      throw new RuntimeException(e);
    }

  }

  private List<Proyecto.Congresista> autores(Element element) {
    return
        element.getElementsByTag("a").stream()
            .map(a -> {
              String email = a.attr("href");
              String nombreCompleto = a.text();
              return Proyecto.Congresista.newBuilder()
                  .setEmail(email)
                  .setNombreCompleto(nombreCompleto)
                  .build();
            })
            .collect(Collectors.toList());
  }

  private List<String> adherentes(Element element) {
    return Arrays.asList(element.text().split(","));
  }

  private Long fecha(String texto) {
    return LocalDate.parse(texto
            .replaceAll("58/08/2018", "08/08/2018")
            .replaceAll("59/02/2017", "06/02/2017")
            .replaceAll("60/02/2017", "06/02/2017")
            .replaceAll("61/02/2017", "06/02/2017")
            .replaceAll("62/02/2017", "06/02/2017")
        , DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        .atStartOfDay()
        .toInstant(ZoneOffset.ofHours(-5))
        .toEpochMilli();
  }
}
