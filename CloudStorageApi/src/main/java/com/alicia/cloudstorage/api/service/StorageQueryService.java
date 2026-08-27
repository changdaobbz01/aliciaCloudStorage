package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.DriveOverviewResponse;
import com.alicia.cloudstorage.api.dto.PageResponse;
import com.alicia.cloudstorage.api.dto.StorageNodeSummaryResponse;
import com.alicia.cloudstorage.api.dto.UsageHistoryPointResponse;
import com.alicia.cloudstorage.api.entity.NodeType;
import com.alicia.cloudstorage.api.entity.StorageNode;
import com.alicia.cloudstorage.api.repository.StorageNodeRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        return listNodesInternal(userId, parentId, false, keyword, rawType, null, page, size, rawSortBy, rawSortDirection);
    }

    public PageResponse<StorageNodeSummaryResponse> listNodes(
            Long userId,
            Long parentId,
            boolean recursive,
            String keyword,
            String rawType,
            Integer page,
            Integer size,
            String rawSortBy,
            String rawSortDirection
    ) {
        return listNodesInternal(userId, parentId, recursive, keyword, rawType, null, page, size, rawSortBy, rawSortDirection);
    }

    public PageResponse<StorageNodeSummaryResponse> listNodes(
            Long userId,
            Long parentId,
            boolean recursive,
            String keyword,
            String rawType,
            String rawCategory,
            Integer page,
            Integer size,
            String rawSortBy,
            String rawSortDirection
    ) {
        return listNodesInternal(userId, parentId, recursive, keyword, rawType, rawCategory, page, size, rawSortBy, rawSortDirection);
    }

    private PageResponse<StorageNodeSummaryResponse> listNodesInternal(
            Long userId,
            Long parentId,
            boolean recursive,
            String keyword,
            String rawType,
            String rawCategory,
            Integer page,
            Integer size,
            String rawSortBy,
            String rawSortDirection
    ) {
        String normalizedKeyword = normalizeKeyword(keyword);
        NodeType nodeType = normalizeNodeType(rawType);
        StorageFileCategory category = StorageFileCategory.fromRaw(rawCategory);
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizePageSize(size);
        String sortBy = normalizeDriveSortBy(rawSortBy);
        Sort.Direction sortDirection = normalizeSortDirection(rawSortDirection, Sort.Direction.ASC);
        Pageable pageable = PageRequest.of(
                normalizedPage - 1,
                normalizedSize,
                buildDriveSort(sortBy, sortDirection)
        );
        if (category != null && (!recursive || parentId == null)) {
            Page<StorageNode> nodes = storageNodeRepository.findAll(
                    buildActiveNodeSpecification(userId, parentId, !recursive, normalizedKeyword, nodeType, category),
                    pageable
            );
            return toPageResponse(nodes, normalizedPage, normalizedSize, sortBy, sortDirection);
        }

        if (recursive) {
            return listNodesRecursively(
                    userId,
                    parentId,
                    normalizedKeyword,
                    nodeType,
                    category,
                    normalizedPage,
                    normalizedSize,
                    sortBy,
                    sortDirection
            );
        }

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
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(normalizedDays - 1L);
        List<UsageHistoryPointResponse> points = new ArrayList<>();

        for (int offset = 0; offset < normalizedDays; offset += 1) {
            LocalDate date = startDate.plusDays(offset);
            LocalDateTime endOfDay = date.plusDays(1).atStartOfDay().minusNanos(1);
            long usedBytes = storageNodeRepository.sumActiveFileSizeByOwnerIdAt(userId, endOfDay);

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

    private PageResponse<StorageNodeSummaryResponse> listNodesRecursively(
            Long userId,
            Long parentId,
            String keyword,
            NodeType nodeType,
            StorageFileCategory category,
            int page,
            int size,
            String sortBy,
            Sort.Direction sortDirection
    ) {
        List<StorageNode> allActiveNodes = storageNodeRepository.findByOwnerIdAndDeletedFalse(userId);
        Set<Long> descendantIds = collectDescendantIds(allActiveNodes, parentId);
        List<StorageNode> filteredNodes = allActiveNodes.stream()
                .filter(node -> descendantIds.contains(node.getId()))
                .filter(node -> keyword == null || node.getNodeName().toLowerCase().contains(keyword.toLowerCase()))
                .filter(node -> nodeType == null || node.getNodeType() == nodeType)
                .filter(node -> category == null || category.matches(node))
                .sorted(buildDriveComparator(sortBy, sortDirection))
                .toList();

        return toPageResponse(filteredNodes, page, size, sortBy, sortDirection);
    }

    private Specification<StorageNode> buildActiveNodeSpecification(
            Long userId,
            Long parentId,
            boolean constrainParent,
            String keyword,
            NodeType nodeType,
            StorageFileCategory category
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("ownerId"), userId));
            predicates.add(criteriaBuilder.isFalse(root.get("deleted")));

            if (constrainParent) {
                predicates.add(parentId == null
                        ? criteriaBuilder.isNull(root.get("parentId"))
                        : criteriaBuilder.equal(root.get("parentId"), parentId));
            }

            if (keyword != null) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("nodeName")),
                        "%" + keyword.toLowerCase() + "%"
                ));
            }

            if (nodeType != null) {
                predicates.add(criteriaBuilder.equal(root.get("nodeType"), nodeType));
            }

            if (category != null) {
                predicates.add(criteriaBuilder.equal(root.get("nodeType"), NodeType.FILE));
                predicates.add(buildCategoryPredicate(category, root.get("fileExtension"), root.get("mimeType"), criteriaBuilder));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Predicate buildCategoryPredicate(
            StorageFileCategory category,
            Expression<String> rawExtension,
            Expression<String> rawMimeType,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder
    ) {
        List<Predicate> predicates = new ArrayList<>();
        Expression<String> extension = criteriaBuilder.lower(rawExtension);
        Expression<String> mimeType = criteriaBuilder.lower(rawMimeType);

        if (!category.extensions().isEmpty()) {
            predicates.add(extension.in(category.extensions()));
        }

        if (!category.exactMimeTypes().isEmpty()) {
            predicates.add(mimeType.in(category.exactMimeTypes()));
        }

        for (String prefix : category.mimePrefixes()) {
            predicates.add(criteriaBuilder.like(mimeType, prefix + "%"));
        }

        return criteriaBuilder.or(predicates.toArray(Predicate[]::new));
    }

    private Set<Long> collectDescendantIds(List<StorageNode> allActiveNodes, Long parentId) {
        Set<Long> descendantIds = new HashSet<>();

        if (parentId == null) {
            allActiveNodes.forEach(node -> descendantIds.add(node.getId()));
            return descendantIds;
        }

        Map<Long, List<StorageNode>> childrenByParentId = new HashMap<>();
        allActiveNodes.forEach(node -> {
            Long normalizedParentId = node.getParentId();
            List<StorageNode> siblings = childrenByParentId.computeIfAbsent(
                    normalizedParentId == null ? 0L : normalizedParentId,
                    ignored -> new ArrayList<>()
            );
            siblings.add(node);
        });

        ArrayDeque<Long> pendingParentIds = new ArrayDeque<>();
        pendingParentIds.add(parentId);

        while (!pendingParentIds.isEmpty()) {
            Long currentParentId = pendingParentIds.removeFirst();
            for (StorageNode child : childrenByParentId.getOrDefault(currentParentId, List.of())) {
                if (descendantIds.add(child.getId()) && child.getNodeType() == NodeType.FOLDER) {
                    pendingParentIds.addLast(child.getId());
                }
            }
        }

        return descendantIds;
    }

    private PageResponse<StorageNodeSummaryResponse> toPageResponse(
            List<StorageNode> nodes,
            int page,
            int size,
            String sortBy,
            Sort.Direction sortDirection
    ) {
        int fromIndex = Math.min((page - 1) * size, nodes.size());
        int toIndex = Math.min(fromIndex + size, nodes.size());
        int totalPages = nodes.isEmpty() ? 0 : (int) Math.ceil((double) nodes.size() / size);

        return new PageResponse<>(
                nodes.subList(fromIndex, toIndex).stream().map(this::toSummary).toList(),
                page,
                size,
                nodes.size(),
                totalPages,
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
            case "name", "size", "updatedAt", "createdAt" -> rawSortBy.trim();
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
            case "createdAt" -> orders.add(new Sort.Order(sortDirection, "createdAt"));
            case "name" -> orders.add(new Sort.Order(sortDirection, "nodeName"));
            default -> throw new IllegalArgumentException("文件列表排序字段不合法。");
        }

        if (!"name".equals(sortBy)) {
            orders.add(new Sort.Order(Sort.Direction.ASC, "nodeName"));
        }

        orders.add(new Sort.Order(Sort.Direction.ASC, "id"));
        return Sort.by(orders);
    }

    private Comparator<StorageNode> buildDriveComparator(String sortBy, Sort.Direction sortDirection) {
        Comparator<StorageNode> comparator = Comparator
                .comparingInt((StorageNode node) -> node.getNodeType() == NodeType.FOLDER ? 0 : 1);

        Comparator<StorageNode> primaryComparator = switch (sortBy) {
            case "size" -> Comparator.comparingLong(StorageNode::getFileSize);
            case "updatedAt" -> Comparator.comparing(StorageNode::getUpdatedAt);
            case "createdAt" -> Comparator.comparing(StorageNode::getCreatedAt);
            case "name" -> Comparator.comparing(StorageNode::getNodeName, String.CASE_INSENSITIVE_ORDER);
            default -> throw new IllegalArgumentException("文件列表排序字段不合法。");
        };

        comparator = comparator.thenComparing(
                sortDirection == Sort.Direction.DESC ? primaryComparator.reversed() : primaryComparator
        );

        if (!"name".equals(sortBy)) {
            comparator = comparator.thenComparing(StorageNode::getNodeName, String.CASE_INSENSITIVE_ORDER);
        }

        return comparator.thenComparing(StorageNode::getId);
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
