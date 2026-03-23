package com.mac.bry.validationsystem.wizard.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

/**
 * JPA converter for storing List<Long> as JSON in database.
 *
 * <p>
 * Converts a list of Long IDs (e.g., selected measurement series IDs)
 * to/from JSON strings for database storage.
 * </p>
 *
 * Usage: @Convert(converter = LongListJsonConverter.class)
 * private List<Long> selectedSeriesIds;
 */
@Converter
public class LongListJsonConverter implements AttributeConverter<List<Long>, String> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TypeReference<List<Long>> listType = new TypeReference<List<Long>>() {};

    @Override
    public String convertToDatabaseColumn(List<Long> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error converting List<Long> to JSON", e);
        }
    }

    @Override
    public List<Long> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        try {
            return objectMapper.readValue(dbData, listType);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error parsing JSON to List<Long>", e);
        }
    }
}
