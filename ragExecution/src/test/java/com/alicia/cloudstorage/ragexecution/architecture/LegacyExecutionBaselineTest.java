package com.alicia.cloudstorage.ragexecution.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyExecutionBaselineTest {

    @Test
    void currentRagPlanVersionAndDisabledAndroidExecutorRemainFrozen() throws IOException {
        String actionPlan = read(Path.of(
                "..", "rag", "src", "main", "java", "com", "alicia", "cloudstorage", "rag",
                "assistant", "ActionPlan.java"
        ));
        String androidExecutor = read(Path.of(
                "..", "phoneAppAdd", "app", "src", "main", "java", "com", "alicia", "cloudstorage",
                "phone", "data", "RagActionExecutor.kt"
        ));

        assertThat(actionPlan).contains("CURRENT_VERSION = \"action_plan_v2\"");
        assertThat(androidExecutor).contains("private val executionEnabled: Boolean = false");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
