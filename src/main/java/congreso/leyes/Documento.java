package congreso.leyes;

import java.time.LocalDate;
import java.time.ZoneOffset;

public class Documento {

  private long fecha;
  private String titulo;
  private String proyecto;
  private String url;

  public Documento() {
  }

  public Documento(LocalDate fecha, String titulo, String url) {
    if (fecha != null) {
      this.fecha = fecha.atStartOfDay().toEpochSecond(ZoneOffset.ofHours(-5));
    }
    this.titulo = titulo;
    this.url = url;
  }

  public Documento(LocalDate fecha, String titulo, String proyecto, String url) {
    if (fecha != null) {
      this.fecha = fecha.atStartOfDay().toEpochSecond(ZoneOffset.ofHours(-5));
    }
    this.titulo = titulo;
    this.proyecto = proyecto;
    this.url = url;
  }

  public long getFecha() {
    return fecha;
  }

  public Documento setFecha(long fecha) {
    this.fecha = fecha;
    return this;
  }

  public String getTitulo() {
    return titulo;
  }

  public Documento setTitulo(String titulo) {
    this.titulo = titulo;
    return this;
  }

  public String getUrl() {
    return url;
  }

  public Documento setUrl(String url) {
    this.url = url;
    return this;
  }

  public String getProyecto() {
    return proyecto;
  }

  public Documento setProyecto(String proyecto) {
    this.proyecto = proyecto;
    return this;
  }
}
