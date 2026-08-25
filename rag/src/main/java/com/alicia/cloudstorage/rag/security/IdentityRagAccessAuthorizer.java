package com.alicia.cloudstorage.rag.security;

import com.alicia.cloudstorage.rag.config.RagIdentityApiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class IdentityRagAccessAuthorizer implements RagAccessAuthorizer {

    private static final Logger log = LoggerFactory.getLogger(IdentityRagAccessAuthorizer.class);
    private static final Set<String> ALLOWED_RAG_ROLES = Set.of("RAG_USER", "RAG_ADMIN");

    private final RestClient restClient;

    @Autowired
    public IdentityRagAccessAuthorizer(RagIdentityApiProperties properties) {
        this(RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(identityRequestFactory(properties))
                .build());
    }

    IdentityRagAccessAuthorizer(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public RagAccessPrincipal requireRagAccess(String authorizationHeader) {
        String authorization = normalizeAuthorization(authorizationHeader);
        try {
            IdentityUserPayload user = restClient.get()
                    .uri("/api/identity/auth/me")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(IdentityUserPayload.class);

            if (user == null || user.id() == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identity user is unavailable.");
            }

            String ragRole = normalizeRole(user.appRoles().get("rag"));
            if (!ALLOWED_RAG_ROLES.contains(ragRole)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "RAG access is not enabled for this account.");
            }

            return new RagAccessPrincipal(user.id(), "rag", ragRole);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            int statusCode = ex.getStatusCode().value();
            if (statusCode == HttpStatus.UNAUTHORIZED.value()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identity token is invalid.");
            }
            if (statusCode == HttpStatus.FORBIDDEN.value()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Identity token is forbidden.");
            }

            log.warn("RAG Identity access check returned HTTP status {}", statusCode);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Identity access check is unavailable.");
        } catch (RestClientException | IllegalArgumentException ex) {
            log.warn("RAG Identity access check failed: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Identity access check is unavailable.");
        }
    }

    private static SimpleClientHttpRequestFactory identityRequestFactory(RagIdentityApiProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return requestFactory;
    }

    private static String normalizeAuthorization(String authorizationHeader) {
        String authorization = authorizationHeader == null ? "" : authorizationHeader.trim();
        if (authorization.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "RAG authorization is required.");
        }
        return authorization;
    }

    private static String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }

    private record IdentityUserPayload(
            Long id,
            String role,
            String status,
            Map<String, String> appRoles
    ) {

        private IdentityUserPayload {
            appRoles = appRoles == null ? Map.of() : Map.copyOf(appRoles);
        }
    }
}
