package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.IdentityUserResponse;
import com.alicia.cloudstorage.identity.dto.UpdateIdentityProfileRequest;
import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IdentityProfileService {

    private final IdentityPrincipalService identityPrincipalService;
    private final IdentityUserRepository identityUserRepository;
    private final IdentityUserInputNormalizer identityUserInputNormalizer;
    private final IdentityAuditLogService identityAuditLogService;
    private final IdentityUserResponseAssembler identityUserResponseAssembler;

    public IdentityProfileService(
            IdentityPrincipalService identityPrincipalService,
            IdentityUserRepository identityUserRepository,
            IdentityUserInputNormalizer identityUserInputNormalizer,
            IdentityAuditLogService identityAuditLogService,
            IdentityUserResponseAssembler identityUserResponseAssembler
    ) {
        this.identityPrincipalService = identityPrincipalService;
        this.identityUserRepository = identityUserRepository;
        this.identityUserInputNormalizer = identityUserInputNormalizer;
        this.identityAuditLogService = identityAuditLogService;
        this.identityUserResponseAssembler = identityUserResponseAssembler;
    }

    @Transactional
    public IdentityUserResponse updateProfile(String authorizationHeader, UpdateIdentityProfileRequest request) {
        IdentityUser user = null;
        try {
            user = identityPrincipalService.requireActiveUser(authorizationHeader);
            String phoneNumber = identityUserInputNormalizer.normalizeOptionalPhoneNumber(request.phoneNumber());
            String nickname = identityUserInputNormalizer.normalizeNickname(request.nickname());
            String avatarUrl = identityUserInputNormalizer.normalizeAvatarUrl(request.avatarUrl());

            if (phoneNumber == null && user.getEmail() == null) {
                throw new IllegalArgumentException("手机号不能为空。");
            }

            if (phoneNumber != null && identityUserRepository.existsByPhoneNumberAndIdNot(phoneNumber, user.getId())) {
                throw new IllegalArgumentException("手机号已被其他账户使用。");
            }

            user.setPhoneNumber(phoneNumber);
            user.setNickname(nickname);
            user.setAvatarUrl(avatarUrl);

            IdentityUser savedUser = identityUserRepository.save(user);
            identityAuditLogService.record(
                    IdentityAuditEventType.PROFILE_UPDATE,
                    IdentityAuditOutcome.SUCCESS,
                    savedUser.getId(),
                    savedUser.getId(),
                    userIdentifier(savedUser),
                    null
            );
            return identityUserResponseAssembler.toResponse(savedUser);
        } catch (RuntimeException ex) {
            identityAuditLogService.record(
                    IdentityAuditEventType.PROFILE_UPDATE,
                    IdentityAuditOutcome.FAILURE,
                    user == null ? null : user.getId(),
                    user == null ? null : user.getId(),
                    userIdentifier(user),
                    ex.getMessage()
            );
            throw ex;
        }
    }

    private String userIdentifier(IdentityUser user) {
        if (user == null) {
            return null;
        }

        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }

        return user.getPhoneNumber();
    }
}
