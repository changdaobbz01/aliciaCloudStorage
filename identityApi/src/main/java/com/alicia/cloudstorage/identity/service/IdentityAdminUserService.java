package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.AdminCreateIdentityUserRequest;
import com.alicia.cloudstorage.identity.dto.IdentityUserResponse;
import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class IdentityAdminUserService {

    private final IdentityPrincipalService identityPrincipalService;
    private final IdentityUserRepository identityUserRepository;
    private final IdentityUserCreationService identityUserCreationService;
    private final IdentityAuditLogService identityAuditLogService;
    private final IdentityUserResponseAssembler identityUserResponseAssembler;

    public IdentityAdminUserService(
            IdentityPrincipalService identityPrincipalService,
            IdentityUserRepository identityUserRepository,
            IdentityUserCreationService identityUserCreationService,
            IdentityAuditLogService identityAuditLogService,
            IdentityUserResponseAssembler identityUserResponseAssembler
    ) {
        this.identityPrincipalService = identityPrincipalService;
        this.identityUserRepository = identityUserRepository;
        this.identityUserCreationService = identityUserCreationService;
        this.identityAuditLogService = identityAuditLogService;
        this.identityUserResponseAssembler = identityUserResponseAssembler;
    }

    @Transactional(readOnly = true)
    public List<IdentityUserResponse> listUsers(String authorizationHeader) {
        identityPrincipalService.requireAdminUser(authorizationHeader);
        return identityUserRepository.findAllByOrderByIdAsc().stream()
                .map(identityUserResponseAssembler::toResponse)
                .toList();
    }

    public IdentityUserResponse createUser(
            String authorizationHeader,
            AdminCreateIdentityUserRequest request
    ) {
        IdentityUser adminUser = null;
        try {
            adminUser = identityPrincipalService.requireAdminUser(authorizationHeader);
            IdentityUser createdUser = identityUserCreationService.createAdminManagedUser(request);
            identityAuditLogService.record(
                    IdentityAuditEventType.ADMIN_USER_CREATE,
                    IdentityAuditOutcome.SUCCESS,
                    adminUser.getId(),
                    createdUser.getId(),
                    requestIdentifier(request),
                    null
            );
            return identityUserResponseAssembler.toResponse(createdUser);
        } catch (RuntimeException ex) {
            identityAuditLogService.record(
                    IdentityAuditEventType.ADMIN_USER_CREATE,
                    IdentityAuditOutcome.FAILURE,
                    adminUser == null ? null : adminUser.getId(),
                    null,
                    requestIdentifier(request),
                    ex.getMessage()
            );
            throw ex;
        }
    }

    private String requestIdentifier(AdminCreateIdentityUserRequest request) {
        if (request == null) {
            return null;
        }

        if (request.email() != null && !request.email().isBlank()) {
            return request.email();
        }

        return request.phoneNumber();
    }

}
