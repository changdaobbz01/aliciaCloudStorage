package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.ApiMessageResponse;
import com.alicia.cloudstorage.api.dto.BatchNodeRequest;
import com.alicia.cloudstorage.api.dto.ScopedTrashPreviewResponse;
import com.alicia.cloudstorage.api.dto.ScopedTrashRequest;
import com.alicia.cloudstorage.api.entity.NodeType;
import com.alicia.cloudstorage.api.entity.StorageNode;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class ScopedCollectionTrashServiceTest {

    @Mock
    private StorageNodeRepository storageNodeRepository;

    @Mock
    private StorageCommandService storageCommandService;

    private ScopedCollectionTrashService service;

    @BeforeEach
    void setUp() {
        service = new ScopedCollectionTrashService(storageNodeRepository, storageCommandService, 500, 2000);
    }

    @Test
    void previewsAndExecutesExactRootFileAndFolderImpact() {
        StorageNode file = node(11L, null, "说明.txt", NodeType.FILE);
        StorageNode folder = node(12L, null, "资料", NodeType.FOLDER);
        StorageNode nestedFile = node(13L, 12L, "报告.pdf", NodeType.FILE);
        when(storageNodeRepository.searchNodes(eq(7L), isNull(), isNull(), isNull(), any()))
                .thenReturn(page(List.of(file, folder)));
        when(storageNodeRepository.searchNodes(eq(7L), eq(12L), isNull(), isNull(), any()))
                .thenReturn(page(List.of(nestedFile)));
        when(storageCommandService.moveNodesToTrash(7L, new BatchNodeRequest(List.of(11L, 12L))))
                .thenReturn(new ApiMessageResponse("已移入回收站"));

        ScopedTrashPreviewResponse preview = service.preview(7L, null, true, List.of("FILE", "FOLDER"));
        ApiMessageResponse result = service.execute(7L, request(preview));

        assertThat(preview.executable()).isTrue();
        assertThat(preview.selectedFileCount()).isEqualTo(1);
        assertThat(preview.selectedFolderCount()).isEqualTo(1);
        assertThat(preview.descendantCount()).isEqualTo(1);
        assertThat(preview.impactCount()).isEqualTo(3);
        assertThat(preview.scopeFingerprint()).isNotBlank();
        assertThat(preview.impactFingerprint()).isNotBlank();
        assertThat(result.message()).isEqualTo("已移入回收站");

        ArgumentCaptor<BatchNodeRequest> captor = ArgumentCaptor.forClass(BatchNodeRequest.class);
        verify(storageCommandService).moveNodesToTrash(org.mockito.ArgumentMatchers.eq(7L), captor.capture());
        assertThat(captor.getValue().nodeIds()).containsExactly(11L, 12L);
    }

    @Test
    void rejectsExecutionWhenRootScopeChangesAfterPreview() {
        StorageNode file = node(11L, null, "说明.txt", NodeType.FILE);
        StorageNode folder = node(12L, null, "资料", NodeType.FOLDER);
        when(storageNodeRepository.searchNodes(eq(7L), isNull(), isNull(), isNull(), any()))
                .thenReturn(page(List.of(file, folder)))
                .thenReturn(page(List.of(file)));
        when(storageNodeRepository.searchNodes(eq(7L), eq(12L), isNull(), isNull(), any()))
                .thenReturn(page(List.of()));

        ScopedTrashPreviewResponse preview = service.preview(7L, null, true, List.of("FILE", "FOLDER"));

        assertThatThrownBy(() -> service.execute(7L, request(preview)))
                .isInstanceOf(ScopedTrashSnapshotStaleException.class)
                .hasMessageContaining("重新预览");
    }

    @Test
    void rejectsExecutionWhenFolderSubtreeChangesAfterPreview() {
        StorageNode folder = node(12L, null, "资料", NodeType.FOLDER);
        StorageNode nestedFile = node(13L, 12L, "报告.pdf", NodeType.FILE);
        StorageNode newNestedFile = node(14L, 12L, "新增.pdf", NodeType.FILE);
        when(storageNodeRepository.searchNodes(eq(7L), isNull(), isNull(), eq(NodeType.FOLDER), any()))
                .thenReturn(page(List.of(folder)));
        when(storageNodeRepository.searchNodes(eq(7L), eq(12L), isNull(), isNull(), any()))
                .thenReturn(page(List.of(nestedFile)))
                .thenReturn(page(List.of(nestedFile, newNestedFile)));

        ScopedTrashPreviewResponse preview = service.preview(7L, null, true, List.of("FOLDER"));

        assertThatThrownBy(() -> service.execute(7L, request(preview)))
                .isInstanceOf(ScopedTrashSnapshotStaleException.class)
                .hasMessageContaining("发生了变化");
    }

    @Test
    void rejectsExecutionWhenNamedSourceFolderChangesAfterPreview() {
        StorageNode sourceFolderBefore = node(20L, null, "项目资料", NodeType.FOLDER);
        StorageNode sourceFolderAfter = node(20L, 30L, "归档资料", NodeType.FOLDER);
        ReflectionTestUtils.setField(sourceFolderAfter, "updatedAt", LocalDateTime.of(2026, 8, 14, 8, 5));
        StorageNode child = node(21L, 20L, "报告.pdf", NodeType.FILE);
        when(storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(20L, 7L))
                .thenReturn(Optional.of(sourceFolderBefore), Optional.of(sourceFolderAfter));
        when(storageNodeRepository.searchNodes(eq(7L), eq(20L), isNull(), eq(NodeType.FILE), any()))
                .thenReturn(page(List.of(child)));

        ScopedTrashPreviewResponse preview = service.preview(7L, 20L, false, List.of("FILE"));

        assertThatThrownBy(() -> service.execute(7L, request(preview)))
                .isInstanceOf(ScopedTrashSnapshotStaleException.class)
                .hasMessageContaining("重新预览");
    }

    @Test
    void stopsFolderTraversalAtConfiguredImpactLimit() {
        ScopedCollectionTrashService boundedService = new ScopedCollectionTrashService(
                storageNodeRepository,
                storageCommandService,
                1,
                2
        );
        StorageNode folder = node(12L, null, "资料", NodeType.FOLDER);
        StorageNode nestedFile = node(13L, 12L, "报告.pdf", NodeType.FILE);
        when(storageNodeRepository.searchNodes(eq(7L), isNull(), isNull(), eq(NodeType.FOLDER), any()))
                .thenReturn(page(List.of(folder)));
        when(storageNodeRepository.searchNodes(eq(7L), eq(12L), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(nestedFile), PageRequest.of(0, 2), 3));

        ScopedTrashPreviewResponse preview = boundedService.preview(7L, null, true, List.of("FOLDER"));

        assertThat(preview.executable()).isFalse();
        assertThat(preview.message()).contains("超过 2 个");
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(storageNodeRepository).searchNodes(
                eq(7L), eq(12L), isNull(), isNull(), pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(2);
    }

    private ScopedTrashRequest request(ScopedTrashPreviewResponse preview) {
        return new ScopedTrashRequest(
                preview.selectorVersion(),
                preview.sourceParentId(),
                preview.root(),
                preview.nodeTypes(),
                preview.items().stream().map(item -> item.id()).toList(),
                preview.scopeFingerprint(),
                preview.impactFingerprint(),
                preview.impactCount()
        );
    }

    private PageImpl<StorageNode> page(List<StorageNode> nodes) {
        return new PageImpl<>(nodes, PageRequest.of(0, Math.max(1, nodes.size())), nodes.size());
    }

    private StorageNode node(Long id, Long parentId, String name, NodeType type) {
        StorageNode node = new StorageNode();
        ReflectionTestUtils.setField(node, "id", id);
        ReflectionTestUtils.setField(node, "updatedAt", LocalDateTime.of(2026, 8, 14, 8, 0));
        node.setOwnerId(7L);
        node.setParentId(parentId);
        node.setNodeName(name);
        node.setNodeType(type);
        node.setFileSize(type == NodeType.FILE ? 10L : 0L);
        return node;
    }
}
