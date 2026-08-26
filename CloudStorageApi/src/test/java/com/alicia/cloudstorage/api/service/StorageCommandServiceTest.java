package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.BatchMoveNodeRequest;
import com.alicia.cloudstorage.api.dto.BatchNodeRequest;
import com.alicia.cloudstorage.api.dto.BatchRenameNodeItem;
import com.alicia.cloudstorage.api.dto.BatchRenameNodeRequest;
import com.alicia.cloudstorage.api.dto.MoveNodeRequest;
import com.alicia.cloudstorage.api.dto.RenameNodeRequest;
import com.alicia.cloudstorage.api.entity.NodeType;
import com.alicia.cloudstorage.api.entity.StorageNode;
import com.alicia.cloudstorage.api.repository.StorageNodeRepository;
import com.alicia.cloudstorage.api.storage.StorageNodeEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageCommandServiceTest {

    @Mock
    private StorageNodeRepository storageNodeRepository;

    @Mock
    private CosFileStorageService cosFileStorageService;

    @Mock
    private StorageQuotaService storageQuotaService;

    @Mock
    private StorageNodeEventPublisher storageNodeEventPublisher;

    @InjectMocks
    private StorageCommandService storageCommandService;

    @Test
    void uploadFileRejectsMissingMultipartFileBeforeTouchingStorage() {
        assertThatThrownBy(() -> storageCommandService.uploadFile(7L, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请选择要上传的文件。");

        verifyNoInteractions(storageNodeRepository, cosFileStorageService, storageQuotaService, storageNodeEventPublisher);
    }

    @Test
    void createFolderRejectsMissingRequestBeforeTouchingRepository() {
        assertThatThrownBy(() -> storageCommandService.createFolder(7L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请求内容不能为空。");

        verifyNoInteractions(storageNodeRepository, cosFileStorageService, storageQuotaService, storageNodeEventPublisher);
    }

    @Test
    void renameNodeRejectsMissingRequestBeforeTouchingRepository() {
        assertThatThrownBy(() -> storageCommandService.renameNode(7L, 11L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请求内容不能为空。");

        verifyNoInteractions(storageNodeRepository, cosFileStorageService, storageQuotaService, storageNodeEventPublisher);
    }

    @Test
    void moveNodeRejectsMissingRequestBeforeTouchingRepository() {
        assertThatThrownBy(() -> storageCommandService.moveNode(7L, 11L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请求内容不能为空。");

        verifyNoInteractions(storageNodeRepository, cosFileStorageService, storageQuotaService, storageNodeEventPublisher);
    }

    @Test
    void batchTrashRejectsMissingRequestBeforeTouchingRepository() {
        assertThatThrownBy(() -> storageCommandService.moveNodesToTrash(7L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请求内容不能为空。");

        verifyNoInteractions(storageNodeRepository, cosFileStorageService, storageQuotaService, storageNodeEventPublisher);
    }

    @Test
    void batchNodeOperationsRejectTooManyItemsBeforeLoadingNodes() {
        List<Long> tooManyNodeIds = LongStream.rangeClosed(1, 501).boxed().toList();

        assertThatThrownBy(() -> storageCommandService.moveNodes(7L, new BatchMoveNodeRequest(tooManyNodeIds, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("单次最多处理 500 个项目。");
        assertThatThrownBy(() -> storageCommandService.moveNodesToTrash(7L, new BatchNodeRequest(tooManyNodeIds)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("单次最多处理 500 个项目。");
        assertThatThrownBy(() -> storageCommandService.restoreNodes(7L, new BatchNodeRequest(tooManyNodeIds)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("单次最多处理 500 个项目。");
        assertThatThrownBy(() -> storageCommandService.permanentlyDeleteNodes(7L, new BatchNodeRequest(tooManyNodeIds)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("单次最多处理 500 个项目。");

        verifyNoInteractions(storageNodeRepository, cosFileStorageService, storageQuotaService, storageNodeEventPublisher);
    }

    @Test
    void batchRenameRejectsTooManyItemsBeforeLoadingNodes() {
        List<BatchRenameNodeItem> tooManyRenameItems = LongStream.rangeClosed(1, 501)
                .mapToObj(nodeId -> new BatchRenameNodeItem(nodeId, "name-" + nodeId))
                .toList();

        assertThatThrownBy(() -> storageCommandService.renameNodes(
                7L,
                new BatchRenameNodeRequest(tooManyRenameItems)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("单次最多重命名 500 个项目。");

        verifyNoInteractions(storageNodeRepository, cosFileStorageService, storageQuotaService, storageNodeEventPublisher);
    }

    @Test
    void batchRenameRejectsNullItemBeforeLoadingNodes() {
        assertThatThrownBy(() -> storageCommandService.renameNodes(
                7L,
                new BatchRenameNodeRequest(java.util.Collections.singletonList(null))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("项目编号不能为空。");

        verifyNoInteractions(storageNodeRepository, cosFileStorageService, storageQuotaService, storageNodeEventPublisher);
    }

    @Test
    void batchRenameRejectsBlankNameBeforeLoadingNodes() {
        assertThatThrownBy(() -> storageCommandService.renameNodes(
                7L,
                new BatchRenameNodeRequest(List.of(new BatchRenameNodeItem(11L, "   ")))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("名称不能为空。");

        verifyNoInteractions(storageNodeRepository, cosFileStorageService, storageQuotaService, storageNodeEventPublisher);
    }

    @Test
    void renameNodeUpdatesFileNameAndExtension() {
        Long userId = 7L;
        StorageNode fileNode = fileNode(11L, userId, null, "report.txt", "report-key");

        when(storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(11L, userId)).thenReturn(Optional.of(fileNode));
        when(storageNodeRepository.existsActiveSiblingNameExcludingId(userId, null, "report-final.pdf", 11L)).thenReturn(false);
        when(storageNodeRepository.save(fileNode)).thenReturn(fileNode);

        var summary = storageCommandService.renameNode(userId, 11L, new RenameNodeRequest("report-final.pdf"));

        assertThat(summary.name()).isEqualTo("report-final.pdf");
        assertThat(fileNode.getNodeName()).isEqualTo("report-final.pdf");
        assertThat(fileNode.getFileExtension()).isEqualTo("pdf");
    }

    @Test
    void renameNodesValidatesThenRenamesFilesAndFoldersTogether() {
        Long userId = 7L;
        StorageNode fileNode = fileNode(11L, userId, null, "report.txt", "report-key");
        StorageNode folderNode = folderNode(12L, userId, null, "资料");
        when(storageNodeRepository.findByOwnerIdAndIdInAndDeletedFalse(userId, List.of(11L, 12L)))
                .thenReturn(List.of(fileNode, folderNode));
        when(storageNodeRepository.findByOwnerIdAndParentIdAndDeletedFalse(userId, null))
                .thenReturn(List.of(fileNode, folderNode));
        when(storageNodeRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = storageCommandService.renameNodes(userId, new BatchRenameNodeRequest(List.of(
                new BatchRenameNodeItem(11L, "archived-report.txt"),
                new BatchRenameNodeItem(12L, "归档资料")
        )));

        assertThat(result).extracting(item -> item.name()).containsExactly("archived-report.txt", "归档资料");
        assertThat(fileNode.getFileExtension()).isEqualTo("txt");
        verify(storageNodeEventPublisher).publishUpsert(List.of(fileNode, folderNode), true);
    }

    @Test
    void renameNodesRejectsConflictingPlannedNamesBeforeMutatingAnything() {
        Long userId = 7L;
        StorageNode first = fileNode(11L, userId, null, "first.txt", "first-key");
        StorageNode second = folderNode(12L, userId, null, "second");
        when(storageNodeRepository.findByOwnerIdAndIdInAndDeletedFalse(userId, List.of(11L, 12L)))
                .thenReturn(List.of(first, second));
        when(storageNodeRepository.findByOwnerIdAndParentIdAndDeletedFalse(userId, null))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> storageCommandService.renameNodes(userId, new BatchRenameNodeRequest(List.of(
                new BatchRenameNodeItem(11L, "same-name"),
                new BatchRenameNodeItem(12L, "same-name")
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("批量重命名后的名称存在冲突。");

        assertThat(first.getNodeName()).isEqualTo("first.txt");
        assertThat(second.getNodeName()).isEqualTo("second");
        verify(storageNodeRepository, never()).saveAll(anyList());
    }

    @Test
    void renameNodesAllowsNamesVacatedByAnotherItemInTheSameBatch() {
        Long userId = 7L;
        StorageNode first = fileNode(11L, userId, null, "report.txt", "first-key");
        StorageNode second = fileNode(12L, userId, null, "archived-report.txt", "second-key");
        when(storageNodeRepository.findByOwnerIdAndIdInAndDeletedFalse(userId, List.of(11L, 12L)))
                .thenReturn(List.of(first, second));
        when(storageNodeRepository.findByOwnerIdAndParentIdAndDeletedFalse(userId, null))
                .thenReturn(List.of(first, second));
        when(storageNodeRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = storageCommandService.renameNodes(userId, new BatchRenameNodeRequest(List.of(
                new BatchRenameNodeItem(11L, "archived-report.txt"),
                new BatchRenameNodeItem(12L, "archived-archived-report.txt")
        )));

        assertThat(result).extracting(item -> item.name())
                .containsExactly("archived-report.txt", "archived-archived-report.txt");
        verify(storageNodeRepository).saveAllAndFlush(List.of(first, second));
    }

    @Test
    void renameNodesRejectsANameOwnedByAnUnchangedSibling() {
        Long userId = 7L;
        StorageNode selected = fileNode(11L, userId, null, "report.txt", "first-key");
        StorageNode unchanged = folderNode(13L, userId, null, "archive");
        when(storageNodeRepository.findByOwnerIdAndIdInAndDeletedFalse(userId, List.of(11L)))
                .thenReturn(List.of(selected));
        when(storageNodeRepository.findByOwnerIdAndParentIdAndDeletedFalse(userId, null))
                .thenReturn(List.of(selected, unchanged));

        assertThatThrownBy(() -> storageCommandService.renameNodes(userId, new BatchRenameNodeRequest(List.of(
                new BatchRenameNodeItem(11L, "archive")
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前目录下已存在同名文件或文件夹。");

        assertThat(selected.getNodeName()).isEqualTo("report.txt");
        verify(storageNodeRepository, never()).saveAll(anyList());
    }

    @Test
    void renameNodesRejectsCaseInsensitiveSiblingConflict() {
        Long userId = 7L;
        StorageNode selected = fileNode(11L, userId, null, "report.txt", "first-key");
        StorageNode unchanged = folderNode(13L, userId, null, "Archive");
        when(storageNodeRepository.findByOwnerIdAndIdInAndDeletedFalse(userId, List.of(11L)))
                .thenReturn(List.of(selected));
        when(storageNodeRepository.findByOwnerIdAndParentIdAndDeletedFalse(userId, null))
                .thenReturn(List.of(selected, unchanged));

        assertThatThrownBy(() -> storageCommandService.renameNodes(userId, new BatchRenameNodeRequest(List.of(
                new BatchRenameNodeItem(11L, "archive")
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前目录下已存在同名文件或文件夹。");

        verify(storageNodeRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void uploadFileRejectsWhenQuotaIsExceededBeforeWritingToCos() {
        Long userId = 6L;
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "archive.zip",
                "application/zip",
                new byte[]{1, 2, 3, 4}
        );

        when(storageNodeRepository.existsActiveSiblingName(userId, null, "archive.zip")).thenReturn(false);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("剩余空间不足。"))
                .when(storageQuotaService)
                .validateUploadFits(userId, 4L);

        assertThatThrownBy(() -> storageCommandService.uploadFile(userId, null, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("剩余空间不足。");

        verify(cosFileStorageService, never()).uploadUserFile(userId, file, "archive.zip");
    }

    @Test
    void uploadFileAutoRenamesWhenSiblingNameExists() {
        Long userId = 6L;
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        when(storageNodeRepository.existsActiveSiblingName(userId, null, "photo.png")).thenReturn(true);
        when(storageNodeRepository.existsActiveSiblingName(userId, null, "photo (1).png")).thenReturn(false);
        when(cosFileStorageService.uploadUserFile(userId, file, "photo (1).png"))
                .thenReturn(new CosFileStorageService.StoredCosFile("cos/photo-copy.png", "image/png", 3L));
        when(storageNodeRepository.save(any(StorageNode.class))).thenAnswer(invocation -> {
            StorageNode node = invocation.getArgument(0);
            ReflectionTestUtils.setField(node, "id", 101L);
            return node;
        });

        var summary = storageCommandService.uploadFile(userId, null, file);

        assertThat(summary.name()).isEqualTo("photo (1).png");
        assertThat(summary.extension()).isEqualTo("png");

        ArgumentCaptor<StorageNode> nodeCaptor = ArgumentCaptor.forClass(StorageNode.class);
        verify(storageNodeRepository).save(nodeCaptor.capture());
        assertThat(nodeCaptor.getValue().getNodeName()).isEqualTo("photo (1).png");
        assertThat(nodeCaptor.getValue().getStoragePath()).isEqualTo("cos/photo-copy.png");
        verify(storageNodeEventPublisher).publishUpsert(nodeCaptor.getValue());
    }

    @Test
    void createFileAccessUrlRejectsMissingFileWithReadableMessage() {
        Long userId = 6L;
        when(storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(404L, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storageCommandService.createFileAccessUrl(userId, 404L, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("文件不存在。");

        verifyNoInteractions(cosFileStorageService);
    }

    @Test
    void createFileAccessUrlRejectsFileWithoutStorageObject() {
        Long userId = 6L;
        StorageNode fileNode = fileNode(12L, userId, null, "lost.txt", "   ");

        when(storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(12L, userId)).thenReturn(Optional.of(fileNode));

        assertThatThrownBy(() -> storageCommandService.createFileAccessUrl(userId, 12L, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("文件未关联云端存储对象。");

        verifyNoInteractions(cosFileStorageService);
    }

    @Test
    void moveNodeRejectsMovingFolderIntoDescendantFolder() {
        Long userId = 8L;
        StorageNode rootFolder = folderNode(21L, userId, null, "项目资料");
        StorageNode childFolder = folderNode(22L, userId, 21L, "归档");

        when(storageNodeRepository.findByOwnerIdAndIdInAndDeletedFalse(userId, List.of(21L))).thenReturn(List.of(rootFolder));
        when(storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(22L, userId)).thenReturn(Optional.of(childFolder));

        assertThatThrownBy(() -> storageCommandService.moveNode(userId, 21L, new MoveNodeRequest(22L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("不能将文件夹移动到自己或自己的子目录内。");
    }

    @Test
    void moveNodeToTrashMarksRootAndDescendantsWithTrashMetadata() {
        Long userId = 9L;
        StorageNode rootFolder = folderNode(31L, userId, 5L, "待删除目录");
        StorageNode childFile = fileNode(32L, userId, 31L, "draft.md", "draft-key");

        when(storageNodeRepository.findByOwnerIdAndIdInAndDeletedFalse(userId, List.of(31L))).thenReturn(List.of(rootFolder));
        when(storageNodeRepository.findByOwnerIdAndParentIdAndDeletedFalse(userId, 31L)).thenReturn(List.of(childFile));
        when(storageNodeRepository.findByOwnerIdAndParentIdAndDeletedFalse(userId, 32L)).thenReturn(List.of());
        when(storageNodeRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        storageCommandService.moveNodeToTrash(userId, 31L);

        assertThat(rootFolder.isDeleted()).isTrue();
        assertThat(rootFolder.getDeletedBy()).isEqualTo(userId);
        assertThat(rootFolder.getDeletedAt()).isNotNull();
        assertThat(rootFolder.getOriginalParentId()).isEqualTo(5L);

        assertThat(childFile.isDeleted()).isTrue();
        assertThat(childFile.getDeletedBy()).isEqualTo(userId);
        assertThat(childFile.getDeletedAt()).isEqualTo(rootFolder.getDeletedAt());
        assertThat(childFile.getOriginalParentId()).isEqualTo(31L);
        verify(storageNodeEventPublisher).publishRemove(List.of(childFile));
    }

    @Test
    void restoreNodeReturnsRootFolderToOriginalParentAndClearsTrashMetadata() {
        Long userId = 10L;
        StorageNode deletedRoot = folderNode(41L, userId, 5L, "已删除目录");
        deletedRoot.setDeleted(true);
        deletedRoot.setOriginalParentId(6L);
        deletedRoot.setDeletedBy(userId);
        deletedRoot.setDeletedAt(LocalDateTime.of(2026, 4, 29, 12, 0));

        StorageNode deletedChild = fileNode(42L, userId, 41L, "合同.pdf", "contract-key");
        deletedChild.setDeleted(true);
        deletedChild.setOriginalParentId(41L);
        deletedChild.setDeletedBy(userId);
        deletedChild.setDeletedAt(deletedRoot.getDeletedAt());

        StorageNode restoredParent = folderNode(6L, userId, null, "项目资料");

        when(storageNodeRepository.findByOwnerIdAndIdInAndDeletedTrue(userId, List.of(41L))).thenReturn(List.of(deletedRoot));
        when(storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(6L, userId)).thenReturn(Optional.of(restoredParent));
        when(storageNodeRepository.existsActiveSiblingNameExcludingId(userId, 6L, "已删除目录", 41L)).thenReturn(false);
        when(storageNodeRepository.findByOwnerIdAndParentId(userId, 41L)).thenReturn(List.of(deletedChild));
        when(storageNodeRepository.findByOwnerIdAndParentId(userId, 42L)).thenReturn(List.of());
        when(storageNodeRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        var summary = storageCommandService.restoreNode(userId, 41L);

        assertThat(summary.parentId()).isEqualTo(6L);
        assertThat(deletedRoot.getParentId()).isEqualTo(6L);
        assertThat(deletedRoot.isDeleted()).isFalse();
        assertThat(deletedRoot.getDeletedAt()).isNull();
        assertThat(deletedRoot.getDeletedBy()).isNull();
        assertThat(deletedRoot.getOriginalParentId()).isNull();

        assertThat(deletedChild.isDeleted()).isFalse();
        assertThat(deletedChild.getDeletedAt()).isNull();
        assertThat(deletedChild.getDeletedBy()).isNull();
        assertThat(deletedChild.getOriginalParentId()).isNull();
        assertThat(deletedChild.getParentId()).isEqualTo(41L);
    }

    @Test
    void restoreNodeFallsBackToRootWhenOriginalParentIsUnavailable() {
        Long userId = 11L;
        StorageNode deletedRoot = folderNode(51L, userId, 99L, "孤立目录");
        deletedRoot.setDeleted(true);
        deletedRoot.setOriginalParentId(77L);
        deletedRoot.setDeletedAt(LocalDateTime.of(2026, 4, 29, 12, 30));

        when(storageNodeRepository.findByOwnerIdAndIdInAndDeletedTrue(userId, List.of(51L))).thenReturn(List.of(deletedRoot));
        when(storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(77L, userId)).thenReturn(Optional.empty());
        when(storageNodeRepository.existsActiveSiblingNameExcludingId(userId, null, "孤立目录", 51L)).thenReturn(false);
        when(storageNodeRepository.findByOwnerIdAndParentId(userId, 51L)).thenReturn(List.of());
        when(storageNodeRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        var summary = storageCommandService.restoreNode(userId, 51L);

        assertThat(summary.parentId()).isNull();
        assertThat(deletedRoot.getParentId()).isNull();
        assertThat(deletedRoot.isDeleted()).isFalse();
    }

    @Test
    void permanentlyDeleteNodeRemovesFilesAndDeletesSubtreeFromLeavesToRoot() {
        Long userId = 12L;
        StorageNode rootFolder = folderNode(61L, userId, null, "历史版本");
        rootFolder.setDeleted(true);
        StorageNode childFolder = folderNode(62L, userId, 61L, "设计稿");
        childFolder.setDeleted(true);
        StorageNode nestedFile = fileNode(63L, userId, 62L, "v1.sketch", "cos/v1.sketch");
        nestedFile.setDeleted(true);
        StorageNode siblingFile = fileNode(64L, userId, 61L, "README.txt", "cos/readme.txt");
        siblingFile.setDeleted(true);

        when(storageNodeRepository.findByOwnerIdAndIdInAndDeletedTrue(userId, List.of(61L))).thenReturn(List.of(rootFolder));
        when(storageNodeRepository.findByOwnerIdAndParentId(userId, 61L)).thenReturn(List.of(childFolder, siblingFile));
        when(storageNodeRepository.findByOwnerIdAndParentId(userId, 62L)).thenReturn(List.of(nestedFile));
        when(storageNodeRepository.findByOwnerIdAndParentId(userId, 63L)).thenReturn(List.of());
        when(storageNodeRepository.findByOwnerIdAndParentId(userId, 64L)).thenReturn(List.of());

        storageCommandService.permanentlyDeleteNode(userId, 61L);

        verify(cosFileStorageService).deleteObjectQuietly("cos/v1.sketch");
        verify(cosFileStorageService).deleteObjectQuietly("cos/readme.txt");

        ArgumentCaptor<List<StorageNode>> deletedNodesCaptor = ArgumentCaptor.forClass(List.class);
        verify(storageNodeRepository).deleteAll(deletedNodesCaptor.capture());
        assertThat(deletedNodesCaptor.getValue())
                .extracting(StorageNode::getId)
                .containsExactly(64L, 63L, 62L, 61L);
    }

    @Test
    void moveNodesCollapsesSelectedDescendantsBeforeSaving() {
        Long userId = 13L;
        StorageNode rootFolder = folderNode(71L, userId, 5L, "项目资料");
        StorageNode nestedFile = fileNode(72L, userId, 71L, "README.md", "cos/readme.md");
        StorageNode targetFolder = folderNode(80L, userId, null, "归档");

        when(storageNodeRepository.findByOwnerIdAndIdInAndDeletedFalse(userId, List.of(71L, 72L)))
                .thenReturn(List.of(rootFolder, nestedFile));
        when(storageNodeRepository.findByIdAndOwnerId(5L, userId)).thenReturn(Optional.empty());
        when(storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(80L, userId)).thenReturn(Optional.of(targetFolder));
        when(storageNodeRepository.existsActiveSiblingNameExcludingId(userId, 80L, "项目资料", 71L)).thenReturn(false);
        when(storageNodeRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        var movedNodes = storageCommandService.moveNodes(
                userId,
                new BatchMoveNodeRequest(List.of(71L, 72L), 80L)
        );

        assertThat(movedNodes).extracting(summary -> summary.id()).containsExactly(71L);
        assertThat(rootFolder.getParentId()).isEqualTo(80L);
        assertThat(nestedFile.getParentId()).isEqualTo(71L);

        ArgumentCaptor<List<StorageNode>> savedNodesCaptor = ArgumentCaptor.forClass(List.class);
        verify(storageNodeRepository).saveAll(savedNodesCaptor.capture());
        assertThat(savedNodesCaptor.getValue())
                .extracting(StorageNode::getId)
                .containsExactly(71L);
        verify(storageNodeRepository, never()).existsActiveSiblingNameExcludingId(userId, 80L, "README.md", 72L);
    }

    @Test
    void moveNodesRejectsCaseInsensitiveConflictsInSameTargetDirectoryBeforeMutatingAnything() {
        Long userId = 15L;
        StorageNode first = fileNode(91L, userId, 10L, "Report.pdf", "cos/report-a.pdf");
        StorageNode second = fileNode(92L, userId, 11L, "report.pdf", "cos/report-b.pdf");
        StorageNode targetFolder = folderNode(93L, userId, null, "归档");

        when(storageNodeRepository.findByOwnerIdAndIdInAndDeletedFalse(userId, List.of(91L, 92L)))
                .thenReturn(List.of(first, second));
        when(storageNodeRepository.findByIdAndOwnerId(10L, userId)).thenReturn(Optional.empty());
        when(storageNodeRepository.findByIdAndOwnerId(11L, userId)).thenReturn(Optional.empty());
        when(storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(93L, userId)).thenReturn(Optional.of(targetFolder));

        assertThatThrownBy(() -> storageCommandService.moveNodes(
                userId,
                new BatchMoveNodeRequest(List.of(91L, 92L), 93L)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("目标目录下已存在同名文件或文件夹。");

        assertThat(first.getParentId()).isEqualTo(10L);
        assertThat(second.getParentId()).isEqualTo(11L);
        verify(storageNodeRepository, never()).saveAll(anyList());
        verify(storageNodeRepository, never()).existsActiveSiblingNameExcludingId(userId, 93L, "Report.pdf", 91L);
    }

    @Test
    void restoreNodesRejectsDuplicateNamesInSameTargetDirectory() {
        Long userId = 14L;
        StorageNode deletedFileA = fileNode(81L, userId, 11L, "合同.pdf", "cos/contract-a.pdf");
        deletedFileA.setDeleted(true);
        deletedFileA.setOriginalParentId(90L);
        StorageNode deletedFileB = fileNode(82L, userId, 12L, "合同.pdf", "cos/contract-b.pdf");
        deletedFileB.setDeleted(true);
        deletedFileB.setOriginalParentId(90L);
        StorageNode targetFolder = folderNode(90L, userId, null, "交接");

        when(storageNodeRepository.findByOwnerIdAndIdInAndDeletedTrue(userId, List.of(81L, 82L)))
                .thenReturn(List.of(deletedFileA, deletedFileB));
        when(storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(90L, userId)).thenReturn(Optional.of(targetFolder));

        assertThatThrownBy(() -> storageCommandService.restoreNodes(
                userId,
                new BatchNodeRequest(List.of(81L, 82L))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("目标目录下已存在同名文件或文件夹。");
    }

    @Test
    void restoreNodesRejectsCaseInsensitiveDuplicateNamesInSameTargetDirectory() {
        Long userId = 16L;
        StorageNode deletedFileA = fileNode(101L, userId, 11L, "Report.pdf", "cos/report-a.pdf");
        deletedFileA.setDeleted(true);
        deletedFileA.setOriginalParentId(105L);
        StorageNode deletedFileB = fileNode(102L, userId, 12L, "report.pdf", "cos/report-b.pdf");
        deletedFileB.setDeleted(true);
        deletedFileB.setOriginalParentId(105L);
        StorageNode targetFolder = folderNode(105L, userId, null, "交接");

        when(storageNodeRepository.findByOwnerIdAndIdInAndDeletedTrue(userId, List.of(101L, 102L)))
                .thenReturn(List.of(deletedFileA, deletedFileB));
        when(storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(105L, userId)).thenReturn(Optional.of(targetFolder));

        assertThatThrownBy(() -> storageCommandService.restoreNodes(
                userId,
                new BatchNodeRequest(List.of(101L, 102L))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("目标目录下已存在同名文件或文件夹。");

        verify(storageNodeRepository, never()).saveAll(anyList());
    }

    private StorageNode folderNode(Long id, Long ownerId, Long parentId, String name) {
        StorageNode node = new StorageNode();
        ReflectionTestUtils.setField(node, "id", id);
        node.setOwnerId(ownerId);
        node.setParentId(parentId);
        node.setNodeName(name);
        node.setNodeType(NodeType.FOLDER);
        node.setFileSize(0L);
        return node;
    }

    private StorageNode fileNode(Long id, Long ownerId, Long parentId, String name, String storagePath) {
        StorageNode node = folderNode(id, ownerId, parentId, name);
        node.setNodeType(NodeType.FILE);
        node.setFileExtension(name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : null);
        node.setMimeType("application/octet-stream");
        node.setStoragePath(storagePath);
        node.setFileSize(1024L);
        return node;
    }
}
