package com.alicia.cloudstorage.api.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionLoggingBoundaryTest {

    @Test
    void defaultConfigurationDoesNotPrintEverySqlStatement() {
        String properties = readRepositoryFile("CloudStorageApi/src/main/resources/application.properties");

        assertThat(properties)
                .contains("spring.jpa.show-sql=false")
                .contains("spring.jpa.properties.hibernate.format_sql=false")
                .doesNotContain("spring.jpa.show-sql=true")
                .doesNotContain("spring.jpa.properties.hibernate.format_sql=true");
    }

    private static String readRepositoryFile(String repositoryRelativePath) {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path repositoryRoot = current.getFileName() != null
                && current.getFileName().toString().equals("CloudStorageApi")
                ? current.getParent()
                : current;
        Path path = repositoryRoot.resolve(repositoryRelativePath);
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read " + path, exception);
        }
    }
}
