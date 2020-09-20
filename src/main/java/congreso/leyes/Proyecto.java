package congreso.leyes;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

public class Proyecto {

  private String numero;
  private Long fechaActualizacion;
  private Long fechaPresentacion;
  private String estado;
  private String titulo;
  private String referencia;

  Proyecto() {
  }

  public Proyecto(String numero,
      Optional<LocalDate> fechaActualizacion,
      LocalDate fechaPresentacion,
      String estado,
      String titulo,
      String referencia) {
    this.numero = numero;
    this.fechaActualizacion = fechaActualizacion
        .map(d -> d.atStartOfDay().toEpochSecond(ZoneOffset.ofHours(-5))).orElse(-1L);
    this.fechaPresentacion = fechaPresentacion.atStartOfDay().toEpochSecond(ZoneOffset.ofHours(-5));
    this.estado = estado;
    this.titulo = titulo;
    this.referencia = referencia;
  }

  public String getNumero() {
    return numero;
  }

  public Proyecto setNumero(String numero) {
    this.numero = numero;
    return this;
  }

  public Long getFechaActualizacion() {
    return fechaActualizacion;
  }

  public Proyecto setFechaActualizacion(Long fechaActualizacion) {
    this.fechaActualizacion = fechaActualizacion;
    return this;
  }

  public Long getFechaPresentacion() {
    return fechaPresentacion;
  }

  public Proyecto setFechaPresentacion(Long fechaPresentacion) {
    this.fechaPresentacion = fechaPresentacion;
    return this;
  }

  public String getEstado() {
    return estado;
  }

  public Proyecto setEstado(String estado) {
    this.estado = estado;
    return this;
  }

  public String getTitulo() {
    return titulo;
  }

  public Proyecto setTitulo(String titulo) {
    this.titulo = titulo;
    return this;
  }

  public String getReferencia() {
    return referencia;
  }

  public Proyecto setReferencia(String referencia) {
    this.referencia = referencia;
    return this;
  }
}
