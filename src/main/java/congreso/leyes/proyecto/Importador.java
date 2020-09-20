package congreso.leyes.proyecto;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Importador {

  static final Logger LOG = LoggerFactory.getLogger(Importador.class);

  final String baseUrl;

  public Importador(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  private static ProyectoImportado parseRow(Element row) {
    var values = row.getElementsByTag("td");
    if (values.size() != 5) {
      throw new IllegalStateException("Unexpected number of values");
    }
    var numero = values.get(0).text();
    var actualizacion = values.get(1).text().isBlank() ? Optional.<LocalDate>empty() :
        Optional
            .of(LocalDate.parse(values.get(1).text(), DateTimeFormatter.ofPattern("MM/dd/yyyy")));
    var presentacion =
        LocalDate.parse(values.get(2).text(), DateTimeFormatter.ofPattern("MM/dd/yyyy"));
    var estado = values.get(3).text();
    var titulo = values.get(4).text();
    var referencia = values.get(0).getElementsByTag("a").attr("href");
    return new ProyectoImportado(numero, actualizacion, presentacion, estado, titulo, referencia);
  }

  public List<ProyectoImportado> getAll(String proyectosUrl) throws IOException {
    return getAll(proyectosUrl, 1);
  }

  public List<ProyectoImportado> getAll(String proyectosUrl, int startAt) throws IOException {
    var index = startAt;
    var batchSize = 0;

    var proyectosAll = new ArrayList<ProyectoImportado>();

    do {
      var proyectos = getPage(proyectosUrl, index);
      proyectosAll.addAll(proyectos);

      batchSize = proyectos.size();
      index = index + batchSize;
    } while (batchSize == 100);
    return proyectosAll;
  }

  ProyectoSeguimientoImportado getProyectoSeguimiento(ProyectoImportado proyectoImportado)
      throws IOException {
    var url = baseUrl + proyectoImportado.getReferencia();
    try {
      ProyectoSeguimientoImportado proyectoSeguimiento = new ProyectoSeguimientoImportado();
      var doc = Jsoup.connect(url).get();
      var scripts = doc.head().getElementsByTag("script");
      if (scripts.size() != 2) {
        throw new IllegalStateException("Unexpected number of tables");
      }
//    var first = scripts.get(0);
      var linkScript = Arrays.stream(scripts.get(1).html().split("\\n"))
          .filter(s -> s.strip().startsWith("var url="))
          .findFirst();
      var expedienteBaseUrl = linkScript
          .map(s -> s.substring(s.indexOf("\""), s.lastIndexOf("\"")));
      var tables = doc.body().getElementsByTag("table");
      if (tables.size() != 2) {
        throw new IllegalStateException("Unexpected number of tables");
      }
      var a = tables.get(0).getElementsByTag("a").first();
      if (a != null) {
        var onclick = a.attr("onclick");
        var expedienteParam = onclick.substring(onclick.indexOf("'"), onclick.lastIndexOf("'"));
        expedienteBaseUrl
            .ifPresent(s -> proyectoSeguimiento.setEnlaceExpedienteDigital(s + expedienteParam));
      }
      var payload = tables.get(1);
      payload.getElementsByTag("tr")
          .forEach(tr -> {
            var tds = tr.getElementsByTag("td");
            var field = tds.get(0).text();
            switch (field) {
              case "Período:" -> proyectoSeguimiento.setPeriodo(tds.get(1).text());
              case "Legislatura:" -> proyectoSeguimiento.setLegislatura(tds.get(1).text());
              case "Fecha Presentación:" -> proyectoSeguimiento.setPresentacion(
                  LocalDate.parse(tds.get(1).text(), DateTimeFormatter.ofPattern("MM/dd/yyyy")));
              case "Número:" -> proyectoSeguimiento.setNumero(tds.get(1).text());
              case "Proponente:" -> proyectoSeguimiento.setProponente(tds.get(1).text());
              case "Grupo Parlamentario:" -> proyectoSeguimiento
                  .setGrupoParlamentario(tds.get(1).text());
              case "Título:" -> proyectoSeguimiento.setTitulo(tds.get(1).text());
              case "Sumilla:" -> proyectoSeguimiento.setSumilla(tds.get(1).text());
              case "Autores (*):", "Autores (***)" -> proyectoSeguimiento
                  .setAutores(parseCongresistasAutores(tds.get(1)));
              case "Adherentes(**):" -> proyectoSeguimiento
                  .setAdherentes(parseCongresistasAdherentes(tds.get(1)));
              case "Seguimiento:" -> proyectoSeguimiento.setSeguimiento(tds.get(1).text());
              case "Iniciativas Agrupadas:" -> proyectoSeguimiento
                  .setIniciativasAgrupadas(tds.get(1).text());
              case "Número de Ley:" -> proyectoSeguimiento.setLeyNumero(tds.get(1).text());
              case "Título de la Ley:" -> proyectoSeguimiento.setLeyTitulo(tds.get(1).text());
              case "Sumilla de la Ley" -> proyectoSeguimiento.setLeySumilla(tds.get(1).text());
              default -> LOG.error("Campo no mapeado: " + field);
            }
          });
      return proyectoSeguimiento;
    } catch (Throwable e) {
      LOG.error(
          "Error procesando proyecto " + proyectoImportado.getNumero() + " referencia " + url);
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

  List<ProyectoImportado> getPage(String proyectosUrl, int index) throws IOException {
    var doc = Jsoup.connect(baseUrl + proyectosUrl + index).get();
    var tables = doc.body().getElementsByTag("table");
    if (tables.size() != 3) {
      throw new IllegalStateException("Unexpected number of tables");
    }
    var table = tables.get(1);
    return table.getElementsByTag("tr").stream()
        .dropWhile(row -> {
          var headers = row.getElementsByTag("th");
          return headers.size() == 5;
        })
        .map(Importador::parseRow)
        .collect(Collectors.toList());
  }
}
