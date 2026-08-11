package com.alicia.cloudstorage.rag.assistant;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

final class TextSupport {

    private TextSupport() {
    }

    static String normalizeText(String text) {
        return text == null ? "" : text.toUpperCase().replaceAll("\\s+", "");
    }

    static String normalizeSearchText(String text) {
        return (text == null ? "" : text)
                .toLowerCase()
                .replaceAll("[，。,.!?！？:：;；\"'`()\\[\\]{}]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    static boolean containsAny(String text, List<String> keywords) {
        String normalized = normalizeText(text);
        return keywords.stream()
                .map(TextSupport::normalizeText)
                .filter(keyword -> !keyword.isBlank())
                .anyMatch(normalized::contains);
    }

    static List<List<String>> parseKeywordGroups(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(";"))
                .map(TextSupport::parseKeywords)
                .filter(group -> !group.isEmpty())
                .toList();
    }

    static List<String> parseKeywords(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\|"))
                .map(String::trim)
                .filter(keyword -> !keyword.isBlank())
                .toList();
    }

    static List<String> tokenize(String value) {
        String normalized = normalizeSearchText(value);
        if (normalized.isBlank()) {
            return List.of();
        }
        return Arrays.stream(normalized.split("[\\s/\\\\_-]+"))
                .map(String::trim)
                .filter(term -> !term.isBlank())
                .toList();
    }

    static String sanitizeNodeName(String value) {
        return value == null ? "" : value.trim().replaceAll("[，。,.!?！？]+$", "");
    }

    static String extractExtension(String name) {
        if (name == null) {
            return "";
        }
        int index = name.lastIndexOf('.');
        return index >= 0 && index < name.length() - 1 ? name.substring(index + 1).toLowerCase() : "";
    }

    static String replaceTerms(String text, List<String> terms) {
        if (text == null || text.isBlank() || terms == null || terms.isEmpty()) {
            return text == null ? "" : text;
        }
        String pattern = terms.stream()
                .filter(term -> term != null && !term.isBlank())
                .sorted((left, right) -> Integer.compare(right.length(), left.length()))
                .map(Pattern::quote)
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
        return pattern.isBlank() ? text : text.replaceAll("(?i)(" + pattern + ")", " ");
    }

    static String safeFormat(String template, java.util.Map<String, ?> values) {
        String result = template == null ? "" : template;
        for (MapEntry entry : values.entrySet().stream()
                .map(item -> new MapEntry(item.getKey(), item.getValue() == null ? "" : String.valueOf(item.getValue())))
                .toList()) {
            result = result.replace("{" + entry.key() + "}", entry.value());
        }
        return result;
    }

    private record MapEntry(String key, String value) {
    }
}
