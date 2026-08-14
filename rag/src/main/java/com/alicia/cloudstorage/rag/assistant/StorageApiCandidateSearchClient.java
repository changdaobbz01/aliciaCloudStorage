package com.alicia.cloudstorage.rag.assistant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StorageApiCandidateSearchClient implements CandidateSearchPort {

    private final StorageApiNodeReadClient storageApi;
    private final CandidateQueryPlanner queryPlanner;

    @Autowired
    public StorageApiCandidateSearchClient(
            StorageApiNodeReadClient storageApi,
            CandidateQueryPlanner queryPlanner
    ) {
        this.storageApi = storageApi;
        this.queryPlanner = queryPlanner;
    }

    StorageApiCandidateSearchClient(StorageApiNodeReadClient storageApi) {
        this(storageApi, CandidateQueryPlanner.defaults());
    }

    @Override
    public CandidateBindingResult search(CandidateSearchRequest request) {
        if (!storageApi.isConfigured()) {
            return CandidateBindingResult.skipped("storage_api_not_configured", "未配置 CloudStorageApi 只读候选查询地址。");
        }
        if (request.authorizationHeader() == null || request.authorizationHeader().isBlank()) {
            return CandidateBindingResult.skipped("missing_authorization", "缺少用户 Authorization，已跳过真实候选绑定。");
        }
        if (!isDirectoryList(request) && (request.query() == null || request.query().isBlank())) {
            return CandidateBindingResult.skipped("missing_query", "缺少可用于候选绑定的自然语言线索。");
        }
        if (!request.category().isBlank() && StorageFileCategory.normalize(request.category()).isBlank()) {
            return CandidateBindingResult.skipped("unsupported_filter", "暂不支持该文件类型筛选。");
        }

        try {
            if (isDirectoryList(request)) {
                return listDirectory(request);
            }
            return "FOLDER".equalsIgnoreCase(request.candidateType())
                    ? searchFolders(request)
                    : searchNodes(request);
        } catch (RuntimeException exception) {
            return CandidateBindingResult.skipped("storage_api_error", "候选查询暂时不可用。");
        }
    }

    private CandidateBindingResult listDirectory(CandidateSearchRequest request) {
        String scope = request.scope().toLowerCase();
        return switch (scope) {
            case FileQueryPlanResolver.SCOPE_ROOT -> listNodesInScope(request, null, false, "根目录");
            case FileQueryPlanResolver.SCOPE_CURRENT -> listNodesInScope(
                    request,
                    request.currentFolderId(),
                    false,
                    request.currentFolderPath().isBlank() ? "当前目录" : request.currentFolderPath()
            );
            case FileQueryPlanResolver.SCOPE_NAMED_FOLDER -> listNamedFolder(request);
            case FileQueryPlanResolver.SCOPE_ALL -> listNodesInScope(request, null, true, "全部云盘");
            default -> listNodesInScope(request, request.currentFolderId(), false, "当前目录");
        };
    }

    private CandidateBindingResult listNamedFolder(CandidateSearchRequest request) {
        if (request.targetFolder().isBlank()) {
            return CandidateBindingResult.skipped("missing_query", "缺少要列出内容的目录范围。");
        }

        List<CandidateItem> allFolders = storageApi.fetchAllFolders(request.authorizationHeader());
        Map<Long, CandidateItem> folderById = storageApi.folderMap(allFolders);
        CandidateSearchRequest folderQuery = new CandidateSearchRequest(
                request.intentId(),
                request.actionType(),
                "FOLDER",
                "target_folder",
                request.targetFolder(),
                FileQueryPlanResolver.NAME_SEARCH,
                FileQueryPlanResolver.SCOPE_ALL,
                "",
                "",
                request.currentFolderId(),
                request.currentFolderPath(),
                request.maxResults(),
                request.authorizationHeader()
        );
        List<String> variants = queryPlanner.variants(folderQuery);
        List<CandidateItem> matchingFolders = storageApi.enrichWithPaths(allFolders, folderById).stream()
                .filter(candidate -> queryPlanner.matchScore(candidate, variants) < Integer.MAX_VALUE)
                .sorted(Comparator.comparingInt(candidate -> queryPlanner.matchScore(candidate, variants)))
                .limit(request.maxResults())
                .toList();
        if (matchingFolders.isEmpty()) {
            return directoryListResult(request, List.of(), "cloud-storage-api:/api/storage/folders", request.targetFolder());
        }

        Map<String, CandidateItem> candidatesByKey = new LinkedHashMap<>();
        for (CandidateItem folder : matchingFolders) {
            if (folder.nodeId() == null || candidatesByKey.size() >= request.maxResults()) {
                continue;
            }
            fetchDirectoryNodes(request, folder.nodeId(), false, folderById).forEach(candidate ->
                    candidatesByKey.putIfAbsent(candidateKey(candidate), candidate)
            );
        }
        return directoryListResult(
                request,
                candidatesByKey.values().stream().limit(request.maxResults()).toList(),
                "cloud-storage-api:/api/storage/nodes",
                request.targetFolder()
        );
    }

    private CandidateBindingResult listNodesInScope(
            CandidateSearchRequest request,
            Long parentId,
            boolean recursive,
            String scopeLabel
    ) {
        Map<Long, CandidateItem> folderById = storageApi.safeFolderMap(request.authorizationHeader());
        List<CandidateItem> candidates = fetchDirectoryNodes(request, parentId, recursive, folderById).stream()
                .limit(request.maxResults())
                .toList();
        return directoryListResult(
                request,
                candidates,
                "cloud-storage-api:/api/storage/nodes",
                scopeLabel
        );
    }

    private List<CandidateItem> fetchDirectoryNodes(
            CandidateSearchRequest request,
            Long parentId,
            boolean recursive,
            Map<Long, CandidateItem> folderById
    ) {
        StorageApiNodePage page = storageApi.searchNodes(new StorageApiNodeQuery(
                parentId,
                recursive,
                "",
                nodeTypeFilter(request),
                categoryFilter(request),
                1,
                request.maxResults(),
                "updatedAt",
                "desc"
        ), request.authorizationHeader());
        return storageApi.enrichWithPaths(page.items(), folderById).stream()
                .filter(candidate -> matchesCandidateType(candidate, request.candidateType()))
                .filter(candidate -> StorageFileCategory.matches(request.category(), candidate))
                .toList();
    }

    private CandidateBindingResult directoryListResult(
            CandidateSearchRequest request,
            List<CandidateItem> candidates,
            String source,
            String scopeLabel
    ) {
        String typeLabel = switch (request.candidateType().toUpperCase()) {
            case "FILE" -> request.category().isBlank() ? "文件" : StorageFileCategory.label(request.category());
            case "FOLDER" -> "文件夹";
            default -> "内容";
        };
        if (candidates.isEmpty()) {
            return new CandidateBindingResult(
                    "no_candidates",
                    source,
                    scopeLabel,
                    request.candidateType(),
                    candidates,
                    scopeLabel + "下没有可展示的" + typeLabel + "。"
            );
        }
        return new CandidateBindingResult(
                "search_results_ready",
                source,
                scopeLabel,
                request.candidateType(),
                candidates,
                "已列出" + scopeLabel + "下的 " + candidates.size() + " 个" + typeLabel + "。"
        );
    }

    private CandidateBindingResult searchNodes(CandidateSearchRequest request) {
        List<String> queryVariants = queryPlanner.variants(request);
        Map<String, CandidateItem> candidatesByKey = new LinkedHashMap<>();
        Map<Long, CandidateItem> folderById = storageApi.safeFolderMap(request.authorizationHeader());

        for (String query : queryVariants) {
            if (candidatesByKey.size() >= request.maxResults()) {
                break;
            }
            StorageApiNodePage page = storageApi.searchNodes(new StorageApiNodeQuery(
                    null,
                    true,
                    query,
                    nodeTypeFilter(request),
                    categoryFilter(request),
                    1,
                    request.maxResults(),
                    "updatedAt",
                    "desc"
            ), request.authorizationHeader());
            storageApi.enrichWithPaths(page.items(), folderById).stream()
                    .filter(candidate -> matchesCandidateType(candidate, request.candidateType()))
                    .filter(candidate -> StorageFileCategory.matches(request.category(), candidate))
                    .forEach(candidate -> candidatesByKey.putIfAbsent(candidateKey(candidate), candidate));
        }

        List<CandidateItem> candidates = narrowToExactMatches(
                rank(candidatesByKey.values().stream().toList(), queryVariants),
                queryVariants
        ).stream()
                .limit(Math.max(1, request.maxResults()))
                .toList();
        return result(request, candidates, "cloud-storage-api:/api/storage/nodes");
    }

    private CandidateBindingResult searchFolders(CandidateSearchRequest request) {
        List<CandidateItem> allFolders = storageApi.fetchAllFolders(request.authorizationHeader());
        Map<Long, CandidateItem> folderById = storageApi.folderMap(allFolders);
        List<String> queryVariants = queryPlanner.variants(request);
        List<CandidateItem> rankedCandidates = storageApi.enrichWithPaths(allFolders, folderById).stream()
                .filter(candidate -> queryPlanner.matchScore(candidate, queryVariants) < Integer.MAX_VALUE)
                .sorted(Comparator.comparingInt(candidate -> queryPlanner.matchScore(candidate, queryVariants)))
                .toList();
        List<CandidateItem> candidates = narrowToExactMatches(rankedCandidates, queryVariants).stream()
                .limit(Math.max(1, request.maxResults()))
                .toList();
        return result(request, candidates, "cloud-storage-api:/api/storage/folders");
    }

    private List<CandidateItem> rank(List<CandidateItem> candidates, List<String> queryVariants) {
        List<CandidateItem> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator.comparingInt(candidate -> queryPlanner.matchScore(candidate, queryVariants)));
        return List.copyOf(ranked);
    }

    private List<CandidateItem> narrowToExactMatches(
            List<CandidateItem> candidates,
            List<String> queryVariants
    ) {
        if (candidates == null || candidates.isEmpty() || queryVariants == null || queryVariants.isEmpty()) {
            return candidates == null ? List.of() : candidates;
        }
        for (String variant : queryVariants) {
            String surface = normalizeMatchValue(variant);
            if (surface.isBlank()) {
                continue;
            }
            List<CandidateItem> exactMatches = candidates.stream()
                    .filter(candidate -> normalizeMatchValue(candidate.name()).equals(surface)
                            || normalizeMatchValue(candidate.path()).equals(surface))
                    .toList();
            if (!exactMatches.isEmpty()) {
                return exactMatches;
            }
        }
        return candidates;
    }

    private String normalizeMatchValue(String value) {
        return value == null ? "" : value.trim().toLowerCase().replaceAll("\\s+", "");
    }

    private String nodeTypeFilter(CandidateSearchRequest request) {
        String candidateType = request.candidateType() == null ? "" : request.candidateType().trim();
        return List.of("FILE", "FOLDER").stream()
                .filter(type -> type.equalsIgnoreCase(candidateType))
                .findFirst()
                .orElse("");
    }

    private String categoryFilter(CandidateSearchRequest request) {
        return StorageFileCategory.normalize(request.category());
    }

    private boolean matchesCandidateType(CandidateItem candidate, String candidateType) {
        if (candidate == null || candidateType == null || candidateType.isBlank()) {
            return true;
        }
        if ("ANY".equalsIgnoreCase(candidateType) || "NODE".equalsIgnoreCase(candidateType)) {
            return true;
        }
        return candidateType.equalsIgnoreCase(candidate.type());
    }

    private String candidateKey(CandidateItem candidate) {
        if (candidate.nodeId() != null) {
            return "id:" + candidate.nodeId();
        }
        return "natural:" + candidate.type() + ":" + candidate.path() + ":" + candidate.name();
    }

    private CandidateBindingResult result(
            CandidateSearchRequest request,
            List<CandidateItem> candidates,
            String source
    ) {
        if (isSearchAction(request)) {
            return searchResult(request, candidates, source);
        }

        return operationBindingResult(request, candidates, source);
    }

    private boolean isSearchAction(CandidateSearchRequest request) {
        return "search".equalsIgnoreCase(request.actionType());
    }

    private boolean isDirectoryList(CandidateSearchRequest request) {
        return FileQueryPlanResolver.DIRECTORY_LIST.equalsIgnoreCase(request.queryMode());
    }

    private CandidateBindingResult searchResult(
            CandidateSearchRequest request,
            List<CandidateItem> candidates,
            String source
    ) {
        if (candidates.isEmpty()) {
            return new CandidateBindingResult(
                    "no_candidates",
                    source,
                    request.query(),
                    request.candidateType(),
                    candidates,
                    "未匹配到候选文件或目录，可调整线索后重新检索。"
            );
        }

        return new CandidateBindingResult(
                "search_results_ready",
                source,
                request.query(),
                request.candidateType(),
                candidates,
                "已匹配到 " + candidates.size() + " 个候选，可展示给用户。"
        );
    }

    private CandidateBindingResult operationBindingResult(
            CandidateSearchRequest request,
            List<CandidateItem> candidates,
            String source
    ) {
        String status = switch (candidates.size()) {
            case 0 -> "no_candidates";
            case 1 -> "single_candidate";
            default -> "multiple_candidates";
        };
        String message = switch (status) {
            case "no_candidates" -> "未匹配到候选文件或目录。";
            case "single_candidate" -> "已匹配到 1 个候选，等待用户确认。";
            default -> "匹配到多个候选，需要用户选择。";
        };
        return new CandidateBindingResult(status, source, request.query(), request.candidateType(), candidates, message);
    }
}
