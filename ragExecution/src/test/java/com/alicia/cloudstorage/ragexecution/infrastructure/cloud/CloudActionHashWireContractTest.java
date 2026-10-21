package com.alicia.cloudstorage.ragexecution.infrastructure.cloud;

import com.alicia.cloudstorage.ragexecution.application.CanonicalJsonHasher;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionActionType;
import com.alicia.cloudstorage.ragexecution.port.CloudActionGateway.CloudActionCommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CloudActionHashWireContractTest {

    private static final String FIXTURE = "test/contracts/rag-execution/cloud-action-hash-v1.json";

    @Test
    void producerHashMatchesTheSharedCloudActionFixture() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JsonNode fixture = objectMapper.readTree(Files.readAllBytes(repositoryRoot().resolve(FIXTURE)));
        CloudActionCommand command = new CloudActionCommand(
                UUID.fromString(fixture.path("executionId").asText()),
                UUID.fromString(fixture.path("stepId").asText()),
                fixture.path("actorUserId").asLong(),
                ExecutionActionType.valueOf(fixture.path("actionType").asText()),
                fixture.path("payloadSchemaVersion").asText(),
                fixture.path("payload")
        );

        String actual = new CloudActionRequestHasher(
                objectMapper,
                new CanonicalJsonHasher(objectMapper)
        ).hash(command);

        assertThat(fixture.path("contractVersion").asText()).isEqualTo("cloud_action_hash_v1");
        assertThat(actual).isEqualTo(fixture.path("expectedRequestHash").asText());
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        return current.getFileName() != null && current.getFileName().toString().equals("ragExecution")
                ? current.getParent()
                : current;
    }
}
