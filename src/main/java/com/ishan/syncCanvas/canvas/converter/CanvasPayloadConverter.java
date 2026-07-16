package com.ishan.syncCanvas.canvas.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.syncCanvas.canvas.domain.CanvasPayload;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Converter
public class CanvasPayloadConverter
        implements AttributeConverter<CanvasPayload, String> {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String convertToDatabaseColumn(CanvasPayload attribute) {

        if (attribute == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Failed to serialize CanvasPayload",
                    e);
        }
    }

    @Override
    public CanvasPayload convertToEntityAttribute(String dbData) {

        if (dbData == null) {
            return null;
        }

        try {
            return objectMapper.readValue(
                    dbData,
                    CanvasPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Failed to deserialize CanvasPayload",
                    e);
        }
    }
}