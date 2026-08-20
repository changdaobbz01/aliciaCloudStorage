package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.identity.IdentityAccount;
import com.alicia.cloudstorage.api.entity.CloudUserProfileEntity;
import com.alicia.cloudstorage.api.identity.IdentityUserGateway;
import com.alicia.cloudstorage.api.repository.CloudUserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CloudProfileStorageQuotaAccountReader implements StorageQuotaAccountReader {

    private final IdentityUserGateway identityUserGateway;
    private final CloudUserProfileRepository cloudUserProfileRepository;

    /**
     * 身份服务提供用户和角色，云盘 profile 拥有存储额度。
     */
    public CloudProfileStorageQuotaAccountReader(
            IdentityUserGateway identityUserGateway,
            CloudUserProfileRepository cloudUserProfileRepository
    ) {
        this.identityUserGateway = identityUserGateway;
        this.cloudUserProfileRepository = cloudUserProfileRepository;
    }

    @Override
    public StorageQuotaAccount requireAccount(Long userId) {
        IdentityAccount account = identityUserGateway.getUser(userId);
        Long storageQuotaBytes = cloudUserProfileRepository.findById(userId)
                .map(CloudUserProfileEntity::getStorageQuotaBytes)
                .orElse(null);

        return new StorageQuotaAccount(account.id(), account.role(), storageQuotaBytes);
    }

    @Override
    public long getTotalAllocatedQuotaBytes() {
        Long allocatedBytes = cloudUserProfileRepository.sumStorageQuotaBytes();
        return allocatedBytes == null ? 0L : allocatedBytes;
    }
}
