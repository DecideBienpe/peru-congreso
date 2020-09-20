package congreso.leyes.proyecto;

import java.util.List;

public class ExpedienteImportado {
  private String numero;
  private String titulo;
  private List<Documento> documentosLey;
  private List<Documento> documentosProyectosLey;
  private List<Documento> documentosAnexos;
  private String enlacePresentarOpinion;
  private String enlaceOpinionesRecibidos;

  public String getNumero() {
    return numero;
  }

  public ExpedienteImportado setNumero(String numero) {
    this.numero = numero;
    return this;
  }

  public String getTitulo() {
    return titulo;
  }

  public ExpedienteImportado setTitulo(String titulo) {
    this.titulo = titulo;
    return this;
  }

  public List<Documento> getDocumentosLey() {
    return documentosLey;
  }

  public ExpedienteImportado setDocumentosLey(
      List<Documento> documentosLey) {
    this.documentosLey = documentosLey;
    return this;
  }

  public List<Documento> getDocumentosProyectosLey() {
    return documentosProyectosLey;
  }

  public ExpedienteImportado setDocumentosProyectosLey(
      List<Documento> documentosProyectosLey) {
    this.documentosProyectosLey = documentosProyectosLey;
    return this;
  }

  public List<Documento> getDocumentosAnexos() {
    return documentosAnexos;
  }

  public ExpedienteImportado setDocumentosAnexos(List<Documento> documentosAnexos) {
    this.documentosAnexos = documentosAnexos;
    return this;
  }

  public String getEnlacePresentarOpinion() {
    return enlacePresentarOpinion;
  }

  public ExpedienteImportado setEnlacePresentarOpinion(String enlacePresentarOpinion) {
    this.enlacePresentarOpinion = enlacePresentarOpinion;
    return this;
  }

  public String getEnlaceOpinionesRecibidos() {
    return enlaceOpinionesRecibidos;
  }

  public ExpedienteImportado setEnlaceOpinionesRecibidos(String enlaceOpinionesRecibidos) {
    this.enlaceOpinionesRecibidos = enlaceOpinionesRecibidos;
    return this;
  }
}
