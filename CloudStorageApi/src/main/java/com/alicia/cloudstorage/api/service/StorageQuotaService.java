package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.entity.SysUser;
import com.alicia.cloudstorage.api.entity.UserRole;
import com.alicia.cloudstorage.api.repository.StorageNodeRepository;
import com.alicia.cloudstorage.api.repository.SysUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class StorageQuotaService {

    private final SysUserRepository sysUserRepository;
    private final StorageNodeRepository storageNodeRepository;
    private final long systemTotalSpaceBytes;
    private final long defaultUserQuotaBytes;

    public StorageQuotaService(
            SysUserRepository sysUserRepository,
            StorageNodeRepository storageNodeRepository,
            @Value("${alicia.storage.total-space-bytes:1073741824}") long systemTotalSpaceBytes,
            @Value("${alicia.storage.default-user-quota-bytes:536870912}") long defaultUserQuotaBytes
    ) {
        if (systemTotalSpaceBytes <= 0) {
            throw new IllegalArgumentException("系统总存储空间配置必须大于 0。");
        }

        if (defaultUserQuotaBytes <= 0) {
            throw new IllegalArgumentException("默认用户存储额度配置必须大于 0。");
        }

        if (defaultUserQuotaBytes > systemTotalSpaceBytes) {
            throw new IllegalArgumentException("默认用户存储额度不能超过系统总存储空间。");
        }

        this.sysUserRepository = sysUserRepository;
        this.storageNodeRepository = storageNodeRepository;
        this.systemTotalSpaceBytes = systemTotalSpaceBytes;
        this.defaultUserQuotaBytes = defaultUserQuotaBytes;
    }

    public long getSystemTotalSpaceBytes() {
        return systemTotalSpaceBytes;
    }

    public long getDefaultUserQuotaBytes() {
        return defaultUserQuotaBytes;
    }

    public boolean isAdmin(Long userId) {
        return requireUser(userId).getRole() == UserRole.ADMIN;
    }

    public long getUserQuotaBytes(Long userId) {
        Long quotaBytes = requireUser(userId).getStorageQuotaBytes();
        return quotaBytes == null ? defaultUserQuotaBytes : quotaBytes;
    }

    public long getUsedBytes(Long userId) {
        Long usedBytes = storageNodeRepository.sumFileSizeByOwnerId(userId);
        return usedBytes == null ? 0L : usedBytes;
    }

    public long getRemainingBytes(Long userId) {
        return getRemainingBytes(getUserQuotaBytes(userId), getUsedBytes(userId));
    }

    public long getTotalAllocatedQuotaBytes() {
        Long allocatedBytes = sysUserRepository.sumStorageQuotaBytes();
        return allocatedBytes == null ? 0L : allocatedBytes;
    }

    public long getTotalActualUsedBytes() {
        Long usedBytes = storageNodeRepository.sumFileSizeAllOwners();
        return usedBytes == null ? 0L : usedBytes;
    }

    public long normalizeQuotaBytes(Long rawQuotaBytes, String fieldName) {
        if (rawQuotaBytes == null) {
            throw new IllegalArgumentException(fieldName + "不能为空。");
        }

        if (rawQuotaBytes <= 0) {
            throw new IllegalArgumentException(fieldName + "必须大于 0。");
        }

        if (rawQuotaBytes > systemTotalSpaceBytes) {
            throw new IllegalArgumentException(fieldName + "不能超过系统总存储空间。");
        }

        return rawQuotaBytes;
    }

    public void validateQuotaAssignment(Long userId, long quotaBytes) {
        long usedBytes = getUsedBytes(userId);

        if (quotaBytes < usedBytes) {
            throw new IllegalArgumentException(
                    "用户当前已用空间为 " + formatBytes(usedBytes) + "，最大额度不能低于已用空间。"
            );
        }
    }

    public void validateUploadFits(Long userId, long uploadBytes) {
        if (uploadBytes <= 0) {
            return;
        }

        if (isAdmin(userId)) {
            return;
        }

        long quotaBytes = getUserQuotaBytes(userId);
        long usedBytes = getUsedBytes(userId);
        long remainingBytes = getRemainingBytes(quotaBytes, usedBytes);

        if (remainingBytes >= uploadBytes) {
            return;
        }

        throw new IllegalArgumentException(
                "剩余空间不足，当前已用 "
                        + formatBytes(usedBytes)
                        + " / "
                        + formatBytes(quotaBytes)
                        + "，本次上传需要 "
                        + formatBytes(uploadBytes)
                        + "。"
        );
    }

    private long getRemainingBytes(long quotaBytes, long usedBytes) {
        return Math.max(0L, quotaBytes - usedBytes);
    }

    private SysUser requireUser(Long userId) {
        return sysUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在。"));
    }

    private String formatBytes(long value) {
        if (value < 1024) {
            return value + " B";
        }

        String[] units = {"KB", "MB", "GB", "TB"};
        double size = value;
        int unitIndex = -1;

        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex += 1;
        }

        String pattern = size >= 100 ? "%.0f %s" : "%.1f %s";
        return String.format(Locale.ROOT, pattern, size, units[unitIndex]);
    }
}
