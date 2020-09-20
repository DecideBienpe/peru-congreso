package congreso.leyes.proyecto;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

public class ProyectoImportado {

  private String numero;
  private Long actualizacion;
  private Long presentacion;
  private String estado;
  private String titulo;
  private String referencia;

  ProyectoImportado() {
  }

  public ProyectoImportado(String numero,
      Optional<LocalDate> actualizacion,
      LocalDate presentacion,
      String estado,
      String titulo,
      String referencia) {
    this.numero = numero;
    this.actualizacion = actualizacion
        .map(d -> d.atStartOfDay().toEpochSecond(ZoneOffset.ofHours(-5))).orElse(-1L);
    this.presentacion = presentacion.atStartOfDay().toEpochSecond(ZoneOffset.ofHours(-5));
    this.estado = estado;
    this.titulo = titulo;
    this.referencia = referencia;
  }

  public String getNumero() {
    return numero;
  }

  public ProyectoImportado setNumero(String numero) {
    this.numero = numero;
    return this;
  }

  public Long getActualizacion() {
    return actualizacion;
  }

  public ProyectoImportado setActualizacion(Long actualizacion) {
    this.actualizacion = actualizacion;
    return this;
  }

  public Long getPresentacion() {
    return presentacion;
  }

  public ProyectoImportado setPresentacion(Long presentacion) {
    this.presentacion = presentacion;
    return this;
  }

  public String getEstado() {
    return estado;
  }

  public ProyectoImportado setEstado(String estado) {
    this.estado = estado;
    return this;
  }

  public String getTitulo() {
    return titulo;
  }

  public ProyectoImportado setTitulo(String titulo) {
    this.titulo = titulo;
    return this;
  }

  public String getReferencia() {
    return referencia;
  }

  public ProyectoImportado setReferencia(String referencia) {
    this.referencia = referencia;
    return this;
  }
}
