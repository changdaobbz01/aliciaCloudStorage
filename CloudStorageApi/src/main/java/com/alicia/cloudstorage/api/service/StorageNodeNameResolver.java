package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.repository.StorageNodeRepository;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class StorageNodeNameResolver {

    private static final int MAX_NAME_ATTEMPTS = 1000;

    private StorageNodeNameResolver() {
    }

    static String resolveAvailableSiblingName(
            StorageNodeRepository storageNodeRepository,
            Long userId,
            Long parentId,
            String originalName
    ) {
        return resolveAvailableSiblingName(storageNodeRepository, userId, parentId, originalName, new HashSet<>());
    }

    static String resolveAvailableSiblingName(
            StorageNodeRepository storageNodeRepository,
            Long userId,
            Long parentId,
            String originalName,
            Set<String> reservedNames
    ) {
        String normalizedOriginalName = normalizeNodeName(originalName);
        normalizeReservedNames(reservedNames);

        for (int index = 0; index < MAX_NAME_ATTEMPTS; index += 1) {
            String candidate = index == 0 ? normalizedOriginalName : buildCopyName(normalizedOriginalName, index);
            String reservedKey = candidate.toLowerCase(Locale.ROOT);

            if (reservedNames.contains(reservedKey)) {
                continue;
            }

            if (!storageNodeRepository.existsActiveSiblingName(userId, parentId, candidate)) {
                reservedNames.add(reservedKey);
                return candidate;
            }
        }

        throw new IllegalArgumentException("目标目录下同名项目过多，请先整理后再重试。");
    }

    private static void normalizeReservedNames(Set<String> reservedNames) {
        if (reservedNames.isEmpty()) {
            return;
        }

        Set<String> normalizedNames = new HashSet<>();
        for (String reservedName : reservedNames) {
            normalizedNames.add(reservedName.toLowerCase(Locale.ROOT));
        }
        reservedNames.clear();
        reservedNames.addAll(normalizedNames);
    }

    private static String normalizeNodeName(String rawNodeName) {
        if (rawNodeName == null || rawNodeName.isBlank()) {
            throw new IllegalArgumentException("名称不能为空。");
        }

        String nodeName = rawNodeName.trim();
        if (nodeName.length() > 255) {
            throw new IllegalArgumentException("名称长度不能超过 255 个字符。");
        }

        if (nodeName.contains("/") || nodeName.contains("\\")) {
            throw new IllegalArgumentException("名称不能包含斜杠。");
        }

        return nodeName;
    }

    private static String buildCopyName(String originalName, int index) {
        String suffix = " (" + index + ")";
        int dotIndex = originalName.lastIndexOf('.');
        boolean hasExtension = dotIndex > 0 && dotIndex < originalName.length() - 1;
        String baseName = hasExtension ? originalName.substring(0, dotIndex) : originalName;
        String extension = hasExtension ? originalName.substring(dotIndex) : "";
        int maxBaseLength = Math.max(1, 255 - suffix.length() - extension.length());

        if (baseName.length() > maxBaseLength) {
            baseName = baseName.substring(0, maxBaseLength);
        }

        return baseName + suffix + extension;
    }
}
