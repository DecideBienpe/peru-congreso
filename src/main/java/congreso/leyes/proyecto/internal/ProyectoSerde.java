package congreso.leyes.proyecto.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import congreso.leyes.proyecto.Proyecto;
import java.io.IOException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class ProyectoSerde implements Serde<Proyecto> {

  static final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public Serializer<Proyecto> serializer() {
    return new ProyectoSerializer();
  }

  @Override
  public Deserializer<Proyecto> deserializer() {
    return new ProyectoDeserializer();
  }

  static class ProyectoSerializer implements Serializer<Proyecto> {

    @Override
    public byte[] serialize(String s, Proyecto proyecto) {
      try {
        return objectMapper.writeValueAsBytes(proyecto);
      } catch (JsonProcessingException e) {
        throw new IllegalStateException("Error serializando proyecto", e);
      }
    }
  }

  static class ProyectoDeserializer implements Deserializer<Proyecto> {

    @Override
    public Proyecto deserialize(String s, byte[] bytes) {
      try {
        return objectMapper.readValue(bytes, Proyecto.class);
      } catch (IOException e) {
        throw new IllegalStateException("Error deserializando proyecto", e);
      }
    }
  }
}
