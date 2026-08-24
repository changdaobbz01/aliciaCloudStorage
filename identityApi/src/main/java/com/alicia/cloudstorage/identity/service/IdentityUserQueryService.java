package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.IdentityUserResponse;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class IdentityUserQueryService {

    private final IdentityUserRepository identityUserRepository;
    private final IdentityUserResponseAssembler identityUserResponseAssembler;

    public IdentityUserQueryService(
            IdentityUserRepository identityUserRepository,
            IdentityUserResponseAssembler identityUserResponseAssembler
    ) {
        this.identityUserRepository = identityUserRepository;
        this.identityUserResponseAssembler = identityUserResponseAssembler;
    }

    public Optional<IdentityUserResponse> findUser(Long userId) {
        return identityUserRepository.findById(userId)
                .map(identityUserResponseAssembler::toResponse);
    }
}
