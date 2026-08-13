package com.alicia.cloudstorage.rag.assistant;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class StorageApiCollectionPreviewClient implements CollectionPreviewPort {

    private static final int API_PAGE_SIZE = 100;

    private final StorageApiNodeReadClient storageApi;

    public StorageApiCollectionPreviewClient(StorageApiNodeReadClient storageApi) {
        this.storageApi = storageApi;
    }

    @Override
    public CollectionPreviewResult preview(CollectionPreviewRequest request) {
        if (!storageApi.isConfigured()) {
            return CollectionPreviewResult.skipped("storage_api_not_configured", "未配置 CloudStorageApi 集合预览地址。");
        }
        if (request.authorizationHeader().isBlank()) {
            return CollectionPreviewResult.skipped("missing_authorization", "缺少用户 Authorization，无法生成集合预览。");
        }

        QueryPlan queryPlan = QueryPlan.from(request.filter());
        if (!queryPlan.hasMeaningfulSelector()) {
            return CollectionPreviewResult.skipped("missing_filter", "缺少可用于集合预览的筛选条件。");
        }
        if (queryPlan.unsupported()) {
            return CollectionPreviewResult.skipped("unsupported_filter", "当前集合筛选条件暂不支持预览。");
        }

        try {
            return previewWithClientFiltering(request, queryPlan);
        } catch (RuntimeException exception) {
            return CollectionPreviewResult.skipped("storage_api_error", "集合预览查询暂时不可用。");
        }
    }

    private CollectionPreviewResult previewWithApiCount(CollectionPreviewRequest request, QueryPlan queryPlan) {
        StorageApiNodePage page = storageApi.searchNodes(queryPlan.toStorageQuery(1, request.maxPreviewItems()), request.authorizationHeader());
        Map<Long, CandidateItem> folderById = storageApi.safeFolderMap(request.authorizationHeader());
        List<CandidateItem> candidates = storageApi.enrichWithPaths(page.items(), folderById);
        int totalCount = safeInt(page.totalItems());
        return new CollectionPreviewResult(
                totalCount == 0 ? "no_candidates" : "preview_ready",
                "cloud-storage-api:/api/storage/nodes",
                request.filter(),
                candidates,
                totalCount,
                true,
                totalCount == 0 ? "未匹配到集合候选。" : "已生成 " + totalCount + " 项集合候选预览。"
        );
    }

    private CollectionPreviewResult previewWithClientFiltering(CollectionPreviewRequest request, QueryPlan queryPlan) {
        List<CandidateItem> matched = new ArrayList<>();
        long scanned = 0L;
        long totalItems = 0L;
        int pageNumber = 1;

        while (scanned < request.maxScanItems()) {
            int pageSize = (int) Math.min(API_PAGE_SIZE, request.maxScanItems() - scanned);
            StorageApiNodePage page = storageApi.searchNodes(queryPlan.toStorageQuery(pageNumber, pageSize), request.authorizationHeader());
            totalItems = page.totalItems();
            for (CandidateItem item : page.items()) {
                scanned++;
                if (queryPlan.matchesClientFilters(item)) {
                    matched.add(item);
                }
            }
            if (page.items().isEmpty() || scanned >= totalItems || pageNumber >= page.totalPages()) {
                break;
            }
            pageNumber++;
        }

        boolean exact = scanned >= totalItems;
        Map<Long, CandidateItem> folderById = storageApi.safeFolderMap(request.authorizationHeader());
        List<CandidateItem> candidates = storageApi.enrichWithPaths(matched, folderById);
        int totalCount = safeInt(matched.size());
        String status = totalCount == 0 ? "no_candidates" : exact ? "preview_ready" : "preview_incomplete";
        String message = switch (status) {
            case "no_candidates" -> exact ? "未匹配到集合候选。" : "扫描范围内未匹配到集合候选，请缩小范围或提高扫描上限。";
            case "preview_incomplete" -> "集合预览超过当前扫描上限，结果不完整，暂不能作为全量批处理依据。";
            default -> "已生成 " + totalCount + " 项集合候选预览。";
        };
        return new CollectionPreviewResult(
                status,
                "cloud-storage-api:/api/storage/nodes",
                request.filter(),
                candidates,
                totalCount,
                exact,
                message
        );
    }

    private int safeInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, value);
    }

    private record QueryPlan(
            Long parentId,
            boolean rootSelector,
            boolean recursive,
            String keyword,
            String exactName,
            String type,
            String category,
            String extension,
            String mimeType,
            boolean unsupported
    ) {
        private static QueryPlan from(Map<String, Object> filter) {
            String nameContains = stringValue(filter.get("nameContains"));
            String exactName = stringValue(filter.get("exactName"));
            String category = normalizeCategory(stringValue(filter.get("category")));
            String extension = normalizeExtension(stringValue(filter.get("extension")));
            String mimeType = normalizeLower(stringValue(filter.get("mimeType")));
            Long parentId = longValue(filter.get("parentId"));
            boolean rootSelector = booleanValue(filter.get("root"));
            boolean directChildren = booleanValue(filter.get("directChildren"));
            boolean includeFolders = booleanValue(filter.get("includeFolders"));
            boolean unsupportedCategory = hasValue(filter.get("category")) && category.isBlank();
            String requestedNodeType = stringValue(filter.get("nodeType")).toUpperCase(Locale.ROOT);
            String type = List.of("FILE", "FOLDER").contains(requestedNodeType)
                    ? requestedNodeType
                    : includeFolders ? "" : "FILE";
            String scope = stringValue(filter.get("scope"));
            boolean allDrive = scope.isBlank()
                    || "all_drive".equalsIgnoreCase(scope)
                    || "all".equalsIgnoreCase(scope);
            boolean unsupportedScope = !scope.isBlank()
                    && !allDrive
                    && !"root".equalsIgnoreCase(scope)
                    && parentId == null;
            return new QueryPlan(
                    parentId,
                    rootSelector || "root".equalsIgnoreCase(scope),
                    parentId == null && allDrive && !directChildren,
                    exactName.isBlank() ? nameContains : exactName,
                    exactName,
                    type,
                    category,
                    extension,
                    mimeType,
                    unsupportedCategory || unsupportedScope
            );
        }

        private boolean hasMeaningfulSelector() {
            return parentId != null
                    || rootSelector
                    || !keyword.isBlank()
                    || !category.isBlank()
                    || !extension.isBlank()
                    || !mimeType.isBlank();
        }

        private boolean needsClientFiltering() {
            return !exactName.isBlank() || !extension.isBlank() || !mimeType.isBlank();
        }

        private StorageApiNodeQuery toStorageQuery(int page, int size) {
            return new StorageApiNodeQuery(
                    parentId,
                    recursive,
                    keyword,
                    type,
                    category,
                    page,
                    size,
                    "updatedAt",
                    "desc"
            );
        }

        private boolean matchesClientFilters(CandidateItem item) {
            if (!exactName.isBlank() && !exactName.equalsIgnoreCase(item.name())) {
                return false;
            }
            if (!extension.isBlank() && !extension.equals(normalizeExtension(item.extension()))) {
                return false;
            }
            return mimeType.isBlank() || normalizeLower(item.mimeType()).contains(mimeType);
        }

        private static String normalizeCategory(String value) {
            if (value.isBlank()) {
                return "";
            }
            Map<String, String> aliases = new LinkedHashMap<>();
            aliases.put("图片", "IMAGE");
            aliases.put("照片", "IMAGE");
            aliases.put("image", "IMAGE");
            aliases.put("photo", "IMAGE");
            aliases.put("视频", "VIDEO");
            aliases.put("video", "VIDEO");
            aliases.put("音频", "AUDIO");
            aliases.put("音乐", "AUDIO");
            aliases.put("audio", "AUDIO");
            aliases.put("music", "AUDIO");
            aliases.put("文档", "DOCUMENT");
            aliases.put("document", "DOCUMENT");
            aliases.put("doc", "DOCUMENT");
            aliases.put("压缩包", "ARCHIVE");
            aliases.put("压缩文件", "ARCHIVE");
            aliases.put("archive", "ARCHIVE");
            aliases.put("zip", "ARCHIVE");
            return aliases.getOrDefault(value.toLowerCase(Locale.ROOT), "");
        }

        private static String normalizeExtension(String value) {
            String normalized = normalizeLower(value);
            return normalized.startsWith(".") ? normalized.substring(1) : normalized;
        }

        private static String normalizeLower(String value) {
            return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        }

        private static String stringValue(Object value) {
            return value == null ? "" : String.valueOf(value).trim();
        }

        private static boolean hasValue(Object value) {
            return value != null && !String.valueOf(value).trim().isBlank();
        }

        private static boolean booleanValue(Object value) {
            if (value instanceof Boolean bool) {
                return bool;
            }
            return value != null && Boolean.parseBoolean(String.valueOf(value));
        }

        private static Long longValue(Object value) {
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value == null || String.valueOf(value).isBlank()) {
                return null;
            }
            try {
                return Long.parseLong(String.valueOf(value).trim());
            } catch (NumberFormatException exception) {
                return null;
            }
        }
    }
}
