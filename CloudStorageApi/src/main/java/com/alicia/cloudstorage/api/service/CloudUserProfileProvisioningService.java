package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.entity.CloudUserProfileEntity;
import com.alicia.cloudstorage.api.entity.SysUser;
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

    public CloudUserProfileEntity ensureCloudProfile(SysUser user) {
        return cloudUserProfileRepository.findById(user.getId())
                .orElseGet(() -> cloudUserProfileRepository.save(createDefaultCloudProfile(user)));
    }

    public CloudUserProfileEntity findExistingOrCreateUnsavedCloudProfile(SysUser user) {
        return cloudUserProfileRepository.findById(user.getId())
                .orElseGet(() -> createDefaultCloudProfile(user));
    }

    private CloudUserProfileEntity createDefaultCloudProfile(SysUser user) {
        CloudUserProfileEntity profile = new CloudUserProfileEntity();
        profile.setIdentityUserId(user.getId());
        profile.setHomeBackgroundUrl(user.getHomeBackgroundUrl());
        profile.setStorageQuotaBytes(defaultUserQuotaBytes);
        return profile;
    }
}
