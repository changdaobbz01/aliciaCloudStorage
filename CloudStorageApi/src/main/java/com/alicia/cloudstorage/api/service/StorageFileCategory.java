package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.entity.NodeType;
import com.alicia.cloudstorage.api.entity.StorageNode;

import java.util.List;
import java.util.Locale;
import java.util.Set;

enum StorageFileCategory {
    IMAGE(
            Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "heif", "avif", "tif", "tiff"),
            Set.of(),
            List.of("image/")
    ),
    VIDEO(
            Set.of("mp4", "mov", "m4v", "mkv", "webm", "avi", "wmv", "flv", "mpeg", "mpg", "3gp", "ts"),
            Set.of(),
            List.of("video/")
    ),
    AUDIO(
            Set.of("mp3", "wav", "flac", "m4a", "aac", "ogg", "oga", "opus", "wma", "aiff", "amr"),
            Set.of(),
            List.of("audio/")
    ),
    DOCUMENT(
            Set.of(
                    "pdf", "txt", "md", "rtf", "csv", "tsv", "json", "xml", "yaml", "yml",
                    "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "pages", "numbers", "key"
            ),
            Set.of(
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/rtf",
                    "application/json",
                    "application/xml",
                    "text/csv"
            ),
            List.of("text/")
    ),
    ARCHIVE(
            Set.of("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "iso", "jar"),
            Set.of(
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/x-rar-compressed",
                    "application/vnd.rar",
                    "application/x-7z-compressed",
                    "application/gzip",
                    "application/x-gzip",
                    "application/x-tar",
                    "application/x-bzip2",
                    "application/x-xz",
                    "application/java-archive"
            ),
            List.of()
    );

    private final Set<String> extensions;
    private final Set<String> exactMimeTypes;
    private final List<String> mimePrefixes;

    StorageFileCategory(Set<String> extensions, Set<String> exactMimeTypes, List<String> mimePrefixes) {
        this.extensions = extensions;
        this.exactMimeTypes = exactMimeTypes;
        this.mimePrefixes = mimePrefixes;
    }

    Set<String> extensions() {
        return extensions;
    }

    Set<String> exactMimeTypes() {
        return exactMimeTypes;
    }

    List<String> mimePrefixes() {
        return mimePrefixes;
    }

    boolean matches(StorageNode node) {
        if (node == null || node.getNodeType() != NodeType.FILE) {
            return false;
        }

        String extension = normalize(node.getFileExtension());
        if (extension != null && extensions.contains(extension)) {
            return true;
        }

        String mimeType = normalize(node.getMimeType());
        if (mimeType == null) {
            return false;
        }

        if (exactMimeTypes.contains(mimeType)) {
            return true;
        }

        return mimePrefixes.stream().anyMatch(mimeType::startsWith);
    }

    static StorageFileCategory fromRaw(String rawCategory) {
        if (rawCategory == null || rawCategory.isBlank()) {
            return null;
        }

        try {
            return StorageFileCategory.valueOf(rawCategory.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid file category.");
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith(".") ? normalized.substring(1) : normalized;
    }
}
