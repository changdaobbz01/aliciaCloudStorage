package com.alicia.cloudstorage.api.ragexecution;

import com.alicia.cloudstorage.api.ragexecution.api.CloudActionRequest;
import com.alicia.cloudstorage.api.ragexecution.application.CloudActionRequestHasher;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CloudActionHashWireContractTest {

    private static final String FIXTURE = "test/contracts/rag-execution/cloud-action-hash-v1.json";

    @Test
    void consumerHashMatchesTheSharedCloudActionFixture() throws Exception {
        JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
        JsonNode fixture = jsonMapper.readTree(Files.readAllBytes(repositoryRoot().resolve(FIXTURE)));
        CloudActionRequest request = new CloudActionRequest(
                fixture.path("executionId").asText(),
                fixture.path("stepId").asText(),
                fixture.path("actorUserId").asLong(),
                fixture.path("actionType").asText(),
                fixture.path("payloadSchemaVersion").asText(),
                fixture.path("payload"),
                fixture.path("expectedRequestHash").asText(),
                Instant.EPOCH
        );

        String actual = new CloudActionRequestHasher(jsonMapper).hash(request);

        assertThat(fixture.path("contractVersion").asText()).isEqualTo("cloud_action_hash_v1");
        assertThat(actual).isEqualTo(request.requestHash());
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        return current.getFileName() != null && current.getFileName().toString().equals("CloudStorageApi")
                ? current.getParent()
                : current;
    }
}
