package com.alicia.cloudstorage.rag.assistant;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class NamePredicateParser {

    private static final String PREDICATE_PREFIX =
            "(?:名字|名称|文件名|name)(?:中|里|里面|内)?\\s*"
                    + "(?:带有|带着|带|包含有|包含|含有|含|有|contains)\\s*";
    private static final String RESULT_TYPE_SUFFIX =
            "(?:的)?\\s*(文件夹|目录|文件|文档|图片|照片|视频|音频)?";
    private static final List<Pattern> NAME_CONTAINS_PATTERNS = List.of(
            Pattern.compile(
                    PREDICATE_PREFIX + "[\\\"'“‘]([^\\\"'”’]+)[\\\"'”’]" + RESULT_TYPE_SUFFIX,
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            ),
            Pattern.compile(
                    PREDICATE_PREFIX + "([^\\\"'“”‘’，。,.!?！？的\\s]+)" + RESULT_TYPE_SUFFIX,
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            )
    );

    private NamePredicateParser() {
    }

    static Optional<NamePredicate> parse(String message) {
        String text = message == null ? "" : message.trim();
        for (Pattern pattern : NAME_CONTAINS_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                if (isExactNameLabel(text, matcher.start())) {
                    continue;
                }
                String value = TextSupport.sanitizeNodeName(matcher.group(1));
                if (!value.isBlank()) {
                    return Optional.of(new NamePredicate(value, resultType(text, matcher.group(2))));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isExactNameLabel(String message, int matchStart) {
        String prefix = message.substring(0, Math.max(0, matchStart)).trim();
        return List.of("名为", "叫做", "叫").stream().anyMatch(prefix::endsWith);
    }

    private static String resultType(String message, String explicitType) {
        if (message.matches(".*(?:文件或文件夹|文件和文件夹|文件及文件夹|文件、文件夹).*")) {
            return "ANY";
        }
        String type = explicitType == null ? "" : explicitType.trim();
        if (type.contains("文件夹") || "目录".equals(type)) {
            return "FOLDER";
        }
        if (!type.isBlank()) {
            return "FILE";
        }
        return "ANY";
    }

    record NamePredicate(String value, String resultType) {
    }
}
