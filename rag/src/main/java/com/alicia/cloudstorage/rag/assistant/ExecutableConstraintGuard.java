package com.alicia.cloudstorage.rag.assistant;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class ExecutableConstraintGuard {

    static final Rejection EXCLUSION = new Rejection(
            "unsupported_exclusion_constraint",
            "当前批量执行还不能安全处理排除条件。请改成一个不含“除了/排除”的明确范围。"
    );
    private final UnsupportedTemporalSizeScopeGuard unsupportedTemporalSizeScopeGuard =
            new UnsupportedTemporalSizeScopeGuard();
    private static final CompoundMutationScopeGuard MUTATION_SCOPE = new CompoundMutationScopeGuard();

    private static final Pattern MUTATION = Pattern.compile(
            "(?:删除|删掉|移动|移到|挪到|转移|搬到|回收站|分享|共享|生成.{0,16}链接)"
    );
    private static final Pattern FOLDER_COLLECTION = Pattern.compile(
            "(?:目录|文件夹)(?:中|里|下|内)(?:的)?.*(?:(?:所有|全部).*(?:文件|文件夹|内容)|(?:文件|文件夹|内容).*(?:都|全部|全都))"
    );
    private static final Pattern EXCLUSION_CUE = Pattern.compile("(?:除了|不包括|排除|但不要|除外)");
    public boolean handlesUnsupportedConstraint(String message) {
        String text = message == null ? "" : message.replaceAll("\\s+", "");
        return MUTATION.matcher(text).find()
                && FOLDER_COLLECTION.matcher(text).find()
                && EXCLUSION_CUE.matcher(text).find();
    }

    Optional<Rejection> evaluate(String message, IntentRecognitionResponse response) {
        String text = message == null ? "" : message.replaceAll("\\s+", "");
        if (response == null) {
            return Optional.empty();
        }
        String actionType = response.actionDraft() == null ? "none" : response.actionDraft().type();
        if (deniesAction(message, actionType)) {
            return reject("explicitly_negated_action", "请明确最终要做的操作及对象。当前不会提交你明确排除的操作。");
        }
        if ("ask_clarification".equals(response.nextAction())) {
            return Optional.empty();
        }
        String expectedOperation = SemanticFrameResolver.operationForAction(actionType);
        if (!"UNKNOWN".equals(expectedOperation) && !"SEARCH".equals(expectedOperation)
                && !expectedOperation.equals(response.semanticFrame().operation())) {
            return reject("semantic_action_mismatch", "我还不能确认要执行哪项操作。请重新描述要做的事和目标文件。");
        }
        if (!MUTATION.matcher(text).find()) {
            return Optional.empty();
        }
        boolean collectionAction = actionType != null && actionType.startsWith("collection.");
        boolean folderCollection = FOLDER_COLLECTION.matcher(text).find();
        if (EXCLUSION_CUE.matcher(text).find() && (collectionAction || folderCollection)) {
            return Optional.of(EXCLUSION);
        }
        if ((unsupportedTemporalSizeScopeGuard.evaluate(message).isPresent()
                || hasValue(response.entities(), "time_range"))
                && hasActionDraft(response)) {
            return reject("unsupported_time_or_size_constraint", "我识别到了时间或大小条件，但当前执行接口还不能完整校验它。请先改用目录、名称、类型或后缀来限定范围。");
        }
        if (folderCollection
                && !List.of(
                        "collection.move",
                        "collection.move_by_category",
                        "collection.move_by_extension",
                        "collection.move_by_name_contains",
                        "collection.trash",
                        "collection.trash_scoped"
                ).contains(actionType)) {
            return reject(
                    "unresolved_folder_collection",
                    "我还没有完整确定“目录范围、处理对象和数量范围”。请明确说，例如“删除测试目录中的所有文件”或“把测试目录中的所有文件移动到资料目录”。"
            );
        }
        return Optional.empty();
    }

    static boolean deniesAction(String message, String actionType) {
        String operation = SemanticFrameResolver.operationForAction(actionType);
        if ("CREATE_FOLDER".equals(operation)) {
            operation = "CREATE";
        }
        return MUTATION_SCOPE.explicitlyNegates(message, operation)
                || "composite.create_folder_then_upload".equals(actionType)
                && MUTATION_SCOPE.explicitlyNegates(message, "CREATE");
    }

    private Optional<Rejection> reject(String reason, String guidance) {
        return Optional.of(new Rejection(reason, guidance));
    }

    record Rejection(String reason, String guidance) {}

    private boolean hasValue(Map<String, Object> entities, String key) {
        Object value = entities == null ? null : entities.get(key);
        return value != null && !String.valueOf(value).isBlank();
    }

    private boolean hasActionDraft(IntentRecognitionResponse response) {
        String actionType = response.actionDraft() == null ? "none" : response.actionDraft().type();
        return actionType != null && !actionType.isBlank() && !"none".equals(actionType);
    }
}
