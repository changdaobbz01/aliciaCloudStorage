package com.alicia.cloudstorage.api.ragexecution.application;

import com.alicia.cloudstorage.api.ragexecution.domain.BatchNodeSnapshotCloud;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Component
public class CloudBatchSnapshotFingerprint {

    public String hash(List<BatchNodeSnapshotCloud> snapshots) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            snapshots.stream()
                    .sorted(Comparator.comparingLong(BatchNodeSnapshotCloud::nodeId))
                    .map(this::canonicalRow)
                    .forEach(row -> digest.update(row.getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to fingerprint a cloud batch snapshot.", exception);
        }
    }

    private String canonicalRow(BatchNodeSnapshotCloud snapshot) {
        return encoded(snapshot.nodeId())
                + encoded(snapshot.parentId())
                + encoded(snapshot.name())
                + encoded(snapshot.nodeType())
                + encoded(OffsetDateTime.parse(snapshot.updatedAt()).toInstant());
    }

    private String encoded(Object value) {
        String text = Objects.toString(value, "");
        return text.length() + ":" + text;
    }
}
