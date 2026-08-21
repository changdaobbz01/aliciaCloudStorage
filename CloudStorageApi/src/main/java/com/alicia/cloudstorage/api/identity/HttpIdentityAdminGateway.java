package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.dto.AdminCreateUserRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;

@Service
public class HttpIdentityAdminGateway implements IdentityAdminGateway {

    private final RestClient restClient;
    private final JsonMapper objectMapper;

    public HttpIdentityAdminGateway(
            RestClient.Builder restClientBuilder,
            JsonMapper objectMapper,
            @Value("${alicia.identity-api.base-url}") String identityApiBaseUrl
    ) {
        this.restClient = restClientBuilder.baseUrl(identityApiBaseUrl).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<IdentityUserSnapshot> listUsers(String authorization) {
        IdentityUserResponsePayload[] response = IdentityGatewaySupport.exchange(() -> restClient.get()
                .uri("/api/identity/admin/users")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .retrieve()
                .body(IdentityUserResponsePayload[].class), objectMapper);

        return Arrays.stream(response == null ? new IdentityUserResponsePayload[0] : response)
                .map(IdentityUserResponsePayload::toSnapshot)
                .toList();
    }

    @Override
    public IdentityUserSnapshot createUser(String authorization, AdminCreateUserRequest request) {
        IdentityCreateUserRequest payload = new IdentityCreateUserRequest(
                request.phoneNumber(),
                null,
                request.nickname(),
                request.avatarUrl(),
                request.password(),
                request.role()
        );

        IdentityUserResponsePayload response = IdentityGatewaySupport.exchange(() -> restClient.post()
                .uri("/api/identity/admin/users")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .body(payload)
                .retrieve()
                .body(IdentityUserResponsePayload.class), objectMapper);

        if (response == null) {
            throw new IllegalStateException("身份服务返回为空。");
        }

        return response.toSnapshot();
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

}
