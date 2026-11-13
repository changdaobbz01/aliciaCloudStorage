package com.alicia.cloudstorage.rag.assistant;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Preserves mutation intent while blocking temporal and file-size selectors
 * that the execution contract cannot represent exactly.
 */
final class UnsupportedTemporalSizeScopeGuard {

    private static final Pattern QUOTED_LITERAL = Pattern.compile(
            "[“\\\"'‘][^”\\\"'’]+[”\\\"'’]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final String NUMBER = "[一二两三四五六七八九十百千万0-9]+";
    private static final String DATE = "20[0-9]{2}(?:年[0-9]{1,2}月(?:[0-9]{1,2}日)?|[-./][0-9]{1,2}(?:[-./][0-9]{1,2})?)";
    private static final String AMOUNT = "[0-9]+(?:\\.[0-9]+)?\\s*(?:B|KB|MB|GB|TB|字节)";
    private static final Pattern TEMPORAL = Pattern.compile(
            "(?:"
                    + "(?:今天|昨天|前天|本周|上周|本月|上月)(?:内|以前|以后|之前|之后)?"
                    + "|(?:最近|近)" + NUMBER + "(?:天|周|个月|月|年)(?:内)?"
                    + "|" + NUMBER + "(?:天|周|个月|月|年)(?:以前|以后|之前|之后|前|后)"
                    + "|(?:早于|晚于|不早于|不晚于)\\s*" + DATE
                    + "|" + DATE + "\\s*(?:以前|以后|之前|之后|前|后)"
                    + ")",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern SIZE = Pattern.compile(
            "(?:"
                    + "(?:文件)?大小(?:为|等于|大于|小于|超过|不足|不超过|不少于)?\\s*" + AMOUNT
                    + "|(?:大于|小于|超过|不足|不超过|不少于|至少|至多|等于)\\s*" + AMOUNT
                    + "|" + AMOUNT + "\\s*(?:到|至|-|—|~)\\s*" + AMOUNT
                    + ")",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern SHARE = Pattern.compile(
            "分享(?!区|空间|目录|文件夹)|共享(?!区|空间|目录|文件夹)|"
                    + "(?:生成|创建|做成).{0,24}(?:分享|共享)?链接|share",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern DELETE = Pattern.compile(
            "删除|删掉|删了|删|移除|清理|清空|回收站|delete|remove|trash",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern MOVE = Pattern.compile(
            "移动|移到|移至|搬到|挪到|转移|归档(?:到|至)|放到|放进|收纳|收拢|归拢|move",
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
        boolean temporal = TEMPORAL.matcher(structural).find();
        boolean size = SIZE.matcher(structural).find();
        if (!temporal && !size) {
            return Optional.empty();
        }

        Map<String, Object> entities = safeEntities(structural, operation);
        return Optional.of(new Decision(
                temporal ? "unsupported_temporal_constraint" : "unsupported_size_constraint",
                operation.intentId,
                "NEW_TASK",
                operation.semanticOperation,
                temporal
                        ? "当前执行接口还不能安全校验相对时间、绝对日期或时间区间。请改为一个明确文件名，或使用已支持的类型、后缀、名称条件。"
                        : "当前执行接口还不能安全校验文件大小比较或大小区间。请改为一个明确文件名，或使用已支持的类型、后缀、名称条件。",
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

    private Map<String, Object> safeEntities(String value, Operation operation) {
        Map<String, Object> entities = new LinkedHashMap<>();
        if (operation == Operation.MOVE) {
            Matcher destination = MOVE_DESTINATION.matcher(value);
            if (destination.find()) {
                entities.put("target_folder", destination.group(1).trim());
            }
        }
        return Map.copyOf(entities);
    }

    private enum Operation {
        MOVE("node_move", "MOVE"),
        DELETE("file_delete", "DELETE"),
        SHARE("file_share", "SHARE");

        private final String intentId;
        private final String semanticOperation;

        Operation(String intentId, String semanticOperation) {
            this.intentId = intentId;
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
