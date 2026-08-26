package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.AdminCloudOperationsOverviewResponse;
import com.alicia.cloudstorage.api.entity.MultipartUploadStatus;
import com.alicia.cloudstorage.api.entity.NodeType;
import com.alicia.cloudstorage.api.entity.ShareLinkStatus;
import com.alicia.cloudstorage.api.repository.MultipartUploadSessionRepository;
import com.alicia.cloudstorage.api.repository.ShareLinkRepository;
import com.alicia.cloudstorage.api.repository.StorageNodeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class AdminCloudOperationsOverviewService {

    private final StorageQuotaService storageQuotaService;
    private final StorageNodeRepository storageNodeRepository;
    private final ShareLinkRepository shareLinkRepository;
    private final MultipartUploadSessionRepository multipartUploadSessionRepository;
    private final long multipartUploadStaleHours;

    public AdminCloudOperationsOverviewService(
            StorageQuotaService storageQuotaService,
            StorageNodeRepository storageNodeRepository,
            ShareLinkRepository shareLinkRepository,
            MultipartUploadSessionRepository multipartUploadSessionRepository,
            @Value("${alicia.multipart-upload.stale-hours:24}") long multipartUploadStaleHours
    ) {
        this.storageQuotaService = storageQuotaService;
        this.storageNodeRepository = storageNodeRepository;
        this.shareLinkRepository = shareLinkRepository;
        this.multipartUploadSessionRepository = multipartUploadSessionRepository;
        this.multipartUploadStaleHours = Math.max(1L, multipartUploadStaleHours);
    }

    public AdminCloudOperationsOverviewResponse getOverview() {
        LocalDateTime now = LocalDateTime.now();

        return new AdminCloudOperationsOverviewResponse(
                now,
                capacityOverview(),
                activeNodeOverview(),
                trashOverview(),
                shareOverview(now),
                multipartUploadOverview(now)
        );
    }

    private AdminCloudOperationsOverviewResponse.CapacityOverview capacityOverview() {
        long systemTotalSpaceBytes = storageQuotaService.getSystemTotalSpaceBytes();
        long allocatedQuotaBytes = storageQuotaService.getTotalAllocatedQuotaBytes();
        long actualUsedBytes = storageQuotaService.getTotalActualUsedBytes();
        long remainingUnallocatedBytes = Math.max(0L, systemTotalSpaceBytes - allocatedQuotaBytes);

        return new AdminCloudOperationsOverviewResponse.CapacityOverview(
                systemTotalSpaceBytes,
                allocatedQuotaBytes,
                actualUsedBytes,
                remainingUnallocatedBytes,
                ratio(allocatedQuotaBytes, systemTotalSpaceBytes),
                ratio(actualUsedBytes, systemTotalSpaceBytes)
        );
    }

    private AdminCloudOperationsOverviewResponse.NodeOverview activeNodeOverview() {
        return new AdminCloudOperationsOverviewResponse.NodeOverview(
                storageNodeRepository.countByDeletedFalse(),
                storageNodeRepository.countByNodeTypeAndDeletedFalse(NodeType.FOLDER),
                storageNodeRepository.countByNodeTypeAndDeletedFalse(NodeType.FILE)
        );
    }

    private AdminCloudOperationsOverviewResponse.TrashOverview trashOverview() {
        return new AdminCloudOperationsOverviewResponse.TrashOverview(
                storageNodeRepository.countByDeletedTrue(),
                storageNodeRepository.countRootTrashNodesAllOwners(),
                storageNodeRepository.countByNodeTypeAndDeletedTrue(NodeType.FOLDER),
                storageNodeRepository.countByNodeTypeAndDeletedTrue(NodeType.FILE),
                nullToZero(storageNodeRepository.sumTrashFileSizeAllOwners()),
                storageNodeRepository.findLatestDeletedAt()
        );
    }

    private AdminCloudOperationsOverviewResponse.ShareOverview shareOverview(LocalDateTime now) {
        return new AdminCloudOperationsOverviewResponse.ShareOverview(
                shareLinkRepository.count(),
                shareLinkRepository.countByStatus(ShareLinkStatus.ACTIVE),
                shareLinkRepository.countAvailableActiveLinks(now),
                shareLinkRepository.countExpiredActiveLinks(now),
                shareLinkRepository.countByStatus(ShareLinkStatus.REVOKED),
                shareLinkRepository.countByPasswordHashIsNotNull(),
                shareLinkRepository.countByAllowDownloadTrue(),
                shareLinkRepository.countByAllowSaveTrue(),
                nullToZero(shareLinkRepository.sumViewCount()),
                shareLinkRepository.findLatestCreatedAt(),
                shareLinkRepository.findLatestAccessedAt()
        );
    }

    private AdminCloudOperationsOverviewResponse.MultipartUploadOverview multipartUploadOverview(LocalDateTime now) {
        LocalDateTime staleCutoff = now.minusHours(multipartUploadStaleHours);

        return new AdminCloudOperationsOverviewResponse.MultipartUploadOverview(
                multipartUploadSessionRepository.count(),
                multipartUploadSessionRepository.countByStatus(MultipartUploadStatus.IN_PROGRESS),
                multipartUploadSessionRepository.countByStatusAndUpdatedAtBefore(
                        MultipartUploadStatus.IN_PROGRESS,
                        staleCutoff
                ),
                multipartUploadSessionRepository.countByStatus(MultipartUploadStatus.COMPLETED),
                multipartUploadSessionRepository.countByStatus(MultipartUploadStatus.ABORTED),
                multipartUploadSessionRepository.findLatestUpdatedAtByStatus(MultipartUploadStatus.IN_PROGRESS),
                multipartUploadStaleHours
        );
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }

        return Math.min(1.0, Math.max(0.0, (double) numerator / denominator));
    }
}
