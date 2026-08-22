package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.dto.UpdateProfileRequest;
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
    public IdentityUserSnapshot me(String authorization) {
        IdentityUserResponsePayload response = IdentityGatewaySupport.exchange(() -> restClient.get()
                .uri("/api/identity/auth/me")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .retrieve()
                .body(IdentityUserResponsePayload.class), objectMapper);

        return IdentityGatewaySupport.mapRequiredBody(
                response,
                "身份服务当前用户响应为空。",
                "身份服务当前用户响应格式异常。",
                IdentityUserResponsePayload::toSnapshot
        );
    }

    @Override
    public IdentityUserSnapshot updateProfile(String authorization, UpdateProfileRequest request) {
        IdentityUserResponsePayload response = IdentityGatewaySupport.exchange(() -> restClient.put()
                .uri("/api/identity/auth/profile")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .body(request)
                .retrieve()
                .body(IdentityUserResponsePayload.class), objectMapper);

        return IdentityGatewaySupport.mapRequiredBody(
                response,
                "身份服务资料更新响应为空。",
                "身份服务资料更新响应格式异常。",
                IdentityUserResponsePayload::toSnapshot
        );
    }
}
