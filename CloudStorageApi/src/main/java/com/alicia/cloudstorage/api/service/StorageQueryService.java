package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.DriveOverviewResponse;
import com.alicia.cloudstorage.api.dto.PageResponse;
import com.alicia.cloudstorage.api.dto.StorageNodeSummaryResponse;
import com.alicia.cloudstorage.api.dto.UsageHistoryPointResponse;
import com.alicia.cloudstorage.api.entity.NodeType;
import com.alicia.cloudstorage.api.entity.StorageNode;
import com.alicia.cloudstorage.api.repository.StorageNodeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class StorageQueryService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_USAGE_HISTORY_DAYS = 30;
    private static final int MAX_USAGE_HISTORY_DAYS = 90;

    private final StorageNodeRepository storageNodeRepository;
    private final StorageQuotaService storageQuotaService;

    public StorageQueryService(
            StorageNodeRepository storageNodeRepository,
            StorageQuotaService storageQuotaService
    ) {
        this.storageNodeRepository = storageNodeRepository;
        this.storageQuotaService = storageQuotaService;
    }

    /**
     * 按父级目录、关键字、节点类型、分页和排序条件查询当前用户的文件列表。
     */
    public PageResponse<StorageNodeSummaryResponse> listNodes(
            Long userId,
            Long parentId,
            String keyword,
            String rawType,
            Integer page,
            Integer size,
            String rawSortBy,
            String rawSortDirection
    ) {
        String normalizedKeyword = normalizeKeyword(keyword);
        NodeType nodeType = normalizeNodeType(rawType);
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizePageSize(size);
        String sortBy = normalizeDriveSortBy(rawSortBy);
        Sort.Direction sortDirection = normalizeSortDirection(rawSortDirection, Sort.Direction.ASC);
        Pageable pageable = PageRequest.of(
                normalizedPage - 1,
                normalizedSize,
                buildDriveSort(sortBy, sortDirection)
        );
        Page<StorageNode> nodes = storageNodeRepository.searchNodes(
                userId,
                parentId,
                normalizedKeyword,
                nodeType,
                pageable
        );

        return toPageResponse(nodes, normalizedPage, normalizedSize, sortBy, sortDirection);
    }

    /**
     * 查询当前用户所有可用文件夹，供移动文件或文件夹时选择目标目录。
     */
    public List<StorageNodeSummaryResponse> listFolders(Long userId) {
        return storageNodeRepository.findActiveFolders(userId).stream()
                .sorted((left, right) -> {
                    long leftParentId = left.getParentId() == null ? 0L : left.getParentId();
                    long rightParentId = right.getParentId() == null ? 0L : right.getParentId();
                    int parentCompare = Long.compare(leftParentId, rightParentId);

                    if (parentCompare != 0) {
                        return parentCompare;
                    }

                    return String.CASE_INSENSITIVE_ORDER.compare(left.getNodeName(), right.getNodeName());
                })
                .map(this::toSummary)
                .toList();
    }

    /**
     * 查询回收站根节点，并支持关键字、类型、分页和排序条件筛选。
     */
    public PageResponse<StorageNodeSummaryResponse> listTrashNodes(
            Long userId,
            String keyword,
            String rawType,
            Integer page,
            Integer size,
            String rawSortBy,
            String rawSortDirection
    ) {
        String normalizedKeyword = normalizeKeyword(keyword);
        NodeType nodeType = normalizeNodeType(rawType);
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizePageSize(size);
        String sortBy = normalizeTrashSortBy(rawSortBy);
        Sort.Direction sortDirection = normalizeSortDirection(rawSortDirection, Sort.Direction.DESC);
        Pageable pageable = PageRequest.of(
                normalizedPage - 1,
                normalizedSize,
                buildTrashSort(sortBy, sortDirection)
        );
        Page<StorageNode> nodes = storageNodeRepository.searchTrashNodes(
                userId,
                normalizedKeyword,
                nodeType,
                pageable
        );

        return toPageResponse(nodes, normalizedPage, normalizedSize, sortBy, sortDirection);
    }

    /**
     * 统计当前用户云盘的基础概览数据。
     */
    public DriveOverviewResponse getOverview(Long userId) {
        if (storageQuotaService.isAdmin(userId)) {
            long totalItems = storageNodeRepository.countByDeletedFalse();
            long totalFolders = storageNodeRepository.countByNodeTypeAndDeletedFalse(NodeType.FOLDER);
            long totalFiles = storageNodeRepository.countByNodeTypeAndDeletedFalse(NodeType.FILE);
            long actualUsedBytes = storageQuotaService.getTotalActualUsedBytes();
            long usedBytes = actualUsedBytes;

            return new DriveOverviewResponse(
                    totalItems,
                    totalFolders,
                    totalFiles,
                    usedBytes,
                    null,
                    actualUsedBytes,
                    "ADMIN"
            );
        }

        long totalItems = storageNodeRepository.countByOwnerIdAndDeletedFalse(userId);
        long totalFolders = storageNodeRepository.countByOwnerIdAndNodeTypeAndDeletedFalse(userId, NodeType.FOLDER);
        long totalFiles = storageNodeRepository.countByOwnerIdAndNodeTypeAndDeletedFalse(userId, NodeType.FILE);
        long usedBytes = storageQuotaService.getUsedBytes(userId);
        long totalSpaceBytes = storageQuotaService.getUserQuotaBytes(userId);

        return new DriveOverviewResponse(
                totalItems,
                totalFolders,
                totalFiles,
                usedBytes,
                totalSpaceBytes,
                usedBytes,
                "USER"
        );
    }

    /**
     * 根据现有元数据回算近一段时间每天结束时的已用空间。
     */
    public List<UsageHistoryPointResponse> getUsageHistory(Long userId, Integer days) {
        int normalizedDays = normalizeUsageHistoryDays(days);
        boolean adminView = storageQuotaService.isAdmin(userId);
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(normalizedDays - 1L);
        List<UsageHistoryPointResponse> points = new ArrayList<>();

        for (int offset = 0; offset < normalizedDays; offset += 1) {
            LocalDate date = startDate.plusDays(offset);
            LocalDateTime endOfDay = date.plusDays(1).atStartOfDay().minusNanos(1);
            long usedBytes = adminView
                    ? storageNodeRepository.sumActiveFileSizeAllOwnersAt(endOfDay)
                    : storageNodeRepository.sumActiveFileSizeByOwnerIdAt(userId, endOfDay);

            points.add(new UsageHistoryPointResponse(date, usedBytes));
        }

        return points;
    }

    private PageResponse<StorageNodeSummaryResponse> toPageResponse(
            Page<StorageNode> nodes,
            int page,
            int size,
            String sortBy,
            Sort.Direction sortDirection
    ) {
        return new PageResponse<>(
                nodes.getContent().stream().map(this::toSummary).toList(),
                page,
                size,
                nodes.getTotalElements(),
                nodes.getTotalPages(),
                sortBy,
                sortDirection.name().toLowerCase()
        );
    }

    /**
     * 将存储节点实体转换成列表摘要响应。
     */
    private StorageNodeSummaryResponse toSummary(StorageNode node) {
        return new StorageNodeSummaryResponse(
                node.getId(),
                node.getParentId(),
                node.getNodeName(),
                node.getNodeType().name(),
                node.getFileSize(),
                node.getFileExtension(),
                node.getMimeType(),
                node.getUpdatedAt(),
                node.getDeletedAt()
        );
    }

    /**
     * 规范化关键字筛选参数，避免空白字符串影响查询逻辑。
     */
    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }

    /**
     * 将前端传入的节点类型字符串转换成系统枚举。
     */
    private NodeType normalizeNodeType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return null;
        }

        try {
            return NodeType.valueOf(rawType.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("文件类型筛选值不合法。");
        }
    }

    private int normalizePage(Integer page) {
        if (page == null) {
            return DEFAULT_PAGE;
        }

        if (page < 1) {
            throw new IllegalArgumentException("分页页码必须大于等于 1。");
        }

        return page;
    }

    private int normalizePageSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("分页大小必须介于 1 到 100 之间。");
        }

        return size;
    }

    private int normalizeUsageHistoryDays(Integer days) {
        if (days == null) {
            return DEFAULT_USAGE_HISTORY_DAYS;
        }

        if (days < 1 || days > MAX_USAGE_HISTORY_DAYS) {
            throw new IllegalArgumentException("占用空间趋势天数必须介于 1 到 90 之间。");
        }

        return days;
    }

    private String normalizeDriveSortBy(String rawSortBy) {
        if (rawSortBy == null || rawSortBy.isBlank()) {
            return "name";
        }

        return switch (rawSortBy.trim()) {
            case "name", "size", "updatedAt" -> rawSortBy.trim();
            default -> throw new IllegalArgumentException("文件列表排序字段不合法。");
        };
    }

    private String normalizeTrashSortBy(String rawSortBy) {
        if (rawSortBy == null || rawSortBy.isBlank()) {
            return "deletedAt";
        }

        return switch (rawSortBy.trim()) {
            case "name", "size", "updatedAt", "deletedAt" -> rawSortBy.trim();
            default -> throw new IllegalArgumentException("回收站排序字段不合法。");
        };
    }

    private Sort.Direction normalizeSortDirection(String rawSortDirection, Sort.Direction defaultDirection) {
        if (rawSortDirection == null || rawSortDirection.isBlank()) {
            return defaultDirection;
        }

        try {
            return Sort.Direction.fromString(rawSortDirection.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("排序方向不合法。");
        }
    }

    private Sort buildDriveSort(String sortBy, Sort.Direction sortDirection) {
        List<Sort.Order> orders = new ArrayList<>();
        orders.add(new Sort.Order(Sort.Direction.DESC, "nodeType"));

        switch (sortBy) {
            case "size" -> orders.add(new Sort.Order(sortDirection, "fileSize"));
            case "updatedAt" -> orders.add(new Sort.Order(sortDirection, "updatedAt"));
            case "name" -> orders.add(new Sort.Order(sortDirection, "nodeName"));
            default -> throw new IllegalArgumentException("文件列表排序字段不合法。");
        }

        if (!"name".equals(sortBy)) {
            orders.add(new Sort.Order(Sort.Direction.ASC, "nodeName"));
        }

        orders.add(new Sort.Order(Sort.Direction.ASC, "id"));
        return Sort.by(orders);
    }

    private Sort buildTrashSort(String sortBy, Sort.Direction sortDirection) {
        List<Sort.Order> orders = new ArrayList<>();

        switch (sortBy) {
            case "deletedAt" -> orders.add(new Sort.Order(sortDirection, "deletedAt").nullsLast());
            case "updatedAt" -> orders.add(new Sort.Order(sortDirection, "updatedAt"));
            case "size" -> orders.add(new Sort.Order(sortDirection, "fileSize"));
            case "name" -> orders.add(new Sort.Order(sortDirection, "nodeName"));
            default -> throw new IllegalArgumentException("回收站排序字段不合法。");
        }

        if (!"deletedAt".equals(sortBy)) {
            orders.add(new Sort.Order(Sort.Direction.DESC, "deletedAt").nullsLast());
        }

        if (!"name".equals(sortBy)) {
            orders.add(new Sort.Order(Sort.Direction.ASC, "nodeName"));
        }

        orders.add(new Sort.Order(Sort.Direction.ASC, "id"));
        return Sort.by(orders);
    }
}
