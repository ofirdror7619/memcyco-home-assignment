package com.memcyco.scheduling.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.LinkedHashMap;
import java.util.Map;

@Converter
public class StringMapConverter implements AttributeConverter<Map<String, String>, String> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, String> value) {
        try {
            return MAPPER.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Task parameters cannot be serialized", exception);
        }
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) return new LinkedHashMap<>();
        try {
            return new LinkedHashMap<>(MAPPER.readValue(value, TYPE));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Stored task parameters are invalid", exception);
        }
    }
}
