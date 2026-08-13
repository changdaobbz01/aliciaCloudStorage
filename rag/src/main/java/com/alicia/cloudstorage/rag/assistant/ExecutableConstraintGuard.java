package com.alicia.cloudstorage.rag.assistant;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ExecutableConstraintGuard {

    private static final Pattern MUTATION = Pattern.compile("(?:删除|删掉|移动|移到|挪到|转移|搬到|回收站)");
    private static final Pattern FOLDER_COLLECTION = Pattern.compile(
            "(?:目录|文件夹)(?:中|里|下|内)(?:的)?.*(?:(?:所有|全部).*(?:文件|文件夹|内容)|(?:文件|文件夹|内容).*(?:都|全部|全都))"
    );
    private static final Pattern EXCLUSION = Pattern.compile("(?:除了|不包括|排除|但不要|除外)");
    private static final Pattern TIME_OR_SIZE = Pattern.compile(
            "(?:今天|昨天|最近|近[一二三四五六七八九十0-9]+天|早于|晚于|大于|小于|超过|不足|MB|GB|KB)",
            Pattern.CASE_INSENSITIVE
    );

    public boolean handlesUnsupportedConstraint(String message) {
        String text = message == null ? "" : message.replaceAll("\\s+", "");
        return MUTATION.matcher(text).find()
                && FOLDER_COLLECTION.matcher(text).find()
                && (EXCLUSION.matcher(text).find() || TIME_OR_SIZE.matcher(text).find());
    }

    public IntentRecognitionResponse apply(String message, IntentRecognitionResponse response) {
        String text = message == null ? "" : message.replaceAll("\\s+", "");
        if (response == null
                || "ask_clarification".equals(response.nextAction())
                || !MUTATION.matcher(text).find()) {
            return response;
        }
        String actionType = response.actionDraft() == null ? "none" : response.actionDraft().type();
        boolean collectionAction = actionType != null && actionType.startsWith("collection.");
        boolean folderCollection = FOLDER_COLLECTION.matcher(text).find();
        if (EXCLUSION.matcher(text).find() && (collectionAction || folderCollection)) {
            return clarify(response, "当前批量执行还不能安全处理排除条件。请改成一个不含“除了/排除”的明确范围。", "unsupported_exclusion_constraint");
        }
        if ((TIME_OR_SIZE.matcher(text).find() || hasValue(response.entities(), "time_range"))
                && (collectionAction || folderCollection)) {
            return clarify(response, "我识别到了时间或大小条件，但当前执行接口还不能完整校验它。请先改用目录、名称、类型或后缀来限定范围。", "unsupported_time_or_size_constraint");
        }
        if (folderCollection
                && !List.of("collection.move", "collection.trash").contains(actionType)) {
            return clarify(
                    response,
                    "我还没有完整确定“目录范围、处理对象和数量范围”。请明确说，例如“删除测试目录中的所有文件”或“把测试目录中的所有文件移动到资料目录”。",
                    "unresolved_folder_collection"
            );
        }
        return response;
    }

    private IntentRecognitionResponse clarify(
            IntentRecognitionResponse response,
            String message,
            String reason
    ) {
        return response.withCapabilityBoundary(reason, message, message)
                .withAssistantText(message);
    }

    private boolean hasValue(Map<String, Object> entities, String key) {
        Object value = entities == null ? null : entities.get(key);
        return value != null && !String.valueOf(value).isBlank();
    }
}
