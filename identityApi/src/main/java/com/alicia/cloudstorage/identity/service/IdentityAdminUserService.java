package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.AdminCreateIdentityUserRequest;
import com.alicia.cloudstorage.identity.dto.IdentityUserResponse;
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

    public IdentityAdminUserService(
            IdentityPrincipalService identityPrincipalService,
            IdentityUserRepository identityUserRepository,
            IdentityUserCreationService identityUserCreationService
    ) {
        this.identityPrincipalService = identityPrincipalService;
        this.identityUserRepository = identityUserRepository;
        this.identityUserCreationService = identityUserCreationService;
    }

    @Transactional(readOnly = true)
    public List<IdentityUserResponse> listUsers(String authorizationHeader) {
        identityPrincipalService.requireAdminUser(authorizationHeader);
        return identityUserRepository.findAllByOrderByIdAsc().stream()
                .map(IdentityUserResponse::from)
                .toList();
    }

    public IdentityUserResponse createUser(
            String authorizationHeader,
            AdminCreateIdentityUserRequest request
    ) {
        identityPrincipalService.requireAdminUser(authorizationHeader);
        return IdentityUserResponse.from(identityUserCreationService.createAdminManagedUser(request));
    }

}
