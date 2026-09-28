package com.alicia.cloudstorage.api.ragexecution.domain;

import java.util.List;

final class CloudActionPayloadRules {

    static final int HARD_MAX_BATCH_NODES = 500;

    private CloudActionPayloadRules() {
    }

    static long positive(long value, String field) {
        if (value <= 0) {
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

    static String requiredName(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        String normalized = value.trim();
        if (normalized.length() > 255) {
            throw new IllegalArgumentException(field + " must not exceed 255 characters.");
        }
        return normalized;
    }

    static String requiredText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " is too long.");
        }
        return normalized;
    }

    static String nodeType(String value) {
        String normalized = requiredText(value, "nodeType", 16).toUpperCase(java.util.Locale.ROOT);
        if (!List.of("FILE", "FOLDER").contains(normalized)) {
            throw new IllegalArgumentException("nodeType must be FILE or FOLDER.");
        }
        return normalized;
    }

    static String offsetTimestamp(String value) {
        String normalized = requiredText(value, "updatedAt", 64);
        try {
            java.time.OffsetDateTime.parse(normalized);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException("updatedAt must be an ISO offset timestamp.", exception);
        }
        return normalized;
    }

    static String sha256(String value, String field) {
        String normalized = requiredText(value, field, 64).toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a SHA-256 digest.");
        }
        return normalized;
    }

    static List<BatchNodeSnapshotCloud> snapshots(List<BatchNodeSnapshotCloud> values) {
        if (values == null || values.isEmpty() || values.size() > HARD_MAX_BATCH_NODES) {
            throw new IllegalArgumentException("items must contain a supported number of nodes.");
        }
        List<BatchNodeSnapshotCloud> copy = List.copyOf(values);
        if (copy.stream().map(BatchNodeSnapshotCloud::nodeId).distinct().count() != copy.size()) {
            throw new IllegalArgumentException("items must not contain duplicate nodeIds.");
        }
        return copy;
    }

    static void snapshotCount(int snapshotCount, int actualCount) {
        if (snapshotCount != actualCount) {
            throw new IllegalArgumentException("snapshotCount must match the item count.");
        }
    }

    static String optionalText(String value, int maximumLength, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " is too long.");
        }
        return normalized;
    }

    static List<Long> nodeIds(List<Long> values) {
        if (values == null || values.isEmpty() || values.size() > 20) {
            throw new IllegalArgumentException("nodeIds must contain 1-20 items.");
        }
        if (values.stream().anyMatch(value -> value == null || value <= 0)) {
            throw new IllegalArgumentException("nodeIds must contain only positive values.");
        }
        if (values.stream().distinct().count() != values.size()) {
            throw new IllegalArgumentException("nodeIds must not contain duplicates.");
        }
        return List.copyOf(values);
    }
}
