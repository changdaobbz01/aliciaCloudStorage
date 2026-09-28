package com.alicia.cloudstorage.rag.execution;

import com.alicia.cloudstorage.rag.assistant.CandidateItem;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Component
public class BatchSnapshotFingerprint {

    public String hash(List<CandidateItem> candidates) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            candidates.stream()
                    .sorted(Comparator.comparing(CandidateItem::nodeId))
                    .map(this::canonicalRow)
                    .forEach(row -> digest.update(row.getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to fingerprint a RAG batch snapshot.", exception);
        }
    }

    private String canonicalRow(CandidateItem candidate) {
        return encoded(candidate.nodeId())
                + encoded(candidate.parentId())
                + encoded(candidate.name())
                + encoded(candidate.type().toUpperCase(java.util.Locale.ROOT))
                + encoded(OffsetDateTime.parse(candidate.updatedAt()).toInstant());
    }

    private String encoded(Object value) {
        String text = Objects.toString(value, "");
        return text.length() + ":" + text;
    }
}
