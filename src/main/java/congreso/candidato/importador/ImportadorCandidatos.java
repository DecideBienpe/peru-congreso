package congreso.candidato.importador;

import static java.lang.System.out;

import congreso.candidato.importador.ImportadorCandidatos.Candidato.Sentencias.Sentencia;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jsoup.Jsoup;

public class ImportadorCandidatos {

  static String baseUrl = "http://peruvotoinformado.com/public";

  public static void main(String[] args) throws IOException {
    var candidatos =
        Files.list(Path.of("static/regiones"))
            .flatMap(f -> {
              try {
                return Files.readAllLines(f).stream();
              } catch (IOException e) {
                e.printStackTrace();
                return Stream.of();
              }
            })
            .parallel()
            .map(Candidato::parse)
            .collect(Collectors.toList());

    {
      var buffer = new StringBuffer();
      buffer.append(Candidato.csvHeaderResumen()).append("\n");
      candidatos.forEach(c -> buffer.append(String.format("%s%n", c.toCsvResumen())));
      Files
          .writeString(Path.of("static/candidatos/2020-candidatos-resumen.csv"), buffer.toString());
    }

    {
      var buffer = new StringBuffer();
      buffer.append(Candidato.csvHeaderSentencias()).append("\n");
      candidatos.stream().flatMap(c->c.toCsvSentencias().stream())
          .forEach(c -> buffer.append(String.format("%s%n", c)));
      Files
          .writeString(Path.of("static/candidatos/2020-candidatos-sentencias.csv"), buffer.toString());
    }
  }


  static class Candidato {

    String href;
    String nombreCompleto;
    String periodoEleccion;
    Imagenes imagenes;
    Resumen resumen;
    Sentencias sentencias;

    static Candidato parse(String ref) {
      try {
        var doc = Jsoup.connect(ref).get();

        var div = doc.select("div.col-sm-9").first();
        if (div == null) {
          throw new RuntimeException("Main div not found");
        }

        var candidato = new Candidato();
        candidato.href = ref;
        candidato.nombreCompleto = div.select("h1").first().text().trim().toUpperCase();
        candidato.periodoEleccion = "2020";

        var imgs = div.getElementsByTag("img");
        if (imgs.size() != 2) {
          out.println("WARN: Expected images not found. " + ref);
        } else {
          var imagenes = new Imagenes();
          imagenes.hrefCandidato = baseUrl + imgs.get(0).attr("src");
          imagenes.hrefPartidoPolitico = baseUrl + imgs.get(1).attr("src");
          candidato.imagenes = imagenes;
        }

        var resumenList = div.select("li.list-group-item");

        var ordenTxt = resumenList.get(0).select("big").first().text();
        Integer orden = null;
        if (!ordenTxt.isBlank()) {
          orden = Integer.parseInt(ordenTxt);
        }

        var dni = resumenList.get(1).text().substring("DNI: ".length());
        var estudios = resumenList.get(2).text().substring("ESTUDIOS UNIVERSITARIOS:".length())
            .trim().toUpperCase().replace("\"", "");
        var trabajo = resumenList.get(3).text().substring("ÚLTIMO TRABAJO U OCUPACIÓN:".length())
            .trim().toUpperCase().replace("\"", "");
        var ingresoAnual = resumenList.get(4).text().substring("INGRESO ANUAL: S/".length()).trim()
            .toUpperCase();
        var genero = resumenList.get(5).text().substring("GÉNERO: ".length()).trim().toUpperCase();
        var fechaNacimiento = resumenList.get(6).text().substring("FECHA NAC.: ".length()).trim()
            .toUpperCase();
        var region = resumenList.get(8).text().substring("REGIÓN: ".length()).trim().toUpperCase();
        var partidoPolitico = resumenList.get(9).text().substring("PARTIDO POLÍTICO: ".length())
            .trim().toUpperCase();

        var resumen = new Resumen();
        resumen.orden = orden;
        resumen.dni = dni;
        resumen.estudiosUniversitarios = estudios;
        resumen.ultimoTrabajoUOcupacion = trabajo;
        resumen.ingresoAnual = ingresoAnual;
        resumen.genero = genero;
        resumen.fechaNacimiento = fechaNacimiento;
        resumen.region = region;
        resumen.partidoPolitico = partidoPolitico;

        candidato.resumen = resumen;

        var divSentencia = div.select("div#sentencia").first();

        if (divSentencia != null) {
          var divSentenciaTipo = divSentencia.select("div.box-info");

          var sentenciasAP = new ArrayList<Sentencia>();

          var divAmbitoPenal = divSentenciaTipo.first();
          var items = divAmbitoPenal.select("div.box-textcand");
          items.forEach(i -> {
            var tipo = i.getElementsByTag("p").first().text().trim();
            var estado = i.text().trim();
            if (!tipo.equals(estado)) {
              var sentencia = new Sentencias.Sentencia();
              sentencia.tipo = tipo;
              sentencia.estado = estado.substring(tipo.length()).trim();
              sentenciasAP.add(sentencia);
            }
          });

          var sentenciasOtras = new ArrayList<Sentencia>();

          var divOtras = divSentenciaTipo.get(1);
          var itemsOtras = divOtras.select("div.box-textcand");
          itemsOtras.forEach(i -> {
            var tipo = i.getElementsByTag("p").first().text();
            var estado = i.text();
            if (!tipo.equals(estado)) {
              var sentencia = new Sentencias.Sentencia();
              sentencia.tipo = tipo;
              sentencia.estado = estado.substring(tipo.length()).trim();
              sentenciasOtras.add(sentencia);
            }
          });

          var sentencias = new Sentencias();
          sentencias.ambitoPenal = sentenciasAP;
          sentencias.otrasSentencias = sentenciasOtras;

          candidato.sentencias = sentencias;
        } else {
          out.println("WARN: no sentencias found. " + ref);
          candidato.sentencias = new Sentencias();
        }

        return candidato;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    public static String csvHeaderResumen() {
      return
          "DNI,"
              + "REGION,"
              + "PARTIDO_POLITICO,"
              + "NOMBRE_COMPLETO,"
              + "GENERO,"
              + "FECHA_NACIMIENTO,"
              + "ESTUDIOS_UNIVERSITARIOS,"
              + "ULTIMO_TRABAJO_OCUPACION,"
              + "INGRESO_ANUAL,"
              + "FECHA_NACIMIENTO";
    }

    public String toCsvResumen() {
      return
          resumen.dni + "," +
              resumen.region + "," +
              resumen.partidoPolitico + "," +
              nombreCompleto + "," +
              resumen.genero + "," +
              resumen.fechaNacimiento + "," +
              resumen.estudiosUniversitarios + "," +
              resumen.ultimoTrabajoUOcupacion + "," +
              resumen.ingresoAnual + "," +
              resumen.fechaNacimiento
          ;
    }

    public static String csvHeaderSentencias() {
      return "DNI,NOMBRE_COMPLETO,PARTIDO_POLITICO,REGION,SENTENCIA_TIPO,SENTENCIA_ESTADO";
    }

    public List<String> toCsvSentencias() {
      return this.sentencias.toCsv().stream()
          .map(s -> resumen.dni
              + "," + nombreCompleto
              + "," + resumen.partidoPolitico
              + "," + resumen.region
              + "," + s
          )
          .collect(Collectors.toList());
    }


    static class Imagenes {

      String hrefCandidato;
      String hrefPartidoPolitico;
    }

    static class Resumen {

      Integer orden;
      String dni;
      String estudiosUniversitarios;
      String ultimoTrabajoUOcupacion;
      String ingresoAnual;
      String genero;
      String fechaNacimiento;
      String region;
      String partidoPolitico;

      @Override
      public String toString() {
        return "Resumen{" +
            "orden=" + orden +
            ", dni='" + dni + '\'' +
            ", estudiosUniversitarios='" + estudiosUniversitarios + '\'' +
            ", ultimoTrabajoUOcupacion='" + ultimoTrabajoUOcupacion + '\'' +
            ", ingresoAnual='" + ingresoAnual + '\'' +
            ", genero='" + genero + '\'' +
            ", fechaNacimiento='" + fechaNacimiento + '\'' +
            ", region='" + region + '\'' +
            ", partidoPolitico='" + partidoPolitico + '\'' +
            '}';
      }
    }

    static class Sentencias {

      List<Sentencia> ambitoPenal = new ArrayList<>();
      List<Sentencia> otrasSentencias = new ArrayList<>();

      public List<String> toCsv() {
        var first = ambitoPenal.stream().map(s -> s.tipo + "," + s.estado)
        .collect(Collectors.toList());
        var second = otrasSentencias.stream().map(s -> s.tipo + "," + s.estado)
            .collect(Collectors.toList());
        first.addAll(second);
        return first;
      }

      static class Sentencia {

        String tipo;
        String estado;

        @Override
        public String toString() {
          return "Sentencia{" +
              "tipo='" + tipo + '\'' +
              ", estado='" + estado + '\'' +
              '}';
        }
      }

    }

    static class IngresoBienesRentas {

      List<Ingreso> ingresos;
      Double totalIngesos;
      List<Bien> bienesInmuebles;
      List<Bien> bienesMueble;
      List<Bien> bienesOtros;

      static class Ingreso {

        String concepto;
        Double sectorPublico, sectorPrivado, total;
      }

      static class Bien {

        String tipo;
        String valor;
      }
    }

    static class InfoAdicional {

      String texto;
    }
  }
//    static class DatosGenerales {
//
//      String cargoAlQuePostula;
//      String lugarAlQuePostula;
//      String dni;
//      String sexo;
//      String fechaNacimiento;
//    }

//    static class LugarNacimiento {
//
//      String pais;
//      String departamento;
//      String provincia;
//      String distrito;
//
//    }

//    static class InformacionAcademica {
//
//      boolean educacionPrimaria;
//      boolean educacionSecundaria;
//      List<Estudio> estudiosTecnicos;
//      List<Estudio> estudiosNoUniversitarios;
//      List<Estudio> estudiosUniversitarios;
//      List<Estudio> estudiosPostgrado;
//
//      static class Estudio {
//
//        String centroEstudios;
//        String carrera;
//        boolean concluido;
//        boolean titulado;
//      }
//    }

//    static class ExperienciaLaboral {
//
//      String centroLaboral;
//      String ocupacion;
//      String periodo;
//    }

//    static class CargoPartidoPolitico {
//
//      List<String> cargosPartidarios;
//      List<String> eleccionesPopulares;
//      List<String> renuncias;
//    }

}
