package com.bfs.fraudcase.persistence;

import com.bfs.fraudcase.domain.Json;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.List;

/**
 * @author Venkateshwara Doijode
 */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        return Json.write(attribute == null ? List.of() : attribute);
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        return dbData == null || dbData.isBlank() ? List.of() : Arrays.asList(Json.read(dbData, String[].class));
    }
}
