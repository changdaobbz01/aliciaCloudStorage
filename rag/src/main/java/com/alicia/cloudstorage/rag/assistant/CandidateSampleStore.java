package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.UUID;

@Component
final class CandidateSampleStore {

    private static final int CAPACITY = 512;
    private final Path directory;
    private final ObjectMapper json;

    CandidateSampleStore(@Value("${alicia.rag.recognition.feedback.candidate-directory:}") String directory,
                         ObjectMapper json) {
        this.directory = directory.isBlank() ? null : Path.of(directory).toAbsolutePath().normalize();
        this.json = json;
    }

    boolean enabled() {
        return directory != null;
    }

    UUID save(RecognitionFeedbackService.Category category, String expression) {
        Path temporary = null;
        try {
            validateDirectory();
            try (var entries = Files.list(directory)) {
                if (entries.filter(path -> path.getFileName().toString().matches("candidate-[0-9a-f-]{36}\\.json"))
                        .limit(CAPACITY).count() >= CAPACITY) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "candidate_pool_full");
                }
            }
            UUID id = UUID.randomUUID();
            var sample = new Sample(id, "candidate", category, expression, "anonymized_expression_v1", Instant.now());
            byte[] bytes = json.writeValueAsBytes(sample);
            boolean posix = Files.getFileStore(directory).supportsFileAttributeView("posix");
            temporary = posix
                    ? Files.createTempFile(directory, ".candidate-", ".tmp",
                            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
                    : Files.createTempFile(directory, ".candidate-", ".tmp");
            Files.write(temporary, bytes);
            Files.move(temporary, directory.resolve("candidate-" + id + ".json"), StandardCopyOption.ATOMIC_MOVE);
            return id;
        } catch (IOException | SecurityException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "candidate_store_unavailable");
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException | SecurityException ignored) {
                    // The temporary file already has the same private permissions as a committed sample.
                }
            }
        }
    }

    private void validateDirectory() throws IOException {
        if (directory == null || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || !Files.isWritable(directory)) {
            throw new IOException("Candidate directory is unavailable.");
        }
        if (Files.getFileStore(directory).supportsFileAttributeView("posix")
                && !Files.getPosixFilePermissions(directory).equals(PosixFilePermissions.fromString("rwx------"))) {
            throw new IOException("Candidate directory must use mode 700.");
        }
    }

    private record Sample(UUID id, String split, RecognitionFeedbackService.Category category, String utterance,
                          String consentVersion, Instant createdAt) {}
}
