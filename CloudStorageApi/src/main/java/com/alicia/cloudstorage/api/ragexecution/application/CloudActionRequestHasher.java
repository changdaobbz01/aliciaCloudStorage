package com.alicia.cloudstorage.api.ragexecution.application;

import com.alicia.cloudstorage.api.ragexecution.api.CloudActionRequest;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.TreeMap;

@Component
public class CloudActionRequestHasher {

    private final JsonMapper jsonMapper;

    public CloudActionRequestHasher(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public String hash(CloudActionRequest request) {
        ObjectNode material = jsonMapper.createObjectNode();
        material.put("executionId", request.executionId());
        material.put("stepId", request.stepId());
        material.put("actorUserId", request.actorUserId());
        material.put("actionType", request.actionType());
        material.put("payloadSchemaVersion", request.payloadSchemaVersion());
        material.set("payload", request.payload());
        try {
            byte[] canonical = jsonMapper.writeValueAsBytes(sort(material));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (Exception exception) {
            throw new CloudActionException(422, "invalid_action_payload", exception);
        }
    }

    private JsonNode sort(JsonNode value) {
        if (value == null || value.isNull() || value.isValueNode()) {
            return value;
        }
        if (value.isArray()) {
            ArrayNode result = jsonMapper.createArrayNode();
            value.forEach(item -> result.add(sort(item)));
            return result;
        }
        ObjectNode result = jsonMapper.createObjectNode();
        TreeMap<String, JsonNode> fields = new TreeMap<>();
        value.properties().forEach(entry -> fields.put(entry.getKey(), entry.getValue()));
        fields.forEach((key, child) -> result.set(key, sort(child)));
        return result;
    }
}
