package com.alicia.cloudstorage.rag.assistant;

import java.util.List;
import java.util.Map;

public interface SemanticExampleRetriever {

    List<SemanticExample> retrieve(String message, int limit);

    default List<SemanticBoundary> retrieveBoundaries(String message, int limit) {
        return List.of();
    }

    record SemanticExample(
            String id,
            String utterance,
            String intentId,
            String operation,
            Map<String, Object> entities,
            double score
    ) {
        public SemanticExample {
            entities = entities == null ? Map.of() : Map.copyOf(entities);
            score = Math.max(0.0, Math.min(score, 1.0));
        }

        public Map<String, Object> promptPayload() {
            return Map.of(
                    "utterance", utterance,
                    "intent_id", intentId,
                    "operation", operation,
                    "entities", entities
            );
        }
    }

    record SemanticBoundary(
            String id,
            String utterance,
            String reason,
            double score
    ) {
        public SemanticBoundary {
            score = Math.max(0.0, Math.min(score, 1.0));
        }
    }
}
