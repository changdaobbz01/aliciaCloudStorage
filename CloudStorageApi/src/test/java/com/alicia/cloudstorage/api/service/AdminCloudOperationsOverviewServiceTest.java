package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.AdminCloudOperationsOverviewResponse;
import com.alicia.cloudstorage.api.entity.MultipartUploadStatus;
import com.alicia.cloudstorage.api.entity.NodeType;
import com.alicia.cloudstorage.api.entity.ShareLinkStatus;
import com.alicia.cloudstorage.api.repository.MultipartUploadSessionRepository;
import com.alicia.cloudstorage.api.repository.ShareLinkRepository;
import com.alicia.cloudstorage.api.repository.StorageNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCloudOperationsOverviewServiceTest {

    @Mock
    private StorageQuotaService storageQuotaService;

    @Mock
    private StorageNodeRepository storageNodeRepository;

    @Mock
    private ShareLinkRepository shareLinkRepository;

    @Mock
    private MultipartUploadSessionRepository multipartUploadSessionRepository;

    private AdminCloudOperationsOverviewService adminCloudOperationsOverviewService;

    @BeforeEach
    void setUp() {
        adminCloudOperationsOverviewService = new AdminCloudOperationsOverviewService(
                storageQuotaService,
                storageNodeRepository,
                shareLinkRepository,
                multipartUploadSessionRepository,
                24L
        );
    }

    @Test
    void getOverviewCombinesCapacityTrashShareAndUploadMetrics() {
        LocalDateTime latestDeletedAt = LocalDateTime.of(2026, 8, 26, 9, 30);
        LocalDateTime latestShareCreatedAt = LocalDateTime.of(2026, 8, 26, 10, 30);
        LocalDateTime latestShareAccessedAt = LocalDateTime.of(2026, 8, 26, 11, 30);
        LocalDateTime latestUploadUpdatedAt = LocalDateTime.of(2026, 8, 26, 12, 30);

        when(storageQuotaService.getSystemTotalSpaceBytes()).thenReturn(10_000L);
        when(storageQuotaService.getTotalAllocatedQuotaBytes()).thenReturn(4_000L);
        when(storageQuotaService.getTotalActualUsedBytes()).thenReturn(1_250L);
        when(storageNodeRepository.countByDeletedFalse()).thenReturn(30L);
        when(storageNodeRepository.countByNodeTypeAndDeletedFalse(NodeType.FOLDER)).thenReturn(8L);
        when(storageNodeRepository.countByNodeTypeAndDeletedFalse(NodeType.FILE)).thenReturn(22L);
        when(storageNodeRepository.countByDeletedTrue()).thenReturn(6L);
        when(storageNodeRepository.countRootTrashNodesAllOwners()).thenReturn(2L);
        when(storageNodeRepository.countByNodeTypeAndDeletedTrue(NodeType.FOLDER)).thenReturn(1L);
        when(storageNodeRepository.countByNodeTypeAndDeletedTrue(NodeType.FILE)).thenReturn(5L);
        when(storageNodeRepository.sumTrashFileSizeAllOwners()).thenReturn(900L);
        when(storageNodeRepository.findLatestDeletedAt()).thenReturn(latestDeletedAt);
        when(shareLinkRepository.count()).thenReturn(12L);
        when(shareLinkRepository.countByStatus(ShareLinkStatus.ACTIVE)).thenReturn(9L);
        when(shareLinkRepository.countAvailableActiveLinks(any(LocalDateTime.class))).thenReturn(7L);
        when(shareLinkRepository.countExpiredActiveLinks(any(LocalDateTime.class))).thenReturn(2L);
        when(shareLinkRepository.countByStatus(ShareLinkStatus.REVOKED)).thenReturn(3L);
        when(shareLinkRepository.countByPasswordHashIsNotNull()).thenReturn(5L);
        when(shareLinkRepository.countByAllowDownloadTrue()).thenReturn(10L);
        when(shareLinkRepository.countByAllowSaveTrue()).thenReturn(8L);
        when(shareLinkRepository.sumViewCount()).thenReturn(88L);
        when(shareLinkRepository.findLatestCreatedAt()).thenReturn(latestShareCreatedAt);
        when(shareLinkRepository.findLatestAccessedAt()).thenReturn(latestShareAccessedAt);
        when(multipartUploadSessionRepository.count()).thenReturn(14L);
        when(multipartUploadSessionRepository.countByStatus(MultipartUploadStatus.IN_PROGRESS)).thenReturn(4L);
        when(multipartUploadSessionRepository.countByStatusAndUpdatedAtBefore(
                any(MultipartUploadStatus.class),
                any(LocalDateTime.class)
        )).thenReturn(1L);
        when(multipartUploadSessionRepository.countByStatus(MultipartUploadStatus.COMPLETED)).thenReturn(7L);
        when(multipartUploadSessionRepository.countByStatus(MultipartUploadStatus.ABORTED)).thenReturn(3L);
        when(multipartUploadSessionRepository.findLatestUpdatedAtByStatus(MultipartUploadStatus.IN_PROGRESS))
                .thenReturn(latestUploadUpdatedAt);

        AdminCloudOperationsOverviewResponse response = adminCloudOperationsOverviewService.getOverview();

        assertThat(response.generatedAt()).isNotNull();
        assertThat(response.capacity().systemTotalSpaceBytes()).isEqualTo(10_000L);
        assertThat(response.capacity().allocatedQuotaBytes()).isEqualTo(4_000L);
        assertThat(response.capacity().actualUsedBytes()).isEqualTo(1_250L);
        assertThat(response.capacity().remainingUnallocatedBytes()).isEqualTo(6_000L);
        assertThat(response.capacity().allocatedUsageRatio()).isEqualTo(0.4);
        assertThat(response.capacity().actualUsageRatio()).isEqualTo(0.125);
        assertThat(response.activeNodes().totalItems()).isEqualTo(30L);
        assertThat(response.activeNodes().folderCount()).isEqualTo(8L);
        assertThat(response.activeNodes().fileCount()).isEqualTo(22L);
        assertThat(response.trash().totalItems()).isEqualTo(6L);
        assertThat(response.trash().rootItems()).isEqualTo(2L);
        assertThat(response.trash().bytes()).isEqualTo(900L);
        assertThat(response.trash().latestDeletedAt()).isEqualTo(latestDeletedAt);
        assertThat(response.shares().totalLinks()).isEqualTo(12L);
        assertThat(response.shares().availableLinks()).isEqualTo(7L);
        assertThat(response.shares().expiredActiveLinks()).isEqualTo(2L);
        assertThat(response.shares().totalViews()).isEqualTo(88L);
        assertThat(response.shares().latestCreatedAt()).isEqualTo(latestShareCreatedAt);
        assertThat(response.shares().latestAccessedAt()).isEqualTo(latestShareAccessedAt);
        assertThat(response.multipartUploads().totalSessions()).isEqualTo(14L);
        assertThat(response.multipartUploads().inProgressSessions()).isEqualTo(4L);
        assertThat(response.multipartUploads().staleInProgressSessions()).isEqualTo(1L);
        assertThat(response.multipartUploads().completedSessions()).isEqualTo(7L);
        assertThat(response.multipartUploads().abortedSessions()).isEqualTo(3L);
        assertThat(response.multipartUploads().latestInProgressUpdatedAt()).isEqualTo(latestUploadUpdatedAt);
        assertThat(response.multipartUploads().staleHours()).isEqualTo(24L);

        verify(multipartUploadSessionRepository).countByStatusAndUpdatedAtBefore(
                MultipartUploadStatus.IN_PROGRESS,
                response.generatedAt().minusHours(24L)
        );
    }

    @Test
    void getOverviewNormalizesNullSumsAndCappedRatios() {
        when(storageQuotaService.getSystemTotalSpaceBytes()).thenReturn(10L);
        when(storageQuotaService.getTotalAllocatedQuotaBytes()).thenReturn(15L);
        when(storageQuotaService.getTotalActualUsedBytes()).thenReturn(12L);
        when(storageNodeRepository.sumTrashFileSizeAllOwners()).thenReturn(null);
        when(shareLinkRepository.sumViewCount()).thenReturn(null);

        AdminCloudOperationsOverviewResponse response = adminCloudOperationsOverviewService.getOverview();

        assertThat(response.capacity().remainingUnallocatedBytes()).isZero();
        assertThat(response.capacity().allocatedUsageRatio()).isEqualTo(1.0);
        assertThat(response.capacity().actualUsageRatio()).isEqualTo(1.0);
        assertThat(response.trash().bytes()).isZero();
        assertThat(response.shares().totalViews()).isZero();
    }
}
