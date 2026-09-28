package com.alicia.cloudstorage.api.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RagExecutionInternalActionBoundaryTest {

    @Test
    void internalActionRouteIsDisabledByDefaultAndBlockedAtPublicNginx() {
        String applicationProperties = readRepositoryFile(
                "CloudStorageApi/src/main/resources/application.properties"
        );
        String compose = readRepositoryFile("compose.yaml");
        String nginx = readRepositoryFile("webApp/nginx/default.conf");

        assertThat(applicationProperties).contains(
                "${ALICIA_RAG_EXECUTION_CLOUD_ACTIONS_ENABLED:false}"
        );
        assertThat(compose).contains(
                "${ALICIA_RAG_EXECUTION_CLOUD_ACTIONS_ENABLED:-false}"
        );
        assertThat(nginx).contains(
                "location ^~ /internal/rag-execution/ {\n        return 404;\n    }"
        );
    }

    @Test
    void internalAdapterUsesExistingBusinessServicesInsteadOfRepositoryWrites() {
        String executor = readRepositoryFile(
                "CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/ragexecution/application/"
                        + "CloudActionTransactionalExecutor.java"
        );

        assertThat(executor)
                .contains("storageCommandService.renameNode(")
                .contains("storageCommandService.moveNodeToTrash(")
                .contains("storageCommandService.moveNode(")
                .contains("storageCommandService.createFolder(")
                .contains("shareLinkService.createShareLink(")
                .doesNotContain("storageNodeRepository.save(")
                .doesNotContain("storageNodeRepository.delete(");
    }

    private static String readRepositoryFile(String repositoryRelativePath) {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path repositoryRoot = current.getFileName() != null
                && current.getFileName().toString().equals("CloudStorageApi")
                ? current.getParent()
                : current;
        Path path = repositoryRoot.resolve(repositoryRelativePath);
        try {
            return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read " + path, exception);
        }
    }
}
