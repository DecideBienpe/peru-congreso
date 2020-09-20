package congreso.leyes.proyecto.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import congreso.leyes.proyecto.Seguimiento;
import java.io.IOException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class SeguimientoSerde implements Serde<Seguimiento> {

  static final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public Serializer<Seguimiento> serializer() {
    return new SeguimientoSerializer();
  }

  @Override
  public Deserializer<Seguimiento> deserializer() {
    return new SeguimientoDeserializer();
  }

  static class SeguimientoSerializer implements Serializer<Seguimiento> {

    @Override
    public byte[] serialize(String s, Seguimiento seguimiento) {
      try {
        return objectMapper.writeValueAsBytes(seguimiento);
      } catch (JsonProcessingException e) {
        throw new IllegalStateException("Error serializando seguimiento", e);
      }
    }
  }

  static class SeguimientoDeserializer implements Deserializer<Seguimiento> {

    @Override
    public Seguimiento deserialize(String s, byte[] bytes) {
      try {
        return objectMapper.readValue(bytes, Seguimiento.class);
      } catch (IOException e) {
        throw new IllegalStateException("Error deserializando seguimiento", e);
      }
    }
  }
}
