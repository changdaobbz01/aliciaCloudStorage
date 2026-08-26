package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.AdminCloudStorageUserUsageResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCloudOperationsDetailServiceTest {

    @Mock
    private AdminCloudUserDirectoryService adminCloudUserDirectoryService;

    @Mock
    private StorageNodeRepository storageNodeRepository;

    @Mock
    private ShareLinkRepository shareLinkRepository;

    @Mock
    private ShareLinkItemRepository shareLinkItemRepository;

    private AdminCloudOperationsDetailService service;

    @BeforeEach
    void setUp() {
        service = new AdminCloudOperationsDetailService(
                adminCloudUserDirectoryService,
                storageNodeRepository,
                shareLinkRepository,
                shareLinkItemRepository
        );
    }

    @Test
    void listShareLinksReturnsSafeOperationalFieldsAndItemCounts() {
        ShareLink expiredLink = shareLink(
                101L,
                7L,
                "项目资料",
                ShareLinkStatus.ACTIVE,
                "hashed-password",
                LocalDateTime.now().minusDays(1),
                18L
        );
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        when(shareLinkRepository.findAll(anyShareLinkSpecification(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(expiredLink), PageRequest.of(1, 5), 11));
        when(shareLinkItemRepository.findByShareIdIn(List.of(101L)))
                .thenReturn(List.of(shareItem(101L), shareItem(101L)));

        var response = service.listShareLinks(7L, "expired", true, 2, 5, "viewCount", "desc");

        verify(shareLinkRepository).findAll(anyShareLinkSpecification(), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort()).containsExactly(
                new Sort.Order(Sort.Direction.DESC, "viewCount").nullsLast(),
                new Sort.Order(Sort.Direction.DESC, "id")
        );
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(5);
        assertThat(response.totalItems()).isEqualTo(11);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.sortBy()).isEqualTo("viewCount");
        assertThat(response.sortDirection()).isEqualTo("desc");
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(101L);
            assertThat(item.ownerId()).isEqualTo(7L);
            assertThat(item.title()).isEqualTo("项目资料");
            assertThat(item.status()).isEqualTo("ACTIVE");
            assertThat(item.effectiveStatus()).isEqualTo("EXPIRED");
            assertThat(item.passwordProtected()).isTrue();
            assertThat(item.viewCount()).isEqualTo(18L);
            assertThat(item.itemCount()).isEqualTo(2L);
        });
    }

    @Test
    void listTrashNodesMarksEntriesUnderDeletedParentsAsNonRootItems() {
        StorageNode deletedParent = trashNode(
                200L,
                8L,
                null,
                null,
                "已删文件夹",
                NodeType.FOLDER,
                0L,
                LocalDateTime.of(2026, 8, 26, 9, 0)
        );
        StorageNode nestedFile = trashNode(
                201L,
                8L,
                200L,
                66L,
                "报告.pdf",
                NodeType.FILE,
                2048L,
                LocalDateTime.of(2026, 8, 26, 9, 1)
        );
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        when(storageNodeRepository.findAll(anyStorageNodeSpecification(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(nestedFile), PageRequest.of(0, 10), 1));
        when(storageNodeRepository.findAllById(any()))
                .thenReturn(List.of(deletedParent));

        var response = service.listTrashNodes(8L, "报告", "FILE", false, 1, 10, "deletedAt", "desc");

        verify(storageNodeRepository).findAll(anyStorageNodeSpecification(), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort()).containsExactly(
                new Sort.Order(Sort.Direction.DESC, "deletedAt").nullsLast(),
                new Sort.Order(Sort.Direction.ASC, "id")
        );
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(201L);
            assertThat(item.ownerId()).isEqualTo(8L);
            assertThat(item.parentId()).isEqualTo(200L);
            assertThat(item.originalParentId()).isEqualTo(66L);
            assertThat(item.name()).isEqualTo("报告.pdf");
            assertThat(item.type()).isEqualTo("FILE");
            assertThat(item.size()).isEqualTo(2048L);
            assertThat(item.rootItem()).isFalse();
        });
    }

    @Test
    void listStorageUsersAggregatesCapacityTrashAndShareUsage() {
        UserProfileResponse activeUser = userProfile(
                7L,
                "13800000000",
                "admin@example.com",
                "青空",
                "ADMIN",
                "ACTIVE",
                1_000L
        );
        UserProfileResponse noQuotaUser = userProfile(
                8L,
                null,
                "user@example.com",
                null,
                "USER",
                "ACTIVE",
                null
        );

        when(adminCloudUserDirectoryService.listUsers("Bearer token"))
                .thenReturn(List.of(noQuotaUser, activeUser));
        when(storageNodeRepository.summarizeActiveNodesByOwnerIds(any()))
                .thenReturn(List.of(activeUsage(7L, 3L, 1L, 2L, 600L)));
        when(storageNodeRepository.countTrashNodesByOwnerIds(any()))
                .thenReturn(List.of(trashCount(7L, 4L)));
        when(shareLinkRepository.countShareLinksByOwnerIds(any()))
                .thenReturn(List.of(shareCount(7L, 5L)));

        PageResponse<AdminCloudStorageUserUsageResponse> response =
                service.listStorageUsers("Bearer token", 1, 10, "usedBytes", "desc");

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalItems()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.sortBy()).isEqualTo("usedBytes");
        assertThat(response.sortDirection()).isEqualTo("desc");
        assertThat(response.items()).extracting(AdminCloudStorageUserUsageResponse::userId)
                .containsExactly(7L, 8L);
        assertThat(response.items().get(0)).satisfies(item -> {
            assertThat(item.storageQuotaBytes()).isEqualTo(1_000L);
            assertThat(item.usedBytes()).isEqualTo(600L);
            assertThat(item.remainingBytes()).isEqualTo(400L);
            assertThat(item.usageRatio()).isEqualTo(0.6);
            assertThat(item.activeItems()).isEqualTo(3L);
            assertThat(item.activeFolders()).isEqualTo(1L);
            assertThat(item.activeFiles()).isEqualTo(2L);
            assertThat(item.trashItems()).isEqualTo(4L);
            assertThat(item.shareLinks()).isEqualTo(5L);
        });
        assertThat(response.items().get(1)).satisfies(item -> {
            assertThat(item.storageQuotaBytes()).isNull();
            assertThat(item.remainingBytes()).isNull();
            assertThat(item.usageRatio()).isNull();
            assertThat(item.usedBytes()).isZero();
        });
    }

    @Test
    void listStorageUsersSortsNullableFieldsWithoutFailing() {
        when(adminCloudUserDirectoryService.listUsers("Bearer token"))
                .thenReturn(List.of(
                        userProfile(1L, null, "one@example.com", null, "USER", "ACTIVE", null),
                        userProfile(2L, null, "two@example.com", "二号", "USER", "ACTIVE", 10_000L),
                        userProfile(3L, null, "three@example.com", "三号", "USER", "ACTIVE", 20_000L)
                ));
        when(storageNodeRepository.summarizeActiveNodesByOwnerIds(any()))
                .thenReturn(List.of());
        when(storageNodeRepository.countTrashNodesByOwnerIds(any()))
                .thenReturn(List.of());
        when(shareLinkRepository.countShareLinksByOwnerIds(any()))
                .thenReturn(List.of());

        var response = service.listStorageUsers("Bearer token", 1, 2, "storageQuotaBytes", "desc");

        assertThat(response.items()).extracting(AdminCloudStorageUserUsageResponse::userId)
                .containsExactly(3L, 2L);
        assertThat(response.totalItems()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(2);
    }

    @Test
    void listShareLinksRejectsInvalidPaginationAndSortInputs() {
        assertThatThrownBy(() -> service.listShareLinks(null, null, null, 0, 10, "createdAt", "desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("分页页码");
        assertThatThrownBy(() -> service.listShareLinks(null, null, null, 1, 101, "createdAt", "desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("分页大小");
        assertThatThrownBy(() -> service.listShareLinks(null, null, null, 1, 10, "shareCode", "desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("分享排序字段");
    }

    private ShareLink shareLink(
            Long id,
            Long ownerId,
            String title,
            ShareLinkStatus status,
            String passwordHash,
            LocalDateTime expiresAt,
            Long viewCount
    ) {
        ShareLink link = new ShareLink();
        ReflectionTestUtils.setField(link, "id", id);
        ReflectionTestUtils.setField(link, "createdAt", LocalDateTime.of(2026, 8, 26, 8, 0));
        ReflectionTestUtils.setField(link, "updatedAt", LocalDateTime.of(2026, 8, 26, 8, 30));
        link.setShareCode("hidden-share-code");
        link.setOwnerId(ownerId);
        link.setTitle(title);
        link.setStatus(status);
        link.setPasswordHash(passwordHash);
        link.setExpiresAt(expiresAt);
        link.setAllowDownload(true);
        link.setAllowSave(false);
        link.setViewCount(viewCount);
        link.setLastAccessedAt(LocalDateTime.of(2026, 8, 26, 8, 45));
        return link;
    }

    private Specification<ShareLink> anyShareLinkSpecification() {
        return any();
    }

    private Specification<StorageNode> anyStorageNodeSpecification() {
        return any();
    }

    private ShareLinkItem shareItem(Long shareId) {
        ShareLinkItem item = new ShareLinkItem();
        item.setShareId(shareId);
        item.setNodeId(900L);
        item.setSortOrder(0);
        return item;
    }

    private StorageNode trashNode(
            Long id,
            Long ownerId,
            Long parentId,
            Long originalParentId,
            String name,
            NodeType nodeType,
            Long size,
            LocalDateTime deletedAt
    ) {
        StorageNode node = new StorageNode();
        ReflectionTestUtils.setField(node, "id", id);
        ReflectionTestUtils.setField(node, "createdAt", LocalDateTime.of(2026, 8, 26, 7, 0));
        ReflectionTestUtils.setField(node, "updatedAt", deletedAt);
        node.setOwnerId(ownerId);
        node.setParentId(parentId);
        node.setOriginalParentId(originalParentId);
        node.setNodeName(name);
        node.setNodeType(nodeType);
        node.setFileSize(size);
        node.setDeleted(true);
        node.setDeletedBy(ownerId);
        node.setDeletedAt(deletedAt);
        return node;
    }

    private UserProfileResponse userProfile(
            Long id,
            String phoneNumber,
            String email,
            String nickname,
            String role,
            String status,
            Long storageQuotaBytes
    ) {
        return new UserProfileResponse(
                id,
                phoneNumber,
                email,
                nickname,
                null,
                null,
                role,
                status,
                LocalDateTime.of(2026, 8, 26, 6, 0),
                storageQuotaBytes,
                0L,
                storageQuotaBytes,
                Map.of("cloud", role.equals("ADMIN") ? "CLOUD_ADMIN" : "CLOUD_USER")
        );
    }

    private StorageNodeRepository.OwnerNodeUsageProjection activeUsage(
            Long ownerId,
            Long totalItems,
            Long folderCount,
            Long fileCount,
            Long usedBytes
    ) {
        return new StorageNodeRepository.OwnerNodeUsageProjection() {
            @Override
            public Long getOwnerId() {
                return ownerId;
            }

            @Override
            public Long getTotalItems() {
                return totalItems;
            }

            @Override
            public Long getFolderCount() {
                return folderCount;
            }

            @Override
            public Long getFileCount() {
                return fileCount;
            }

            @Override
            public Long getUsedBytes() {
                return usedBytes;
            }
        };
    }

    private StorageNodeRepository.OwnerTrashCountProjection trashCount(Long ownerId, Long itemCount) {
        return new StorageNodeRepository.OwnerTrashCountProjection() {
            @Override
            public Long getOwnerId() {
                return ownerId;
            }

            @Override
            public Long getItemCount() {
                return itemCount;
            }
        };
    }

    private ShareLinkRepository.OwnerShareLinkCountProjection shareCount(Long ownerId, Long linkCount) {
        return new ShareLinkRepository.OwnerShareLinkCountProjection() {
            @Override
            public Long getOwnerId() {
                return ownerId;
            }

            @Override
            public Long getLinkCount() {
                return linkCount;
            }
        };
    }
}
