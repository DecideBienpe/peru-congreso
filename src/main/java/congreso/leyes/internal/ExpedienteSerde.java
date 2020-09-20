package congreso.leyes.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import congreso.leyes.Expediente;
import java.io.IOException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

public class ExpedienteSerde implements Serde<Expediente> {

  static final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public Serializer<Expediente> serializer() {
    return new ExpedienteSerializer();
  }

  @Override
  public Deserializer<Expediente> deserializer() {
    return new ExpedienteDeserializer();
  }

  static class ExpedienteSerializer implements Serializer<Expediente> {

    @Override
    public byte[] serialize(String s, Expediente expediente) {
      try {
        return objectMapper.writeValueAsBytes(expediente);
      } catch (JsonProcessingException e) {
        throw new IllegalStateException("Error serializando expediente", e);
      }
    }
  }

  static class ExpedienteDeserializer implements Deserializer<Expediente> {

    @Override
    public Expediente deserialize(String s, byte[] bytes) {
      try {
        return objectMapper.readValue(bytes, Expediente.class);
      } catch (IOException e) {
        throw new IllegalStateException("Error deserializando expediente", e);
      }
    }
  }
}
