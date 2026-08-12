package com.alicia.cloudstorage.rag.assistant;

import java.util.LinkedHashMap;
import java.util.Map;

public record AssistantClientContext(
        Long currentFolderId,
        String currentFolderPath,
        Map<String, Integer> availableClientInputs
) {
    public AssistantClientContext {
        currentFolderPath = currentFolderPath == null ? "" : currentFolderPath.trim();
        availableClientInputs = normalizeInputs(availableClientInputs);
    }

    public AssistantClientContext(Long currentFolderId, String currentFolderPath) {
        this(currentFolderId, currentFolderPath, Map.of());
    }

    public static AssistantClientContext empty() {
        return new AssistantClientContext(null, "", Map.of());
    }

    public boolean provides(String field) {
        String normalizedField = normalizeField(field);
        return availableClientInputs.entrySet().stream()
                .anyMatch(entry -> normalizeField(entry.getKey()).equals(normalizedField) && entry.getValue() > 0);
    }

    private static Map<String, Integer> normalizeInputs(Map<String, Integer> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> normalized = new LinkedHashMap<>();
        inputs.forEach((key, count) -> {
            String field = normalizeField(key);
            int safeCount = count == null ? 0 : Math.max(0, count);
            if (!field.isBlank() && safeCount > 0) {
                normalized.merge(field, safeCount, Integer::sum);
            }
        });
        return Map.copyOf(normalized);
    }

    private static String normalizeField(String field) {
        String value = field == null ? "" : field.trim().toLowerCase();
        return switch (value) {
            case "file", "client_file", "client_files" -> "files";
            default -> value;
        };
    }
}
