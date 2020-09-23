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
    return String.join(",",
        List.of(
            "periodo",
            "numero_periodo",
            "numero_unico",
            "estado",
            "fecha_publicacion",
            "fecha_actualizacion",
            "titulo",
            "sumilla",
            "legislatura",
            "proponente",
            "grupo_parlamentario",
            "iniciativas_agrupadas",
            "autores",
            "adherentes",
            "sectores"));
  }

  String toCsvLine() {
    return String.join(",",
        List.of(
            periodo,
            numeroPeriodo,
            estado,
            fechaPublicacion,
            fechaActualizacion,
            titulo,
            sumilla,
            legislatura,
            proponente,
            grupoParlamentario,
            iniciativasAgrupadas,
            autores,
            adherentes,
            sectores
        ));
  }
}
