package congreso.leyes.proyecto;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

public class Seguimiento {

  private String periodo;
  private String legislatura;
  private long presentacion;
  private String numero;
  private String proponente;
  private String grupoParlamentario;
  private String titulo;
  private String sumilla;
  private List<Congresista> autores;
  private List<String> adherentes;
  private String enlaceExpedienteDigital;
  private String seguimiento;
  private String iniciativasAgrupadas;
  private String leyNumero;
  private String leyTitulo;
  private String leySumilla;

  public Seguimiento() {
  }

  public Seguimiento(
      String periodo,
      String legislatura,
      LocalDate presentacion,
      String numero,
      String proponente,
      String grupoParlamentario,
      String titulo,
      String sumilla,
      List<Congresista> autores,
      List<String> adherentes,
      String enlaceExpedienteDigital,
      String seguimiento,
      String iniciativasAgrupadas,
      String leyNumero) {
    this.periodo = periodo;
    this.legislatura = legislatura;
    this.presentacion = presentacion.atStartOfDay().toEpochSecond(ZoneOffset.ofHours(-5));
    this.numero = numero;
    this.proponente = proponente;
    this.grupoParlamentario = grupoParlamentario;
    this.titulo = titulo;
    this.sumilla = sumilla;
    this.autores = autores;
    this.adherentes = adherentes;
    this.enlaceExpedienteDigital = enlaceExpedienteDigital;
    this.seguimiento = seguimiento;
    this.iniciativasAgrupadas = iniciativasAgrupadas;
    this.leyNumero = leyNumero;
  }

  public String getPeriodo() {
    return periodo;
  }

  public Seguimiento setPeriodo(String periodo) {
    this.periodo = periodo;
    return this;
  }

  public String getLegislatura() {
    return legislatura;
  }

  public Seguimiento setLegislatura(String legislatura) {
    this.legislatura = legislatura;
    return this;
  }

  public long getPresentacion() {
    return presentacion;
  }

  public Seguimiento setPresentacion(Long presentacion) {
    this.presentacion = presentacion;
    return this;
  }

  public Seguimiento setPresentacionLocalDate(LocalDate presentacion) {
    this.presentacion = presentacion.atStartOfDay().toEpochSecond(ZoneOffset.ofHours(-5));
    return this;
  }

  public String getNumero() {
    return numero;
  }

  public Seguimiento setNumero(String numero) {
    this.numero = numero;
    return this;
  }

  public String getProponente() {
    return proponente;
  }

  public Seguimiento setProponente(String proponente) {
    this.proponente = proponente;
    return this;
  }

  public String getGrupoParlamentario() {
    return grupoParlamentario;
  }

  public Seguimiento setGrupoParlamentario(String grupoParlamentario) {
    this.grupoParlamentario = grupoParlamentario;
    return this;
  }

  public String getTitulo() {
    return titulo;
  }

  public Seguimiento setTitulo(String titulo) {
    this.titulo = titulo;
    return this;
  }

  public String getSumilla() {
    return sumilla;
  }

  public Seguimiento setSumilla(String sumilla) {
    this.sumilla = sumilla;
    return this;
  }

  public List<Congresista> getAutores() {
    return autores;
  }

  public Seguimiento setAutores(List<Congresista> autores) {
    this.autores = autores;
    return this;
  }

  public String getEnlaceExpedienteDigital() {
    return enlaceExpedienteDigital;
  }

  public Seguimiento setEnlaceExpedienteDigital(
      String enlaceExpedienteDigital) {
    this.enlaceExpedienteDigital = enlaceExpedienteDigital;
    return this;
  }

  public List<String> getAdherentes() {
    return adherentes;
  }

  public Seguimiento setAdherentes(
      List<String> adherentes) {
    this.adherentes = adherentes;
    return this;
  }

  public String getSeguimiento() {
    return seguimiento;
  }

  public Seguimiento setSeguimiento(String seguimiento) {
    this.seguimiento = seguimiento;
    return this;
  }

  public String getIniciativasAgrupadas() {
    return iniciativasAgrupadas;
  }

  public Seguimiento setIniciativasAgrupadas(String iniciativasAgrupadas) {
    this.iniciativasAgrupadas = iniciativasAgrupadas;
    return this;
  }

  public String getLeyNumero() {
    return leyNumero;
  }

  public Seguimiento setLeyNumero(String leyNumero) {
    this.leyNumero = leyNumero;
    return this;
  }

  public String getLeyTitulo() {
    return leyTitulo;
  }

  public Seguimiento setLeyTitulo(String leyTitulo) {
    this.leyTitulo = leyTitulo;
    return this;
  }

  public String getLeySumilla() {
    return leySumilla;
  }

  public Seguimiento setLeySumilla(String leySumilla) {
    this.leySumilla = leySumilla;
    return this;
  }
}
