package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.dto.UpdateProfileRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Service
public class HttpIdentityAuthGateway implements IdentityAuthGateway {

    private final RestClient restClient;
    private final JsonMapper objectMapper;
    private final IdentityAccessTokenPreflightVerifier preflightVerifier;
    private final IdentityGatewayTelemetry telemetry;
    private final IdentityCurrentUserCache currentUserCache;

    public HttpIdentityAuthGateway(
            @Qualifier("identityRestClient") RestClient restClient,
            JsonMapper objectMapper,
            IdentityAccessTokenPreflightVerifier preflightVerifier,
            IdentityGatewayTelemetry telemetry,
            IdentityCurrentUserCache currentUserCache
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.preflightVerifier = preflightVerifier;
        this.telemetry = telemetry;
        this.currentUserCache = currentUserCache;
    }

    @Override
    public IdentityUserSnapshot me(String authorization) {
        return currentUserCache.get(authorization)
                .map(snapshot -> telemetry.observe("auth.me.cacheHit", () -> {
                    try {
                        preflightVerifier.verify(authorization);
                    } catch (RuntimeException ex) {
                        currentUserCache.invalidate(authorization);
                        throw ex;
                    }
                    return snapshot;
                }))
                .orElseGet(() -> fetchCurrentUser(authorization));
    }

    private IdentityUserSnapshot fetchCurrentUser(String authorization) {
        return telemetry.observe("auth.me", () -> {
            try {
                preflightVerifier.verify(authorization);

                IdentityUserResponsePayload response = IdentityGatewaySupport.exchange(() -> restClient.get()
                        .uri("/api/identity/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .retrieve()
                        .body(IdentityUserResponsePayload.class), objectMapper);

                IdentityUserSnapshot snapshot = IdentityGatewaySupport.mapRequiredBody(
                        response,
                        "身份服务当前用户响应为空。",
                        "身份服务当前用户响应格式异常。",
                        IdentityUserResponsePayload::toSnapshot
                );
                currentUserCache.put(authorization, snapshot);
                return snapshot;
            } catch (RuntimeException ex) {
                currentUserCache.invalidate(authorization);
                throw ex;
            }
        });
    }

    @Override
    public IdentityUserSnapshot updateProfile(String authorization, UpdateProfileRequest request) {
        try {
            return telemetry.observe("auth.updateProfile", () -> {
                IdentityUserResponsePayload response = IdentityGatewaySupport.exchange(() -> restClient.put()
                        .uri("/api/identity/auth/profile")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .body(request)
                        .retrieve()
                        .body(IdentityUserResponsePayload.class), objectMapper);

                IdentityUserSnapshot snapshot = IdentityGatewaySupport.mapRequiredBody(
                        response,
                        "身份服务资料更新响应为空。",
                        "身份服务资料更新响应格式异常。",
                        IdentityUserResponsePayload::toSnapshot
                );
                currentUserCache.put(authorization, snapshot);
                return snapshot;
            });
        } catch (RuntimeException ex) {
            currentUserCache.invalidate(authorization);
            throw ex;
        }
    }
}
