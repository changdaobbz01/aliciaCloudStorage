package com.alicia.cloudstorage.ragexecution.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionIsolationBoundaryTest {

    private static final Path MAIN_SOURCE = Path.of("src", "main", "java");

    @Test
    void phaseFourUsesAnHttpPortWithoutDependingOnCloudBusinessCode() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
        assertThat(pom).doesNotContain("<artifactId>cloud-storage-api</artifactId>");

        List<String> violations;
        try (Stream<Path> files = Files.walk(MAIN_SOURCE)) {
            violations = files
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .flatMap(path -> restrictedFragments(path).stream())
                    .toList();
        }

        assertThat(violations)
                .as("Phase 4 may call the fixed internal action endpoint but must not depend on CloudStorageApi code or public write routes.")
                .isEmpty();
    }

    @Test
    void composeKeepsTheNewServiceOptInAndEveryExecutionSwitchOff() throws IOException {
        String compose = Files.readString(Path.of("..", "compose.yaml"), StandardCharsets.UTF_8);
        String applicationProperties = Files.readString(
                Path.of("src", "main", "resources", "application.properties"),
                StandardCharsets.UTF_8
        );

        assertThat(compose.lines()
                .filter(line -> line.trim().equals("profiles: [\"rag-execution-foundation\"]"))
                .count()).isEqualTo(2L);
        assertThat(compose).contains(
                "ALICIA_RAG_EXECUTION_ENABLED: ${ALICIA_RAG_EXECUTION_ENABLED:-false}",
                "ALICIA_RAG_EXECUTION_REGISTRATION_ENABLED: ${ALICIA_RAG_EXECUTION_REGISTRATION_ENABLED:-false}",
                "ALICIA_RAG_EXECUTION_PUBLIC_CONFIRM_ENABLED: ${ALICIA_RAG_EXECUTION_PUBLIC_CONFIRM_ENABLED:-false}",
                "ALICIA_RAG_EXECUTION_WORKER_ENABLED: ${ALICIA_RAG_EXECUTION_WORKER_ENABLED:-false}",
                "ALICIA_RAG_EXECUTION_CLOUD_DISPATCH_ENABLED: ${ALICIA_RAG_EXECUTION_CLOUD_DISPATCH_ENABLED:-false}"
        );
        assertThat(applicationProperties).contains(
                "alicia.rag-execution.features.enabled=${ALICIA_RAG_EXECUTION_ENABLED:false}",
                "alicia.rag-execution.features.registration-enabled=${ALICIA_RAG_EXECUTION_REGISTRATION_ENABLED:false}",
                "alicia.rag-execution.features.public-confirm-enabled=${ALICIA_RAG_EXECUTION_PUBLIC_CONFIRM_ENABLED:false}",
                "alicia.rag-execution.features.worker-enabled=${ALICIA_RAG_EXECUTION_WORKER_ENABLED:false}",
                "alicia.rag-execution.features.cloud-dispatch-enabled=${ALICIA_RAG_EXECUTION_CLOUD_DISPATCH_ENABLED:false}"
        );
    }

    private List<String> restrictedFragments(Path path) {
        String source;
        try {
            source = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read " + path, exception);
        }
        return Stream.of(
                        "WebClient",
                        "/api/storage",
                        "/api/share-links",
                        "com.alicia.cloudstorage.api.",
                        "StorageNodeRepository",
                        "ShareLinkRepository"
                )
                .filter(source::contains)
                .map(fragment -> path + " contains " + fragment)
                .toList();
    }
}
