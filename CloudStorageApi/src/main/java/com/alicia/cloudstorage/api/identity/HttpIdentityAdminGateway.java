package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.auth.AuthException;
import com.alicia.cloudstorage.api.dto.AdminCreateUserRequest;
import com.alicia.cloudstorage.api.dto.AdminResetUserPasswordRequest;
import com.alicia.cloudstorage.api.entity.UserRole;
import com.alicia.cloudstorage.api.entity.UserStatus;
import com.alicia.cloudstorage.api.service.IdentityAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

@Service
public class HttpIdentityAdminGateway implements IdentityAdminGateway {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpIdentityAdminGateway(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${alicia.identity-api.base-url}") String identityApiBaseUrl
    ) {
        this.restClient = restClientBuilder.baseUrl(identityApiBaseUrl).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<IdentityAccount> listUsers(String authorization) {
        IdentityUserResponse[] response = exchange(() -> restClient.get()
                .uri("/api/identity/admin/users")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .retrieve()
                .body(IdentityUserResponse[].class));

        return Arrays.stream(response == null ? new IdentityUserResponse[0] : response)
                .map(IdentityUserResponse::toAccount)
                .toList();
    }

    @Override
    public IdentityAccount createUser(String authorization, AdminCreateUserRequest request) {
        IdentityCreateUserRequest payload = new IdentityCreateUserRequest(
                request.phoneNumber(),
                null,
                request.nickname(),
                request.avatarUrl(),
                request.password(),
                request.role()
        );

        IdentityUserResponse response = exchange(() -> restClient.post()
                .uri("/api/identity/admin/users")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .body(payload)
                .retrieve()
                .body(IdentityUserResponse.class));

        if (response == null) {
            throw new IllegalStateException("身份服务返回为空。");
        }

        return response.toAccount();
    }

    @Override
    public void resetUserPassword(String authorization, Long targetUserId, AdminResetUserPasswordRequest request) {
        exchange(() -> restClient.put()
                .uri("/api/identity/admin/users/{userId}/password", targetUserId)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .body(request)
                .retrieve()
                .toBodilessEntity());
    }

    private <T> T exchange(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException ex) {
            throw translateResponseException(ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("身份服务暂不可用。", ex);
        }
    }

    private RuntimeException translateResponseException(RestClientResponseException ex) {
        String message = extractErrorMessage(ex);
        int status = ex.getStatusCode().value();

        if (status == 401 || status == 403) {
            return new AuthException(message);
        }

        if (status >= 400 && status < 500) {
            return new IllegalArgumentException(message);
        }

        return new IllegalStateException(message);
    }

    private String extractErrorMessage(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString(StandardCharsets.UTF_8);
        if (body == null || body.isBlank()) {
            return "身份服务请求失败。";
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            String error = root.path("error").asText();
            return error == null || error.isBlank() ? "身份服务请求失败。" : error;
        } catch (Exception ignored) {
            return "身份服务请求失败。";
        }
    }

    private record IdentityCreateUserRequest(
            String phoneNumber,
            String email,
            String nickname,
            String avatarUrl,
            String password,
            String role
    ) {
    }

    private record IdentityUserResponse(
            Long id,
            String phoneNumber,
            String email,
            String emailVerifiedAt,
            String nickname,
            String avatarUrl,
            Long tokenVersion,
            String role,
            String status,
            String createdAt
    ) {

        private IdentityAccount toAccount() {
            return new IdentityAccount(
                    id,
                    phoneNumber,
                    email,
                    nickname,
                    avatarUrl,
                    UserRole.valueOf(role),
                    UserStatus.valueOf(status),
                    LocalDateTime.parse(createdAt)
            );
        }
    }
}
