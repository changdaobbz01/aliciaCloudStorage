package com.alicia.cloudstorage.ragexecution.domain.action;

import java.util.List;

final class ActionPayloadRules {

    static final int HARD_MAX_BATCH_NODES = 500;

    private ActionPayloadRules() {
    }

    static long requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be positive.");
        }
        return value;
    }

    static Long optionalPositive(Long value, String field) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(field + " must be positive when present.");
        }
        return value;
    }

    static Long optionalNonNegative(Long value, String field) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative when present.");
        }
        return value;
    }

    static String requireName(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        String normalized = value.trim();
        if (normalized.length() > 255) {
            throw new IllegalArgumentException(field + " must not exceed 255 characters.");
        }
        return normalized;
    }

    static String requireText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " is too long.");
        }
        return normalized;
    }

    static String requireNodeType(String value) {
        String normalized = requireText(value, "nodeType", 16).toUpperCase(java.util.Locale.ROOT);
        if (!List.of("FILE", "FOLDER").contains(normalized)) {
            throw new IllegalArgumentException("nodeType must be FILE or FOLDER.");
        }
        return normalized;
    }

    static String requireTimestamp(String value) {
        String normalized = requireText(value, "updatedAt", 64);
        try {
            java.time.OffsetDateTime.parse(normalized);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException("updatedAt must be an ISO offset timestamp.", exception);
        }
        return normalized;
    }

    static String requireSha256(String value, String field) {
        String normalized = requireText(value, field, 64).toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a SHA-256 digest.");
        }
        return normalized;
    }

    static String requireIdentifier(String value, String field) {
        String normalized = requireText(value, field, 64);
        if (!normalized.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new IllegalArgumentException(field + " is not a safe identifier.");
        }
        return normalized;
    }

    static List<BatchNodeSnapshot> requireSnapshots(List<BatchNodeSnapshot> values) {
        if (values == null || values.isEmpty() || values.size() > HARD_MAX_BATCH_NODES) {
            throw new IllegalArgumentException("items must contain between 1 and " + HARD_MAX_BATCH_NODES + " nodes.");
        }
        List<BatchNodeSnapshot> copy = List.copyOf(values);
        if (copy.stream().map(BatchNodeSnapshot::nodeId).distinct().count() != copy.size()) {
            throw new IllegalArgumentException("items must not contain duplicate nodeIds.");
        }
        return copy;
    }

    static void requireSnapshotCount(Integer snapshotCount, int actualCount) {
        if (snapshotCount == null || snapshotCount != actualCount) {
            throw new IllegalArgumentException("snapshotCount must match the item count.");
        }
    }

    static List<Long> requireNodeIds(List<Long> values, int maximum) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("nodeIds must not be empty.");
        }
        if (values.size() > maximum) {
            throw new IllegalArgumentException("nodeIds must not contain more than " + maximum + " items.");
        }
        List<Long> copy = List.copyOf(values);
        copy.forEach(value -> requirePositive(value, "nodeId"));
        if (copy.stream().distinct().count() != copy.size()) {
            throw new IllegalArgumentException("nodeIds must not contain duplicates.");
        }
        return copy;
    }
}
