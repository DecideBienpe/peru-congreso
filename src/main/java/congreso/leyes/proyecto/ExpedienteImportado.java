package congreso.leyes.proyecto;

import java.util.List;

public class ExpedienteImportado {
  private String titulo1;
  private String titulo2;
  private List<Documento> documentosLey;
  private List<Documento> documentosProyectosLey;
  private List<Documento> documentosAnexos;
  private String enlacePresentarOpinion;
  private String enlaceOpinionesRecibidos;

  public String getTitulo1() {
    return titulo1;
  }

  public ExpedienteImportado setTitulo1(String titulo1) {
    this.titulo1 = titulo1;
    return this;
  }

  public String getTitulo2() {
    return titulo2;
  }

  public ExpedienteImportado setTitulo2(String titulo2) {
    this.titulo2 = titulo2;
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
