package congreso.leyes.exportador.csv;

import java.util.List;

public class ProyectoCsv {
  public String periodo;
  public String numeroPeriodo;
  public String numeroUnico;
  public String estado;
  public String fechaPublicacion;
  public String fechaActualizacion;
  public String titulo;
  public String sumilla;
  public String legislatura;
  public String proponente;
  public String grupoParlamentario;
  public String iniciativasAgrupadas;
  public String autores;
  public String adherentes;
  public String sectores;
  public String ley;

  static String header() {
    return String.join("\t",
        List.of(
            "periodo",
            "numero_periodo",
            "numero_unico",
            "estado",
            "fecha_publicacion",
            "fecha_actualizacion",
            "legislatura",
            "proponente",
            "grupo_parlamentario",
            "iniciativas_agrupadas",
            "autores",
            "adherentes",
            "sectores",
            "tiene_ley",
            "titulo"
            ));
  }

  String toCsvLine() {
    return String.join("\t",
        List.of(
            periodo,
            numeroPeriodo,
            numeroUnico,
            estado,
            fechaPublicacion,
            fechaActualizacion,
            legislatura,
            proponente,
            grupoParlamentario,
            iniciativasAgrupadas,
            "\""+autores+"\"",
            "\""+adherentes+"\"",
            "\""+sectores+"\"",
            ley,
            titulo
        ));
  }
}
