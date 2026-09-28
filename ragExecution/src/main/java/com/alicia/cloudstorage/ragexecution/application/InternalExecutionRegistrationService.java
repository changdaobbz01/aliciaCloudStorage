package com.alicia.cloudstorage.ragexecution.application;

import com.alicia.cloudstorage.ragexecution.api.internal.RegisterExecutionRequest;
import com.alicia.cloudstorage.ragexecution.api.internal.RegisterExecutionResponse;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionJpaEntity;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionJpaRepository;
import com.alicia.cloudstorage.ragexecution.port.ExecutionClock;
import com.alicia.cloudstorage.ragexecution.port.IdentityAccessVerifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class InternalExecutionRegistrationService {

    private final IdentityAccessVerifier identityAccessVerifier;
    private final ExecutionRegistrationValidator validator;
    private final ExecutionJpaRepository executionRepository;
    private final ExecutionRegistrationCreator creator;
    private final ExecutionClock clock;

    public InternalExecutionRegistrationService(
            IdentityAccessVerifier identityAccessVerifier,
            ExecutionRegistrationValidator validator,
            ExecutionJpaRepository executionRepository,
            ExecutionRegistrationCreator creator,
            ExecutionClock clock
    ) {
        this.identityAccessVerifier = identityAccessVerifier;
        this.validator = validator;
        this.executionRepository = executionRepository;
        this.creator = creator;
        this.clock = clock;
    }

    public RegisterExecutionResponse register(RegisterExecutionRequest request, String authorizationHeader) {
        IdentityAccessVerifier.IdentityPrincipal principal =
                identityAccessVerifier.requireRagAccess(authorizationHeader);
        ExecutionRegistrationValidator.ValidatedRegistration validated = validator.validate(request);

        ExecutionJpaEntity existing = findExisting(principal.userId(), request);
        if (existing != null) {
            return response(existing);
        }
        try {
            return response(creator.create(principal.userId(), request, validated, clock.now()));
        } catch (DataIntegrityViolationException exception) {
            ExecutionJpaEntity concurrent = findExisting(principal.userId(), request);
            if (concurrent != null) {
                return response(concurrent);
            }
            throw new PlanRegistrationException(409, "plan_registration_conflict");
        }
    }

    private ExecutionJpaEntity findExisting(long ownerUserId, RegisterExecutionRequest request) {
        ExecutionJpaEntity existing = executionRepository.findByOwnerUserIdAndPlanId(
                ownerUserId,
                request.planId()
        ).orElse(null);
        if (existing != null && !existing.getPlanHash().equals(request.planHash())) {
            throw new PlanRegistrationException(409, "plan_hash_conflict");
        }
        return existing;
    }

    private RegisterExecutionResponse response(ExecutionJpaEntity entity) {
        return new RegisterExecutionResponse(
                entity.getId().toString(),
                entity.getStatus().name(),
                entity.getVersion(),
                entity.getExpiresAt()
        );
    }
}
