package com.ultracards.server.entity.games.briskula.converters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultracards.games.briskula.BriskulaCard;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.IOException;
import java.util.List;

@Converter(autoApply = true)
public class BriskulaCardConverter implements AttributeConverter<List<BriskulaCard>, String> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<BriskulaCard> convertToEntityAttribute(String dbData) {
        try {
            var type = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, BriskulaCard.class);
            return objectMapper.readValue(dbData, type);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read BriskulaCard list from JSON", e);
        }
    }

    @Override
    public String convertToDatabaseColumn(List<BriskulaCard> briskulaCards) {
        try {
            return objectMapper.writeValueAsString(briskulaCards);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert BriskulaCard list to JSON", e);
        }
    }
}
