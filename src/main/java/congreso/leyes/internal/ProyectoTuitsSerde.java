package congreso.leyes.internal;

import congreso.leyes.Proyecto.Tuits;
import java.io.IOException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class ProyectoTuitsSerde implements Serde<Tuits> {


  @Override
  public Serializer<Tuits> serializer() {
    return new ProyectoSerializer();
  }

  @Override
  public Deserializer<Tuits> deserializer() {
    return new ProyectoDeserializer();
  }

  static class ProyectoSerializer implements Serializer<Tuits> {

    @Override
    public byte[] serialize(String s, Tuits proyecto) {
      return proyecto.toByteArray();
    }
  }

  static class ProyectoDeserializer implements Deserializer<Tuits> {

    @Override
    public Tuits deserialize(String s, byte[] bytes) {
      try {
        return Tuits.parseFrom(bytes);
      } catch (IOException e) {
        throw new IllegalStateException("Error deserializando proyecto", e);
      }
    }
  }
}
