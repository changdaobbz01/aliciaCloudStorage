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
    private static final String SEARCH_PREDICATE_PREFIX =
            "(?:文件(?:的)?名(?:称|字)?|名字|名称|name)(?:里面|中|里|内)?\\s*"
                    + "(?:带有|带着|带|包含有|包含|含有|含|有|出现|沾着|contains)\\s*";
    private static final String CHARACTER_COUNT = "\\s*(?:这|那)[一二两三四五六七八九十几0-9]+(?:个)?(?:字|字符)";
    private static final String UNQUOTED_CLUE = "[^\\s，。,.!?！？；;“”‘’\"'《》「」『』]+?";
    private static final List<Pattern> SEARCH_NAME_CONTAINS_PATTERNS = List.of(
            Pattern.compile(SEARCH_PREDICATE_PREFIX + "(" + TextSupport.QUOTED_NODE_NAME + ")"
                    + "(?:" + CHARACTER_COUNT + ")?" + RESULT_TYPE_SUFFIX, Pattern.CASE_INSENSITIVE),
            Pattern.compile(SEARCH_PREDICATE_PREFIX + "(" + UNQUOTED_CLUE + ")" + CHARACTER_COUNT
                    + RESULT_TYPE_SUFFIX, Pattern.CASE_INSENSITIVE),
            Pattern.compile(SEARCH_PREDICATE_PREFIX
                    + "([^\\s，。,.!?！？；;“”‘’\"'《》「」『』的]+)" + RESULT_TYPE_SUFFIX, Pattern.CASE_INSENSITIVE)
    );
    private static final Pattern SEARCH_COMMAND = Pattern.compile(
            "搜索|搜一下|搜出来|搜出|搜(?=文件名|名称|名字)|查找|查一下|查询|检索|定位|翻一下|找|\\b(?:search|find)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MUTATION_COMMAND = Pattern.compile(
            "删除|删掉|清空|移动|移到|挪到|搬到|归档到|重命名|改名|分享|共享|上传|新建|创建"
                    + "|\\b(?:delete|move|rename|share|upload|create)\\b",
            Pattern.CASE_INSENSITIVE
    );
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
        return parse(message, NAME_CONTAINS_PATTERNS, false);
    }

    static Optional<NamePredicate> parseSearch(String message) {
        return parse(message, SEARCH_NAME_CONTAINS_PATTERNS, true);
    }

    static boolean explicitlyRequestsSearch(String message) {
        String structural = TextSupport.maskQuotedNodeNames(message);
        return SEARCH_COMMAND.matcher(structural).find() && !MUTATION_COMMAND.matcher(structural).find();
    }

    private static Optional<NamePredicate> parse(String message, List<Pattern> patterns, boolean search) {
        String text = message == null ? "" : message;
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                if (isExactNameLabel(text, matcher.start())
                        || search && TextSupport.isInsideQuotedNodeName(text, matcher.start())) {
                    continue;
                }
                String raw = matcher.group(1);
                String unwrapped = TextSupport.unwrapNodeName(raw);
                String value = raw.equals(unwrapped) ? TextSupport.sanitizeNodeName(raw) : unwrapped;
                if (!value.isBlank()) {
                    String resultText = search ? TextSupport.maskQuotedNodeNames(text) : text;
                    return Optional.of(new NamePredicate(
                            value, resultType(resultText, matcher.group(2)), matcher.start(), matcher.end()));
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

    record NamePredicate(String value, String resultType, int start, int end) {
    }
}
