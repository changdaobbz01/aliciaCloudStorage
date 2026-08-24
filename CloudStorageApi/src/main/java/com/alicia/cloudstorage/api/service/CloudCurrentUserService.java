package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.identity.IdentityUserSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CloudCurrentUserService {

    private final CloudUserProfileService cloudUserProfileService;

    public CloudCurrentUserService(CloudUserProfileService cloudUserProfileService) {
        this.cloudUserProfileService = cloudUserProfileService;
    }

    public UserProfileResponse getCurrentUser(IdentityUserSnapshot account) {
        return cloudUserProfileService.getCurrentUser(account);
    }
}
