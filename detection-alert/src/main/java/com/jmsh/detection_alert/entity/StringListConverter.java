package com.jmsh.detection_alert.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {
    private static final String DELIMITER = ";;";

    @Override
    public String convertToDatabaseColumn(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        return String.join(DELIMITER, list);
    }

    @Override
    public List<String> convertToEntityAttribute(String data) {
        if (data == null || data.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(data.split(DELIMITER, -1)));
    }
}
