package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RagConfigLoader {

    private final ObjectMapper objectMapper;

    public RagConfigLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode loadJson(String classpathLocation) {
        try {
            return objectMapper.readTree(new ClassPathResource(classpathLocation).getInputStream());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load RAG JSON config: " + classpathLocation, exception);
        }
    }

    public Map<String, Object> loadJsonMap(String classpathLocation) {
        return objectMapper.convertValue(loadJson(classpathLocation), new TypeReference<>() {
        });
    }

    public List<Map<String, String>> loadCsv(String classpathLocation) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(classpathLocation).getInputStream(),
                StandardCharsets.UTF_8
        ))) {
            return parseCsv(reader.lines().reduce("", (left, right) -> left + right + "\n"));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load RAG CSV config: " + classpathLocation, exception);
        }
    }

    private List<Map<String, String>> parseCsv(String content) {
        List<List<String>> rows = parseCsvRows(stripBom(content));
        if (rows.isEmpty()) {
            return List.of();
        }

        List<String> headers = rows.getFirst().stream().map(String::trim).toList();
        List<Map<String, String>> records = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> values = rows.get(rowIndex);
            Map<String, String> record = new LinkedHashMap<>();
            for (int index = 0; index < headers.size(); index++) {
                String value = index < values.size() ? values.get(index) : "";
                record.put(headers.get(index), value.trim());
            }
            records.add(record);
        }
        return records;
    }

    private List<List<String>> parseCsvRows(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for (int index = 0; index < content.length(); index++) {
            char character = content.charAt(index);
            char next = index + 1 < content.length() ? content.charAt(index + 1) : '\0';

            if (character == '"' && quoted && next == '"') {
                current.append('"');
                index++;
                continue;
            }

            if (character == '"') {
                quoted = !quoted;
                continue;
            }

            if (character == ',' && !quoted) {
                row.add(current.toString());
                current.setLength(0);
                continue;
            }

            if ((character == '\n' || character == '\r') && !quoted) {
                if (character == '\r' && next == '\n') {
                    index++;
                }
                row.add(current.toString());
                addCsvRow(rows, row);
                row = new ArrayList<>();
                current.setLength(0);
                continue;
            }

            current.append(character);
        }

        row.add(current.toString());
        addCsvRow(rows, row);
        return rows;
    }

    private void addCsvRow(List<List<String>> rows, List<String> row) {
        if (row.size() == 1 && row.getFirst().isBlank()) {
            return;
        }
        rows.add(row);
    }

    private String stripBom(String content) {
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            return content.substring(1);
        }
        return content;
    }
}
