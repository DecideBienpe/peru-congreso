package congreso.leyes.internal;

import congreso.leyes.Proyecto.ProyectoLey.Id;
import java.io.IOException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class ProyectoIdSerde implements Serde<Id> {


  @Override
  public Serializer<Id> serializer() {
    return new ProyectoIdSerializer();
  }

  @Override
  public Deserializer<Id> deserializer() {
    return new ProyectoIdDeserializer();
  }

  static class ProyectoIdSerializer implements Serializer<Id> {

    @Override
    public byte[] serialize(String s, Id proyecto) {
      return proyecto.toByteArray();
    }
  }

  static class ProyectoIdDeserializer implements Deserializer<Id> {

    @Override
    public Id deserialize(String s, byte[] bytes) {
      try {
        return Id.parseFrom(bytes);
      } catch (IOException e) {
        throw new IllegalStateException("Error deserializando proyecto", e);
      }
    }
  }
}
