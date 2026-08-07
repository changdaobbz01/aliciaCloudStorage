package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.SaveShareLinkRequest;
import com.alicia.cloudstorage.api.dto.StorageNodeSummaryResponse;
import com.alicia.cloudstorage.api.dto.VerifySharePasswordRequest;
import com.alicia.cloudstorage.api.entity.NodeType;
import com.alicia.cloudstorage.api.entity.ShareLink;
import com.alicia.cloudstorage.api.entity.ShareLinkItem;
import com.alicia.cloudstorage.api.entity.ShareLinkStatus;
import com.alicia.cloudstorage.api.entity.StorageNode;
import com.alicia.cloudstorage.api.repository.ShareLinkItemRepository;
import com.alicia.cloudstorage.api.repository.ShareLinkRepository;
import com.alicia.cloudstorage.api.repository.StorageNodeRepository;
import com.alicia.cloudstorage.api.repository.SysUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareLinkServiceTest {

    @Mock
    private ShareLinkRepository shareLinkRepository;

    @Mock
    private ShareLinkItemRepository shareLinkItemRepository;

    @Mock
    private StorageNodeRepository storageNodeRepository;

    @Mock
    private SysUserRepository sysUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private StorageCommandService storageCommandService;

    @Mock
    private CosFileStorageService cosFileStorageService;

    private ShareLinkService shareLinkService;

    @BeforeEach
    void setUp() {
        shareLinkService = new ShareLinkService(
                shareLinkRepository,
                shareLinkItemRepository,
                storageNodeRepository,
                sysUserRepository,
                passwordEncoder,
                storageCommandService,
                cosFileStorageService,
                "share-test-secret"
        );
    }

    @Test
    void protectedShareDetailRequiresVerifiedShareAccessToken() {
        ShareLink shareLink = activeShare(1L, 9L, "share-code");
        shareLink.setPasswordHash("encoded-password");

        when(shareLinkRepository.findByShareCode("share-code")).thenReturn(Optional.of(shareLink));

        assertThatThrownBy(() -> shareLinkService.getShareDetail(20L, "share-code", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请先输入分享提取码。");

        verify(shareLinkItemRepository, never()).findByShareIdOrderBySortOrderAsc(1L);
    }

    @Test
    void verifyPasswordReturnsShortLivedAccessToken() {
        ShareLink shareLink = activeShare(2L, 9L, "share-code");
        shareLink.setPasswordHash("encoded-password");

        when(shareLinkRepository.findByShareCode("share-code")).thenReturn(Optional.of(shareLink));
        when(passwordEncoder.matches("1234", "encoded-password")).thenReturn(true);

        var response = shareLinkService.verifyPassword("share-code", new VerifySharePasswordRequest("1234"), "127.0.0.1");

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.expiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void verifyPasswordTemporarilyLocksRepeatedFailuresFromSameClient() {
        ShareLink shareLink = activeShare(5L, 9L, "share-code");
        shareLink.setPasswordHash("encoded-password");

        when(shareLinkRepository.findByShareCode("share-code")).thenReturn(Optional.of(shareLink));
        when(passwordEncoder.matches("bad", "encoded-password")).thenReturn(false);

        for (int index = 0; index < 5; index += 1) {
            assertThatThrownBy(() -> shareLinkService.verifyPassword(
                    "share-code",
                    new VerifySharePasswordRequest("bad"),
                    "127.0.0.1"
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("提取码不正确。");
        }

        assertThatThrownBy(() -> shareLinkService.verifyPassword(
                "share-code",
                new VerifySharePasswordRequest("bad"),
                "127.0.0.1"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("提取码错误次数过多，请稍后再试。");
    }

    @Test
    void shareDownloadRejectsFileOutsideSharedScope() {
        ShareLink shareLink = activeShare(3L, 9L, "share-code");
        StorageNode sharedRoot = folderNode(31L, 9L, null, "shared");
        ShareLinkItem shareItem = shareItem(3L, 31L);

        when(shareLinkRepository.findByShareCode("share-code")).thenReturn(Optional.of(shareLink));
        when(shareLinkItemRepository.findByShareIdOrderBySortOrderAsc(3L)).thenReturn(List.of(shareItem));
        when(storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(31L, 9L)).thenReturn(Optional.of(sharedRoot));
        when(storageNodeRepository.findByOwnerIdAndParentIdAndDeletedFalse(9L, 31L)).thenReturn(List.of());

        assertThatThrownBy(() -> shareLinkService.createShareFileAccessUrl(20L, "share-code", 99L, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("分享文件不存在。");

        verify(cosFileStorageService, never()).createAttachmentDownloadUrl(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void saveShareDelegatesToStorageCopyService() {
        ShareLink shareLink = activeShare(4L, 9L, "share-code");
        StorageNode sharedFile = fileNode(41L, 9L, null, "report.pdf", "cos/report.pdf");
        ShareLinkItem shareItem = shareItem(4L, 41L);
        StorageNodeSummaryResponse copiedFile = new StorageNodeSummaryResponse(
                80L,
                null,
                "report.pdf",
                "FILE",
                1024L,
                "pdf",
                "application/pdf",
                LocalDateTime.now(),
                null
        );

        when(shareLinkRepository.findByShareCode("share-code")).thenReturn(Optional.of(shareLink));
        when(shareLinkItemRepository.findByShareIdOrderBySortOrderAsc(4L)).thenReturn(List.of(shareItem));
        when(storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(41L, 9L)).thenReturn(Optional.of(sharedFile));
        when(storageCommandService.copySharedNodesToUser(20L, List.of(sharedFile), null)).thenReturn(List.of(copiedFile));

        var copiedNodes = shareLinkService.saveShare(20L, "share-code", null, new SaveShareLinkRequest(null, null));

        assertThat(copiedNodes).containsExactly(copiedFile);
        verify(storageCommandService).copySharedNodesToUser(20L, List.of(sharedFile), null);
    }

    @Test
    void saveShareRejectsSelectedItemOutsideSharedScope() {
        ShareLink shareLink = activeShare(6L, 9L, "share-code");
        StorageNode sharedFile = fileNode(61L, 9L, null, "report.pdf", "cos/report.pdf");
        ShareLinkItem shareItem = shareItem(6L, 61L);

        when(shareLinkRepository.findByShareCode("share-code")).thenReturn(Optional.of(shareLink));
        when(shareLinkItemRepository.findByShareIdOrderBySortOrderAsc(6L)).thenReturn(List.of(shareItem));
        when(storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(61L, 9L)).thenReturn(Optional.of(sharedFile));

        assertThatThrownBy(() -> shareLinkService.saveShare(
                20L,
                "share-code",
                null,
                new SaveShareLinkRequest(null, List.of(99L))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selected share item is not available.");

        verify(storageCommandService, never()).copySharedNodesToUser(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void saveShareCollapsesSelectedParentAndChildBeforeCopying() {
        ShareLink shareLink = activeShare(7L, 9L, "share-code");
        StorageNode sharedFolder = folderNode(71L, 9L, null, "docs");
        StorageNode sharedChild = fileNode(72L, 9L, 71L, "report.pdf", "cos/report.pdf");
        ShareLinkItem shareItem = shareItem(7L, 71L);
        StorageNodeSummaryResponse copiedFolder = new StorageNodeSummaryResponse(
                90L,
                null,
                "docs",
                "FOLDER",
                0L,
                null,
                null,
                LocalDateTime.now(),
                null
        );

        when(shareLinkRepository.findByShareCode("share-code")).thenReturn(Optional.of(shareLink));
        when(shareLinkItemRepository.findByShareIdOrderBySortOrderAsc(7L)).thenReturn(List.of(shareItem));
        when(storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(71L, 9L)).thenReturn(Optional.of(sharedFolder));
        when(storageNodeRepository.findByOwnerIdAndParentIdAndDeletedFalse(9L, 71L)).thenReturn(List.of(sharedChild));
        when(storageCommandService.copySharedNodesToUser(20L, List.of(sharedFolder), null)).thenReturn(List.of(copiedFolder));

        var copiedNodes = shareLinkService.saveShare(
                20L,
                "share-code",
                null,
                new SaveShareLinkRequest(null, List.of(71L, 72L))
        );

        assertThat(copiedNodes).containsExactly(copiedFolder);
        verify(storageCommandService).copySharedNodesToUser(20L, List.of(sharedFolder), null);
    }

    private ShareLink activeShare(Long id, Long ownerId, String shareCode) {
        ShareLink shareLink = new ShareLink();
        ReflectionTestUtils.setField(shareLink, "id", id);
        ReflectionTestUtils.setField(shareLink, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(shareLink, "updatedAt", LocalDateTime.now());
        shareLink.setShareCode(shareCode);
        shareLink.setOwnerId(ownerId);
        shareLink.setTitle("share");
        shareLink.setAllowDownload(true);
        shareLink.setAllowSave(true);
        shareLink.setStatus(ShareLinkStatus.ACTIVE);
        shareLink.setViewCount(0L);
        return shareLink;
    }

    private ShareLinkItem shareItem(Long shareId, Long nodeId) {
        ShareLinkItem item = new ShareLinkItem();
        item.setShareId(shareId);
        item.setNodeId(nodeId);
        item.setSortOrder(0);
        return item;
    }

    private StorageNode folderNode(Long id, Long ownerId, Long parentId, String name) {
        StorageNode node = new StorageNode();
        ReflectionTestUtils.setField(node, "id", id);
        ReflectionTestUtils.setField(node, "updatedAt", LocalDateTime.now());
        node.setOwnerId(ownerId);
        node.setParentId(parentId);
        node.setNodeName(name);
        node.setNodeType(NodeType.FOLDER);
        node.setFileSize(0L);
        node.setDeleted(false);
        return node;
    }

    private StorageNode fileNode(Long id, Long ownerId, Long parentId, String name, String storagePath) {
        StorageNode node = folderNode(id, ownerId, parentId, name);
        node.setNodeType(NodeType.FILE);
        node.setFileSize(1024L);
        node.setFileExtension("pdf");
        node.setMimeType("application/pdf");
        node.setStoragePath(storagePath);
        return node;
    }
}
