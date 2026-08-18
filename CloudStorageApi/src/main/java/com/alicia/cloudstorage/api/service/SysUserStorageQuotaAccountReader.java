package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.entity.CloudUserProfileEntity;
import com.alicia.cloudstorage.api.entity.SysUser;
import com.alicia.cloudstorage.api.repository.CloudUserProfileRepository;
import com.alicia.cloudstorage.api.repository.SysUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SysUserStorageQuotaAccountReader implements StorageQuotaAccountReader {

    private final SysUserRepository sysUserRepository;
    private final CloudUserProfileRepository cloudUserProfileRepository;

    public SysUserStorageQuotaAccountReader(
            SysUserRepository sysUserRepository,
            CloudUserProfileRepository cloudUserProfileRepository
    ) {
        this.sysUserRepository = sysUserRepository;
        this.cloudUserProfileRepository = cloudUserProfileRepository;
    }

    @Override
    public StorageQuotaAccount requireAccount(Long userId) {
        SysUser user = sysUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在。"));
        Long storageQuotaBytes = cloudUserProfileRepository.findById(userId)
                .map(CloudUserProfileEntity::getStorageQuotaBytes)
                .orElse(user.getStorageQuotaBytes());

        return new StorageQuotaAccount(user.getId(), user.getRole(), storageQuotaBytes);
    }

    @Override
    public long getTotalAllocatedQuotaBytes() {
        Long allocatedBytes = cloudUserProfileRepository.sumStorageQuotaBytes();
        return allocatedBytes == null ? 0L : allocatedBytes;
    }
}
