package com.alicia.cloudstorage.api.identity;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Service
public class HttpIdentityUserGateway implements IdentityUserGateway {

    private final RestClient restClient;
    private final JsonMapper objectMapper;

    public HttpIdentityUserGateway(
            @Qualifier("identityRestClient") RestClient restClient,
            JsonMapper objectMapper
    ) {
        this.restClient = restClient;
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
