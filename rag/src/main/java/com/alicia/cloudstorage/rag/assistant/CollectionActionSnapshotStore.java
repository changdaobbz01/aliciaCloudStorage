package com.alicia.cloudstorage.rag.assistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class CollectionActionSnapshotStore {

    private final ConcurrentMap<String, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final int maxSnapshots;

    public CollectionActionSnapshotStore(
            @Value("${alicia.rag.collection-snapshot.ttl-minutes:30}") long ttlMinutes,
            @Value("${alicia.rag.collection-snapshot.max-snapshots:1000}") int maxSnapshots
    ) {
        this.ttl = Duration.ofMinutes(Math.max(1L, ttlMinutes));
        this.maxSnapshots = Math.max(32, maxSnapshots);
    }

    public String save(String planId, String authorizationHeader, List<CandidateItem> candidates) {
        purgeExpired();
        if (snapshots.size() >= maxSnapshots) {
            snapshots.entrySet().stream()
                    .min(java.util.Comparator.comparing(entry -> entry.getValue().expiresAt()))
                    .ifPresent(entry -> snapshots.remove(entry.getKey(), entry.getValue()));
        }
        String snapshotId = "cs_" + UUID.randomUUID();
        snapshots.put(snapshotId, new Snapshot(
                planId == null ? "" : planId,
                authorizationFingerprint(authorizationHeader),
                candidates == null ? List.of() : List.copyOf(candidates),
                Instant.now().plus(ttl)
        ));
        return snapshotId;
    }

    public Optional<List<CandidateItem>> load(
            String snapshotId,
            String planId,
            String authorizationHeader
    ) {
        purgeExpired();
        Snapshot snapshot = snapshots.get(snapshotId == null ? "" : snapshotId);
        if (snapshot == null
                || !snapshot.planId().equals(planId == null ? "" : planId)
                || !snapshot.authorizationFingerprint().equals(authorizationFingerprint(authorizationHeader))) {
            return Optional.empty();
        }
        return Optional.of(snapshot.candidates());
    }

    public void removeByPlanId(String planId, String authorizationHeader) {
        if (planId != null && !planId.isBlank()) {
            String fingerprint = authorizationFingerprint(authorizationHeader);
            snapshots.entrySet().removeIf(entry -> planId.equals(entry.getValue().planId())
                    && fingerprint.equals(entry.getValue().authorizationFingerprint()));
        }
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        snapshots.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private String authorizationFingerprint(String authorizationHeader) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((authorizationHeader == null ? "" : authorizationHeader.trim())
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to fingerprint authorization context.", exception);
        }
    }

    private record Snapshot(
            String planId,
            String authorizationFingerprint,
            List<CandidateItem> candidates,
            Instant expiresAt
    ) {
    }
}
