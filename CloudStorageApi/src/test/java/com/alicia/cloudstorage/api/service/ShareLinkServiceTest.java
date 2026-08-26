package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.identity.IdentityUserSnapshot;
import com.alicia.cloudstorage.api.dto.BatchNodeRequest;
import com.alicia.cloudstorage.api.dto.CreateShareLinkRequest;
import com.alicia.cloudstorage.api.dto.SaveShareLinkRequest;
import com.alicia.cloudstorage.api.dto.StorageNodeSummaryResponse;
import com.alicia.cloudstorage.api.dto.VerifySharePasswordRequest;
import com.alicia.cloudstorage.api.entity.NodeType;
import com.alicia.cloudstorage.api.entity.ShareLink;
import com.alicia.cloudstorage.api.entity.ShareLinkItem;
import com.alicia.cloudstorage.api.entity.ShareLinkStatus;
import com.alicia.cloudstorage.api.entity.StorageNode;
import com.alicia.cloudstorage.api.identity.UserRole;
import com.alicia.cloudstorage.api.identity.UserStatus;
import com.alicia.cloudstorage.api.identity.IdentityUserGateway;
import com.alicia.cloudstorage.api.repository.ShareLinkItemRepository;
import com.alicia.cloudstorage.api.repository.ShareLinkRepository;
import com.alicia.cloudstorage.api.repository.StorageNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private IdentityUserGateway identityUserGateway;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private StorageCommandService storageCommandService;

    @Mock
    private StorageArchiveService storageArchiveService;

    @Mock
    private CosFileStorageService cosFileStorageService;

    private ShareLinkService shareLinkService;

    @BeforeEach
    void setUp() {
        shareLinkService = new ShareLinkService(
                shareLinkRepository,
                shareLinkItemRepository,
                storageNodeRepository,
                identityUserGateway,
                passwordEncoder,
                storageCommandService,
                storageArchiveService,
                cosFileStorageService,
                "share-test-secret",
                5_000
        );
    }

    @Test
    void createShareRejectsMissingRequestBeforeLoadingNodes() {
        assertThatThrownBy(() -> shareLinkService.createShareLink(9L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请求内容不能为空。");

        verifyNoInteractions(storageNodeRepository, shareLinkItemRepository, identityUserGateway, passwordEncoder,
                storageCommandService, storageArchiveService, cosFileStorageService);
    }

    @Test
    void createSharePersistsMultipleRootsAndCollapsesSelectedDescendants() {
        StorageNode folder = folderNode(11L, 9L, null, "docs");
        StorageNode child = fileNode(12L, 9L, 11L, "report.pdf", "cos/report.pdf");
        StorageNode image = fileNode(13L, 9L, null, "cover.png", "cos/cover.png");

        when(storageNodeRepository.findByOwnerIdAndIdInAndDeletedFalse(9L, List.of(11L, 12L, 13L)))
                .thenReturn(List.of(image, child, folder));
        when(storageNodeRepository.findByOwnerIdAndParentIdInAndDeletedFalseOrderByParentIdAscIdAsc(
                9L,
                List.of(11L)
        )).thenReturn(List.of(child));
        doAnswer(invocation -> {
            ShareLink shareLink = invocation.getArgument(0);
            ReflectionTestUtils.setField(shareLink, "id", 101L);
            ReflectionTestUtils.setField(shareLink, "createdAt", LocalDateTime.now());
            ReflectionTestUtils.setField(shareLink, "updatedAt", LocalDateTime.now());
            return shareLink;
        }).when(shareLinkRepository).save(any(ShareLink.class));

        var response = shareLinkService.createShareLink(
                9L,
                new CreateShareLinkRequest(
                        List.of(11L, 12L, 13L),
                        null,
                        null,
                        7,
                        true,
                        true
                )
        );

        assertThat(response.itemCount()).isEqualTo(2L);
        assertThat(response.title()).isEqualTo("共 2 项分享内容");
        verify(shareLinkItemRepository).saveAll(org.mockito.ArgumentMatchers.argThat(items ->
                matchesSavedItems(items, List.of(11L, 13L))
        ));
    }

    @Test
    void createShareDeduplicatesSelectedNodeIdsBeforeLoadingNodes() {
        StorageNode file = fileNode(41L, 9L, null, "report.pdf", "cos/report.pdf");

        when(storageNodeRepository.findByOwnerIdAndIdInAndDeletedFalse(9L, List.of(41L)))
                .thenReturn(List.of(file));
        doAnswer(invocation -> {
            ShareLink shareLink = invocation.getArgument(0);
            ReflectionTestUtils.setField(shareLink, "id", 102L);
            ReflectionTestUtils.setField(shareLink, "createdAt", LocalDateTime.now());
            ReflectionTestUtils.setField(shareLink, "updatedAt", LocalDateTime.now());
            return shareLink;
        }).when(shareLinkRepository).save(any(ShareLink.class));

        var response = shareLinkService.createShareLink(
                9L,
                new CreateShareLinkRequest(List.of(41L, 41L), null, null, 7, true, true)
        );

        assertThat(response.itemCount()).isEqualTo(1L);
        verify(storageNodeRepository).findByOwnerIdAndIdInAndDeletedFalse(9L, List.of(41L));
        verify(shareLinkItemRepository).saveAll(org.mockito.ArgumentMatchers.argThat(items ->
                matchesSavedItems(items, List.of(41L))
        ));
    }

    @Test
    void createShareRejectsSelectionWhenAnyNodeIsUnavailable() {
        StorageNode available = fileNode(21L, 9L, null, "available.txt", "cos/available.txt");
        when(storageNodeRepository.findByOwnerIdAndIdInAndDeletedFalse(9L, List.of(21L, 22L)))
                .thenReturn(List.of(available));

        assertThatThrownBy(() -> shareLinkService.createShareLink(
                9L,
                new CreateShareLinkRequest(List.of(21L, 22L), null, null, 7, true, true)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("分享项目不存在或已被删除。");

        verify(shareLinkRepository, never()).save(any(ShareLink.class));
    }

    @Test
    void createShareRejectsExpandedSelectionBeyondConfiguredLimit() {
        shareLinkService = new ShareLinkService(
                shareLinkRepository,
                shareLinkItemRepository,
                storageNodeRepository,
                identityUserGateway,
                passwordEncoder,
                storageCommandService,
                storageArchiveService,
                cosFileStorageService,
                "share-test-secret",
                2
        );
        StorageNode folder = folderNode(31L, 9L, null, "docs");
        StorageNode firstChild = fileNode(32L, 9L, 31L, "one.txt", "cos/one.txt");
        StorageNode secondChild = fileNode(33L, 9L, 31L, "two.txt", "cos/two.txt");

        when(storageNodeRepository.findByOwnerIdAndIdInAndDeletedFalse(9L, List.of(31L)))
                .thenReturn(List.of(folder));
        when(storageNodeRepository.findByOwnerIdAndParentIdInAndDeletedFalseOrderByParentIdAscIdAsc(
                9L,
                List.of(31L)
        )).thenReturn(List.of(firstChild, secondChild));

        assertThatThrownBy(() -> shareLinkService.createShareLink(
                9L,
                new CreateShareLinkRequest(List.of(31L), null, null, 7, true, true)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("分享内容过多，请拆分后重新分享。");

        verify(shareLinkRepository, never()).save(any(ShareLink.class));
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
    void publicStatusHidesTitleForExpiredShare() {
        ShareLink shareLink = activeShare(12L, 9L, "share-code");
        shareLink.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(shareLinkRepository.findByShareCode("share-code")).thenReturn(Optional.of(shareLink));

        var response = shareLinkService.getPublicStatus("share-code");

        assertThat(response.available()).isFalse();
        assertThat(response.title()).isNull();
        assertThat(response.reason()).isEqualTo("EXPIRED");
    }

    @Test
    void publicStatusHidesTitleForRevokedShare() {
        ShareLink shareLink = activeShare(13L, 9L, "share-code");
        shareLink.setStatus(ShareLinkStatus.REVOKED);

        when(shareLinkRepository.findByShareCode("share-code")).thenReturn(Optional.of(shareLink));

        var response = shareLinkService.getPublicStatus("share-code");

        assertThat(response.available()).isFalse();
        assertThat(response.title()).isNull();
        assertThat(response.reason()).isEqualTo("REVOKED");
    }

    @Test
    void invalidShareCodeFailsBeforeRepositoryLookup() {
        assertThatThrownBy(() -> shareLinkService.getPublicStatus("../bad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("分享链接不存在。");

        verifyNoInteractions(shareLinkRepository);
    }

    @Test
    void shareDetailReadsOwnerNicknameFromIdentityApi() {
        ShareLink shareLink = activeShare(8L, 9L, "share-code");
        StorageNode sharedFile = fileNode(81L, 9L, null, "report.pdf", "cos/report.pdf");
        ShareLinkItem shareItem = shareItem(8L, 81L);

        when(shareLinkRepository.findByShareCode("share-code")).thenReturn(Optional.of(shareLink));
        when(shareLinkItemRepository.findByShareIdOrderBySortOrderAsc(8L)).thenReturn(List.of(shareItem));
        when(storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(81L, 9L)).thenReturn(Optional.of(sharedFile));
        when(identityUserGateway.getUser(9L)).thenReturn(identityUserSnapshot(9L, "Owner Alicia"));

        var response = shareLinkService.getShareDetail(20L, "share-code", null);

        assertThat(response.ownerNickname()).isEqualTo("Owner Alicia");
        assertThat(response.rootNodeIds()).containsExactly(81L);
        verify(identityUserGateway).getUser(9L);
    }

    @Test
    void protectedSharePasswordVerificationRejectsMissingRequestBeforeCheckingPassword() {
        ShareLink shareLink = activeShare(2L, 9L, "share-code");
        shareLink.setPasswordHash("encoded-password");

        when(shareLinkRepository.findByShareCode("share-code")).thenReturn(Optional.of(shareLink));

        assertThatThrownBy(() -> shareLinkService.verifyPassword("share-code", null, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请求内容不能为空。");

        verifyNoInteractions(passwordEncoder, storageCommandService, storageArchiveService, cosFileStorageService);
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
        when(storageNodeRepository.findByOwnerIdAndParentIdInAndDeletedFalseOrderByParentIdAscIdAsc(
                9L,
                List.of(31L)
        )).thenReturn(List.of());

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
    void shareDownloadRejectsFileWithoutStorageObject() {
        ShareLink shareLink = activeShare(14L, 9L, "share-code");
        StorageNode sharedFile = fileNode(141L, 9L, null, "lost.pdf", "   ");
        ShareLinkItem shareItem = shareItem(14L, 141L);

        when(shareLinkRepository.findByShareCode("share-code")).thenReturn(Optional.of(shareLink));
        when(shareLinkItemRepository.findByShareIdOrderBySortOrderAsc(14L)).thenReturn(List.of(shareItem));
        when(storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(141L, 9L)).thenReturn(Optional.of(sharedFile));

        assertThatThrownBy(() -> shareLinkService.createShareFileAccessUrl(20L, "share-code", 141L, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("分享文件不再可用。");

        verify(cosFileStorageService, never()).createAttachmentDownloadUrl(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void createShareArchiveRejectsWhenDownloadDisabled() {
        ShareLink shareLink = activeShare(15L, 9L, "share-code");
        shareLink.setAllowDownload(false);

        when(shareLinkRepository.findByShareCode("share-code")).thenReturn(Optional.of(shareLink));

        assertThatThrownBy(() -> shareLinkService.createShareArchive(
                20L,
                "share-code",
                null,
                new BatchNodeRequest(List.of(151L))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("分享者未允许下载。");

        verifyNoInteractions(storageArchiveService);
    }

    @Test
    void createShareArchiveDelegatesCollapsedSelectedRootsToArchiveService() {
        ShareLink shareLink = activeShare(16L, 9L, "share-code");
        StorageNode sharedFolder = folderNode(161L, 9L, null, "docs");
        StorageNode sharedChild = fileNode(162L, 9L, 161L, "report.pdf", "cos/report.pdf");
        ShareLinkItem shareItem = shareItem(16L, 161L);
        StorageArchiveService.StorageArchivePayload archivePayload =
                new StorageArchiveService.StorageArchivePayload("share.zip", 0L, outputStream -> {
                });

        when(shareLinkRepository.findByShareCode("share-code")).thenReturn(Optional.of(shareLink));
        when(shareLinkItemRepository.findByShareIdOrderBySortOrderAsc(16L)).thenReturn(List.of(shareItem));
        when(storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(161L, 9L)).thenReturn(Optional.of(sharedFolder));
        when(storageNodeRepository.findByOwnerIdAndParentIdInAndDeletedFalseOrderByParentIdAscIdAsc(
                9L,
                List.of(161L)
        )).thenReturn(List.of(sharedChild));
        when(storageArchiveService.createArchiveFromAuthorizedNodes(9L, List.of(sharedFolder), "share"))
                .thenReturn(archivePayload);

        var response = shareLinkService.createShareArchive(
                20L,
                "share-code",
                null,
                new BatchNodeRequest(List.of(161L, 162L))
        );

        assertThat(response).isSameAs(archivePayload);
        verify(storageArchiveService).createArchiveFromAuthorizedNodes(9L, List.of(sharedFolder), "share");
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
                .hasMessage("选择的分享内容不存在或已不可用。");

        verify(storageCommandService, never()).copySharedNodesToUser(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void saveShareRejectsNullSelectedItemIdWithReadableMessage() {
        ShareLink shareLink = activeShare(10L, 9L, "share-code");
        StorageNode sharedFile = fileNode(101L, 9L, null, "report.pdf", "cos/report.pdf");
        ShareLinkItem shareItem = shareItem(10L, 101L);

        when(shareLinkRepository.findByShareCode("share-code")).thenReturn(Optional.of(shareLink));
        when(shareLinkItemRepository.findByShareIdOrderBySortOrderAsc(10L)).thenReturn(List.of(shareItem));
        when(storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(101L, 9L)).thenReturn(Optional.of(sharedFile));

        assertThatThrownBy(() -> shareLinkService.saveShare(
                20L,
                "share-code",
                null,
                new SaveShareLinkRequest(null, java.util.Arrays.asList(101L, null))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("分享项目编号不能为空。");

        verify(storageCommandService, never()).copySharedNodesToUser(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void saveShareDeduplicatesSelectedItemsBeforeCopying() {
        ShareLink shareLink = activeShare(11L, 9L, "share-code");
        StorageNode sharedFile = fileNode(111L, 9L, null, "report.pdf", "cos/report.pdf");
        ShareLinkItem shareItem = shareItem(11L, 111L);
        StorageNodeSummaryResponse copiedFile = new StorageNodeSummaryResponse(
                112L,
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
        when(shareLinkItemRepository.findByShareIdOrderBySortOrderAsc(11L)).thenReturn(List.of(shareItem));
        when(storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(111L, 9L)).thenReturn(Optional.of(sharedFile));
        when(storageCommandService.copySharedNodesToUser(20L, List.of(sharedFile), null)).thenReturn(List.of(copiedFile));

        var copiedNodes = shareLinkService.saveShare(
                20L,
                "share-code",
                null,
                new SaveShareLinkRequest(null, List.of(111L, 111L))
        );

        assertThat(copiedNodes).containsExactly(copiedFile);
        verify(storageCommandService).copySharedNodesToUser(20L, List.of(sharedFile), null);
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
        when(storageNodeRepository.findByOwnerIdAndParentIdInAndDeletedFalseOrderByParentIdAscIdAsc(
                9L,
                List.of(71L)
        )).thenReturn(List.of(sharedChild));
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

    private boolean matchesSavedItems(Iterable<ShareLinkItem> items, List<Long> expectedNodeIds) {
        List<ShareLinkItem> savedItems = new ArrayList<>();
        items.forEach(savedItems::add);
        if (savedItems.size() != expectedNodeIds.size()) {
            return false;
        }

        for (int index = 0; index < expectedNodeIds.size(); index += 1) {
            ShareLinkItem item = savedItems.get(index);
            if (!expectedNodeIds.get(index).equals(item.getNodeId()) || item.getSortOrder() != index) {
                return false;
            }
        }
        return true;
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

    private IdentityUserSnapshot identityUserSnapshot(Long id, String nickname) {
        return new IdentityUserSnapshot(
                id,
                "13900000000",
                "user@example.com",
                nickname,
                null,
                UserRole.USER,
                UserStatus.ACTIVE,
                LocalDateTime.of(2026, 4, 29, 15, 30)
        );
    }
}
