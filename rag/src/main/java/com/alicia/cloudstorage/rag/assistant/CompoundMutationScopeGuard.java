package com.alicia.cloudstorage.rag.assistant;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rejects mutation requests whose action or object cardinality cannot be represented by one public plan.
 * Literal names are masked before structural inspection so conjunctions inside a name remain data.
 */
final class CompoundMutationScopeGuard {

    private static final Pattern QUOTED_LITERAL = Pattern.compile(
            "[“\"'‘《][^”\"'’》]+[”\"'’》]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern PATH_LITERAL = Pattern.compile(
            "/.*?(?=(?:[，,。；;!?！？:：]|重命名|改名|移除|删除|删掉|挪到|搬到|移动到|移到|放进|归档到)|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern EXPLICIT_RENAME_TARGET_LITERAL = Pattern.compile(
            "(重命名(?:为|成)|改名(?:为|成)?)\\s*.*?"
                    + "(?=(?:然后|接着|随后|同时|并且|再把|再将|"
                    + "并(?=分享|共享|删除|删掉|移动|搬|挪|上传|创建|新建)|[，,；;]|$))",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern CONDITIONAL_LEAD = Pattern.compile(
            "^(?:如果|假如|假设|要是)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern FILE_TOKEN = Pattern.compile(
            "[\\p{L}\\p{N}_-]+(?:\\.[A-Za-z0-9]{1,12})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern QUOTED_FILE_TOKEN = Pattern.compile(
            "[“\"'‘《][^”\"'’》]+\\.[A-Za-z0-9]{1,12}[”\"'’》]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern MULTIPLE_ORDINAL_REFERENCES = Pattern.compile(
            "第(?:一|二|三|四|五|六|七|八|九|十|\\d+)个?.{0,8}(?:和|与|、|以及|并且).{0,8}"
                    + "第(?:一|二|三|四|五|六|七|八|九|十|\\d+)个?(?:候选|结果|文件|文件夹)?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern STRUCTURAL_CONNECTOR = Pattern.compile(
            "然后|接着|随后|同时|并且|再把|再将|[，,；;].{0,12}(?:把|将)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern NEGATION_OR_CANCELLATION = Pattern.compile(
            "不要|(?<!分)别|不用|无需|不必|不再|取消|停止|撤销",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern MOVE_DESTINATION = Pattern.compile(
            "(?:移动到|移到|移至|搬到|挪到|归档到|放到|放进|转移到|到|至|进)\\s*"
                    + "([^，,。；;!?！？]+?)(?:目录|文件夹)?(?:[。！!]*)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern RENAME_SOURCE = Pattern.compile(
            "(?:把|将)\\s*[《“\\\"'‘]?([^》”\\\"'’，,]+?)[》”\\\"'’]?\\s*"
                    + "(?:重命名|改名|改成|改为)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern RENAME_TARGET = Pattern.compile(
            "(?:重命名(?:为|成)|改名(?:为|成)?|改成|改为)\\s*"
                    + "[《“\\\"'‘]?([^》”\\\"'’，,。；;!?！？]+)[》”\\\"'’]?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Map<Operation, Pattern> OPERATION_PATTERNS = operationPatterns();

    Optional<Decision> evaluate(String message) {
        String value = clean(message);
        if (value.isBlank()) {
            return Optional.empty();
        }
        if (CONDITIONAL_LEAD.matcher(value).find()) {
            return Optional.empty();
        }

        String structural = maskRenameTargetLiterals(maskPathLiterals(maskQuotedLiterals(value)));
        List<OperationOccurrence> occurrences = affirmativeOperations(structural);
        Set<Operation> operations = new LinkedHashSet<>();
        occurrences.forEach(occurrence -> operations.add(occurrence.operation()));

        if (MULTIPLE_ORDINAL_REFERENCES.matcher(structural).find()) {
            return Optional.of(unsupportedScope(
                    "multiple_ordinal_targets",
                    "这句话同时引用了多个候选，但当前一次计划只能安全绑定一个明确对象。请先选择其中一个。"
            ));
        }

        if (operations.equals(Set.of(Operation.CREATE, Operation.UPLOAD))) {
            return Optional.empty();
        }

        if (operations.size() > 1) {
            return Optional.of(new Decision(
                    "unsupported_compound_mutations",
                    "fallback",
                    "NEW_TASK",
                    "UNKNOWN",
                    "这句话包含多个文件变更步骤，而当前公开计划不能安全表达这组组合。请先选择一个操作。",
                    renameContext(value)
            ));
        }

        if (hasRepeatedOperationClauses(structural, occurrences)) {
            return Optional.of(unsupportedScope(
                    "multiple_independent_mutations",
                    "这句话包含多个独立对象或目标，当前一次计划不能安全表示全部范围。请先保留其中一个操作。"
            ));
        }

        Operation singleOperation = operations.size() == 1 ? operations.iterator().next() : null;
        if (singleOperation != Operation.RENAME
                && singleOperation != null
                && hasMultipleExplicitObjects(value, structural)) {
            Operation operation = singleOperation;
            return switch (operation) {
                case MOVE -> Optional.of(new Decision(
                        "multiple_move_targets",
                        "node_move",
                        "NEW_TASK",
                        "MOVE",
                        "我识别到你想移动多个明确文件，但当前单对象移动计划不会自动截取其中一个。请先指定一个文件。",
                        moveContext(value)
                ));
                case DELETE -> Optional.of(new Decision(
                        "multiple_delete_targets",
                        "file_delete",
                        "NEW_TASK",
                        "DELETE",
                        "我识别到你想删除多个明确文件，但当前单对象删除计划不会自动截取其中一个。请先指定一个文件。",
                        Map.of()
                ));
                case SHARE -> Optional.of(new Decision(
                        "multiple_share_targets",
                        "file_share",
                        "NEW_TASK",
                        "SHARE",
                        "我识别到你想分享多个明确文件，但当前单对象分享计划不会自动截取其中一个。请先指定一个文件。",
                        Map.of()
                ));
                default -> Optional.of(unsupportedScope(
                        "multiple_mutation_targets",
                        "这句话包含多个明确对象，当前一次计划不能安全表示全部范围。请先指定一个对象。"
                ));
            };
        }

        return Optional.empty();
    }

    private boolean hasRepeatedOperationClauses(
            String message,
            List<OperationOccurrence> occurrences
    ) {
        if (!STRUCTURAL_CONNECTOR.matcher(message).find()) {
            return false;
        }
        Map<Operation, Integer> counts = new EnumMap<>(Operation.class);
        occurrences.forEach(occurrence -> counts.merge(occurrence.operation(), 1, Integer::sum));
        return counts.values().stream().anyMatch(count -> count > 1);
    }

    private boolean hasMultipleExplicitObjects(String original, String structural) {
        int fileTokens = countMatches(FILE_TOKEN, structural) + countMatches(QUOTED_FILE_TOKEN, original);
        if (fileTokens < 2) {
            return false;
        }
        return Pattern.compile("和|与|、|以及|分别", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(original)
                .find();
    }

    private List<OperationOccurrence> affirmativeOperations(String message) {
        List<OperationOccurrence> result = new ArrayList<>();
        for (Map.Entry<Operation, Pattern> entry : OPERATION_PATTERNS.entrySet()) {
            Matcher matcher = entry.getValue().matcher(message);
            while (matcher.find()) {
                if (!isNegated(message, matcher.start())) {
                    result.add(new OperationOccurrence(entry.getKey(), matcher.start()));
                }
            }
        }
        result.sort(java.util.Comparator.comparingInt(OperationOccurrence::position));
        return List.copyOf(result);
    }

    private boolean isNegated(String message, int operationStart) {
        int boundary = 0;
        for (String delimiter : List.of("，", ",", "。", "；", ";", "然后", "接着", "随后", "同时", "并且")) {
            int index = message.lastIndexOf(delimiter, Math.max(0, operationStart - 1));
            if (index >= 0) {
                boundary = Math.max(boundary, index + delimiter.length());
            }
        }
        String prefix = message.substring(boundary, operationStart);
        return NEGATION_OR_CANCELLATION.matcher(prefix).find();
    }

    private Map<String, Object> moveContext(String message) {
        Matcher destination = MOVE_DESTINATION.matcher(message);
        if (!destination.find()) {
            return Map.of();
        }
        String targetFolder = cleanEntity(destination.group(1));
        return targetFolder.isBlank() ? Map.of() : Map.of("target_folder", targetFolder);
    }

    private Map<String, Object> renameContext(String message) {
        Map<String, Object> entities = new LinkedHashMap<>();
        Matcher source = RENAME_SOURCE.matcher(message);
        if (source.find()) {
            String value = cleanEntity(source.group(1));
            if (!value.isBlank()) {
                entities.put("target_name", value);
            }
        }
        Matcher target = RENAME_TARGET.matcher(message);
        if (target.find()) {
            String value = cleanEntity(target.group(1));
            if (!value.isBlank()) {
                entities.put("new_name", value);
            }
        }
        return entities.isEmpty() ? Map.of() : Map.copyOf(entities);
    }

    private Decision unsupportedScope(String id, String assistantText) {
        return new Decision(id, "fallback", "NEW_TASK", "UNKNOWN", assistantText, Map.of());
    }

    private int countMatches(Pattern pattern, String value) {
        int count = 0;
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private String maskQuotedLiterals(String message) {
        return QUOTED_LITERAL.matcher(message).replaceAll("“字面量”");
    }

    private String maskPathLiterals(String message) {
        return PATH_LITERAL.matcher(message).replaceAll("路径");
    }

    private String maskRenameTargetLiterals(String message) {
        return EXPLICIT_RENAME_TARGET_LITERAL.matcher(message).replaceAll("$1新名称");
    }

    private String cleanEntity(String value) {
        return clean(value)
                .replaceAll("^[《“\\\"'‘]+|[》”\\\"'’]+$", "")
                .replaceAll("(?:目录|文件夹)$", "")
                .trim();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static Map<Operation, Pattern> operationPatterns() {
        Map<Operation, Pattern> patterns = new EnumMap<>(Operation.class);
        patterns.put(Operation.DELETE, Pattern.compile(
                "删除|删掉|删了|移除|清理|清空|扔到回收站|移入回收站|移到回收站|移动到回收站|delete|remove|trash",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        ));
        patterns.put(Operation.MOVE, Pattern.compile(
                "移动(?!到回收站)|移到(?!回收站)|移进(?:去)?|搬到|搬|挪到|挪|归档到|放进|转移到|move\\s+to",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        ));
        patterns.put(Operation.RENAME, Pattern.compile(
                "重命名|改名|(?<!名字)(?<!名称)(?:改成|改为)(?!\\s*(?:(?:把|将|给|请).{0,8})?"
                        + "(?:删除|删|移动|搬|挪|分享|上传|创建|新建))|rename",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        ));
        patterns.put(Operation.SHARE, Pattern.compile(
                "分享(?!区|空间|目录|文件夹)|共享(?!区|空间|目录|文件夹)|"
                        + "(?:生成|创建|建立).{0,4}(?:分享|共享)?链接|share",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        ));
        patterns.put(Operation.UPLOAD, Pattern.compile(
                "上传|传进去|传进|传文件|upload",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        ));
        patterns.put(Operation.CREATE, Pattern.compile(
                "(?:新建|创建|建立|建一个|建个|mkdir|create)(?![^，,。；;]{0,8}(?:分享|共享)?链接)",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        ));
        return Map.copyOf(patterns);
    }

    private enum Operation {
        DELETE,
        MOVE,
        RENAME,
        SHARE,
        UPLOAD,
        CREATE
    }

    private record OperationOccurrence(Operation operation, int position) {
    }

    record Decision(
            String id,
            String intentId,
            String relation,
            String operation,
            String assistantText,
            Map<String, Object> entities
    ) {
        Decision {
            entities = entities == null || entities.isEmpty() ? Map.of() : Map.copyOf(entities);
        }
    }
}
