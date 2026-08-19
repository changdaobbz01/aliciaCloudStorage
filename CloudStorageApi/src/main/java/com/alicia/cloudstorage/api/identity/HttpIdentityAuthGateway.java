package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.dto.ChangePasswordRequest;
import com.alicia.cloudstorage.api.dto.LoginRequest;
import com.alicia.cloudstorage.api.service.IdentityLoginSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Service
public class HttpIdentityAuthGateway implements IdentityAuthGateway {

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
        IdentityLoginPayload response = IdentityGatewaySupport.exchange(() -> restClient.post()
                .uri("/api/identity/auth/login")
                .body(request)
                .retrieve()
                .body(IdentityLoginPayload.class), objectMapper);

        if (response == null) {
            throw new IllegalStateException("身份服务登录响应为空。");
        }

        return response.toSession();
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
}
