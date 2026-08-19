package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.service.IdentityAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Service
public class HttpIdentityUserGateway implements IdentityUserGateway {

    private final RestClient restClient;
    private final JsonMapper objectMapper;

    public HttpIdentityUserGateway(
            RestClient.Builder restClientBuilder,
            JsonMapper objectMapper,
            @Value("${alicia.identity-api.base-url}") String identityApiBaseUrl
    ) {
        this.restClient = restClientBuilder.baseUrl(identityApiBaseUrl).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public IdentityAccount getUser(Long userId) {
        IdentityUserPayload response = IdentityGatewaySupport.exchange(() -> restClient.get()
                .uri("/api/identity/internal/users/{userId}", userId)
                .retrieve()
                .body(IdentityUserPayload.class), objectMapper);

        if (response == null) {
            throw new IllegalStateException("身份服务用户查询响应为空。");
        }

        return response.toAccount();
    }
}
