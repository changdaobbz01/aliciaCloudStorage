package com.alicia.cloudstorage.api.service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record StorageDownloadRange(long startInclusive, long endInclusive) {

    private static final Pattern SINGLE_RANGE_PATTERN = Pattern.compile("(?i)^bytes\\s*=\\s*(\\d*)\\s*-\\s*(\\d*)\\s*$");

    public StorageDownloadRange {
        if (startInclusive < 0 || endInclusive < startInclusive) {
            throw new IllegalArgumentException("Invalid download range.");
        }
    }

    public long length() {
        return endInclusive - startInclusive + 1;
    }

    public String toContentRangeHeader(long totalLength) {
        return "bytes " + startInclusive + "-" + endInclusive + "/" + Math.max(0L, totalLength);
    }

    public static Optional<StorageDownloadRange> parse(String rangeHeader, long totalLength) {
        if (rangeHeader == null || rangeHeader.isBlank()) {
            return Optional.empty();
        }

        long safeTotalLength = Math.max(0L, totalLength);
        String normalizedRangeHeader = rangeHeader.trim();
        if (normalizedRangeHeader.contains(",")) {
            throw invalid(safeTotalLength);
        }

        Matcher matcher = SINGLE_RANGE_PATTERN.matcher(normalizedRangeHeader);
        if (!matcher.matches()) {
            throw invalid(safeTotalLength);
        }

        String startPart = matcher.group(1);
        String endPart = matcher.group(2);
        if (startPart.isEmpty() && endPart.isEmpty()) {
            throw invalid(safeTotalLength);
        }
        if (safeTotalLength <= 0L) {
            throw invalid(safeTotalLength);
        }

        if (startPart.isEmpty()) {
            long suffixLength = parsePositiveLong(endPart, safeTotalLength);
            if (suffixLength <= 0L) {
                throw invalid(safeTotalLength);
            }
            long start = Math.max(0L, safeTotalLength - suffixLength);
            return Optional.of(new StorageDownloadRange(start, safeTotalLength - 1));
        }

        long start = parseNonNegativeLong(startPart, safeTotalLength);
        long end = endPart.isEmpty()
                ? safeTotalLength - 1
                : parseNonNegativeLong(endPart, safeTotalLength);
        if (start >= safeTotalLength || end < start) {
            throw invalid(safeTotalLength);
        }

        return Optional.of(new StorageDownloadRange(start, Math.min(end, safeTotalLength - 1)));
    }

    private static long parsePositiveLong(String value, long totalLength) {
        long parsed = parseNonNegativeLong(value, totalLength);
        if (parsed <= 0L) {
            throw invalid(totalLength);
        }
        return parsed;
    }

    private static long parseNonNegativeLong(String value, long totalLength) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0L) {
                throw invalid(totalLength);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw invalid(totalLength);
        }
    }

    private static InvalidDownloadRangeException invalid(long totalLength) {
        return new InvalidDownloadRangeException("请求的下载区间无效。", totalLength);
    }
}
