package com.alicia.cloudstorage.rag.assistant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperationArgumentResolverTest {

    private final OperationArgumentResolver resolver = new OperationArgumentResolver();

    @Test
    void treatsIndefiniteFileAsMissingMutationTarget() {
        OperationArgumentResolver.Resolution resolution = resolver
                .resolve("删除一个文件", "DELETE")
                .orElseThrow();

        assertThat(resolution.source().nameKind())
                .isEqualTo(OperationArgumentResolver.NameKind.GENERIC);
        assertThat(resolution.source().name()).isBlank();
        assertThat(resolution.needsClarification()).isTrue();
    }

    @Test
    void keepsNoisyExtensionMoveAsAnExplicitCollection() {
        OperationArgumentResolver.Resolution resolution = resolver
                .resolve("所有 .png 的文件，都给我挪到素材库里", "MOVE")
                .orElseThrow();

        assertThat(resolution.source().quantifier())
                .as("parsed source: %s", resolution.source())
                .isEqualTo(OperationArgumentResolver.Quantifier.EXPLICIT_ALL);
        assertThat(resolution.destination().surface()).isEqualTo("素材库");
    }
}
