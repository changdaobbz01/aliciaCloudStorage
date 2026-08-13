package com.alicia.cloudstorage.rag.assistant;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SemanticFrameResolver {

    private static final Set<String> OPERATIONS = Set.of(
            "UNKNOWN", "RESPOND", "SEARCH", "LIST_CHILDREN", "OPEN_FILE", "NAVIGATE",
            "UPLOAD", "DELETE", "MOVE", "RENAME", "SHARE", "CREATE_FOLDER"
    );
    private static final Set<String> RELATIONS = Set.of(
            "NEW_TASK", "FOLLOW_UP", "CORRECTION", "SLOT_FILL", "CANDIDATE_SELECTION", "CONFIRMATION", "CANCELLATION"
    );
    private static final Set<String> QUERY_MODES = Set.of(
            "NONE", "NAME_SEARCH", "NAME_EXACT", "NAME_CONTAINS", "LIST_CHILDREN", "FILTER"
    );
    private static final Set<String> RESULT_TYPES = Set.of("ANY", "FILE", "FOLDER");
    private static final Set<String> SCOPE_TYPES = Set.of("ALL", "ROOT", "CURRENT", "PARENT", "NAMED_FOLDER", "PREVIOUS_RESULTS");
    private static final Set<String> REFERENCE_TYPES = Set.of(
            "NONE", "PREVIOUS_CANDIDATE", "PREVIOUS_CANDIDATE_SET", "SELECTED_CANDIDATE",
            "ANOTHER_CANDIDATE", "REMAINING_CANDIDATES", "PREVIOUS_ACTION", "CLIENT_INPUT"
    );
    private static final Set<String> FILTER_KEYS = Set.of("extension", "file_type", "time_range");

    private static final Pattern NAME_FILTER = Pattern.compile(
            "(?:名字|名称|文件名)(?:中|里|里面|内)?\\s*(?:带有|带|包含|含有|有)\\s*[\\\"'“”]*([^\\\"'“”，。,.!?！？的]+)[\\\"'“”]*(?:的)?\\s*(文件夹|目录|文件|文档)?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NAMED_DIRECTORY_CONTENT = Pattern.compile(
            "(?:列出|列一下|展示|显示|查看|看看|看下|浏览)?\\s*(?:在\\s*)?(.+?(?:目录|文件夹))\\s*(?:下|中|里|内)(?:的)?\\s*(文件夹|目录|文件|内容|列表)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NAMED_LOCATION_CONTENT = Pattern.compile(
            "(?:列出|列一下|展示|显示|查看|看看|看下|浏览)?\\s*(?:在\\s*)?([^\\s，。,.!?！？]+?)\\s*(?:下|中|里|内)(?:的)?\\s*(文件夹|目录|文件|内容|列表)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern UPLOAD_DESTINATION = Pattern.compile(
            "(?:上传到|上传至|传到|传进|放到|upload\\s+to)\\s*([^，。,.!?！？]+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MOVE_DESTINATION = Pattern.compile(
            "(?:移动到|移到|归档到|转移到|移动至|放进|move\\s+to)\\s*([^，。,.!?！？]+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SEARCH_COMMAND = Pattern.compile(
            "(?:找到|找出|查找|搜索|检索|定位|查看|打开|找|查)(?:一下|下)?|看看|看下|列出|列一下|展示|显示",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern REFERENTIAL_OBJECT_SUFFIX = Pattern.compile(
            "^(.+?)(?:这个|那个|该)(?:文件夹|目录|文件)$",
            Pattern.CASE_INSENSITIVE
    );

    public SemanticFrame resolve(
            String message,
            IntentRecognitionResponse response,
            AssistantConversationState conversation,
            AssistantClientContext clientContext,
            Map<String, Object> modelPayload
    ) {
        SemanticFrame modelFrame = frameFromModel(modelPayload);
        SemanticFrame rawFrame = modelFrame == null
                ? localFrame(message, response, conversation)
                : modelFrame;
        rawFrame = applyMessageSemantics(message, rawFrame, conversation);
        rawFrame = reconcileMutationTarget(rawFrame, response, message);
        return validate(rawFrame, response, clientContext);
    }

    private SemanticFrame reconcileMutationTarget(
            SemanticFrame frame,
            IntentRecognitionResponse response,
            String message
    ) {
        if (frame == null
                || response == null
                || !List.of("DELETE", "MOVE", "RENAME", "SHARE").contains(frame.operation())) {
            return frame;
        }
        String localTarget = cleanReferentialObjectSuffix(text(response.entities().get("target_name")));
        if (localTarget.isBlank() || message == null || !message.contains(localTarget)) {
            return frame;
        }
        String resultType = frame.query().resultType();
        if ("ANY".equalsIgnoreCase(resultType)
                && (localTarget.endsWith("目录") || localTarget.endsWith("文件夹"))) {
            resultType = "FOLDER";
        }
        return copyFrame(
                frame,
                frame.relation(),
                frame.operation(),
                new SemanticFrame.Query(
                        frame.query().mode(),
                        resultType,
                        localTarget,
                        normalizeName(localTarget),
                        frame.query().filters()
                ),
                frame.scope(),
                frame.reference(),
                frame.ambiguities(),
                frame.clarification()
        );
    }

    public boolean shouldReusePreviousIntent(
            SemanticFrame frame,
            IntentRecognitionResponse response,
            AssistantConversationState conversation
    ) {
        if (frame == null || response == null || conversation == null) {
            return false;
        }
        return "fallback".equals(response.intentId())
                && (List.of("CORRECTION", "SLOT_FILL").contains(frame.relation())
                || "FOLLOW_UP".equals(frame.relation()) && "SEARCH".equals(frame.operation()))
                && conversation.pendingIntentId() != null
                && !conversation.pendingIntentId().isBlank()
                && !"UNKNOWN".equals(frame.operation());
    }

    public Map<String, Object> entitiesForFrame(IntentRecognitionResponse response, SemanticFrame frame) {
        Map<String, Object> entities = new LinkedHashMap<>();
        if (response != null && response.entities() != null && !"CORRECTION".equals(frame.relation())) {
            entities.putAll(response.entities());
        }

        if (List.of("DELETE", "MOVE", "RENAME", "SHARE").contains(frame.operation())
                && !frame.query().nameSurface().isBlank()) {
            entities.put("target_name", frame.query().nameSurface());
            entities.put("result_type", frame.query().resultType());
        }

        if ("SEARCH".equals(frame.operation())) {
            entities.keySet().removeAll(List.of("target_name", "target_folder", "query_mode", "scope", "result_type"));
            entities.put("query_mode", "LIST_CHILDREN".equals(frame.query().mode()) ? "directory_list" : "name_search");
            entities.put("scope", frame.scope().type().toLowerCase(Locale.ROOT));
            entities.put("result_type", frame.query().resultType());
            if (!"LIST_CHILDREN".equals(frame.query().mode()) && !frame.query().nameSurface().isBlank()) {
                entities.put("target_name", frame.query().nameSurface());
            }
            if ("NAMED_FOLDER".equals(frame.scope().type()) && !frame.scope().folderSurface().isBlank()) {
                entities.put("target_folder", frame.scope().folderSurface());
            }
            entities.putAll(frame.query().filters());
        } else if (List.of("UPLOAD", "MOVE", "CREATE_FOLDER").contains(frame.operation())) {
            if (!frame.scope().folderSurface().isBlank()) {
                entities.put("target_folder", frame.scope().folderSurface());
            }
            entities.putAll(frame.query().filters());
        }
        return Map.copyOf(entities);
    }

    public ActionDraft actionDraftFor(IntentRecognitionResponse response, SemanticFrame frame, Map<String, Object> entities) {
        String type = switch (frame.operation()) {
            case "SEARCH" -> "search";
            case "UPLOAD" -> response != null
                    && response.actionDraft() != null
                    && "composite.create_folder_then_upload".equals(response.actionDraft().type())
                    ? response.actionDraft().type()
                    : "upload_target";
            case "DELETE" -> response != null && response.actionDraft() != null ? response.actionDraft().type() : "delete";
            case "MOVE", "RENAME", "SHARE", "CREATE_FOLDER" ->
                    response != null && response.actionDraft() != null ? response.actionDraft().type() : "none";
            default -> response != null && response.actionDraft() != null ? response.actionDraft().type() : "none";
        };
        return new ActionDraft(type, entities, !"none".equals(type));
    }

    private SemanticFrame frameFromModel(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        Map<String, Object> frame = objectMap(payload.get("semantic_frame"));
        if (frame.isEmpty()) {
            return null;
        }

        Map<String, Object> query = objectMap(frame.get("query"));
        Map<String, Object> scope = objectMap(frame.get("scope"));
        Map<String, Object> reference = objectMap(frame.get("reference"));
        Map<String, Object> clarification = objectMap(frame.get("clarification"));
        return new SemanticFrame(
                string(frame, "schema_version", SemanticFrame.VERSION),
                string(frame, "relation", "NEW_TASK"),
                string(frame, "operation", "UNKNOWN"),
                new SemanticFrame.Query(
                        string(query, "mode", "NONE"),
                        string(query, "result_type", "ANY"),
                        string(query, "name_surface", ""),
                        string(query, "name_normalized", ""),
                        safeFilters(objectMap(query.get("filters")))
                ),
                new SemanticFrame.Scope(
                        string(scope, "type", "ALL"),
                        string(scope, "folder_surface", ""),
                        string(scope, "folder_normalized", "")
                ),
                new SemanticFrame.Reference(
                        string(reference, "type", "NONE"),
                        longValue(reference.get("candidate_id")),
                        integer(reference.get("candidate_index"))
                ),
                decimal(frame.get("confidence")),
                stringList(frame.get("ambiguities")),
                new SemanticFrame.Clarification(
                        string(clarification, "reason", ""),
                        string(clarification, "question", ""),
                        stringList(clarification.get("suggestions"))
                )
        );
    }

    private SemanticFrame localFrame(
            String message,
            IntentRecognitionResponse response,
            AssistantConversationState conversation
    ) {
        String safeMessage = message == null ? "" : message.trim();
        String operation = operationFor(response);
        String relation = "NEW_TASK";
        SemanticFrame.Reference reference = SemanticFrame.Reference.empty();
        if (isContextFollowUp(safeMessage, conversation)) {
            operation = "RESPOND";
            relation = "FOLLOW_UP";
            reference = contextReference(conversation);
        } else if (isContextMutationFollowUp(safeMessage, operation, conversation)) {
            relation = "FOLLOW_UP";
            reference = contextReference(conversation);
        } else if (isSearchCorrection(safeMessage, response, conversation)) {
            operation = "SEARCH";
            relation = "CORRECTION";
        }

        SemanticFrame.Query query = "CORRECTION".equals(relation) && conversation != null
                ? queryFromEntities(conversation.entities())
                : queryFromResponse(response);
        SemanticFrame.Scope scope = "CORRECTION".equals(relation) && conversation != null
                ? scopeFromEntities(conversation.entities())
                : scopeFromResponse(response);
        if ("SEARCH".equals(operation)) {
            SearchSemantics semantics = parseSearch(safeMessage, query, scope);
            query = semantics.query();
            scope = semantics.scope();
        } else if ("UPLOAD".equals(operation)) {
            scope = destinationScope(safeMessage, UPLOAD_DESTINATION, scope);
        } else if ("MOVE".equals(operation)) {
            scope = destinationScope(safeMessage, MOVE_DESTINATION, scope);
        }

        return new SemanticFrame(
                SemanticFrame.VERSION,
                relation,
                operation,
                query,
                scope,
                reference,
                response == null ? 0.0 : response.confidence(),
                List.of(),
                SemanticFrame.Clarification.empty()
        );
    }

    private SearchSemantics parseSearch(
            String message,
            SemanticFrame.Query fallbackQuery,
            SemanticFrame.Scope fallbackScope
    ) {
        Matcher filterMatcher = NAME_FILTER.matcher(message);
        if (filterMatcher.find()) {
            String clue = cleanName(filterMatcher.group(1));
            String resultType = resultType(filterMatcher.group(2), message, "ANY");
            return new SearchSemantics(
                    new SemanticFrame.Query("NAME_CONTAINS", resultType, clue, normalizeName(clue), fallbackQuery.filters()),
                    new SemanticFrame.Scope("ALL", "", "")
            );
        }

        Matcher directoryMatcher = NAMED_DIRECTORY_CONTENT.matcher(message);
        if (!directoryMatcher.find()) {
            directoryMatcher = NAMED_LOCATION_CONTENT.matcher(message);
        }
        if (directoryMatcher.find(0)) {
            String folder = cleanFolderSurface(directoryMatcher.group(1));
            String resultType = resultType(directoryMatcher.group(2), message, "ANY");
            String scopeType = rootScope(folder);
            return new SearchSemantics(
                    new SemanticFrame.Query("LIST_CHILDREN", resultType, "", "", fallbackQuery.filters()),
                    new SemanticFrame.Scope(
                            scopeType,
                            "NAMED_FOLDER".equals(scopeType) ? folder : "",
                            "NAMED_FOLDER".equals(scopeType) ? normalizeName(folder) : ""
                    )
            );
        }

        if (looksLikeExplicitRootList(message)) {
            return new SearchSemantics(
                    new SemanticFrame.Query("LIST_CHILDREN", resultType("", message, fallbackQuery.resultType()), "", "", fallbackQuery.filters()),
                    new SemanticFrame.Scope(message.contains("当前") ? "CURRENT" : "ROOT", "", "")
            );
        }

        String clue = cleanSearchClue(message);
        if (!clue.isBlank()) {
            String resultType = resultType("", message, fallbackQuery.resultType());
            return new SearchSemantics(
                    new SemanticFrame.Query("NAME_SEARCH", resultType, clue, normalizeName(clue), fallbackQuery.filters()),
                    new SemanticFrame.Scope("ALL", "", "")
            );
        }

        return new SearchSemantics(fallbackQuery, fallbackScope);
    }

    private SemanticFrame applyMessageSemantics(
            String message,
            SemanticFrame frame,
            AssistantConversationState conversation
    ) {
        String safeMessage = message == null ? "" : message.trim();
        SearchSemantics directoryContents = List.of("UNKNOWN", "SEARCH").contains(frame.operation())
                ? parseDirectoryContents(safeMessage, frame.query())
                : null;
        if (directoryContents != null) {
            SemanticFrame.Scope parsedScope = directoryContents.scope();
            if (isPreviousFolderReference(parsedScope.folderSurface())) {
                boolean hasFolderContext = hasSingleFolderContext(conversation);
                SemanticFrame.Clarification clarification = hasFolderContext
                        ? SemanticFrame.Clarification.empty()
                        : new SemanticFrame.Clarification(
                        "missing_folder_reference",
                        "我还不知道“这个文件夹”指的是哪一个，请先找到或选择一个文件夹。",
                        List.of("找到测试目录", "打开项目资料文件夹", "列出根目录文件夹")
                );
                return copyFrame(
                        frame,
                        "FOLLOW_UP",
                        "SEARCH",
                        directoryContents.query(),
                        new SemanticFrame.Scope("PREVIOUS_RESULTS", "", ""),
                        contextReference(conversation),
                        hasFolderContext ? List.of() : List.of("folder_reference"),
                        clarification
                );
            }
            return copyFrame(
                    frame,
                    "NEW_TASK",
                    "SEARCH",
                    directoryContents.query(),
                    parsedScope,
                    SemanticFrame.Reference.empty(),
                    List.of(),
                    SemanticFrame.Clarification.empty()
            );
        }

        if (looksLikeNavigation(safeMessage) && List.of("UNKNOWN", "SEARCH", "NAVIGATE", "OPEN_FILE").contains(frame.operation())) {
            if (TextSupport.containsAny(safeMessage, List.of("根目录", "云盘首页"))) {
                return copyFrame(
                        frame,
                        "NEW_TASK",
                        "NAVIGATE",
                        new SemanticFrame.Query("LIST_CHILDREN", "FOLDER", "", "", frame.query().filters()),
                        new SemanticFrame.Scope("ROOT", "", ""),
                        SemanticFrame.Reference.empty(),
                        List.of(),
                        SemanticFrame.Clarification.empty()
                );
            }
            if (TextSupport.containsAny(safeMessage, List.of("上一级", "上一层", "父目录"))) {
                return copyFrame(
                        frame,
                        "NEW_TASK",
                        "NAVIGATE",
                        new SemanticFrame.Query("LIST_CHILDREN", "FOLDER", "", "", frame.query().filters()),
                        new SemanticFrame.Scope("PARENT", "", ""),
                        SemanticFrame.Reference.empty(),
                        List.of(),
                        SemanticFrame.Clarification.empty()
                );
            }
            String operation = navigationOperation(safeMessage, frame.query().resultType(), conversation);
            if (isGenericNavigationReference(safeMessage) && conversation != null && conversation.focus() != null
                    && conversation.focus().hasSingleCandidateFocus()) {
                CandidateItem candidate = conversation.focus().effectiveCandidate();
                String resultType = candidate == null ? frame.query().resultType() : candidate.type();
                return copyFrame(
                        frame,
                        "FOLLOW_UP",
                        operationForCandidate(candidate, operation),
                        new SemanticFrame.Query("NAME_EXACT", resultType, "", "", frame.query().filters()),
                        new SemanticFrame.Scope("PREVIOUS_RESULTS", "", ""),
                        contextReference(conversation),
                        List.of(),
                        SemanticFrame.Clarification.empty()
                );
            }
            if (isGenericNavigationReference(safeMessage)
                    && conversation != null
                    && conversation.focus() != null
                    && conversation.focus().candidateCount() > 1) {
                return copyFrame(
                        frame,
                        "FOLLOW_UP",
                        operation,
                        new SemanticFrame.Query("NAME_EXACT", frame.query().resultType(), "", "", frame.query().filters()),
                        new SemanticFrame.Scope("PREVIOUS_RESULTS", "", ""),
                        new SemanticFrame.Reference("PREVIOUS_CANDIDATE_SET", null, null),
                        List.of("candidate_reference"),
                        new SemanticFrame.Clarification(
                                "candidate_reference",
                                "上一轮有多个候选，请告诉我要打开第几个，或者直接点选对应文件或文件夹。",
                                List.of("打开第一个", "打开第二个")
                        )
                );
            }
            return copyFrame(
                    frame,
                    "NEW_TASK",
                    operation,
                    frame.query(),
                    frame.scope(),
                    frame.reference(),
                    frame.ambiguities(),
                    frame.clarification()
            );
        }

        Integer ordinal = ordinalIndex(safeMessage);
        if (ordinal != null && List.of("DELETE", "MOVE", "RENAME", "SHARE").contains(frame.operation())) {
            return copyFrame(
                    frame,
                    "FOLLOW_UP",
                    frame.operation(),
                    frame.query(),
                    frame.scope(),
                    ordinalReference(conversation, ordinal),
                    frame.ambiguities(),
                    frame.clarification()
            );
        }
        return frame;
    }

    private boolean looksLikeNavigation(String message) {
        return TextSupport.containsAny(message, List.of("打开", "进入", "进去", "跳转到", "前往", "回到", "返回", "open"));
    }

    private boolean isGenericNavigationReference(String message) {
        String value = normalizeName(message)
                .replace("打开", "")
                .replace("进入", "")
                .replace("进去", "")
                .replace("跳转到", "")
                .replace("前往", "")
                .trim();
        return List.of("文件", "这个文件", "文件夹", "这个文件夹", "目录", "这个目录", "它", "那个").contains(value);
    }

    private String navigationOperation(String message, String resultType, AssistantConversationState conversation) {
        if ("FILE".equalsIgnoreCase(resultType) || message.contains("文件") && !message.contains("文件夹")) {
            return "OPEN_FILE";
        }
        if (conversation != null && conversation.focus() != null && conversation.focus().effectiveCandidate() != null) {
            return operationForCandidate(conversation.focus().effectiveCandidate(), "NAVIGATE");
        }
        return "NAVIGATE";
    }

    private String operationForCandidate(CandidateItem candidate, String fallback) {
        if (candidate == null) {
            return fallback;
        }
        return "FILE".equalsIgnoreCase(candidate.type()) ? "OPEN_FILE" : "NAVIGATE";
    }

    private SearchSemantics parseDirectoryContents(String message, SemanticFrame.Query fallbackQuery) {
        Matcher directoryMatcher = NAMED_DIRECTORY_CONTENT.matcher(message);
        if (!directoryMatcher.find()) {
            directoryMatcher = NAMED_LOCATION_CONTENT.matcher(message);
        }
        if (!directoryMatcher.find(0)) {
            return null;
        }
        String folder = cleanFolderSurface(directoryMatcher.group(1));
        String type = resultType(directoryMatcher.group(2), message, "ANY");
        String scopeType = isPreviousFolderReference(folder) ? "PREVIOUS_RESULTS" : rootScope(folder);
        return new SearchSemantics(
                new SemanticFrame.Query("LIST_CHILDREN", type, "", "", fallbackQuery.filters()),
                new SemanticFrame.Scope(
                        scopeType,
                        "NAMED_FOLDER".equals(scopeType) || "PREVIOUS_RESULTS".equals(scopeType) ? folder : "",
                        "NAMED_FOLDER".equals(scopeType) ? normalizeName(folder) : ""
                )
        );
    }

    private SemanticFrame copyFrame(
            SemanticFrame source,
            String relation,
            String operation,
            SemanticFrame.Query query,
            SemanticFrame.Scope scope,
            SemanticFrame.Reference reference,
            List<String> ambiguities,
            SemanticFrame.Clarification clarification
    ) {
        return new SemanticFrame(
                SemanticFrame.VERSION,
                relation,
                operation,
                query,
                scope,
                reference,
                source.confidence(),
                ambiguities,
                clarification
        );
    }

    private SemanticFrame validate(
            SemanticFrame frame,
            IntentRecognitionResponse response,
            AssistantClientContext clientContext
    ) {
        String relation = allowed(frame.relation(), RELATIONS, "NEW_TASK");
        String operation = allowed(frame.operation(), OPERATIONS, operationFor(response));
        String mode = allowed(frame.query().mode(), QUERY_MODES, "NONE");
        String nameSurface = cleanReferentialObjectSuffix(frame.query().nameSurface());
        String resultType = allowed(frame.query().resultType(), RESULT_TYPES, "ANY");
        if ("ANY".equals(resultType)
                && !"LIST_CHILDREN".equals(mode)
                && (nameSurface.endsWith("文件夹") || nameSurface.endsWith("目录"))) {
            resultType = "FOLDER";
        }
        String scopeType = allowed(frame.scope().type(), SCOPE_TYPES, "ALL");
        SemanticFrame.Query query = new SemanticFrame.Query(
                mode,
                resultType,
                nameSurface,
                frame.query().nameNormalized().isBlank()
                        ? normalizeName(nameSurface)
                        : normalizeName(nameSurface),
                safeFilters(frame.query().filters())
        );
        SemanticFrame.Scope scope = new SemanticFrame.Scope(
                scopeType,
                frame.scope().folderSurface(),
                frame.scope().folderNormalized().isBlank()
                        ? normalizeName(frame.scope().folderSurface())
                        : frame.scope().folderNormalized()
        );

        List<String> ambiguities = new ArrayList<>(frame.ambiguities());
        SemanticFrame.Clarification clarification = frame.clarification();
        if ("UNKNOWN".equals(operation)) {
            addAmbiguity(ambiguities, "operation");
            clarification = clarification(
                    clarification,
                    "operation",
                    "我还没能准确判断你想进行哪种操作，可以再明确一点吗？",
                    List.of("查找云盘文件", "查看文件夹内容", "上传文件")
            );
        } else if ("SEARCH".equals(operation)
                && !"LIST_CHILDREN".equals(mode)
                && query.nameSurface().isBlank()
                && query.filters().isEmpty()) {
            addAmbiguity(ambiguities, "search_query");
            clarification = clarification(
                    clarification,
                    "search_query",
                    "你想查找什么文件或文件夹？可以告诉我名称、后缀或类型。",
                    List.of("查找名字带测试的文件夹", "查找 PDF 文件", "列出当前文件夹")
            );
        } else if ("SEARCH".equals(operation)
                && "LIST_CHILDREN".equals(mode)
                && "NAMED_FOLDER".equals(scope.type())
                && scope.folderSurface().isBlank()) {
            addAmbiguity(ambiguities, "target_folder");
            clarification = clarification(
                    clarification,
                    "target_folder",
                    "你想查看哪个文件夹里的内容？请告诉我文件夹名称。",
                    List.of("列出测试目录下的文件", "列出根目录文件", "列出当前文件夹")
            );
        } else if ("UPLOAD".equals(operation)
                && !"ROOT".equals(scope.type())
                && scope.folderSurface().isBlank()) {
            addAmbiguity(ambiguities, "target_folder");
            clarification = clarification(
                    clarification,
                    "target_folder",
                    "你想把文件上传到哪个云盘文件夹？",
                    List.of("上传到根目录", "上传到测试目录", "上传到当前文件夹")
            );
        }

        double confidence = frame.confidence();
        if (!ambiguities.isEmpty()) {
            confidence = Math.min(confidence, 0.69);
        }
        return new SemanticFrame(
                SemanticFrame.VERSION,
                relation,
                operation,
                query,
                scope,
                new SemanticFrame.Reference(
                        allowed(frame.reference().type(), REFERENCE_TYPES, "NONE"),
                        frame.reference().candidateId(),
                        frame.reference().candidateIndex()
                ),
                confidence,
                ambiguities,
                clarification
        );
    }

    private SemanticFrame.Clarification clarification(
            SemanticFrame.Clarification current,
            String reason,
            String question,
            List<String> suggestions
    ) {
        if (current != null && !current.question().isBlank()) {
            return current;
        }
        return new SemanticFrame.Clarification(reason, question, suggestions);
    }

    private void addAmbiguity(List<String> ambiguities, String value) {
        if (!ambiguities.contains(value)) {
            ambiguities.add(value);
        }
    }

    private SemanticFrame.Query queryFromResponse(IntentRecognitionResponse response) {
        return queryFromEntities(response == null ? Map.of() : response.entities());
    }

    private SemanticFrame.Query queryFromEntities(Map<String, Object> source) {
        Map<String, Object> entities = source == null ? Map.of() : source;
        String rawMode = text(entities.get("query_mode"));
        String mode = switch (rawMode.toLowerCase(Locale.ROOT)) {
            case "directory_list", "list", "list_children" -> "LIST_CHILDREN";
            case "name_search", "search", "keyword_search" -> "NAME_SEARCH";
            default -> entities.containsKey("target_name") ? "NAME_SEARCH" : "NONE";
        };
        String resultType = text(entities.get("result_type"));
        String name = text(entities.get("target_name"));
        Map<String, Object> filters = new LinkedHashMap<>();
        List.of("extension", "file_type", "time_range").forEach(key -> {
            if (entities.containsKey(key)) {
                filters.put(key, entities.get(key));
            }
        });
        if (name.isBlank() && !filters.isEmpty() && "NONE".equals(mode)) {
            mode = "FILTER";
        }
        return new SemanticFrame.Query(
                mode,
                resultType.isBlank() ? "ANY" : resultType,
                name,
                normalizeName(name),
                filters
        );
    }

    private SemanticFrame.Scope scopeFromResponse(IntentRecognitionResponse response) {
        return scopeFromEntities(response == null ? Map.of() : response.entities());
    }

    private SemanticFrame.Scope scopeFromEntities(Map<String, Object> source) {
        Map<String, Object> entities = source == null ? Map.of() : source;
        String targetFolder = text(entities.get("target_folder"));
        String type = text(entities.get("scope"));
        if (type.isBlank()) {
            type = targetFolder.isBlank() ? "ALL" : rootScope(targetFolder);
        }
        return new SemanticFrame.Scope(type, "NAMED_FOLDER".equalsIgnoreCase(type) ? targetFolder : "", normalizeName(targetFolder));
    }

    private SemanticFrame.Scope destinationScope(
            String message,
            Pattern pattern,
            SemanticFrame.Scope fallback
    ) {
        Matcher matcher = pattern.matcher(message == null ? "" : message);
        if (!matcher.find()) {
            return fallback;
        }
        String folder = cleanDestination(matcher.group(1));
        String type = rootScope(folder);
        return new SemanticFrame.Scope(
                type,
                "NAMED_FOLDER".equals(type) ? folder : "",
                "NAMED_FOLDER".equals(type) ? normalizeName(folder) : ""
        );
    }

    private boolean isSearchCorrection(
            String message,
            IntentRecognitionResponse response,
            AssistantConversationState conversation
    ) {
        if (conversation == null || !"file_search".equals(conversation.pendingIntentId())) {
            return false;
        }
        if (response != null && !"fallback".equals(response.intentId())) {
            return startsLikeCorrection(message);
        }
        return startsLikeCorrection(message)
                || TextSupport.containsAny(message, List.of("名字", "名称", "文件名", "文件夹", "目录", "文件", "文档"));
    }

    private boolean isContextFollowUp(String message, AssistantConversationState conversation) {
        if (conversation == null || conversation.focus() == null || !conversation.focus().hasCandidateContext()) {
            return false;
        }
        String value = message == null ? "" : message.trim();
        boolean hasReference = TextSupport.containsAny(value, List.of(
                "它", "这个", "那个", "刚才", "上一个", "前一个", "第一个", "第二个", "第三个",
                "另一个", "另外一个", "下一个", "剩下的", "剩余的", "其余的"
        ));
        boolean asksProperty = TextSupport.containsAny(value, List.of(
                "什么格式", "什么类型", "多大", "大小", "后缀", "扩展名", "名称", "名字", "什么时候", "修改时间", "路径"
        ));
        return hasReference && asksProperty;
    }

    private boolean isContextMutationFollowUp(
            String message,
            String operation,
            AssistantConversationState conversation
    ) {
        if (conversation == null
                || conversation.focus() == null
                || !conversation.focus().hasCandidateContext()
                || !List.of("DELETE", "MOVE", "RENAME", "SHARE").contains(operation)) {
            return false;
        }
        return TextSupport.containsAny(message, List.of(
                "它", "这个", "那个", "刚才", "上一个", "前一个", "第一个", "第二个", "第三个",
                "另一个", "另外一个", "下一个", "剩下的", "剩余的", "其余的"
        ));
    }

    private SemanticFrame.Reference contextReference(AssistantConversationState conversation) {
        if (conversation == null || conversation.focus() == null) {
            return new SemanticFrame.Reference("PREVIOUS_CANDIDATE_SET", null, null);
        }
        CandidateBindingResult binding = conversation.candidateBinding();
        if (binding != null && binding.selectedCandidate() != null) {
            return new SemanticFrame.Reference("SELECTED_CANDIDATE", binding.selectedCandidate().nodeId(), null);
        }
        if (conversation.focus().hasSingleCandidateFocus() && conversation.focus().effectiveCandidate() != null) {
            return new SemanticFrame.Reference(
                    "PREVIOUS_CANDIDATE",
                    conversation.focus().effectiveCandidate().nodeId(),
                    null
            );
        }
        return new SemanticFrame.Reference("PREVIOUS_CANDIDATE_SET", null, null);
    }

    private SemanticFrame.Reference ordinalReference(AssistantConversationState conversation, int ordinal) {
        if (conversation != null
                && conversation.focus() != null
                && conversation.focus().candidateBinding() != null
                && ordinal > 0
                && ordinal <= conversation.focus().candidateBinding().candidates().size()) {
            CandidateItem candidate = conversation.focus().candidateBinding().candidates().get(ordinal - 1);
            return new SemanticFrame.Reference("SELECTED_CANDIDATE", candidate.nodeId(), ordinal);
        }
        return new SemanticFrame.Reference("PREVIOUS_CANDIDATE_SET", null, ordinal);
    }

    private boolean hasSingleFolderContext(AssistantConversationState conversation) {
        if (conversation == null || conversation.focus() == null || !conversation.focus().hasSingleCandidateFocus()) {
            return false;
        }
        CandidateItem candidate = conversation.focus().effectiveCandidate();
        return candidate != null && "FOLDER".equalsIgnoreCase(candidate.type()) && candidate.nodeId() != null;
    }

    private boolean isPreviousFolderReference(String value) {
        String normalized = normalizeName(value);
        return List.of(
                "这个文件夹", "这个目录", "那个文件夹", "那个目录", "该文件夹", "该目录",
                "刚才那个文件夹", "刚才那个目录", "上一个文件夹", "上一个目录"
        ).contains(normalized);
    }

    private Integer ordinalIndex(String message) {
        Matcher matcher = Pattern.compile("第\\s*(\\d+|一|二|三|四|五|六|七|八|九|十)\\s*个?").matcher(message == null ? "" : message);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1);
        return switch (value) {
            case "一" -> 1;
            case "二" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> Integer.parseInt(value);
        };
    }

    private String cleanReferentialObjectSuffix(String value) {
        String surface = cleanName(value);
        Matcher matcher = REFERENTIAL_OBJECT_SUFFIX.matcher(surface);
        if (matcher.matches() && !matcher.group(1).isBlank()) {
            return cleanName(matcher.group(1));
        }
        return surface;
    }

    private Map<String, Object> safeFilters(Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        filters.forEach((key, value) -> {
            if (FILTER_KEYS.contains(key) && value != null && !String.valueOf(value).isBlank()) {
                safe.put(key, value);
            }
        });
        return Map.copyOf(safe);
    }

    private boolean startsLikeCorrection(String message) {
        String value = message == null ? "" : message.trim();
        return value.startsWith("是")
                || value.startsWith("不是")
                || value.startsWith("不对")
                || value.startsWith("我是说")
                || value.startsWith("改成")
                || value.endsWith("呢？")
                || value.endsWith("呢?");
    }

    private String operationFor(IntentRecognitionResponse response) {
        if (response == null) {
            return "UNKNOWN";
        }
        if (response.intentId() != null && response.intentId().startsWith("assistant_")) {
            return "RESPOND";
        }
        String action = response.actionDraft() == null ? "" : response.actionDraft().type();
        return switch (action == null ? "" : action) {
            case "search" -> "SEARCH";
            case "upload_target", "composite.create_folder_then_upload" -> "UPLOAD";
            case "delete", "collection.trash_by_name_contains", "collection.trash_by_category" -> "DELETE";
            case "collection.move_exact", "collection.move_by_category", "collection.move_by_extension", "collection.move_by_name_contains", "move" -> "MOVE";
            case "rename", "collection.rename_add_prefix" -> "RENAME";
            case "share" -> "SHARE";
            case "folder.create" -> "CREATE_FOLDER";
            default -> "fallback".equals(response.intentId()) ? "UNKNOWN" : "RESPOND";
        };
    }

    private String cleanSearchClue(String message) {
        String value = message == null ? "" : message.trim();
        value = value.replaceFirst("^(?:你|请|麻烦)?(?:给我|帮我)?\\s*", "");
        value = value.replaceFirst("^(?:我(?:想要|想|要)|想要|想)\\s*", "");
        value = value.replaceFirst("^(?:能不能|能否|可不可以|可以不可以|可以)\\s*", "");
        value = value.replaceFirst("^(?:把|将|给)\\s*", "");
        Matcher commandMatcher = SEARCH_COMMAND.matcher(value);
        if (commandMatcher.find() && commandMatcher.start() == 0) {
            value = value.substring(commandMatcher.end()).trim();
        }
        value = value.replaceFirst("^(?:是|不是|不对|我是说|改成)\\s*", "");
        value = value.replaceFirst("^(?:有没有|是否有|有无|名为|名称为|叫做|叫)\\s*", "");
        value = value.replaceFirst("(?:列出来|列出|找出来|展示出来|显示出来)$", "");
        value = value.replaceFirst("的(?:文件|文档|资料)$", "");
        value = value.replaceAll("[呢吗呀啊吧\\s？?。！!]+$", "");
        return cleanName(value);
    }

    private String cleanFolderSurface(String value) {
        String folder = value == null ? "" : value.trim();
        folder = folder.replaceFirst("^(?:你|请|麻烦)?(?:给我|帮我)?\\s*", "");
        folder = folder.replaceFirst("^(?:列出|列一下|展示|显示|查看|看看|看下|浏览)\\s*", "");
        folder = folder.replaceFirst("^在\\s*", "");
        return cleanName(folder);
    }

    private String cleanDestination(String value) {
        String folder = cleanName(value);
        folder = folder.replaceAll("(?:中|里|内|下)(?:面)?(?:吧)?$", "");
        folder = folder.replaceAll("(?:吧|呢)$", "");
        return folder.trim();
    }

    private String cleanName(String value) {
        String name = value == null ? "" : value.trim();
        name = name.replaceAll("^[\\\"'“”]+|[\\\"'“”]+$", "");
        return TextSupport.sanitizeNodeName(name);
    }

    private String normalizeName(String value) {
        return cleanName(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String rootScope(String folder) {
        String normalized = normalizeName(folder);
        if (List.of("根", "根目录", "根文件夹", "云盘根目录", "/").contains(normalized)) {
            return "ROOT";
        }
        if (List.of("当前", "当前目录", "当前文件夹").contains(normalized)) {
            return "CURRENT";
        }
        return folder == null || folder.isBlank() ? "ALL" : "NAMED_FOLDER";
    }

    private boolean looksLikeExplicitRootList(String message) {
        return TextSupport.containsAny(message, List.of("列出", "列一下", "展示", "显示", "有哪些", "list", "show"))
                && TextSupport.containsAny(message, List.of("根目录", "根文件夹", "当前目录", "当前文件夹"));
    }

    private String resultType(String explicitType, String message, String fallback) {
        String value = explicitType == null ? "" : explicitType.trim();
        String text = message == null ? "" : message;
        if (text.matches(".*(?:文件或文件夹|文件和文件夹|文件及文件夹|文件、文件夹).*")) {
            return "ANY";
        }
        if (value.contains("文件夹") || "目录".equals(value)) {
            return "FOLDER";
        }
        if (value.contains("文件") || value.contains("文档")) {
            return "FILE";
        }
        if (text.contains("文件夹") || text.matches(".*(?:目录列表|有哪些目录|所有目录).*")) {
            return "FOLDER";
        }
        if (text.matches(".*(?:目录下的文件|目录里的文件|目录中的文件|目录的文件|文件夹下的文件|文件夹里的文件|文件列表|有哪些文件|所有文件).*")) {
            return "FILE";
        }
        String safeFallback = fallback == null ? "" : fallback.trim().toUpperCase(Locale.ROOT);
        return RESULT_TYPES.contains(safeFallback) ? safeFallback : "ANY";
    }

    private String allowed(String value, Set<String> allowed, String fallback) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (allowed.contains(normalized)) {
            return normalized;
        }
        String safeFallback = fallback == null ? "" : fallback.trim().toUpperCase(Locale.ROOT);
        return allowed.contains(safeFallback) ? safeFallback : allowed.iterator().next();
    }

    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).filter(item -> !item.isBlank()).toList();
    }

    private String string(Map<String, Object> map, String key, String fallback) {
        String value = text(map.get(key));
        return value.isBlank() ? fallback : value;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private double decimal(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? 0.0 : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private record SearchSemantics(SemanticFrame.Query query, SemanticFrame.Scope scope) {
    }
}
