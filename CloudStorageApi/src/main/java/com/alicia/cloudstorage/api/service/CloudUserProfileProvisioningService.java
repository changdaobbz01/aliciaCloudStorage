package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.entity.CloudUserProfileEntity;
import com.alicia.cloudstorage.api.repository.CloudUserProfileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CloudUserProfileProvisioningService {

    private final CloudUserProfileRepository cloudUserProfileRepository;
    private final long defaultUserQuotaBytes;

    public CloudUserProfileProvisioningService(
            CloudUserProfileRepository cloudUserProfileRepository,
            @Value("${alicia.storage.default-user-quota-bytes:536870912}") long defaultUserQuotaBytes
    ) {
        if (defaultUserQuotaBytes <= 0) {
            throw new IllegalArgumentException("默认用户存储额度配置必须大于 0。");
        }

        this.cloudUserProfileRepository = cloudUserProfileRepository;
        this.defaultUserQuotaBytes = defaultUserQuotaBytes;
    }

    public CloudUserProfileEntity ensureCloudProfile(IdentityAccount account) {
        Long userId = requireIdentityUserId(account);
        return cloudUserProfileRepository.findById(userId)
                .orElseGet(() -> cloudUserProfileRepository.save(createDefaultCloudProfile(userId)));
    }

    public CloudUserProfileEntity findExistingOrCreateUnsavedCloudProfile(IdentityAccount account) {
        Long userId = requireIdentityUserId(account);
        return cloudUserProfileRepository.findById(userId)
                .orElseGet(() -> createDefaultCloudProfile(userId));
    }

    private CloudUserProfileEntity createDefaultCloudProfile(Long userId) {
        CloudUserProfileEntity profile = new CloudUserProfileEntity();
        profile.setIdentityUserId(userId);
        profile.setHomeBackgroundUrl(null);
        profile.setStorageQuotaBytes(defaultUserQuotaBytes);
        return profile;
    }

    private Long requireIdentityUserId(IdentityAccount account) {
        if (account == null || account.id() == null) {
            throw new IllegalArgumentException("用户不存在。");
        }

        return account.id();
    }
}
