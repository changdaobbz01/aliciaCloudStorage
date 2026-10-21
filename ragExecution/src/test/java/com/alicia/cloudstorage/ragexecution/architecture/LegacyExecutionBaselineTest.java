package com.alicia.cloudstorage.ragexecution.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyExecutionBaselineTest {

    @Test
    void currentRagPlanVersionAndRemovedAndroidExecutorRemainFrozen() throws IOException {
        String actionPlan = read(Path.of(
                "..", "rag", "src", "main", "java", "com", "alicia", "cloudstorage", "rag",
                "assistant", "ActionPlan.java"
        ));
        Path androidExecutor = Path.of(
                "..", "phoneAppAdd", "app", "src", "main", "java", "com", "alicia", "cloudstorage",
                "phone", "data", "RagActionExecutor.kt"
        );
        String androidBuild = read(Path.of("..", "phoneAppAdd", "app", "build.gradle.kts"));
        String assistantViewModel = read(Path.of(
                "..", "phoneAppAdd", "app", "src", "main", "java", "com", "alicia", "cloudstorage",
                "phone", "ui", "RagAssistantViewModel.kt"
        ));

        assertThat(actionPlan).contains("CURRENT_VERSION = \"action_plan_v2\"");
        assertThat(androidExecutor).doesNotExist();
        assertThat(androidBuild).doesNotContain("RAG_ACTION_EXECUTION_ENABLED");
        assertThat(assistantViewModel)
                .doesNotContain("RagActionExecutor")
                .doesNotContain("executeLegacyDraft")
                .doesNotContain("LEGACY_LOCAL");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
