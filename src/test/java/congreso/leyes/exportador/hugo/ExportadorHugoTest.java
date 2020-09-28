package congreso.leyes.exportador.hugo;

import static org.junit.jupiter.api.Assertions.*;

import congreso.leyes.Proyecto.ProyectoLey;
import congreso.leyes.Proyecto.ProyectoLey.Detalle;
import congreso.leyes.Proyecto.ProyectoLey.Id;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ExportadorHugoTest {

  @Test void testCrearPagina() throws IOException {
    var pagina = ExportadorHugo.crearPagina(ProyectoLey.newBuilder()
        .setId(Id.newBuilder().setPeriodo("2016-2021").setNumeroPeriodo("000001").build())
        .setDetalle(Detalle.newBuilder().setTitulo("TEST PROYECTO").build())
        .build());
    System.out.println(pagina);
  }
}