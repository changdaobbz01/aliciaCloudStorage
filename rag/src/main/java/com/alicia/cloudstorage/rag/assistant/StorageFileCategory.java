package com.alicia.cloudstorage.rag.assistant;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class StorageFileCategory {

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("image", "IMAGE"),
            Map.entry("images", "IMAGE"),
            Map.entry("photo", "IMAGE"),
            Map.entry("photos", "IMAGE"),
            Map.entry("图片", "IMAGE"),
            Map.entry("照片", "IMAGE"),
            Map.entry("video", "VIDEO"),
            Map.entry("videos", "VIDEO"),
            Map.entry("视频", "VIDEO"),
            Map.entry("audio", "AUDIO"),
            Map.entry("music", "AUDIO"),
            Map.entry("音频", "AUDIO"),
            Map.entry("音乐", "AUDIO"),
            Map.entry("document", "DOCUMENT"),
            Map.entry("documents", "DOCUMENT"),
            Map.entry("doc", "DOCUMENT"),
            Map.entry("文档", "DOCUMENT"),
            Map.entry("archive", "ARCHIVE"),
            Map.entry("archives", "ARCHIVE"),
            Map.entry("zip", "ARCHIVE"),
            Map.entry("压缩包", "ARCHIVE"),
            Map.entry("压缩文件", "ARCHIVE")
    );

    private static final Map<String, Definition> DEFINITIONS = Map.of(
            "IMAGE", new Definition(
                    Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "heif", "avif", "tif", "tiff"),
                    Set.of(),
                    List.of("image/")
            ),
            "VIDEO", new Definition(
                    Set.of("mp4", "mov", "m4v", "mkv", "webm", "avi", "wmv", "flv", "mpeg", "mpg", "3gp", "ts"),
                    Set.of(),
                    List.of("video/")
            ),
            "AUDIO", new Definition(
                    Set.of("mp3", "wav", "flac", "m4a", "aac", "ogg", "oga", "opus", "wma", "aiff", "amr"),
                    Set.of(),
                    List.of("audio/")
            ),
            "DOCUMENT", new Definition(
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
            "ARCHIVE", new Definition(
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
            )
    );

    private StorageFileCategory() {
    }

    static String normalize(Object value) {
        String raw = value == null ? "" : String.valueOf(value).trim();
        if (raw.isBlank()) {
            return "";
        }
        String normalized = raw.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        String alias = ALIASES.get(normalized);
        if (alias != null) {
            return alias;
        }
        String canonical = raw.toUpperCase(Locale.ROOT);
        return DEFINITIONS.containsKey(canonical) ? canonical : "";
    }

    static boolean matches(String rawCategory, CandidateItem candidate) {
        String category = normalize(rawCategory);
        if (category.isBlank()) {
            return rawCategory == null || rawCategory.isBlank();
        }
        if (candidate == null || !"FILE".equalsIgnoreCase(candidate.type())) {
            return false;
        }
        Definition definition = DEFINITIONS.get(category);
        String extension = normalizeExtension(candidate.extension());
        if (!extension.isBlank() && definition.extensions().contains(extension)) {
            return true;
        }
        String mimeType = normalizeMimeType(candidate.mimeType());
        return !mimeType.isBlank()
                && (definition.exactMimeTypes().contains(mimeType)
                || definition.mimePrefixes().stream().anyMatch(mimeType::startsWith));
    }

    static String label(String rawCategory) {
        return switch (normalize(rawCategory)) {
            case "IMAGE" -> "图片文件";
            case "VIDEO" -> "视频文件";
            case "AUDIO" -> "音频文件";
            case "DOCUMENT" -> "文档";
            case "ARCHIVE" -> "压缩文件";
            default -> "文件";
        };
    }

    private static String normalizeExtension(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith(".") ? normalized.substring(1) : normalized;
    }

    private static String normalizeMimeType(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record Definition(
            Set<String> extensions,
            Set<String> exactMimeTypes,
            List<String> mimePrefixes
    ) {
    }
}
