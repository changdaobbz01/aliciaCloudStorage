package com.alicia.cloudstorage.api.ragexecution.security;

import com.alicia.cloudstorage.api.ragexecution.config.RagExecutionCloudActionProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(
        prefix = "alicia.rag-execution.internal-actions",
        name = "enabled",
        havingValue = "true"
)
public class CloudActionAuthenticationFilter extends OncePerRequestFilter {

    public static final String ACTION_PATH = "/internal/rag-execution/actions";

    private final RagExecutionCloudActionProperties properties;
    private final CloudActionRequestAuthenticator authenticator;

    public CloudActionAuthenticationFilter(
            RagExecutionCloudActionProperties properties,
            CloudActionRequestAuthenticator authenticator
    ) {
        this.properties = properties;
        this.authenticator = authenticator;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !ACTION_PATH.equals(request.getRequestURI()) || !properties.enabled();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        byte[] body = request.getInputStream().readNBytes(properties.maxRequestBytes() + 1);
        if (body.length > properties.maxRequestBytes()) {
            writeError(response, 413, "action_payload_too_large");
            return;
        }
        try {
            authenticator.authenticate(
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getHeader(CloudActionRequestAuthenticator.CALLER_HEADER),
                    request.getHeader(CloudActionRequestAuthenticator.TIMESTAMP_HEADER),
                    request.getHeader(CloudActionRequestAuthenticator.NONCE_HEADER),
                    request.getHeader(CloudActionRequestAuthenticator.BODY_SHA256_HEADER),
                    request.getHeader(CloudActionRequestAuthenticator.SIGNATURE_HEADER),
                    body
            );
            filterChain.doFilter(new CachedBodyHttpServletRequest(request, body), response);
        } catch (CloudActionAuthenticationException exception) {
            writeError(response, exception.statusCode(), exception.errorCode());
        }
    }

    private void writeError(HttpServletResponse response, int status, String errorCode) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + errorCode + "\"}");
    }
}
