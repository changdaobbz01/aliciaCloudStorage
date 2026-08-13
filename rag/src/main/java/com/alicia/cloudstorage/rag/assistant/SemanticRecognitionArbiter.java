package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

final class SemanticRecognitionArbiter {

    private static final String CONFIG_PATH = "rag/conversation/semantic_arbitration.json";

    private final double modelUncertainThreshold;
    private final double uncertainLocalThreshold;
    private final double authoritativeLocalThreshold;
    private final Set<String> authoritativeLocalIntents;
    private final Set<Pattern> clarificationPatterns;

    SemanticRecognitionArbiter(RagConfigLoader configLoader) {
        JsonNode config = configLoader.loadJson(CONFIG_PATH);
        this.modelUncertainThreshold = config.path("modelUncertainThreshold").asDouble(0.65);
        this.uncertainLocalThreshold = config.path("uncertainLocalThreshold").asDouble(0.9);
        this.authoritativeLocalThreshold = config.path("authoritativeLocalThreshold").asDouble(0.9);
        Set<String> intentIds = new LinkedHashSet<>();
        config.path("authoritativeLocalIntents").forEach(node -> {
            String intentId = node.asText("").trim();
            if (!intentId.isBlank()) {
                intentIds.add(intentId);
            }
        });
        this.authoritativeLocalIntents = Set.copyOf(intentIds);
        Set<Pattern> patterns = new LinkedHashSet<>();
        config.path("clarificationPatterns").forEach(node -> {
            String pattern = node.asText("").trim();
            if (!pattern.isBlank()) {
                patterns.add(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            }
        });
        this.clarificationPatterns = Set.copyOf(patterns);
    }

    ArbitrationDecision decide(
            String message,
            IntentRecognitionResponse modelResponse,
            IntentRouter.IntentRouteResult localRoute
    ) {
        if (modelResponse == null || localRoute == null) {
            return ArbitrationDecision.keepModel("no_usable_local_route");
        }
        if (clarificationPatterns.stream().anyMatch(pattern -> pattern.matcher(message == null ? "" : message).find())) {
            return ArbitrationDecision.useLocal("configured_underspecified_request_guard");
        }
        if ("fallback".equals(localRoute.intent())) {
            return ArbitrationDecision.keepModel("no_usable_local_route");
        }
        boolean authoritativeStructure = authoritativeLocalIntents.contains(localRoute.intent())
                && localRoute.confidence() >= authoritativeLocalThreshold;
        if (authoritativeStructure) {
            if (localRoute.intent().equals(modelResponse.intentId())) {
                return ArbitrationDecision.useLocalStructure("authoritative_structural_rule_agreement");
            }
            return ArbitrationDecision.useLocal("authoritative_structural_rule");
        }
        if (localRoute.intent().equals(modelResponse.intentId())) {
            return ArbitrationDecision.keepModel("model_and_local_route_agree");
        }

        boolean modelUncertain = "fallback".equals(modelResponse.intentId())
                || modelResponse.confidence() < modelUncertainThreshold;
        if (modelUncertain && localRoute.confidence() >= uncertainLocalThreshold) {
            return ArbitrationDecision.useLocal("model_uncertain_local_rule_high_confidence");
        }
        return ArbitrationDecision.keepModel("model_retained");
    }

    record ArbitrationDecision(boolean useLocalRoute, boolean useLocalStructure, String reason) {

        static ArbitrationDecision useLocal(String reason) {
            return new ArbitrationDecision(true, true, reason);
        }

        static ArbitrationDecision useLocalStructure(String reason) {
            return new ArbitrationDecision(false, true, reason);
        }

        static ArbitrationDecision keepModel(String reason) {
            return new ArbitrationDecision(false, false, reason);
        }
    }
}
