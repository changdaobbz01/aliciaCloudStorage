package com.alicia.cloudstorage.rag.assistant;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

public record AssistantClientContext(
        Long currentFolderId,
        String currentFolderPath,
        Map<String, Integer> availableClientInputs,
        String actionContractVersion,
        List<String> supportedActionTypes
) {
    public AssistantClientContext {
        currentFolderPath = currentFolderPath == null ? "" : currentFolderPath.trim();
        availableClientInputs = normalizeInputs(availableClientInputs);
        actionContractVersion = actionContractVersion == null ? "" : actionContractVersion.trim();
        supportedActionTypes = supportedActionTypes == null
                ? List.of()
                : supportedActionTypes.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().toList();
    }

    public AssistantClientContext(
            Long currentFolderId,
            String currentFolderPath,
            Map<String, Integer> availableClientInputs
    ) {
        this(currentFolderId, currentFolderPath, availableClientInputs, "", List.of());
    }

    public AssistantClientContext(Long currentFolderId, String currentFolderPath) {
        this(currentFolderId, currentFolderPath, Map.of(), "", List.of());
    }

    public static AssistantClientContext empty() {
        return new AssistantClientContext(null, "", Map.of(), "", List.of());
    }

    public boolean provides(String field) {
        String normalizedField = normalizeField(field);
        return availableClientInputs.entrySet().stream()
                .anyMatch(entry -> normalizeField(entry.getKey()).equals(normalizedField) && entry.getValue() > 0);
    }

    public boolean supportsAction(String actionType) {
        return supportedActionTypes.contains(actionType == null ? "" : actionType.trim());
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
