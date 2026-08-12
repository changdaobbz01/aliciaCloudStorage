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
        if (request.query() == null || request.query().isBlank()) {
            return CandidateBindingResult.skipped("missing_query", "缺少可用于候选绑定的自然语言线索。");
        }

        try {
            return "FOLDER".equalsIgnoreCase(request.candidateType())
                    ? searchFolders(request)
                    : searchNodes(request);
        } catch (RuntimeException exception) {
            return CandidateBindingResult.skipped("storage_api_error", "候选查询暂时不可用。");
        }
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
                    null,
                    1,
                    request.maxResults(),
                    "updatedAt",
                    "desc"
            ), request.authorizationHeader());
            storageApi.enrichWithPaths(page.items(), folderById).stream()
                    .filter(candidate -> matchesCandidateType(candidate, request.candidateType()))
                    .forEach(candidate -> candidatesByKey.putIfAbsent(candidateKey(candidate), candidate));
        }

        List<CandidateItem> candidates = rank(candidatesByKey.values().stream().toList(), queryVariants).stream()
                .limit(Math.max(1, request.maxResults()))
                .toList();
        return result(request, candidates, "cloud-storage-api:/api/storage/nodes");
    }

    private CandidateBindingResult searchFolders(CandidateSearchRequest request) {
        List<CandidateItem> allFolders = storageApi.fetchAllFolders(request.authorizationHeader());
        Map<Long, CandidateItem> folderById = storageApi.folderMap(allFolders);
        List<String> queryVariants = queryPlanner.variants(request);
        List<CandidateItem> candidates = storageApi.enrichWithPaths(allFolders, folderById).stream()
                .filter(candidate -> queryPlanner.matchScore(candidate, queryVariants) < Integer.MAX_VALUE)
                .sorted(Comparator.comparingInt(candidate -> queryPlanner.matchScore(candidate, queryVariants)))
                .limit(Math.max(1, request.maxResults()))
                .toList();
        return result(request, candidates, "cloud-storage-api:/api/storage/folders");
    }

    private List<CandidateItem> rank(List<CandidateItem> candidates, List<String> queryVariants) {
        List<CandidateItem> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator.comparingInt(candidate -> queryPlanner.matchScore(candidate, queryVariants)));
        return List.copyOf(ranked);
    }

    private String nodeTypeFilter(CandidateSearchRequest request) {
        String candidateType = request.candidateType() == null ? "" : request.candidateType().trim();
        return "FILE".equalsIgnoreCase(candidateType) ? "FILE" : "";
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
