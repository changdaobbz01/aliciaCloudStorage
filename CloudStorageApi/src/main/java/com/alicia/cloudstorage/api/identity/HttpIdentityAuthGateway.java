package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.dto.ChangePasswordRequest;
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
    public void changePassword(String authorization, ChangePasswordRequest request) {
        IdentityGatewaySupport.exchange(() -> restClient.put()
                .uri("/api/identity/auth/password")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .body(request)
                .retrieve()
                .toBodilessEntity(), objectMapper);
    }
}
