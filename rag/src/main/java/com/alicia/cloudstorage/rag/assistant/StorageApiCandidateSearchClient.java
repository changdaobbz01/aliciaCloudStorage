package com.alicia.cloudstorage.rag.assistant;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class StorageApiCandidateSearchClient implements CandidateSearchPort {

    private final StorageApiNodeReadClient storageApi;

    public StorageApiCandidateSearchClient(StorageApiNodeReadClient storageApi) {
        this.storageApi = storageApi;
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
        StorageApiNodePage page = storageApi.searchNodes(new StorageApiNodeQuery(
                null,
                true,
                request.query(),
                null,
                null,
                1,
                request.maxResults(),
                "updatedAt",
                "desc"
        ), request.authorizationHeader());
        Map<Long, CandidateItem> folderById = storageApi.safeFolderMap(request.authorizationHeader());
        List<CandidateItem> candidates = storageApi.enrichWithPaths(page.items(), folderById).stream()
                .limit(Math.max(1, request.maxResults()))
                .toList();
        return result(request, candidates, "cloud-storage-api:/api/storage/nodes");
    }

    private CandidateBindingResult searchFolders(CandidateSearchRequest request) {
        List<CandidateItem> allFolders = storageApi.fetchAllFolders(request.authorizationHeader());
        Map<Long, CandidateItem> folderById = storageApi.folderMap(allFolders);
        String query = request.query().toLowerCase(Locale.ROOT);
        List<CandidateItem> candidates = storageApi.enrichWithPaths(allFolders, folderById).stream()
                .filter(candidate -> candidate.name() != null && candidate.name().toLowerCase(Locale.ROOT).contains(query))
                .limit(Math.max(1, request.maxResults()))
                .toList();
        return result(request, candidates, "cloud-storage-api:/api/storage/folders");
    }

    private CandidateBindingResult result(
            CandidateSearchRequest request,
            List<CandidateItem> candidates,
            String source
    ) {
        if ("search".equalsIgnoreCase(request.actionType())) {
            String message = candidates.isEmpty()
                    ? "未匹配到候选文件或目录，可调整线索后重新检索。"
                    : "已匹配到 " + candidates.size() + " 个候选，可展示给用户。";
            return new CandidateBindingResult(
                    "search_results_ready",
                    source,
                    request.query(),
                    request.candidateType(),
                    candidates,
                    message
            );
        }

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
