package com.alicia.cloudstorage.rag.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class HttpExecutionRegistrationClient implements ExecutionRegistrationClient {

    private static final String PATH = "/internal/executions";
    private static final String CALLER = "rag";

    private final RagExecutionRegistrationProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final RestClient restClient;

    public HttpExecutionRegistrationClient(
            RagExecutionRegistrationProperties properties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public ExecutionRegistrationResponse register(
            ExecutionRegistrationRequest request,
            String authorizationHeader
    ) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(request);
            String timestamp = Long.toString(clock.instant().getEpochSecond());
            String nonce = UUID.randomUUID().toString();
            String bodyHash = sha256(body);
            String canonical = String.join("\n", "POST", PATH, CALLER, timestamp, nonce, bodyHash);
            String signature = hmac(properties.serviceSecret(), canonical);
            return restClient.post()
                    .uri(PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader == null ? "" : authorizationHeader)
                    .header("X-Alicia-Caller", CALLER)
                    .header("X-Alicia-Timestamp", timestamp)
                    .header("X-Alicia-Nonce", nonce)
                    .header("X-Alicia-Content-SHA256", bodyHash)
                    .header("X-Alicia-Signature", signature)
                    .body(body)
                    .retrieve()
                    .body(ExecutionRegistrationResponse.class);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Execution registration request could not be serialized.", exception);
        }
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static String hmac(String secret, String canonical) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    }
}
