package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.dto.AdminCreateUserRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Service
public class HttpIdentityAdminGateway implements IdentityAdminGateway {

    private final RestClient restClient;
    private final JsonMapper objectMapper;
    private final IdentityGatewayTelemetry telemetry;

    public HttpIdentityAdminGateway(
            @Qualifier("identityRestClient") RestClient restClient,
            JsonMapper objectMapper,
            IdentityGatewayTelemetry telemetry
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.telemetry = telemetry;
    }

    @Override
    public List<IdentityUserSnapshot> listUsers(String authorization) {
        return telemetry.observe("admin.listUsers", () -> {
            IdentityUserResponsePayload[] response = IdentityGatewaySupport.exchange(() -> restClient.get()
                    .uri("/api/identity/admin/users")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(IdentityUserResponsePayload[].class), objectMapper);

            return IdentityGatewaySupport.mapRequiredArrayBody(
                    response,
                    "身份服务管理员用户列表响应为空。",
                    "身份服务管理员用户列表响应格式异常。",
                    IdentityUserResponsePayload::toSnapshot
            );
        });
    }

    @Override
    public IdentityUserSnapshot createUser(String authorization, AdminCreateUserRequest request) {
        return telemetry.observe("admin.createUser", () -> {
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

            return IdentityGatewaySupport.mapRequiredBody(
                    response,
                    "身份服务创建用户响应为空。",
                    "身份服务创建用户响应格式异常。",
                    IdentityUserResponsePayload::toSnapshot
            );
        });
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
