package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class FileQueryPlanResolver {

    static final String NAME_SEARCH = "name_search";
    static final String DIRECTORY_LIST = "directory_list";
    static final String SCOPE_ALL = "all";
    static final String SCOPE_ROOT = "root";
    static final String SCOPE_CURRENT = "current";
    static final String SCOPE_NAMED_FOLDER = "named_folder";

    private final Settings settings;

    @Autowired
    public FileQueryPlanResolver(RagConfigLoader configLoader) {
        this(Settings.from(configLoader.loadJson("rag/conversation/query_rules.json")
                .path("candidate_binding")
                .path("semantic_query")));
    }

    static FileQueryPlanResolver defaults() {
        return new FileQueryPlanResolver(Settings.defaults());
    }

    FileQueryPlanResolver(Settings settings) {
        this.settings = settings;
    }

    FileQueryPlan resolve(
            IntentRouter.IntentDefinition intent,
            IntentRecognitionResponse response,
            String defaultQueryRole,
            String defaultQuery
    ) {
        if (intent == null || response == null || !"search".equalsIgnoreCase(intent.actionType())) {
            return new FileQueryPlan(
                    NAME_SEARCH,
                    SCOPE_ALL,
                    intent == null ? "ANY" : intent.candidateType(),
                    "",
                    defaultQueryRole,
                    defaultQuery
            );
        }

        Map<String, Object> entities = response.entities() == null ? Map.of() : response.entities();
        String message = response.message() == null ? "" : response.message();
        String queryMode = canonical(entities.get("query_mode"), settings.queryModeAliases());
        if (queryMode.isBlank() && isLocalFallback(response) && looksLikeDirectoryList(message)) {
            queryMode = DIRECTORY_LIST;
        }
        if (queryMode.isBlank()) {
            queryMode = NAME_SEARCH;
        }

        String targetFolder = TextSupport.sanitizeNodeName(stringValue(entities.get("target_folder")));
        String scope = canonical(entities.get("scope"), settings.scopeAliases());
        if (scope.isBlank()) {
            scope = inferScope(message, targetFolder, queryMode);
        }
        if (!targetFolder.isBlank()) {
            String targetScope = canonical(targetFolder, settings.scopeAliases());
            if (SCOPE_ROOT.equals(targetScope) || SCOPE_CURRENT.equals(targetScope)) {
                scope = targetScope;
                targetFolder = "";
            } else if (DIRECTORY_LIST.equals(queryMode)) {
                scope = SCOPE_NAMED_FOLDER;
            }
        }

        String resultType = canonical(entities.get("result_type"), settings.resultTypeAliases());
        if (resultType.isBlank()) {
            resultType = resultTypeFromFileType(entities.get("file_type"));
        }
        if (resultType.isBlank() && isLocalFallback(response) && DIRECTORY_LIST.equals(queryMode)) {
            resultType = inferResultType(message);
        }
        if (resultType.isBlank()) {
            resultType = normalizeCandidateType(intent.candidateType());
        }

        return new FileQueryPlan(
                queryMode,
                scope,
                resultType,
                targetFolder,
                DIRECTORY_LIST.equals(queryMode) ? "directory_scope" : defaultQueryRole,
                DIRECTORY_LIST.equals(queryMode) ? "" : defaultQuery
        );
    }

    private boolean isLocalFallback(IntentRecognitionResponse response) {
        return "local_fallback".equalsIgnoreCase(response.provider());
    }

    private boolean looksLikeDirectoryList(String message) {
        return TextSupport.containsAny(message, settings.listVerbs())
                && TextSupport.containsAny(message, settings.listContextTerms());
    }

    private String inferScope(String message, String targetFolder, String queryMode) {
        String configuredScope = canonicalContained(message, settings.scopeAliases());
        if (!configuredScope.isBlank()) {
            return configuredScope;
        }
        if (!targetFolder.isBlank()) {
            return SCOPE_NAMED_FOLDER;
        }
        return DIRECTORY_LIST.equals(queryMode) ? SCOPE_CURRENT : SCOPE_ALL;
    }

    private String inferResultType(String message) {
        if (TextSupport.containsAny(message, settings.folderResultPhrases())) {
            return "FOLDER";
        }
        if (TextSupport.containsAny(message, settings.fileResultPhrases())) {
            return "FILE";
        }
        return "ANY";
    }

    private String resultTypeFromFileType(Object value) {
        String raw = stringValue(value);
        if (raw.isBlank()) {
            return "";
        }
        String canonical = canonical(raw, settings.resultTypeAliases());
        return canonical.isBlank() ? "FILE" : canonical;
    }

    private String normalizeCandidateType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return List.of("FILE", "FOLDER", "ANY", "NODE").contains(normalized)
                ? ("NODE".equals(normalized) ? "ANY" : normalized)
                : "ANY";
    }

    private String canonical(Object value, Map<String, String> aliases) {
        return canonical(stringValue(value), aliases);
    }

    private String canonical(String value, Map<String, String> aliases) {
        return aliases.getOrDefault(normalize(value), "");
    }

    private String canonicalContained(String value, Map<String, String> aliases) {
        String normalized = normalize(value);
        return aliases.entrySet().stream()
                .filter(entry -> !entry.getKey().isBlank() && normalized.contains(entry.getKey()))
                .sorted((left, right) -> Integer.compare(right.getKey().length(), left.getKey().length()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("");
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String normalize(String value) {
        if ("/".equals(value == null ? "" : value.trim())) {
            return "/";
        }
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s，。,.!?！？:：;；]+", "");
    }

    record FileQueryPlan(
            String queryMode,
            String scope,
            String resultType,
            String targetFolder,
            String queryRole,
            String query
    ) {
    }

    record Settings(
            Map<String, String> queryModeAliases,
            Map<String, String> scopeAliases,
            Map<String, String> resultTypeAliases,
            List<String> listVerbs,
            List<String> listContextTerms,
            List<String> folderResultPhrases,
            List<String> fileResultPhrases
    ) {
        private static Settings from(JsonNode node) {
            Settings defaults = defaults();
            if (node == null || !node.isObject()) {
                return defaults;
            }
            JsonNode fallback = node.path("local_fallback");
            return new Settings(
                    aliasMap(node.path("query_mode_aliases"), defaults.queryModeAliases()),
                    aliasMap(node.path("scope_aliases"), defaults.scopeAliases()),
                    aliasMap(node.path("result_type_aliases"), defaults.resultTypeAliases()),
                    stringList(fallback.path("list_verbs"), defaults.listVerbs()),
                    stringList(fallback.path("list_context_terms"), defaults.listContextTerms()),
                    stringList(fallback.path("folder_result_phrases"), defaults.folderResultPhrases()),
                    stringList(fallback.path("file_result_phrases"), defaults.fileResultPhrases())
            );
        }

        static Settings defaults() {
            return new Settings(
                    aliases(Map.of(
                            NAME_SEARCH, List.of(NAME_SEARCH, "search", "keyword_search", "按名称搜索", "名称检索"),
                            DIRECTORY_LIST, List.of(DIRECTORY_LIST, "list", "list_children", "browse", "列目录", "目录列表")
                    )),
                    aliases(Map.of(
                            SCOPE_ALL, List.of(SCOPE_ALL, "global", "cloud", "全部", "全盘", "整个云盘"),
                            SCOPE_ROOT, List.of(SCOPE_ROOT, "根", "根目录", "云盘根目录", "/"),
                            SCOPE_CURRENT, List.of(SCOPE_CURRENT, "current_folder", "当前", "当前目录", "当前文件夹"),
                            SCOPE_NAMED_FOLDER, List.of(SCOPE_NAMED_FOLDER, "folder", "指定目录")
                    )),
                    aliases(Map.of(
                            "ANY", List.of("ANY", "NODE", "all", "内容", "全部"),
                            "FILE", List.of("FILE", "files", "文件", "文档", "图片", "视频", "音频", "压缩包"),
                            "FOLDER", List.of("FOLDER", "folders", "directory", "directories", "文件夹", "目录")
                    )),
                    List.of("列出", "列一下", "展示", "显示", "有哪些", "list", "show"),
                    List.of("目录", "文件夹", "列表", "内容", "云盘"),
                    List.of("文件夹列表", "目录列表", "有哪些文件夹", "所有文件夹"),
                    List.of("文件列表", "有哪些文件", "所有文件", "目录下的文件", "目录里的文件", "目录的文件", "文件夹下的文件", "文件夹里的文件")
            );
        }

        private static Map<String, String> aliasMap(JsonNode node, Map<String, String> fallback) {
            if (node == null || !node.isObject()) {
                return fallback;
            }
            Map<String, List<String>> groups = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> groups.put(
                    entry.getKey(),
                    stringList(entry.getValue(), List.of(entry.getKey()))
            ));
            return aliases(groups);
        }

        private static Map<String, String> aliases(Map<String, List<String>> groups) {
            Map<String, String> result = new LinkedHashMap<>();
            groups.forEach((canonical, values) -> {
                result.put(normalize(canonical), canonical);
                values.forEach(value -> result.put(normalize(value), canonical));
            });
            return Map.copyOf(result);
        }

        private static List<String> stringList(JsonNode node, List<String> fallback) {
            if (node == null || !node.isArray()) {
                return fallback;
            }
            List<String> values = new ArrayList<>();
            node.forEach(item -> {
                String value = item.asText("").trim();
                if (!value.isBlank()) {
                    values.add(value);
                }
            });
            return values.isEmpty() ? fallback : List.copyOf(values);
        }
    }
}
