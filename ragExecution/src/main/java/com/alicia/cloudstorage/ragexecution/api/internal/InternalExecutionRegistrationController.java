package com.alicia.cloudstorage.ragexecution.api.internal;

import com.alicia.cloudstorage.ragexecution.application.InternalExecutionRegistrationService;
import com.alicia.cloudstorage.ragexecution.config.RagExecutionFeatureProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class InternalExecutionRegistrationController {

    private final RagExecutionFeatureProperties features;
    private final InternalExecutionRegistrationService registrationService;

    public InternalExecutionRegistrationController(
            RagExecutionFeatureProperties features,
            InternalExecutionRegistrationService registrationService
    ) {
        this.features = features;
        this.registrationService = registrationService;
    }

    @PostMapping("/internal/executions")
    public RegisterExecutionResponse register(
            @RequestBody RegisterExecutionRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        if (!features.enabled() || !features.registrationEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return registrationService.register(request, authorizationHeader);
    }
}
