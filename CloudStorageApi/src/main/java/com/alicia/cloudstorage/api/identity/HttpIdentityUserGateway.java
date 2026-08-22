package com.alicia.cloudstorage.api.identity;

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
    public IdentityUserSnapshot getUser(Long userId) {
        IdentityUserResponsePayload response = IdentityGatewaySupport.exchange(() -> restClient.get()
                .uri("/api/identity/internal/users/{userId}", userId)
                .retrieve()
                .body(IdentityUserResponsePayload.class), objectMapper);

        return IdentityGatewaySupport.mapRequiredBody(
                response,
                "身份服务用户查询响应为空。",
                "身份服务用户查询响应格式异常。",
                IdentityUserResponsePayload::toSnapshot
        );
    }
}
