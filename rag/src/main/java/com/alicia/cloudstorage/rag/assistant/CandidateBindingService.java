package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class CandidateBindingService {

    private final CandidateSearchPort candidateSearchPort;
    private final IntentRouter intentRouter;
    private final FileQueryPlanResolver fileQueryPlanResolver;
    private final int maxResults;
    private final int directoryListMaxResults;
    private final Set<String> virtualRootAliases;
    private final Set<String> virtualRootActions;

    @Autowired
    public CandidateBindingService(
            CandidateSearchPort candidateSearchPort,
            IntentRouter intentRouter,
            @Value("${alicia.rag.candidate-binding.max-results:5}") int maxResults,
            @Value("${alicia.rag.candidate-binding.directory-list-max-results:50}") int directoryListMaxResults,
            RagConfigLoader configLoader,
            FileQueryPlanResolver fileQueryPlanResolver
    ) {
        this(
                candidateSearchPort,
                intentRouter,
                fileQueryPlanResolver,
                maxResults,
                directoryListMaxResults,
                loadVirtualRootConfig(configLoader)
        );
    }

    CandidateBindingService(
            CandidateSearchPort candidateSearchPort,
            IntentRouter intentRouter,
            int maxResults
    ) {
        this(
                candidateSearchPort,
                intentRouter,
                FileQueryPlanResolver.defaults(),
                maxResults,
                50,
                VirtualRootConfig.defaults()
        );
    }

    private CandidateBindingService(
            CandidateSearchPort candidateSearchPort,
            IntentRouter intentRouter,
            FileQueryPlanResolver fileQueryPlanResolver,
            int maxResults,
            int directoryListMaxResults,
            VirtualRootConfig virtualRootConfig
    ) {
        this.candidateSearchPort = candidateSearchPort;
        this.intentRouter = intentRouter;
        this.fileQueryPlanResolver = fileQueryPlanResolver;
        this.maxResults = Math.max(1, maxResults);
        this.directoryListMaxResults = Math.max(this.maxResults, directoryListMaxResults);
        this.virtualRootAliases = virtualRootConfig.aliases();
        this.virtualRootActions = virtualRootConfig.actions();
    }

    public CandidateBindingResult bind(IntentRecognitionResponse response, String authorizationHeader) {
        return bind(response, authorizationHeader, AssistantClientContext.empty());
    }

    public CandidateBindingResult bind(
            IntentRecognitionResponse response,
            String authorizationHeader,
            AssistantClientContext clientContext
    ) {
        if (response == null) {
            return CandidateBindingResult.skipped("not_requested", "没有可绑定的识别结果。");
        }
        String actionType = response.actionDraft() == null ? "none" : response.actionDraft().type();
        if (actionType == null || actionType.isBlank() || "none".equals(actionType)) {
            return CandidateBindingResult.skipped("not_requested", "当前回复不需要候选绑定。");
        }
        if (!response.missingSlots().isEmpty() || "ask_clarification".equals(response.nextAction())) {
            return CandidateBindingResult.skipped("waiting_for_clarification", "仍有缺失信息，暂不查询真实候选。");
        }
        if (!"wait_for_backend_binding".equals(response.nextAction())) {
            return CandidateBindingResult.skipped("not_requested", "当前步骤不需要候选绑定。");
        }

        IntentRouter.IntentDefinition intent = intentRouter.getIntent(response.intentId());
        if ("NONE".equalsIgnoreCase(intent.candidateType())) {
            return CandidateBindingResult.skipped(
                    "collection_filter_only",
                    "当前意图使用集合筛选条件生成 ActionPlan，暂不执行单对象候选绑定。"
            );
        }
        QueryRoleAndValue query = bindingQuery(intent, response);
        if (isVirtualRootTarget(intent.actionType(), query)) {
            CandidateItem root = new CandidateItem(
                    null,
                    null,
                    "根目录",
                    "FOLDER",
                    0L,
                    "",
                    "",
                    "",
                    "/",
                    List.of()
            );
            return new CandidateBindingResult(
                    "single_candidate",
                    "virtual:cloud-drive-root",
                    query.value(),
                    intent.candidateType(),
                    List.of(root),
                    "已定位到云盘根目录。"
            );
        }
        FileQueryPlanResolver.FileQueryPlan semanticPlan = semanticQueryPlan(response, intent, query);
        if (semanticPlan != null) {
            String rawCategory = fileCategory(response, semanticPlan.resultType());
            String category = StorageFileCategory.normalize(rawCategory);
            if (!rawCategory.isBlank() && category.isBlank()) {
                return CandidateBindingResult.skipped("unsupported_filter", "暂不支持该文件类型筛选，请改用图片、视频、音频、文档或压缩包。");
            }
            return search(
                    response,
                    intent,
                    semanticPlan,
                    category,
                    authorizationHeader,
                    clientContext
            );
        }
        FileQueryPlanResolver.FileQueryPlan queryPlan = fileQueryPlanResolver.resolve(
                intent,
                response,
                query.role(),
                query.value()
        );
        String rawCategory = fileCategory(response, queryPlan.resultType());
        String category = StorageFileCategory.normalize(rawCategory);
        if (!rawCategory.isBlank() && category.isBlank()) {
            return CandidateBindingResult.skipped("unsupported_filter", "暂不支持该文件类型筛选，请改用图片、视频、音频、文档或压缩包。");
        }
        return search(response, intent, queryPlan, category, authorizationHeader, clientContext);
    }

    private CandidateBindingResult search(
            IntentRecognitionResponse response,
            IntentRouter.IntentDefinition intent,
            FileQueryPlanResolver.FileQueryPlan queryPlan,
            String category,
            String authorizationHeader,
            AssistantClientContext clientContext
    ) {
        AssistantClientContext safeClientContext = clientContext == null
                ? AssistantClientContext.empty()
                : clientContext;
        return candidateSearchPort.search(new CandidateSearchRequest(
                response.intentId(),
                intent.actionType(),
                queryPlan.resultType(),
                queryPlan.queryRole(),
                queryPlan.query(),
                queryPlan.queryMode(),
                queryPlan.scope(),
                queryPlan.targetFolder(),
                category,
                safeClientContext.currentFolderId(),
                safeClientContext.currentFolderPath(),
                FileQueryPlanResolver.DIRECTORY_LIST.equals(queryPlan.queryMode())
                        ? directoryListMaxResults
                        : maxResults,
                authorizationHeader == null ? "" : authorizationHeader.trim()
        ));
    }

    private FileQueryPlanResolver.FileQueryPlan semanticQueryPlan(
            IntentRecognitionResponse response,
            IntentRouter.IntentDefinition intent,
            QueryRoleAndValue fallbackQuery
    ) {
        SemanticFrame frame = response.semanticFrame();
        if (frame == null || !SemanticFrame.VERSION.equals(frame.schemaVersion())) {
            return null;
        }
        if ("SEARCH".equals(frame.operation())) {
            boolean filterOnly = "FILTER".equals(frame.query().mode())
                    && frame.query().nameSurface().isBlank();
            String queryMode = "LIST_CHILDREN".equals(frame.query().mode()) || filterOnly
                    ? FileQueryPlanResolver.DIRECTORY_LIST
                    : FileQueryPlanResolver.NAME_SEARCH;
            String queryRole = FileQueryPlanResolver.DIRECTORY_LIST.equals(queryMode)
                    ? "directory_scope"
                    : "FOLDER".equals(frame.query().resultType()) ? "target_folder" : "target_name";
            String scope = switch (frame.scope().type()) {
                case "ROOT" -> FileQueryPlanResolver.SCOPE_ROOT;
                case "CURRENT" -> FileQueryPlanResolver.SCOPE_CURRENT;
                case "PREVIOUS_RESULTS" -> FileQueryPlanResolver.SCOPE_CURRENT;
                case "NAMED_FOLDER" -> FileQueryPlanResolver.SCOPE_NAMED_FOLDER;
                default -> FileQueryPlanResolver.SCOPE_ALL;
            };
            return new FileQueryPlanResolver.FileQueryPlan(
                    queryMode,
                    scope,
                    frame.query().resultType(),
                    frame.scope().folderSurface(),
                    queryRole,
                    FileQueryPlanResolver.DIRECTORY_LIST.equals(queryMode) ? "" : frame.query().nameSurface()
            );
        }
        if (List.of("NAVIGATE", "OPEN_FILE").contains(frame.operation())
                && !frame.query().nameSurface().isBlank()) {
            boolean navigateToFolder = "NAVIGATE".equals(frame.operation());
            return new FileQueryPlanResolver.FileQueryPlan(
                    FileQueryPlanResolver.NAME_SEARCH,
                    FileQueryPlanResolver.SCOPE_ALL,
                    navigateToFolder ? "FOLDER" : "FILE",
                    "",
                    navigateToFolder ? "target_folder" : "target_name",
                    frame.query().nameSurface()
            );
        }
        if ("MOVE".equals(frame.operation())) {
            String targetFolder = TextSupport.sanitizeNodeName(String.valueOf(
                    response.entities().getOrDefault("target_folder", "")
            ));
            if (!targetFolder.isBlank()) {
                return new FileQueryPlanResolver.FileQueryPlan(
                        FileQueryPlanResolver.NAME_SEARCH,
                        FileQueryPlanResolver.SCOPE_ALL,
                        "FOLDER",
                        "",
                        "target_folder",
                        targetFolder
                );
            }
        }
        if (List.of("DELETE", "RENAME", "SHARE").contains(frame.operation())
                && !frame.query().nameSurface().isBlank()) {
            return new FileQueryPlanResolver.FileQueryPlan(
                    FileQueryPlanResolver.NAME_SEARCH,
                    semanticScope(frame),
                    frame.query().resultType(),
                    "NAMED_FOLDER".equals(frame.scope().type()) ? frame.scope().folderSurface() : "",
                    "target_name",
                    frame.query().nameSurface()
            );
        }
        if (List.of("UPLOAD", "CREATE_FOLDER").contains(frame.operation())
                && !frame.scope().folderSurface().isBlank()) {
            return new FileQueryPlanResolver.FileQueryPlan(
                    FileQueryPlanResolver.NAME_SEARCH,
                    FileQueryPlanResolver.SCOPE_ALL,
                    intent.candidateType(),
                    "",
                    "target_folder",
                    frame.scope().folderSurface()
            );
        }
        return null;
    }

    private String semanticScope(SemanticFrame frame) {
        return switch (frame.scope().type()) {
            case "ROOT" -> FileQueryPlanResolver.SCOPE_ROOT;
            case "CURRENT", "PREVIOUS_RESULTS" -> FileQueryPlanResolver.SCOPE_CURRENT;
            case "NAMED_FOLDER" -> FileQueryPlanResolver.SCOPE_NAMED_FOLDER;
            default -> FileQueryPlanResolver.SCOPE_ALL;
        };
    }

    private String fileCategory(IntentRecognitionResponse response, String resultType) {
        if ("FOLDER".equalsIgnoreCase(resultType) || response == null) {
            return "";
        }
        Object entityValue = response.entities() == null ? null : response.entities().get("file_type");
        if (entityValue != null && !String.valueOf(entityValue).isBlank()) {
            return String.valueOf(entityValue).trim();
        }
        SemanticFrame frame = response.semanticFrame();
        if (frame == null || frame.query() == null || frame.query().filters() == null) {
            return "";
        }
        Object filterValue = frame.query().filters().get("file_type");
        return filterValue == null ? "" : String.valueOf(filterValue).trim();
    }

    private boolean isVirtualRootTarget(String actionType, QueryRoleAndValue query) {
        return "target_folder".equals(query.role())
                && virtualRootActions.contains(actionType)
                && virtualRootAliases.contains(normalizeRootAlias(query.value()));
    }

    private static VirtualRootConfig loadVirtualRootConfig(RagConfigLoader configLoader) {
        JsonNode config = configLoader.loadJson("rag/conversation/query_rules.json")
                .path("candidate_binding")
                .path("virtual_roots");
        Set<String> aliases = textSet(config.path("aliases"), true);
        Set<String> actions = textSet(config.path("supported_actions"), false);
        return aliases.isEmpty() || actions.isEmpty()
                ? VirtualRootConfig.defaults()
                : new VirtualRootConfig(aliases, actions);
    }

    private static Set<String> textSet(JsonNode node, boolean normalizeAlias) {
        Set<String> values = new LinkedHashSet<>();
        if (node.isArray()) {
            node.forEach(item -> {
                String value = item.asText("").trim();
                if (!value.isBlank()) {
                    values.add(normalizeAlias ? normalizeRootAlias(value) : value);
                }
            });
        }
        return Set.copyOf(values);
    }

    private static String normalizeRootAlias(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s，。,.!?！？]+", "");
    }

    private QueryRoleAndValue bindingQuery(IntentRouter.IntentDefinition intent, IntentRecognitionResponse response) {
        String role = "FOLDER".equalsIgnoreCase(intent.candidateType()) ? "target_folder" : "target_name";
        Object target = response.entities().get(role);
        if (target != null && !String.valueOf(target).isBlank()) {
            return new QueryRoleAndValue(role, TextSupport.sanitizeNodeName(String.valueOf(target)));
        }
        if (response.normalizedQuery() != null && !response.normalizedQuery().isBlank()) {
            return new QueryRoleAndValue("search_query", TextSupport.sanitizeNodeName(response.normalizedQuery()));
        }
        return new QueryRoleAndValue("message", TextSupport.sanitizeNodeName(response.message()));
    }

    private record QueryRoleAndValue(
            String role,
            String value
    ) {
    }

    private record VirtualRootConfig(
            Set<String> aliases,
            Set<String> actions
    ) {
        private static VirtualRootConfig defaults() {
            return new VirtualRootConfig(
                    Set.of("根", "根目录", "根文件夹", "云盘根目录", "我的云盘", "顶层目录", "最外层", "/"),
                    Set.of("upload_target", "composite.create_folder_then_upload")
            );
        }
    }
}
