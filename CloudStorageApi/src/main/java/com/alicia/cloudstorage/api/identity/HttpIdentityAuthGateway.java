package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.dto.ChangePasswordRequest;
import com.alicia.cloudstorage.api.dto.LoginRequest;
import com.alicia.cloudstorage.api.dto.RequestEmailRegistrationCodeRequest;
import com.alicia.cloudstorage.api.dto.UpdateProfileRequest;
import com.alicia.cloudstorage.api.dto.VerifyEmailRegistrationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Service
public class HttpIdentityAuthGateway implements IdentityAuthGateway {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final RestClient restClient;
    private final JsonMapper objectMapper;

    public HttpIdentityAuthGateway(
            RestClient.Builder restClientBuilder,
            JsonMapper objectMapper,
            @Value("${alicia.identity-api.base-url}") String identityApiBaseUrl
    ) {
        this.restClient = restClientBuilder.baseUrl(identityApiBaseUrl).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public IdentityLoginSession login(LoginRequest request) {
        IdentityLoginResponsePayload response = IdentityGatewaySupport.exchange(() -> restClient.post()
                .uri("/api/identity/auth/login")
                .body(request)
                .retrieve()
                .body(IdentityLoginResponsePayload.class), objectMapper);

        if (response == null) {
            throw new IllegalStateException("身份服务登录响应为空。");
        }

        return response.toSession();
    }

    @Override
    public IdentityAccount me(String authorization) {
        IdentityUserResponsePayload response = IdentityGatewaySupport.exchange(() -> restClient.get()
                .uri("/api/identity/auth/me")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .retrieve()
                .body(IdentityUserResponsePayload.class), objectMapper);

        if (response == null) {
            throw new IllegalStateException("身份服务当前用户响应为空。");
        }

        return response.toAccount();
    }

    @Override
    public IdentityAccount updateProfile(String authorization, UpdateProfileRequest request) {
        IdentityUserResponsePayload response = IdentityGatewaySupport.exchange(() -> restClient.put()
                .uri("/api/identity/auth/profile")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .body(request)
                .retrieve()
                .body(IdentityUserResponsePayload.class), objectMapper);

        if (response == null) {
            throw new IllegalStateException("身份服务资料更新响应为空。");
        }

        return response.toAccount();
    }

    @Override
    public void changePassword(String authorization, ChangePasswordRequest request) {
        IdentityGatewaySupport.exchange(() -> restClient.put()
                .uri("/api/identity/auth/password")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .body(request)
                .retrieve()
                .toBodilessEntity(), objectMapper);
    }

    @Override
    public void requestEmailRegistrationCode(
            RequestEmailRegistrationCodeRequest request,
            String clientIp,
            String userAgent
    ) {
        IdentityGatewaySupport.exchange(() -> restClient.post()
                .uri("/api/identity/auth/register/email-code")
                .headers(headers -> applyClientMetadata(headers, clientIp, userAgent))
                .body(request)
                .retrieve()
                .toBodilessEntity(), objectMapper);
    }

    @Override
    public IdentityLoginSession verifyEmailRegistration(VerifyEmailRegistrationRequest request) {
        IdentityLoginResponsePayload response = IdentityGatewaySupport.exchange(() -> restClient.post()
                .uri("/api/identity/auth/register/verify")
                .body(request)
                .retrieve()
                .body(IdentityLoginResponsePayload.class), objectMapper);

        if (response == null) {
            throw new IllegalStateException("身份服务注册响应为空。");
        }

        return response.toSession();
    }

    private void applyClientMetadata(HttpHeaders headers, String clientIp, String userAgent) {
        if (clientIp != null && !clientIp.isBlank()) {
            headers.set(FORWARDED_FOR_HEADER, clientIp.trim());
        }

        if (userAgent != null && !userAgent.isBlank()) {
            headers.set(HttpHeaders.USER_AGENT, userAgent.trim());
        }
    }
}
