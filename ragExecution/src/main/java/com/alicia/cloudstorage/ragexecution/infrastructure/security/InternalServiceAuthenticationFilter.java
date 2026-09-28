package com.alicia.cloudstorage.ragexecution.infrastructure.security;

import com.alicia.cloudstorage.ragexecution.config.RagExecutionFeatureProperties;
import com.alicia.cloudstorage.ragexecution.config.RagExecutionLimitsProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class InternalServiceAuthenticationFilter extends OncePerRequestFilter {

    private static final String REGISTRATION_PATH = "/internal/executions";

    private final RagExecutionFeatureProperties features;
    private final RagExecutionLimitsProperties limits;
    private final ServiceRequestAuthenticator authenticator;

    public InternalServiceAuthenticationFilter(
            RagExecutionFeatureProperties features,
            RagExecutionLimitsProperties limits,
            ServiceRequestAuthenticator authenticator
    ) {
        this.features = features;
        this.limits = limits;
        this.authenticator = authenticator;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !REGISTRATION_PATH.equals(request.getRequestURI())
                || !features.enabled()
                || !features.registrationEnabled();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        byte[] body = request.getInputStream().readNBytes(limits.maxPlanBytes() + 1);
        if (body.length > limits.maxPlanBytes()) {
            writeError(response, 413, "plan_payload_too_large");
            return;
        }
        try {
            authenticator.authenticate(
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getHeader(ServiceRequestAuthenticator.CALLER_HEADER),
                    request.getHeader(ServiceRequestAuthenticator.TIMESTAMP_HEADER),
                    request.getHeader(ServiceRequestAuthenticator.NONCE_HEADER),
                    request.getHeader(ServiceRequestAuthenticator.BODY_SHA256_HEADER),
                    request.getHeader(ServiceRequestAuthenticator.SIGNATURE_HEADER),
                    body
            );
            filterChain.doFilter(new CachedBodyHttpServletRequest(request, body), response);
        } catch (ServiceRequestAuthenticationException exception) {
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
