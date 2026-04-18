package com.recipeplatform.api.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA converters for Recipe JSON columns.
 * Uses Jackson to serialize/deserialize, compatible with both PostgreSQL jsonb and H2.
 */
public final class RecipeJsonConverters {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RecipeJsonConverters() {}

    @Converter
    public static class IngredientListConverter
            implements AttributeConverter<List<Recipe.IngredientItem>, String> {

        private static final TypeReference<List<Recipe.IngredientItem>> TYPE_REF =
                new TypeReference<>() {};

        @Override
        public String convertToDatabaseColumn(List<Recipe.IngredientItem> attribute) {
            if (attribute == null) return "[]";
            try {
                return MAPPER.writeValueAsString(attribute);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Error serializing ingredients", e);
            }
        }

        @Override
        public List<Recipe.IngredientItem> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) return new ArrayList<>();
            try {
                return MAPPER.readValue(dbData, TYPE_REF);
            } catch (IOException e) {
                throw new IllegalArgumentException("Error deserializing ingredients", e);
            }
        }
    }

    @Converter
    public static class StepListConverter
            implements AttributeConverter<List<Recipe.StepItem>, String> {

        private static final TypeReference<List<Recipe.StepItem>> TYPE_REF =
                new TypeReference<>() {};

        @Override
        public String convertToDatabaseColumn(List<Recipe.StepItem> attribute) {
            if (attribute == null) return "[]";
            try {
                return MAPPER.writeValueAsString(attribute);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Error serializing steps", e);
            }
        }

        @Override
        public List<Recipe.StepItem> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) return new ArrayList<>();
            try {
                return MAPPER.readValue(dbData, TYPE_REF);
            } catch (IOException e) {
                throw new IllegalArgumentException("Error deserializing steps", e);
            }
        }
    }

    @Converter
    public static class LabelListConverter
            implements AttributeConverter<List<String>, String> {

        private static final TypeReference<List<String>> TYPE_REF =
                new TypeReference<>() {};

        @Override
        public String convertToDatabaseColumn(List<String> attribute) {
            if (attribute == null) return "[]";
            try {
                return MAPPER.writeValueAsString(attribute);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Error serializing labels", e);
            }
        }

        @Override
        public List<String> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) return new ArrayList<>();
            try {
                return MAPPER.readValue(dbData, TYPE_REF);
            } catch (IOException e) {
                throw new IllegalArgumentException("Error deserializing labels", e);
            }
        }
    }
}
