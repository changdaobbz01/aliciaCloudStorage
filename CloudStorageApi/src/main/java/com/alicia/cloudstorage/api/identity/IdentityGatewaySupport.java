package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.principal.PrincipalAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

final class IdentityGatewaySupport {

    private IdentityGatewaySupport() {
    }

    static <T> T exchange(Supplier<T> request, ObjectMapper objectMapper) {
        try {
            return request.get();
        } catch (RestClientResponseException ex) {
            throw translateResponseException(ex, objectMapper);
        } catch (RestClientException ex) {
            throw new IdentityServiceUnavailableException("身份服务暂不可用。", ex);
        }
    }

    static <T, R> R mapRequiredBody(
            T response,
            String emptyMessage,
            String invalidMessage,
            Function<T, R> mapper
    ) {
        if (response == null) {
            throw new IdentityServiceUnavailableException(emptyMessage);
        }

        try {
            return mapper.apply(response);
        } catch (RuntimeException ex) {
            throw new IdentityServiceUnavailableException(invalidMessage, ex);
        }
    }

    static <T, R> List<R> mapRequiredArrayBody(
            T[] response,
            String emptyMessage,
            String invalidMessage,
            Function<T, R> mapper
    ) {
        if (response == null) {
            throw new IdentityServiceUnavailableException(emptyMessage);
        }

        try {
            return Arrays.stream(response)
                    .map(item -> {
                        if (item == null) {
                            throw new IllegalStateException("Identity response item is null.");
                        }
                        return mapper.apply(item);
                    })
                    .toList();
        } catch (RuntimeException ex) {
            throw new IdentityServiceUnavailableException(invalidMessage, ex);
        }
    }

    private static RuntimeException translateResponseException(
            RestClientResponseException ex,
            ObjectMapper objectMapper
    ) {
        String message = extractErrorMessage(ex, objectMapper);
        int status = ex.getStatusCode().value();

        if (status == 401 || status == 403) {
            return new PrincipalAccessException(message);
        }

        if (status >= 400 && status < 500) {
            return new IllegalArgumentException(message);
        }

        return new IdentityServiceUnavailableException(message, ex);
    }

    private static String extractErrorMessage(RestClientResponseException ex, ObjectMapper objectMapper) {
        String body = ex.getResponseBodyAsString(StandardCharsets.UTF_8);
        if (body == null || body.isBlank()) {
            return "身份服务请求失败。";
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            String error = root.path("error").asText();
            return error == null || error.isBlank() ? "身份服务请求失败。" : error;
        } catch (Exception ignored) {
            return "身份服务请求失败。";
        }
    }
}
