package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.entity.NodeType;
import com.alicia.cloudstorage.api.entity.StorageNode;
import com.alicia.cloudstorage.api.repository.StorageNodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageQueryServiceTest {

    @Mock
    private StorageNodeRepository storageNodeRepository;

    @Mock
    private StorageQuotaService storageQuotaService;

    @InjectMocks
    private StorageQueryService storageQueryService;

    @Test
    void listNodesUsesDefaultDrivePaginationAndFolderFirstSort() {
        Long userId = 5L;
        StorageNode folder = activeNode(11L, userId, "文档", NodeType.FOLDER, 0L);
        StorageNode file = activeNode(12L, userId, "notes.txt", NodeType.FILE, 128L);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        when(storageNodeRepository.searchNodes(eq(userId), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(folder, file)));

        var response = storageQueryService.listNodes(userId, null, null, null, null, null, null, null);

        verify(storageNodeRepository).searchNodes(eq(userId), isNull(), isNull(), isNull(), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();

        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort()).containsExactly(
                new Sort.Order(Sort.Direction.DESC, "nodeType"),
                new Sort.Order(Sort.Direction.ASC, "nodeName"),
                new Sort.Order(Sort.Direction.ASC, "id")
        );
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.sortBy()).isEqualTo("name");
        assertThat(response.sortDirection()).isEqualTo("asc");
        assertThat(response.items()).extracting(item -> item.name()).containsExactly("文档", "notes.txt");
    }

    @Test
    void listNodesUsesRequestedPageSizeAndUpdatedAtSort() {
        Long userId = 9L;
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        when(storageNodeRepository.searchNodes(eq(userId), eq(88L), eq("周报"), eq(NodeType.FILE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(1, 20), 31));

        var response = storageQueryService.listNodes(userId, 88L, "周报", "FILE", 2, 20, "updatedAt", "desc");

        verify(storageNodeRepository).searchNodes(eq(userId), eq(88L), eq("周报"), eq(NodeType.FILE), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();

        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).containsExactly(
                new Sort.Order(Sort.Direction.DESC, "nodeType"),
                new Sort.Order(Sort.Direction.DESC, "updatedAt"),
                new Sort.Order(Sort.Direction.ASC, "nodeName"),
                new Sort.Order(Sort.Direction.ASC, "id")
        );
        assertThat(response.totalItems()).isEqualTo(31);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.sortBy()).isEqualTo("updatedAt");
        assertThat(response.sortDirection()).isEqualTo("desc");
    }

    @Test
    void listTrashNodesDefaultsToDeletedTimeDescending() {
        Long userId = 5L;
        StorageNode newerNode = deletedFolder(12L, userId, "新项目", LocalDateTime.of(2026, 4, 29, 9, 0));
        StorageNode olderNode = deletedFolder(11L, userId, "旧项目", LocalDateTime.of(2026, 4, 28, 9, 0));
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        when(storageNodeRepository.searchTrashNodes(eq(userId), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(newerNode, olderNode)));

        var response = storageQueryService.listTrashNodes(userId, null, null, null, null, null, null);

        verify(storageNodeRepository).searchTrashNodes(eq(userId), isNull(), isNull(), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();

        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort()).containsExactly(
                new Sort.Order(Sort.Direction.DESC, "deletedAt").nullsLast(),
                new Sort.Order(Sort.Direction.ASC, "nodeName"),
                new Sort.Order(Sort.Direction.ASC, "id")
        );
        assertThat(response.sortBy()).isEqualTo("deletedAt");
        assertThat(response.sortDirection()).isEqualTo("desc");
        assertThat(response.items()).extracting(item -> item.name()).containsExactly("新项目", "旧项目");
    }

    @Test
    void listTrashNodesSupportsNameSortingAndPaging() {
        Long userId = 6L;
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        when(storageNodeRepository.searchTrashNodes(eq(userId), eq("报告"), eq(NodeType.FILE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(2, 5), 17));

        var response = storageQueryService.listTrashNodes(userId, "报告", "FILE", 3, 5, "name", "asc");

        verify(storageNodeRepository).searchTrashNodes(eq(userId), eq("报告"), eq(NodeType.FILE), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort()).containsExactly(
                new Sort.Order(Sort.Direction.ASC, "nodeName"),
                new Sort.Order(Sort.Direction.DESC, "deletedAt").nullsLast(),
                new Sort.Order(Sort.Direction.ASC, "id")
        );
        assertThat(response.totalItems()).isEqualTo(17);
        assertThat(response.totalPages()).isEqualTo(4);
        assertThat(response.sortBy()).isEqualTo("name");
        assertThat(response.sortDirection()).isEqualTo("asc");
    }

    @Test
    void getOverviewReturnsUserQuotaForRegularUser() {
        Long userId = 18L;

        when(storageQuotaService.isAdmin(userId)).thenReturn(false);
        when(storageNodeRepository.countByOwnerIdAndDeletedFalse(userId)).thenReturn(12L);
        when(storageNodeRepository.countByOwnerIdAndNodeTypeAndDeletedFalse(userId, NodeType.FOLDER)).thenReturn(5L);
        when(storageNodeRepository.countByOwnerIdAndNodeTypeAndDeletedFalse(userId, NodeType.FILE)).thenReturn(7L);
        when(storageQuotaService.getUsedBytes(userId)).thenReturn(4096L);
        when(storageQuotaService.getUserQuotaBytes(userId)).thenReturn(10240L);

        var response = storageQueryService.getOverview(userId);

        assertThat(response.totalItems()).isEqualTo(12L);
        assertThat(response.totalFolders()).isEqualTo(5L);
        assertThat(response.totalFiles()).isEqualTo(7L);
        assertThat(response.usedBytes()).isEqualTo(4096L);
        assertThat(response.totalSpaceBytes()).isEqualTo(10240L);
        assertThat(response.actualUsedBytes()).isEqualTo(4096L);
        assertThat(response.scope()).isEqualTo("USER");
    }

    @Test
    void getOverviewReturnsSystemAllocationForAdmin() {
        Long userId = 19L;

        when(storageQuotaService.isAdmin(userId)).thenReturn(true);
        when(storageNodeRepository.countByDeletedFalse()).thenReturn(40L);
        when(storageNodeRepository.countByNodeTypeAndDeletedFalse(NodeType.FOLDER)).thenReturn(13L);
        when(storageNodeRepository.countByNodeTypeAndDeletedFalse(NodeType.FILE)).thenReturn(27L);
        when(storageQuotaService.getTotalActualUsedBytes()).thenReturn(2048L);

        var response = storageQueryService.getOverview(userId);

        assertThat(response.totalItems()).isEqualTo(40L);
        assertThat(response.totalFolders()).isEqualTo(13L);
        assertThat(response.totalFiles()).isEqualTo(27L);
        assertThat(response.usedBytes()).isEqualTo(2048L);
        assertThat(response.totalSpaceBytes()).isNull();
        assertThat(response.actualUsedBytes()).isEqualTo(2048L);
        assertThat(response.scope()).isEqualTo("ADMIN");
    }

    @Test
    void getUsageHistoryAggregatesAllOwnersForAdmin() {
        Long userId = 20L;
        LocalDate today = LocalDate.now();

        when(storageQuotaService.isAdmin(userId)).thenReturn(true);
        when(storageNodeRepository.sumActiveFileSizeAllOwnersAt(any(LocalDateTime.class)))
                .thenReturn(512L, 768L, 1024L);

        var points = storageQueryService.getUsageHistory(userId, 3);

        assertThat(points).hasSize(3);
        assertThat(points).extracting(point -> point.date()).containsExactly(
                today.minusDays(2),
                today.minusDays(1),
                today
        );
        assertThat(points).extracting(point -> point.usedBytes()).containsExactly(512L, 768L, 1024L);
        verify(storageNodeRepository, org.mockito.Mockito.times(3)).sumActiveFileSizeAllOwnersAt(any(LocalDateTime.class));
    }

    private StorageNode activeNode(Long id, Long ownerId, String name, NodeType nodeType, Long size) {
        StorageNode node = new StorageNode();
        ReflectionTestUtils.setField(node, "id", id);
        ReflectionTestUtils.setField(node, "updatedAt", LocalDateTime.of(2026, 4, 29, 8, 0));
        node.setOwnerId(ownerId);
        node.setParentId(null);
        node.setNodeName(name);
        node.setNodeType(nodeType);
        node.setFileSize(size);
        node.setDeleted(false);
        return node;
    }

    private StorageNode deletedFolder(Long id, Long ownerId, String name, LocalDateTime deletedAt) {
        StorageNode node = activeNode(id, ownerId, name, NodeType.FOLDER, 0L);
        node.setDeleted(true);
        node.setDeletedAt(deletedAt);
        return node;
    }
}
