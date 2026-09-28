package com.alicia.cloudstorage.ragexecution.infrastructure.identity;

import com.alicia.cloudstorage.ragexecution.config.RagExecutionIdentityProperties;
import com.alicia.cloudstorage.ragexecution.port.IdentityAccessVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class HttpIdentityAccessVerifier implements IdentityAccessVerifier {

    private static final Logger log = LoggerFactory.getLogger(HttpIdentityAccessVerifier.class);
    private static final Set<String> ALLOWED_ROLES = Set.of("RAG_USER", "RAG_ADMIN");

    private final RestClient restClient;

    public HttpIdentityAccessVerifier(RagExecutionIdentityProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public IdentityPrincipal requireRagAccess(String authorizationHeader) {
        String authorization = authorizationHeader == null ? "" : authorizationHeader.trim();
        if (authorization.isBlank()) {
            throw new IdentityAccessVerificationException(401, "authorization_required");
        }
        try {
            IdentityUserPayload user = restClient.get()
                    .uri("/api/identity/auth/me")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(IdentityUserPayload.class);
            if (user == null || user.id() == null || user.id() <= 0) {
                throw new IdentityAccessVerificationException(401, "identity_user_unavailable");
            }
            String ragRole = normalizeRole(user.appRoles().get("rag"));
            if (!ALLOWED_ROLES.contains(ragRole)) {
                throw new IdentityAccessVerificationException(403, "rag_access_forbidden");
            }
            return new IdentityPrincipal(user.id(), ragRole);
        } catch (IdentityAccessVerificationException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new IdentityAccessVerificationException(status, "identity_access_rejected");
            }
            log.warn("Identity plan-registration check returned HTTP status {}", status);
            throw new IdentityAccessVerificationException(503, "identity_unavailable");
        } catch (RestClientException exception) {
            log.warn("Identity plan-registration check failed");
            throw new IdentityAccessVerificationException(503, "identity_unavailable");
        }
    }

    private static String normalizeRole(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record IdentityUserPayload(Long id, Map<String, String> appRoles) {
        private IdentityUserPayload {
            appRoles = appRoles == null ? Map.of() : Map.copyOf(appRoles);
        }
    }
}
