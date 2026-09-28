package com.alicia.cloudstorage.ragexecution.infrastructure.cloud;

import com.alicia.cloudstorage.ragexecution.application.CanonicalJsonHasher;
import com.alicia.cloudstorage.ragexecution.port.CloudActionGateway.CloudActionCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class CloudActionRequestHasher {

    private final ObjectMapper objectMapper;
    private final CanonicalJsonHasher canonicalJsonHasher;

    public CloudActionRequestHasher(ObjectMapper objectMapper, CanonicalJsonHasher canonicalJsonHasher) {
        this.objectMapper = objectMapper;
        this.canonicalJsonHasher = canonicalJsonHasher;
    }

    public String hash(CloudActionCommand command) {
        ObjectNode material = objectMapper.createObjectNode();
        material.put("executionId", command.executionId().toString());
        material.put("stepId", command.stepId().toString());
        material.put("actorUserId", command.actorUserId());
        material.put("actionType", command.actionType().name());
        material.put("payloadSchemaVersion", command.payloadSchemaVersion());
        material.set("payload", command.payload());
        return canonicalJsonHasher.payloadHash(material);
    }
}
