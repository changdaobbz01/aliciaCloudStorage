package com.alicia.cloudstorage.rag.assistant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record SemanticFrame(
        String schemaVersion,
        String relation,
        String operation,
        Query query,
        Scope scope,
        Reference reference,
        double confidence,
        List<String> ambiguities,
        Clarification clarification
) {
    public static final String VERSION = "semantic_frame_v2";

    public SemanticFrame {
        schemaVersion = textOrDefault(schemaVersion, VERSION);
        relation = canonical(relation, "NEW_TASK");
        operation = canonical(operation, "UNKNOWN");
        query = query == null ? Query.empty() : query;
        scope = scope == null ? Scope.empty() : scope;
        reference = reference == null ? Reference.empty() : reference;
        confidence = Math.max(0.0, Math.min(confidence, 1.0));
        ambiguities = ambiguities == null ? List.of() : ambiguities.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        clarification = clarification == null ? Clarification.empty() : clarification;
    }

    public static SemanticFrame empty() {
        return new SemanticFrame(
                VERSION,
                "NEW_TASK",
                "UNKNOWN",
                Query.empty(),
                Scope.empty(),
                Reference.empty(),
                0.0,
                List.of(),
                Clarification.empty()
        );
    }

    public boolean needsClarification() {
        return !ambiguities.isEmpty() || !clarification.question().isBlank();
    }

    public SemanticFrame forCandidateSelection(CandidateItem candidate, Integer candidateIndex) {
        return new SemanticFrame(
                schemaVersion,
                "CANDIDATE_SELECTION",
                operation,
                query,
                scope,
                new Reference(
                        candidate == null ? "PREVIOUS_CANDIDATE_SET" : "SELECTED_CANDIDATE",
                        candidate == null ? null : candidate.nodeId(),
                        candidateIndex
                ),
                confidence,
                List.of(),
                Clarification.empty()
        );
    }

    public record Query(
            String mode,
            String resultType,
            String nameSurface,
            String nameNormalized,
            Map<String, Object> filters
    ) {
        public Query {
            mode = canonical(mode, "NONE");
            resultType = canonical(resultType, "ANY");
            nameSurface = safeText(nameSurface);
            nameNormalized = safeText(nameNormalized);
            filters = filters == null || filters.isEmpty()
                    ? Map.of()
                    : Map.copyOf(new LinkedHashMap<>(filters));
        }

        public static Query empty() {
            return new Query("NONE", "ANY", "", "", Map.of());
        }
    }

    public record Scope(
            String type,
            String folderSurface,
            String folderNormalized
    ) {
        public Scope {
            type = canonical(type, "ALL");
            folderSurface = safeText(folderSurface);
            folderNormalized = safeText(folderNormalized);
        }

        public static Scope empty() {
            return new Scope("ALL", "", "");
        }
    }

    public record Reference(
            String type,
            Long candidateId,
            Integer candidateIndex
    ) {
        public Reference {
            type = canonical(type, "NONE");
            candidateIndex = candidateIndex != null && candidateIndex > 0 ? candidateIndex : null;
        }

        public static Reference empty() {
            return new Reference("NONE", null, null);
        }
    }

    public record Clarification(
            String reason,
            String question,
            List<String> suggestions
    ) {
        public Clarification {
            reason = safeText(reason);
            question = safeText(question);
            suggestions = suggestions == null ? List.of() : suggestions.stream()
                    .filter(item -> item != null && !item.isBlank())
                    .map(String::trim)
                    .distinct()
                    .limit(3)
                    .toList();
        }

        public static Clarification empty() {
            return new Clarification("", "", List.of());
        }
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String textOrDefault(String value, String fallback) {
        String text = safeText(value);
        return text.isBlank() ? fallback : text;
    }

    private static String canonical(String value, String fallback) {
        String text = safeText(value);
        return text.isBlank() ? fallback : text.toUpperCase();
    }
}
