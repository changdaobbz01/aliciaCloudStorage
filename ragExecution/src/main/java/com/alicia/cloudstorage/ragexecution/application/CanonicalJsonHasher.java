package com.alicia.cloudstorage.ragexecution.application;

import com.alicia.cloudstorage.ragexecution.api.internal.RegisterExecutionRequest;
import com.alicia.cloudstorage.ragexecution.api.internal.RegisterExecutionStepRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.TreeMap;

@Component
public class CanonicalJsonHasher {

    private final ObjectMapper objectMapper;

    public CanonicalJsonHasher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String planHash(RegisterExecutionRequest request) {
        ObjectNode material = objectMapper.createObjectNode();
        material.put("planSchemaVersion", request.planSchemaVersion());
        material.put("planId", request.planId());
        material.put("intentId", request.intentId());
        material.put("risk", request.risk());
        material.put("summary", request.summary());
        ArrayNode steps = material.putArray("steps");
        for (RegisterExecutionStepRequest step : request.steps()) {
            ObjectNode item = steps.addObject();
            item.put("stepKey", step.stepKey());
            item.put("actionType", step.actionType());
            item.put("payloadSchemaVersion", step.payloadSchemaVersion());
            item.set("payload", objectMapper.valueToTree(step.payload()));
            item.set("dependsOn", objectMapper.valueToTree(step.dependsOn()));
            item.put("outputKey", step.outputKey());
            item.set("requiredClientFields", objectMapper.valueToTree(step.requiredClientFields()));
        }
        return sha256(canonicalBytes(material));
    }

    public String payloadHash(JsonNode payload) {
        return sha256(canonicalBytes(payload));
    }

    public byte[] canonicalBytes(JsonNode value) {
        try {
            return objectMapper.writeValueAsBytes(sort(value));
        } catch (Exception exception) {
            throw new IllegalArgumentException("JSON payload cannot be canonicalized.", exception);
        }
    }

    private JsonNode sort(JsonNode value) {
        if (value == null || value.isNull() || value.isValueNode()) {
            return value;
        }
        if (value.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            value.forEach(item -> result.add(sort(item)));
            return result;
        }
        ObjectNode result = objectMapper.createObjectNode();
        TreeMap<String, JsonNode> fields = new TreeMap<>();
        value.properties().forEach(entry -> fields.put(entry.getKey(), entry.getValue()));
        fields.forEach((key, child) -> result.set(key, sort(child)));
        return result;
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
