package com.alicia.cloudstorage.rag.assistant;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CollectionOperationSelectorResolver {

    static final String SELECTOR_VERSION = "source_selector_v2";
    static final String SOURCE_NAMED_FOLDER = CollectionSourceExpressionParser.SOURCE_NAMED_FOLDER;
    static final String SOURCE_PREVIOUS_RESULTS = CollectionSourceExpressionParser.SOURCE_PREVIOUS_RESULTS;
    static final String SOURCE_CURRENT_FOLDER = CollectionSourceExpressionParser.SOURCE_CURRENT_FOLDER;
    static final String SOURCE_CONTEXT_FOLDER = CollectionSourceExpressionParser.SOURCE_CONTEXT_FOLDER;
    static final String SOURCE_ROOT = CollectionSourceExpressionParser.SOURCE_ROOT;

    private final CollectionSourceExpressionParser sourceExpressionParser = new CollectionSourceExpressionParser();

    private static final Pattern MOVE_SPLIT = Pattern.compile(
            "^(.*?)(?:移动到|移到|挪到|转移到|搬到|放到|放进|移动至|挪至|转移至|搬至)(.+)$"
    );
    private static final Pattern DELETE_PREFIX = Pattern.compile(
            "^(?:请)?(?:删除|删掉|删了|删除掉|移入回收站|放进回收站)(.+)$"
    );
    private static final Pattern DELETE_SUFFIX = Pattern.compile(
            "^(.+?)(?:删除|删掉|删了|删除掉|移入回收站|放进回收站)$"
    );
    private static final Pattern CLEAR_FOLDER = Pattern.compile("^(?:请)?清空(.+)$");
    private static final Pattern RECURSIVE_WORDS = Pattern.compile("(?:递归|所有层级|全部层级|包含子文件夹|连同子文件夹|子目录也)");
    private static final Pattern ROOT_NODE_DELETE = Pattern.compile(
            "^(?:请)?(?:删除|删掉|删了|删除掉)(?:云盘)?(?:根目录|根文件夹)$"
    );
    private static final Pattern UNSCOPED_COLLECTION_DELETE = Pattern.compile(
            "^(?:请)?(?:(?:删除|删掉|删除掉|移入回收站|放进回收站))?(?:把|将)?(?:所有|全部)(?:的)?(?:文件(?:夹)?(?:以及|和|与|及|、)文件(?:夹)?|文件|文件夹|内容|东西)(?:都|全部|全都|全)?(?:删除|删掉|删了|删除掉|移入回收站|放进回收站)?$"
    );

    public boolean handles(String message, AssistantConversationState conversation) {
        String text = normalize(message);
        if (text.isBlank() || isConfirmationOrSelection(text)) {
            return false;
        }
        return CLEAR_FOLDER.matcher(text).matches()
                || ROOT_NODE_DELETE.matcher(text).matches()
                || UNSCOPED_COLLECTION_DELETE.matcher(text).matches()
                || RECURSIVE_WORDS.matcher(text).find()
                || parseMove(text, conversation).isPresent()
                || parseTrash(text, conversation).isPresent();
    }

    public IntentRecognitionResponse apply(
            String message,
            AssistantConversationState conversation,
            IntentRecognitionResponse baseResponse
    ) {
        String text = normalize(message);
        if (text.isBlank() || isConfirmationOrSelection(text)) {
            return baseResponse;
        }

        Matcher clear = CLEAR_FOLDER.matcher(text);
        if (clear.matches()) {
            return clarification(
                    baseResponse,
                    "“清空”可能指只删除文件，也可能包含子文件夹。请明确要删除哪一类内容，例如“删除测试目录中的所有文件”。",
                    "ambiguous_clear_folder"
            );
        }
        if (ROOT_NODE_DELETE.matcher(text).matches()) {
            return clarification(
                    baseResponse,
                    "根目录本身不能删除。你可以明确说“删除根目录下所有文件”或“删除根目录下所有文件和文件夹”。",
                    "root_node_cannot_be_deleted"
            );
        }
        if (UNSCOPED_COLLECTION_DELETE.matcher(text).matches()) {
            return clarification(
                    baseResponse,
                    "请补充要处理的目录范围，例如“删除根目录下所有文件和文件夹”或“删除测试目录中的所有文件”。",
                    "collection_scope_required"
            );
        }
        if (RECURSIVE_WORDS.matcher(text).find()) {
            return clarification(
                    baseResponse,
                    "目前批量操作默认只处理目录的直接子项。请明确是只处理当前层，还是需要包含所有子目录。",
                    "recursive_scope_requires_confirmation"
            );
        }

        Optional<CollectionSelector> selector = parseMove(text, conversation)
                .or(() -> parseTrash(text, conversation));
        return selector.map(value -> override(baseResponse, value)).orElse(baseResponse);
    }

    public boolean isScopedCollection(IntentRecognitionResponse response) {
        if (response == null || response.actionDraft() == null) {
            return false;
        }
        return List.of("collection.move", "collection.trash", "collection.trash_scoped").contains(response.actionDraft().type());
    }

    public IntentRecognitionResponse restoreStored(
            IntentRecognitionResponse response,
            AssistantConversationState conversation
    ) {
        if (conversation == null
                || conversation.pendingActionDraft() == null
                || !List.of("collection.move", "collection.trash", "collection.trash_scoped").contains(conversation.pendingActionDraft().type())) {
            return response;
        }
        Map<String, Object> stored = conversation.entities();
        String sourceKind = stringValue(stored.get("source_kind"));
        Set<CollectionSourceExpressionParser.NodeKind> sourceNodeKinds = storedNodeKinds(stored);
        String sourceFolder = stringValue(stored.get("source_folder"));
        String targetFolder = stringValue(stored.get("target_folder"));
        CollectionSelector selector = new CollectionSelector(
                "collection.move".equals(conversation.pendingActionDraft().type()) ? "MOVE" : "DELETE",
                new SourceSelector(sourceKind, sourceFolder, sourceNodeKinds),
                targetFolder
        );
        IntentRecognitionResponse restored = override(response, selector);
        Map<String, Object> entities = new LinkedHashMap<>(restored.entities());
        entities.putAll(stored);
        return restored.withPlanningState(
                Map.copyOf(entities),
                restored.candidateBinding(),
                restored.actionPlan(),
                restored.nextAction(),
                restored.assistantText()
        );
    }

    private Optional<CollectionSelector> parseMove(String text, AssistantConversationState conversation) {
        Matcher matcher = MOVE_SPLIT.matcher(text);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String sourceSurface = cleanSource(matcher.group(1));
        String destination = cleanDestination(matcher.group(2));
        if (destination.isBlank()) {
            return Optional.empty();
        }
        return parseSource(sourceSurface, conversation)
                .map(source -> new CollectionSelector("MOVE", source, destination));
    }

    private Optional<CollectionSelector> parseTrash(String text, AssistantConversationState conversation) {
        Matcher prefix = DELETE_PREFIX.matcher(text);
        Matcher suffix = DELETE_SUFFIX.matcher(text);
        String sourceSurface;
        if (prefix.matches()) {
            sourceSurface = cleanSource(prefix.group(1));
        } else if (suffix.matches()) {
            sourceSurface = cleanSource(suffix.group(1));
        } else {
            return Optional.empty();
        }
        return parseSource(sourceSurface, conversation)
                .map(source -> new CollectionSelector("DELETE", source, ""));
    }

    private Optional<SourceSelector> parseSource(String surface, AssistantConversationState conversation) {
        return sourceExpressionParser.parse(surface, conversation)
                .map(source -> new SourceSelector(source.kind(), source.folder(), source.nodeKinds()));
    }

    private IntentRecognitionResponse override(IntentRecognitionResponse response, CollectionSelector selector) {
        Map<String, Object> entities = new LinkedHashMap<>();
        entities.put("selector_version", SELECTOR_VERSION);
        entities.put("source_kind", selector.source().kind());
        entities.put("source_node_type", selector.source().legacyNodeType());
        entities.put("source_node_types", selector.source().nodeTypeNames());
        entities.put("source_recursive", false);
        entities.put("source_quantifier", "ALL");
        if (!selector.source().folder().isBlank()) {
            entities.put("source_folder", selector.source().folder());
        }
        if (!selector.destination().isBlank()) {
            entities.put("target_folder", selector.destination());
        }

        boolean move = "MOVE".equals(selector.operation());
        String actionType = move
                ? "collection.move"
                : SOURCE_PREVIOUS_RESULTS.equals(selector.source().kind())
                ? "collection.trash"
                : "collection.trash_scoped";
        String intentId = move ? "node_move" : "file_delete";
        String intentName = move ? "批量移动目录内容" : "批量删除目录内容";
        String assistantText = move
                ? "我会先分别核对源目录、目录中的完整文件集合和目标目录，确认无误后再批量移动。"
                : "我会先核对要处理的完整文件集合，列出预览并等待你确认后再移入回收站。";
        SemanticFrame frame = new SemanticFrame(
                SemanticFrame.VERSION,
                SOURCE_PREVIOUS_RESULTS.equals(selector.source().kind()) ? "FOLLOW_UP" : "NEW_TASK",
                selector.operation(),
                new SemanticFrame.Query("COLLECTION", selector.source().legacyNodeType(), "", "", Map.of(
                        "quantifier", "ALL",
                        "recursive", false,
                        "nodeTypes", selector.source().nodeTypeNames()
                )),
                semanticScope(selector.source()),
                new SemanticFrame.Reference(
                        SOURCE_PREVIOUS_RESULTS.equals(selector.source().kind()) ? "PREVIOUS_CANDIDATE_SET" : "NONE",
                        null,
                        null
                ),
                0.99,
                List.of(),
                SemanticFrame.Clarification.empty()
        );
        return response.withPlanningOverride(
                intentId,
                intentName,
                "FILE_OPERATION",
                Map.copyOf(entities),
                "wait_for_backend_binding",
                new SafetyDecision(move ? "medium" : "high", true, false, "集合操作必须预览并确认。"),
                new ActionDraft(actionType, Map.copyOf(entities), true),
                assistantText,
                "已生成可执行的目录集合选择器。",
                frame
        );
    }

    private IntentRecognitionResponse clarification(
            IntentRecognitionResponse response,
            String question,
            String reason
    ) {
        SemanticFrame frame = new SemanticFrame(
                SemanticFrame.VERSION,
                "NEW_TASK",
                "UNKNOWN",
                SemanticFrame.Query.empty(),
                SemanticFrame.Scope.empty(),
                SemanticFrame.Reference.empty(),
                1.0,
                List.of(reason),
                new SemanticFrame.Clarification(reason, question, List.of())
        );
        return response.withCapabilityBoundary(reason, question, question)
                .withSemanticFrame(frame, Map.of(), new ActionDraft("none", Map.of(), false))
                .withAssistantText(question);
    }

    private SemanticFrame.Scope semanticScope(SourceSelector source) {
        String type = switch (source.kind()) {
            case SOURCE_ROOT -> "ROOT";
            case SOURCE_CURRENT_FOLDER, SOURCE_CONTEXT_FOLDER -> "CURRENT";
            case SOURCE_PREVIOUS_RESULTS -> "PREVIOUS_RESULTS";
            default -> "NAMED_FOLDER";
        };
        String surface = SOURCE_PREVIOUS_RESULTS.equals(source.kind()) ? "" : source.folder();
        return new SemanticFrame.Scope(type, surface, surface.toLowerCase(java.util.Locale.ROOT));
    }

    private Set<CollectionSourceExpressionParser.NodeKind> storedNodeKinds(Map<String, Object> stored) {
        Object value = stored.get("source_node_types");
        java.util.EnumSet<CollectionSourceExpressionParser.NodeKind> kinds =
                java.util.EnumSet.noneOf(CollectionSourceExpressionParser.NodeKind.class);
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> addNodeKind(kinds, item));
        }
        if (kinds.isEmpty()) {
            String legacy = stringValue(stored.get("source_node_type"));
            if ("ANY".equalsIgnoreCase(legacy)) {
                kinds.addAll(java.util.EnumSet.allOf(CollectionSourceExpressionParser.NodeKind.class));
            } else {
                addNodeKind(kinds, legacy);
            }
        }
        return kinds.isEmpty()
                ? Set.copyOf(java.util.EnumSet.allOf(CollectionSourceExpressionParser.NodeKind.class))
                : Set.copyOf(kinds);
    }

    private void addNodeKind(Set<CollectionSourceExpressionParser.NodeKind> kinds, Object value) {
        try {
            kinds.add(CollectionSourceExpressionParser.NodeKind.valueOf(stringValue(value).toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            // Stored v1 values outside FILE/FOLDER are treated as an unspecified node set.
        }
    }

    private boolean isConfirmationOrSelection(String text) {
        return List.of("确认", "确认计划", "继续", "执行", "取消").contains(text)
                || text.startsWith("选择第");
    }

    private String cleanSource(String value) {
        return normalize(value).replaceFirst("^(?:请)?(?:把|将)", "");
    }

    private String cleanDestination(String value) {
        return normalize(value)
                .replaceFirst("^(?:到|至|进|入)", "")
                .replaceFirst("(?:吧|中|里|下|内)$", "");
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().replaceAll("[，。！？!?；;]+$", "").replaceAll("\\s+", "");
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record CollectionSelector(String operation, SourceSelector source, String destination) {
    }

    private record SourceSelector(
            String kind,
            String folder,
            Set<CollectionSourceExpressionParser.NodeKind> nodeKinds
    ) {
        private List<String> nodeTypeNames() {
            return nodeKinds.stream().map(Enum::name).sorted().toList();
        }

        private String legacyNodeType() {
            return nodeKinds.size() == 1 ? nodeKinds.iterator().next().name() : "ANY";
        }
    }
}
