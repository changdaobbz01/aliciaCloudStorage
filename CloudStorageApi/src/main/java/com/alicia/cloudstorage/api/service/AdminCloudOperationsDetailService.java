package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.AdminCloudShareLinkResponse;
import com.alicia.cloudstorage.api.dto.AdminCloudStorageUserUsageResponse;
import com.alicia.cloudstorage.api.dto.AdminCloudTrashNodeResponse;
import com.alicia.cloudstorage.api.dto.PageResponse;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.entity.NodeType;
import com.alicia.cloudstorage.api.entity.ShareLink;
import com.alicia.cloudstorage.api.entity.ShareLinkItem;
import com.alicia.cloudstorage.api.entity.ShareLinkStatus;
import com.alicia.cloudstorage.api.entity.StorageNode;
import com.alicia.cloudstorage.api.repository.ShareLinkItemRepository;
import com.alicia.cloudstorage.api.repository.ShareLinkRepository;
import com.alicia.cloudstorage.api.repository.StorageNodeRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AdminCloudOperationsDetailService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminCloudUserDirectoryService adminCloudUserDirectoryService;
    private final StorageNodeRepository storageNodeRepository;
    private final ShareLinkRepository shareLinkRepository;
    private final ShareLinkItemRepository shareLinkItemRepository;

    public AdminCloudOperationsDetailService(
            AdminCloudUserDirectoryService adminCloudUserDirectoryService,
            StorageNodeRepository storageNodeRepository,
            ShareLinkRepository shareLinkRepository,
            ShareLinkItemRepository shareLinkItemRepository
    ) {
        this.adminCloudUserDirectoryService = adminCloudUserDirectoryService;
        this.storageNodeRepository = storageNodeRepository;
        this.shareLinkRepository = shareLinkRepository;
        this.shareLinkItemRepository = shareLinkItemRepository;
    }

    public PageResponse<AdminCloudShareLinkResponse> listShareLinks(
            Long ownerId,
            String rawStatus,
            Boolean passwordProtected,
            Integer page,
            Integer size,
            String rawSortBy,
            String rawSortDirection
    ) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizePageSize(size);
        String sortBy = normalizeShareSortBy(rawSortBy);
        Sort.Direction sortDirection = normalizeSortDirection(rawSortDirection, Sort.Direction.DESC);
        LocalDateTime now = LocalDateTime.now();
        ShareStatusFilter statusFilter = ShareStatusFilter.fromRaw(rawStatus);
        Pageable pageable = PageRequest.of(
                normalizedPage - 1,
                normalizedSize,
                buildShareSort(sortBy, sortDirection)
        );

        Page<ShareLink> shareLinks = shareLinkRepository.findAll(
                buildShareSpecification(ownerId, statusFilter, passwordProtected, now),
                pageable
        );
        Map<Long, Long> itemCounts = loadShareItemCounts(shareLinks.getContent().stream().map(ShareLink::getId).toList());

        return new PageResponse<>(
                shareLinks.getContent().stream()
                        .map(link -> toShareResponse(link, itemCounts.getOrDefault(link.getId(), 0L), now))
                        .toList(),
                normalizedPage,
                normalizedSize,
                shareLinks.getTotalElements(),
                shareLinks.getTotalPages(),
                sortBy,
                sortDirection.name().toLowerCase(Locale.ROOT)
        );
    }

    public PageResponse<AdminCloudTrashNodeResponse> listTrashNodes(
            Long ownerId,
            String keyword,
            String rawType,
            Boolean rootOnly,
            Integer page,
            Integer size,
            String rawSortBy,
            String rawSortDirection
    ) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizePageSize(size);
        String sortBy = normalizeTrashSortBy(rawSortBy);
        Sort.Direction sortDirection = normalizeSortDirection(rawSortDirection, Sort.Direction.DESC);
        Pageable pageable = PageRequest.of(
                normalizedPage - 1,
                normalizedSize,
                buildTrashSort(sortBy, sortDirection)
        );

        Page<StorageNode> trashNodes = storageNodeRepository.searchOperationalTrashNodes(
                ownerId,
                normalizeKeyword(keyword),
                normalizeNodeType(rawType),
                rootOnly == null || rootOnly,
                pageable
        );
        Set<Long> deletedParentIds = loadDeletedParentIds(trashNodes.getContent());

        return new PageResponse<>(
                trashNodes.getContent().stream()
                        .map(node -> toTrashResponse(node, isRootTrashItem(node, deletedParentIds)))
                        .toList(),
                normalizedPage,
                normalizedSize,
                trashNodes.getTotalElements(),
                trashNodes.getTotalPages(),
                sortBy,
                sortDirection.name().toLowerCase(Locale.ROOT)
        );
    }

    private boolean isRootTrashItem(StorageNode node, Set<Long> deletedParentIds) {
        Long parentId = node.getParentId();
        return parentId == null || !deletedParentIds.contains(parentId);
    }

    public PageResponse<AdminCloudStorageUserUsageResponse> listStorageUsers(
            String authorization,
            Integer page,
            Integer size,
            String rawSortBy,
            String rawSortDirection
    ) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizePageSize(size);
        String sortBy = normalizeStorageUserSortBy(rawSortBy);
        Sort.Direction sortDirection = normalizeSortDirection(rawSortDirection, Sort.Direction.DESC);
        List<UserProfileResponse> profiles = adminCloudUserDirectoryService.listUsers(authorization);
        List<Long> userIds = profiles.stream().map(UserProfileResponse::id).toList();
        Map<Long, StorageNodeRepository.OwnerNodeUsageProjection> activeUsageByOwner =
                loadActiveUsageByOwner(userIds);
        Map<Long, Long> trashItemsByOwner = loadTrashItemsByOwner(userIds);
        Map<Long, Long> shareLinksByOwner = loadShareLinksByOwner(userIds);
        List<AdminCloudStorageUserUsageResponse> responses = profiles.stream()
                .map(profile -> toStorageUserResponse(
                        profile,
                        activeUsageByOwner.get(profile.id()),
                        trashItemsByOwner.getOrDefault(profile.id(), 0L),
                        shareLinksByOwner.getOrDefault(profile.id(), 0L)
                ))
                .sorted(buildStorageUserComparator(sortBy, sortDirection))
                .toList();

        return toPageResponse(responses, normalizedPage, normalizedSize, sortBy, sortDirection);
    }

    private Specification<ShareLink> buildShareSpecification(
            Long ownerId,
            ShareStatusFilter statusFilter,
            Boolean passwordProtected,
            LocalDateTime now
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (ownerId != null) {
                predicates.add(criteriaBuilder.equal(root.get("ownerId"), ownerId));
            }

            switch (statusFilter) {
                case ACTIVE -> predicates.add(criteriaBuilder.equal(root.get("status"), ShareLinkStatus.ACTIVE));
                case REVOKED -> predicates.add(criteriaBuilder.equal(root.get("status"), ShareLinkStatus.REVOKED));
                case AVAILABLE -> {
                    predicates.add(criteriaBuilder.equal(root.get("status"), ShareLinkStatus.ACTIVE));
                    predicates.add(criteriaBuilder.or(
                            criteriaBuilder.isNull(root.get("expiresAt")),
                            criteriaBuilder.greaterThan(root.get("expiresAt"), now)
                    ));
                }
                case EXPIRED -> {
                    predicates.add(criteriaBuilder.equal(root.get("status"), ShareLinkStatus.ACTIVE));
                    predicates.add(criteriaBuilder.isNotNull(root.get("expiresAt")));
                    predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("expiresAt"), now));
                }
                case ALL -> {
                }
            }

            if (passwordProtected != null) {
                predicates.add(passwordProtected
                        ? criteriaBuilder.isNotNull(root.get("passwordHash"))
                        : criteriaBuilder.isNull(root.get("passwordHash")));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Map<Long, Long> loadShareItemCounts(List<Long> shareIds) {
        Map<Long, Long> itemCounts = new HashMap<>();
        if (shareIds.isEmpty()) {
            return itemCounts;
        }

        for (ShareLinkItem item : shareLinkItemRepository.findByShareIdIn(shareIds)) {
            itemCounts.merge(item.getShareId(), 1L, Long::sum);
        }
        return itemCounts;
    }

    private Map<Long, StorageNodeRepository.OwnerNodeUsageProjection> loadActiveUsageByOwner(List<Long> userIds) {
        Map<Long, StorageNodeRepository.OwnerNodeUsageProjection> usageByOwner = new HashMap<>();
        if (userIds.isEmpty()) {
            return usageByOwner;
        }

        storageNodeRepository.summarizeActiveNodesByOwnerIds(userIds)
                .forEach(usage -> usageByOwner.put(usage.getOwnerId(), usage));
        return usageByOwner;
    }

    private Map<Long, Long> loadTrashItemsByOwner(List<Long> userIds) {
        Map<Long, Long> trashItemsByOwner = new HashMap<>();
        if (userIds.isEmpty()) {
            return trashItemsByOwner;
        }

        storageNodeRepository.countTrashNodesByOwnerIds(userIds)
                .forEach(usage -> trashItemsByOwner.put(usage.getOwnerId(), nullToZero(usage.getItemCount())));
        return trashItemsByOwner;
    }

    private Map<Long, Long> loadShareLinksByOwner(List<Long> userIds) {
        Map<Long, Long> shareLinksByOwner = new HashMap<>();
        if (userIds.isEmpty()) {
            return shareLinksByOwner;
        }

        shareLinkRepository.countShareLinksByOwnerIds(userIds)
                .forEach(usage -> shareLinksByOwner.put(usage.getOwnerId(), nullToZero(usage.getLinkCount())));
        return shareLinksByOwner;
    }

    private Set<Long> loadDeletedParentIds(List<StorageNode> nodes) {
        Set<Long> parentIds = nodes.stream()
                .map(StorageNode::getParentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (parentIds.isEmpty()) {
            return Set.of();
        }

        return storageNodeRepository.findAllById(parentIds).stream()
                .filter(StorageNode::isDeleted)
                .map(StorageNode::getId)
                .collect(Collectors.toSet());
    }

    private AdminCloudShareLinkResponse toShareResponse(ShareLink link, long itemCount, LocalDateTime now) {
        boolean expired = link.getStatus() == ShareLinkStatus.ACTIVE
                && link.getExpiresAt() != null
                && !link.getExpiresAt().isAfter(now);

        return new AdminCloudShareLinkResponse(
                link.getId(),
                link.getOwnerId(),
                link.getTitle(),
                link.getStatus().name(),
                expired ? "EXPIRED" : link.getStatus().name(),
                link.getPasswordHash() != null && !link.getPasswordHash().isBlank(),
                link.isAllowDownload(),
                link.isAllowSave(),
                nullToZero(link.getViewCount()),
                itemCount,
                link.getExpiresAt(),
                link.getLastAccessedAt(),
                link.getCreatedAt(),
                link.getUpdatedAt()
        );
    }

    private AdminCloudTrashNodeResponse toTrashResponse(StorageNode node, boolean rootItem) {
        return new AdminCloudTrashNodeResponse(
                node.getId(),
                node.getOwnerId(),
                node.getParentId(),
                node.getOriginalParentId(),
                node.getNodeName(),
                node.getNodeType() == null ? "UNKNOWN" : node.getNodeType().name(),
                nullToZero(node.getFileSize()),
                node.getDeletedBy(),
                rootItem,
                node.getDeletedAt(),
                node.getUpdatedAt()
        );
    }

    private AdminCloudStorageUserUsageResponse toStorageUserResponse(
            UserProfileResponse profile,
            StorageNodeRepository.OwnerNodeUsageProjection activeUsage,
            long trashItems,
            long shareLinks
    ) {
        long usedBytes = activeUsage == null ? 0L : nullToZero(activeUsage.getUsedBytes());
        long activeItems = activeUsage == null ? 0L : nullToZero(activeUsage.getTotalItems());
        long activeFolders = activeUsage == null ? 0L : nullToZero(activeUsage.getFolderCount());
        long activeFiles = activeUsage == null ? 0L : nullToZero(activeUsage.getFileCount());
        Long storageQuotaBytes = profile.storageQuotaBytes();
        Long remainingBytes = storageQuotaBytes == null ? null : Math.max(0L, storageQuotaBytes - usedBytes);
        Double usageRatio = storageQuotaBytes == null || storageQuotaBytes <= 0
                ? null
                : Math.min(1.0, Math.max(0.0, (double) usedBytes / storageQuotaBytes));

        return new AdminCloudStorageUserUsageResponse(
                profile.id(),
                profile.phoneNumber(),
                profile.email(),
                profile.nickname(),
                profile.role(),
                profile.status(),
                storageQuotaBytes,
                usedBytes,
                remainingBytes,
                usageRatio,
                activeItems,
                activeFolders,
                activeFiles,
                trashItems,
                shareLinks,
                profile.createdAt()
        );
    }

    private <T> PageResponse<T> toPageResponse(
            List<T> items,
            int page,
            int size,
            String sortBy,
            Sort.Direction sortDirection
    ) {
        int fromIndex = Math.min((page - 1) * size, items.size());
        int toIndex = Math.min(fromIndex + size, items.size());
        int totalPages = items.isEmpty() ? 0 : (int) Math.ceil((double) items.size() / size);

        return new PageResponse<>(
                items.subList(fromIndex, toIndex),
                page,
                size,
                items.size(),
                totalPages,
                sortBy,
                sortDirection.name().toLowerCase(Locale.ROOT)
        );
    }

    private Comparator<AdminCloudStorageUserUsageResponse> buildStorageUserComparator(
            String sortBy,
            Sort.Direction sortDirection
    ) {
        Comparator<AdminCloudStorageUserUsageResponse> comparator = switch (sortBy) {
            case "usedBytes" -> compareLong(AdminCloudStorageUserUsageResponse::usedBytes, sortDirection);
            case "storageQuotaBytes" -> compareNullable(AdminCloudStorageUserUsageResponse::storageQuotaBytes, sortDirection);
            case "remainingBytes" -> compareNullable(AdminCloudStorageUserUsageResponse::remainingBytes, sortDirection);
            case "usageRatio" -> compareNullable(AdminCloudStorageUserUsageResponse::usageRatio, sortDirection);
            case "activeItems" -> compareLong(AdminCloudStorageUserUsageResponse::activeItems, sortDirection);
            case "trashItems" -> compareLong(AdminCloudStorageUserUsageResponse::trashItems, sortDirection);
            case "shareLinks" -> compareLong(AdminCloudStorageUserUsageResponse::shareLinks, sortDirection);
            case "createdAt" -> compareNullable(AdminCloudStorageUserUsageResponse::createdAt, sortDirection);
            case "nickname" -> compareString(AdminCloudStorageUserUsageResponse::nickname, sortDirection);
            case "id" -> compareNullable(AdminCloudStorageUserUsageResponse::userId, sortDirection);
            default -> throw new IllegalArgumentException("用户容量排序字段不合法。");
        };

        return comparator.thenComparing(
                AdminCloudStorageUserUsageResponse::userId,
                Comparator.nullsLast(Comparator.naturalOrder())
        );
    }

    private Comparator<AdminCloudStorageUserUsageResponse> compareLong(
            ToLongFunction<AdminCloudStorageUserUsageResponse> extractor,
            Sort.Direction direction
    ) {
        Comparator<AdminCloudStorageUserUsageResponse> comparator = Comparator.comparingLong(extractor);
        return direction == Sort.Direction.DESC ? comparator.reversed() : comparator;
    }

    private <T extends Comparable<? super T>> Comparator<AdminCloudStorageUserUsageResponse> compareNullable(
            Function<AdminCloudStorageUserUsageResponse, T> extractor,
            Sort.Direction direction
    ) {
        Comparator<T> valueComparator = direction == Sort.Direction.DESC
                ? Comparator.nullsLast(Comparator.reverseOrder())
                : Comparator.nullsLast(Comparator.naturalOrder());
        return Comparator.comparing(extractor, valueComparator);
    }

    private Comparator<AdminCloudStorageUserUsageResponse> compareString(
            Function<AdminCloudStorageUserUsageResponse, String> extractor,
            Sort.Direction direction
    ) {
        Comparator<String> valueComparator = direction == Sort.Direction.DESC
                ? Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER.reversed())
                : Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);
        return Comparator.comparing(extractor, valueComparator);
    }

    private Sort buildShareSort(String sortBy, Sort.Direction sortDirection) {
        String property = switch (sortBy) {
            case "title" -> "title";
            case "ownerId" -> "ownerId";
            case "expiresAt" -> "expiresAt";
            case "lastAccessedAt" -> "lastAccessedAt";
            case "updatedAt" -> "updatedAt";
            case "viewCount" -> "viewCount";
            case "createdAt" -> "createdAt";
            default -> throw new IllegalArgumentException("分享排序字段不合法。");
        };

        return Sort.by(
                new Sort.Order(sortDirection, property).nullsLast(),
                new Sort.Order(Sort.Direction.DESC, "id")
        );
    }

    private Sort buildTrashSort(String sortBy, Sort.Direction sortDirection) {
        String property = switch (sortBy) {
            case "name" -> "nodeName";
            case "ownerId" -> "ownerId";
            case "size" -> "fileSize";
            case "updatedAt" -> "updatedAt";
            case "deletedAt" -> "deletedAt";
            default -> throw new IllegalArgumentException("回收站排序字段不合法。");
        };

        return Sort.by(
                new Sort.Order(sortDirection, property).nullsLast(),
                new Sort.Order(Sort.Direction.ASC, "id")
        );
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

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }

    private NodeType normalizeNodeType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return null;
        }

        try {
            return NodeType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("文件类型筛选值不合法。");
        }
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

    private String normalizeShareSortBy(String rawSortBy) {
        if (rawSortBy == null || rawSortBy.isBlank()) {
            return "createdAt";
        }

        return switch (rawSortBy.trim()) {
            case "title", "ownerId", "expiresAt", "lastAccessedAt", "updatedAt", "viewCount", "createdAt" ->
                    rawSortBy.trim();
            default -> throw new IllegalArgumentException("分享排序字段不合法。");
        };
    }

    private String normalizeTrashSortBy(String rawSortBy) {
        if (rawSortBy == null || rawSortBy.isBlank()) {
            return "deletedAt";
        }

        return switch (rawSortBy.trim()) {
            case "name", "ownerId", "size", "updatedAt", "deletedAt" -> rawSortBy.trim();
            default -> throw new IllegalArgumentException("回收站排序字段不合法。");
        };
    }

    private String normalizeStorageUserSortBy(String rawSortBy) {
        if (rawSortBy == null || rawSortBy.isBlank()) {
            return "usedBytes";
        }

        return switch (rawSortBy.trim()) {
            case "usedBytes", "storageQuotaBytes", "remainingBytes", "usageRatio", "activeItems",
                    "trashItems", "shareLinks", "createdAt", "nickname", "id" -> rawSortBy.trim();
            default -> throw new IllegalArgumentException("用户容量排序字段不合法。");
        };
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private enum ShareStatusFilter {
        ALL,
        ACTIVE,
        AVAILABLE,
        EXPIRED,
        REVOKED;

        private static ShareStatusFilter fromRaw(String rawStatus) {
            if (rawStatus == null || rawStatus.isBlank()) {
                return ALL;
            }

            try {
                return ShareStatusFilter.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("分享状态筛选值不合法。");
            }
        }
    }
}
