package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.entity.SysUser;
import com.alicia.cloudstorage.api.repository.SysUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SysUserStorageQuotaAccountReader implements StorageQuotaAccountReader {

    private final SysUserRepository sysUserRepository;

    public SysUserStorageQuotaAccountReader(SysUserRepository sysUserRepository) {
        this.sysUserRepository = sysUserRepository;
    }

    @Override
    public StorageQuotaAccount requireAccount(Long userId) {
        SysUser user = sysUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在。"));
        return new StorageQuotaAccount(user.getId(), user.getRole(), user.getStorageQuotaBytes());
    }

    @Override
    public long getTotalAllocatedQuotaBytes() {
        Long allocatedBytes = sysUserRepository.sumStorageQuotaBytes();
        return allocatedBytes == null ? 0L : allocatedBytes;
    }
}
