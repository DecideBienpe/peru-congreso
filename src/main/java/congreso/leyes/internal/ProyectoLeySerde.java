package congreso.leyes.internal;

import congreso.leyes.Proyecto.ProyectoLey;
import java.io.IOException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class ProyectoLeySerde implements Serde<ProyectoLey> {


  @Override
  public Serializer<ProyectoLey> serializer() {
    return new ProyectoSerializer();
  }

  @Override
  public Deserializer<ProyectoLey> deserializer() {
    return new ProyectoDeserializer();
  }

  static class ProyectoSerializer implements Serializer<ProyectoLey> {

    @Override
    public byte[] serialize(String s, ProyectoLey proyecto) {
      return proyecto.toByteArray();
    }
  }

  static class ProyectoDeserializer implements Deserializer<ProyectoLey> {

    @Override
    public ProyectoLey deserialize(String s, byte[] bytes) {
      try {
        return ProyectoLey.parseFrom(bytes);
      } catch (IOException e) {
        throw new IllegalStateException("Error deserializando proyecto", e);
      }
    }
  }
}
