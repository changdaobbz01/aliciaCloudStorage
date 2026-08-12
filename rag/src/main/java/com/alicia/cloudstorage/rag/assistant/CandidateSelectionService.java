package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

@Service
public class CandidateSelectionService {

    private final Map<String, Integer> ordinalAliases;
    private final List<String> noiseWords;

    public CandidateSelectionService(RagConfigLoader configLoader) {
        JsonNode config = configLoader.loadJson("rag/conversation/query_rules.json").path("candidate_selection");
        this.ordinalAliases = loadOrdinalAliases(config.path("ordinal_aliases"));
        this.noiseWords = stringList(config.path("noise_words"));
    }

    public SelectionAttempt select(CandidateBindingResult binding, String message) {
        return select(binding, message, AssistantClientEvent.none());
    }

    public SelectionAttempt select(
            CandidateBindingResult binding,
            String message,
            AssistantClientEvent clientEvent
    ) {
        AssistantClientEvent safeEvent = clientEvent == null ? AssistantClientEvent.none() : clientEvent;
        if (safeEvent.isCandidateSelection()) {
            if (binding == null || binding.candidates().isEmpty()) {
                return unavailableSelection();
            }
            Integer eventIndex = candidateIndex(binding, safeEvent);
            if (eventIndex == null) {
                return new SelectionAttempt(true, new CandidateBindingResult(
                        "candidate_selection_out_of_range",
                        binding.source(),
                        binding.query(),
                        binding.candidateType(),
                        binding.candidates(),
                        "选择的候选已经失效，请重新选择。"
                ));
            }
            return new SelectionAttempt(true, binding.select(eventIndex));
        }

        OptionalInt index = parseSelectionIndex(message);
        if (index.isEmpty()) {
            return SelectionAttempt.notMatched();
        }
        if (binding == null || binding.candidates().isEmpty()) {
            return unavailableSelection();
        }
        return new SelectionAttempt(true, binding.select(index.getAsInt()));
    }

    private SelectionAttempt unavailableSelection() {
        return new SelectionAttempt(true, CandidateBindingResult.skipped(
                "candidate_selection_unavailable",
                "当前没有可选择的候选，请重新告诉我你想处理的文件或文件夹。"
        ));
    }

    private Integer candidateIndex(CandidateBindingResult binding, AssistantClientEvent event) {
        if (event.candidateId() != null) {
            for (int index = 0; index < binding.candidates().size(); index++) {
                if (event.candidateId().equals(binding.candidates().get(index).nodeId())) {
                    return index;
                }
            }
            return null;
        }
        if (event.candidateIndex() != null) {
            int zeroBasedIndex = event.candidateIndex() - 1;
            return zeroBasedIndex >= 0 && zeroBasedIndex < binding.candidates().size()
                    ? zeroBasedIndex
                    : null;
        }
        return null;
    }

    private OptionalInt parseSelectionIndex(String message) {
        String normalized = normalize(message);
        if (normalized.isBlank()) {
            return OptionalInt.empty();
        }

        Integer exactValue = ordinalAliases.get(normalized);
        if (exactValue != null) {
            return toZeroBasedIndex(exactValue);
        }

        String stripped = normalized;
        for (String noiseWord : noiseWords) {
            stripped = stripped.replace(normalize(noiseWord), "");
        }
        Integer strippedValue = ordinalAliases.get(stripped);
        return strippedValue == null ? OptionalInt.empty() : toZeroBasedIndex(strippedValue);
    }

    private OptionalInt toZeroBasedIndex(Integer oneBasedIndex) {
        if (oneBasedIndex == null || oneBasedIndex <= 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(oneBasedIndex - 1);
    }

    private Map<String, Integer> loadOrdinalAliases(JsonNode node) {
        if (!node.isObject()) {
            return Map.of();
        }
        Map<String, Integer> aliases = new LinkedHashMap<>();
        node.properties().forEach(entry -> {
            String alias = normalize(entry.getKey());
            int value = entry.getValue().asInt(0);
            if (!alias.isBlank() && value > 0) {
                aliases.put(alias, value);
            }
        });
        return Map.copyOf(aliases);
    }

    private List<String> stringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            String value = item.asText("").trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        });
        return List.copyOf(values);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "");
    }

    public record SelectionAttempt(
            boolean matched,
            CandidateBindingResult candidateBinding
    ) {
        static SelectionAttempt notMatched() {
            return new SelectionAttempt(false, null);
        }
    }
}
