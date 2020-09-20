package congreso.leyes.proyecto;

public class Congresista {

  private String email;
  private String nombreCompleto;

  public Congresista(String email, String nombreCompleto) {
    this.email = email;
    this.nombreCompleto = nombreCompleto;
  }

  public String getEmail() {
    return email;
  }

  public Congresista setEmail(String email) {
    this.email = email;
    return this;
  }

  public String getNombreCompleto() {
    return nombreCompleto;
  }

  public Congresista setNombreCompleto(String nombreCompleto) {
    this.nombreCompleto = nombreCompleto;
    return this;
  }
}
