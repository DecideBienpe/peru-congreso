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
            .of(parseDate(values.get(1)));
    var presentacion =
        parseDate(values.get(2));
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

  ExpedienteImportado getExpediente(
      SeguimientoImportado seguimientoImportado) {
    if (seguimientoImportado.getEnlaceExpedienteDigital() == null) return null;
    var url = baseUrl + seguimientoImportado.getEnlaceExpedienteDigital();
    try {
      var expediente = new ExpedienteImportado();
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

      if (expedienteTables.size() == 3) { //contiene dictamenes
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
        var opinionesUrl = getEnlaceOpinionesPresentadas(doc, expedienteOpiniones.get(1));
        expediente.setEnlaceOpinionesRecibidos(opinionesUrl);
      }
      if (expedienteOpiniones.size() == 1) {
        var opinionesUrl = getEnlaceOpinionesPresentadas(doc, expedienteOpiniones.get(0));
        expediente.setEnlaceOpinionesRecibidos(opinionesUrl);
      }

      return expediente;
    } catch (Throwable e) {
      e.printStackTrace();
      return null;
    }
  }

  private String getEnlaceOpinionesPresentadas(org.jsoup.nodes.Document doc, Element opinionTable) {

    var scripts = doc.head().getElementsByTag("script");
//    var first = scripts.get(0);
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


  private String getEnlacePresentarOpinion(org.jsoup.nodes.Document doc, Element opinionTable) {
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

  SeguimientoImportado getProyectoSeguimiento(ProyectoImportado proyectoImportado)
      throws IOException {
    var url = baseUrl + proyectoImportado.getReferencia();
    try {
      SeguimientoImportado proyectoSeguimiento = new SeguimientoImportado();
      var doc = Jsoup.connect(url).get();
      var scripts = doc.head().getElementsByTag("script");
      if (scripts.size() != 2) {
        LOG.error("Unexpected number of tables url={}", url);
        throw new IllegalStateException("Unexpected number of tables");
      }
//    var first = scripts.get(0);
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
              case "Fecha Presentación:" -> proyectoSeguimiento.setPresentacionLocalDate(
                  parseDate(tds.get(1)));
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

  private static LocalDate parseDate(Element td) {
    return LocalDate.parse(td.text(), DateTimeFormatter.ofPattern("MM/dd/yyyy"));
  }

  private static LocalDate parseDate2(Element td) {
    if (td.text().isBlank()) {
      return null;
    }
    return LocalDate.parse(td.text()
            .replaceAll("\\s+", "")
            .replaceAll("\\+", "")
            .replaceAll("//", "/"),
        DateTimeFormatter.ofPattern("dd/MM/yy"));
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
    var url = baseUrl + proyectosUrl + index;
    var doc = Jsoup.connect(url).get();
    var tables = doc.body().getElementsByTag("table");
    if (tables.size() != 3) {
      LOG.error("Unexpected number of tables url={}", url);
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
