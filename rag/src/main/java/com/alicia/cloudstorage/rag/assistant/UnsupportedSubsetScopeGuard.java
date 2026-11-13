package com.alicia.cloudstorage.rag.assistant;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Blocks mutation requests whose target set cannot yet be represented exactly by
 * the execution contract. The recognized operation is retained for a useful
 * correction turn, but this guard never creates an executable action draft.
 */
final class UnsupportedSubsetScopeGuard {

    private static final Pattern QUOTED_LITERAL = Pattern.compile(
            "[“\\\"'‘][^”\\\"'’]+[”\\\"'’]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final String NUMBER = "[一二两三四五六七八九十百0-9]+";
    private static final Pattern CARDINALITY = Pattern.compile(
            "(?:"
                    + "(?:最近|最早|最后|最新|最前面|最后面|前|后)(?:的)?" + NUMBER
                    + "(?:个|份|张|部|条|篇|段|首)"
                    + "|(?:第" + NUMBER + "(?:个)?)(?:到|至|-|—)(?:第)?" + NUMBER + "(?:个)?"
                    + "|随机(?:选|挑|抽)?" + NUMBER + "(?:个|份|张|部|条|篇|段|首)?"
                    + ")",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern EXCLUSION_OR_REMAINDER = Pattern.compile(
            "(?:除了|(?:除|不包括)[^，,。；;]{1,48}(?:外|以外|之外)|排除|[^，,。；;]{1,48}除外|"
                    + "其余|剩下(?:的)?|保留[^，,。；;]{0,48}(?:其余|剩下)|"
                    + "其他(?:文件|文档|资料|图片|内容|东西)?(?:都|全部|全都))",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern MOVE_EXCEPTION = Pattern.compile(
            "(?:但|不过|可是)[^，,。；;]{1,48}(?:不要动|别动|不处理)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern SHARE = Pattern.compile(
            "分享|共享|(?:生成|创建|做成).{0,24}(?:分享|共享)?链接|share",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern DELETE = Pattern.compile(
            "删除|删掉|删了|删|移除|清理|清空|回收站|delete|remove|trash",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern MOVE = Pattern.compile(
            "移动|移到|移至|搬到|挪到|转移|归档|放到|放进|收纳|收拢|归拢|move",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern EXTENSION = Pattern.compile("\\.([A-Za-z0-9]{1,16})(?=\\s*(?:文件|文档|资料|格式|$))");
    private static final Pattern CATEGORY = Pattern.compile(
            "(图片|照片|相片|截图|视频|视屏|影片|音频|音乐|文档|压缩包|压缩文件)(?:类|类型|类别)?(?:的)?(?:文件|资料)?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern REMAINDER_GENERIC_OBJECT = Pattern.compile(
            "(?:其他|其余|剩下(?:的)?)\\s*(?:文件|文档|资料|内容|东西)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern MOVE_DESTINATION = Pattern.compile(
            "(?:移动到|移到|移至|搬到|挪到|归档到|归档至|放到|放进|转移到|集中到|归拢到)\\s*"
                    + "([^\\s，,。；;!?！？]+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    Optional<Decision> evaluate(String message) {
        String value = message == null ? "" : message.trim();
        if (value.isBlank()) {
            return Optional.empty();
        }
        String structural = QUOTED_LITERAL.matcher(value).replaceAll("“字面量”");
        Operation operation = operation(structural);
        if (operation == null) {
            return Optional.empty();
        }
        boolean cardinality = CARDINALITY.matcher(structural).find();
        boolean exclusion = EXCLUSION_OR_REMAINDER.matcher(structural).find()
                || operation == Operation.MOVE && MOVE_EXCEPTION.matcher(structural).find();
        if (!cardinality && !exclusion) {
            return Optional.empty();
        }

        Map<String, Object> entities = safeEntities(value, structural, operation);
        String reason = cardinality ? "unsupported_bounded_subset" : "unsupported_exclusion_remainder";
        String assistantText = cardinality
                ? "当前还不能安全地把“前几个、最近几个、区间或随机几个”精确绑定为执行对象。请改为一个明确文件名，或使用已支持的类型、后缀、名称条件。"
                : "当前还不能安全地执行“除了某项、剩下或其余全部”这类排除范围。请改为一个明确文件名，或改用不含例外的正向条件。";
        return Optional.of(new Decision(
                reason,
                cardinality ? genericIntentId(operation) : intentId(structural, operation),
                "NEW_TASK",
                operation.semanticOperation,
                assistantText,
                entities
        ));
    }

    private Operation operation(String value) {
        if (SHARE.matcher(value).find()) {
            return Operation.SHARE;
        }
        if (DELETE.matcher(value).find()) {
            return Operation.DELETE;
        }
        if (MOVE.matcher(value).find()) {
            return Operation.MOVE;
        }
        return null;
    }

    private String intentId(String value, Operation operation) {
        if (operation == Operation.SHARE) {
            return "file_share";
        }
        if (operation == Operation.DELETE) {
            return "file_delete";
        }
        if (NamePredicateParser.parse(value).isPresent()) {
            return "collection_move_by_name";
        }
        if (EXTENSION.matcher(value).find()) {
            return "collection_move_by_extension";
        }
        if (!REMAINDER_GENERIC_OBJECT.matcher(value).find() && CATEGORY.matcher(value).find()) {
            return "collection_move_by_category";
        }
        return "node_move";
    }

    private String genericIntentId(Operation operation) {
        return switch (operation) {
            case SHARE -> "file_share";
            case DELETE -> "file_delete";
            case MOVE -> "node_move";
        };
    }

    private Map<String, Object> safeEntities(String original, String structural, Operation operation) {
        Map<String, Object> entities = new LinkedHashMap<>();
        if (operation == Operation.MOVE) {
            Matcher destination = MOVE_DESTINATION.matcher(structural);
            if (destination.find()) {
                entities.put("target_folder", clean(destination.group(1)));
            }
            Matcher extension = EXTENSION.matcher(structural);
            if (extension.find()) {
                entities.put("extension", extension.group(1).toUpperCase());
            }
            NamePredicateParser.parse(original).ifPresent(predicate -> {
                entities.put("target_name", predicate.value());
                entities.put("result_type", predicate.resultType());
            });
            Matcher category = CATEGORY.matcher(structural);
            if (category.find()) {
                entities.put("file_type", category.group(1));
            }
        }
        return Map.copyOf(entities);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private enum Operation {
        MOVE("MOVE"),
        DELETE("DELETE"),
        SHARE("SHARE");

        private final String semanticOperation;

        Operation(String semanticOperation) {
            this.semanticOperation = semanticOperation;
        }
    }

    record Decision(
            String id,
            String intentId,
            String relation,
            String operation,
            String assistantText,
            Map<String, Object> entities
    ) {
    }
}
