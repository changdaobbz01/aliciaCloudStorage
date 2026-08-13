package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CandidateQueryPlanner {

    private final Rules rules;

    @Autowired
    public CandidateQueryPlanner(RagConfigLoader configLoader) {
        this(Rules.from(configLoader.loadJson("rag/conversation/query_rules.json").path("candidate_binding")));
    }

    private CandidateQueryPlanner(Rules rules) {
        this.rules = rules;
    }

    static CandidateQueryPlanner defaults() {
        return new CandidateQueryPlanner(Rules.defaults());
    }

    public List<String> variants(CandidateSearchRequest request) {
        String rawQuery = TextSupport.sanitizeNodeName(request == null ? "" : request.query());
        if (rawQuery.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> variants = new LinkedHashSet<>();
        addVariant(variants, rawQuery);

        RoleRules roleRules = rules.roleRules(queryRole(request));
        String descriptorStripped = stripConfiguredDescriptor(rawQuery, roleRules.removableDescriptors());
        addVariant(variants, descriptorStripped);

        TextSupport.tokenize(descriptorStripped).forEach(token -> addVariant(variants, token));
        addNounPhraseVariants(variants, descriptorStripped, roleRules);

        return variants.stream()
                .limit(rules.maxQueryVariants())
                .toList();
    }

    public int matchScore(CandidateItem candidate, List<String> queryVariants) {
        if (candidate == null || queryVariants == null || queryVariants.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        String name = normalize(candidate.name());
        String path = normalize(candidate.path());
        for (int index = 0; index < queryVariants.size(); index++) {
            String query = normalize(queryVariants.get(index));
            if (query.isBlank()) {
                continue;
            }
            if (name.equals(query)) {
                return index;
            }
            if (name.contains(query)) {
                return 10 + index;
            }
            if (rules.matchPath() && path.contains(query)) {
                return 30 + index;
            }
        }
        return Integer.MAX_VALUE;
    }

    private void addNounPhraseVariants(
            LinkedHashSet<String> variants,
            String value,
            RoleRules roleRules
    ) {
        if (value == null || value.isBlank()) {
            return;
        }
        List<String> headNouns = roleRules.candidateHeadNouns().stream()
                .sorted((left, right) -> Integer.compare(right.length(), left.length()))
                .toList();
        for (String headNoun : headNouns) {
            if (headNoun.isBlank() || !value.endsWith(headNoun) || value.length() <= headNoun.length()) {
                continue;
            }
            String modifier = TextSupport.sanitizeNodeName(value.substring(0, value.length() - headNoun.length()));
            if (roleRules.includeModifierAlone()) {
                addVariant(variants, modifier);
            }
            if (roleRules.includeHeadNounAlone()) {
                addVariant(variants, headNoun);
            }
            return;
        }
    }

    private String queryRole(CandidateSearchRequest request) {
        if (request != null && request.queryRole() != null && !request.queryRole().isBlank()) {
            return request.queryRole();
        }
        if (request != null && "FOLDER".equalsIgnoreCase(request.candidateType())) {
            return "target_folder";
        }
        if (request != null && "search".equalsIgnoreCase(request.actionType())) {
            return "search_query";
        }
        return "target_name";
    }

    private String stripConfiguredDescriptor(String value, List<String> descriptors) {
        if (value == null || value.isBlank() || descriptors.isEmpty()) {
            return value == null ? "" : value;
        }
        return descriptors.stream()
                .filter(descriptor -> descriptor != null && !descriptor.isBlank())
                .sorted((left, right) -> Integer.compare(right.length(), left.length()))
                .filter(descriptor -> value.endsWith(descriptor))
                .map(descriptor -> value.substring(0, value.length() - descriptor.length()))
                .map(TextSupport::sanitizeNodeName)
                .filter(prefix -> prefix.length() >= rules.minVariantLength())
                .findFirst()
                .orElse(value);
    }

    private void addVariant(LinkedHashSet<String> variants, String value) {
        String sanitized = TextSupport.sanitizeNodeName(value);
        if (sanitized.length() >= rules.minVariantLength()) {
            variants.add(sanitized);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private record Rules(
            int maxQueryVariants,
            int minVariantLength,
            Map<String, RoleRules> roles,
            boolean matchPath
    ) {
        private RoleRules roleRules(String role) {
            RoleRules fallback = roles.getOrDefault("target_name", RoleRules.defaults());
            return roles.getOrDefault(role, fallback);
        }

        private static Rules from(JsonNode node) {
            Rules defaults = defaults();
            if (node == null || node.isMissingNode() || node.isNull()) {
                return defaults;
            }
            return new Rules(
                    Math.max(1, node.path("max_query_variants").asInt(defaults.maxQueryVariants())),
                    Math.max(1, node.path("min_variant_length").asInt(defaults.minVariantLength())),
                    roleMap(node.path("roles"), defaults.roles()),
                    node.path("match_path").asBoolean(defaults.matchPath())
            );
        }

        private static Rules defaults() {
            return new Rules(
                    6,
                    2,
                    Map.of(
                            "target_folder", new RoleRules(
                                    "object",
                                    List.of("文件夹", "目录", "文件目录", "文件夹目录"),
                                    List.of(
                                            "这个文件夹", "那个文件夹", "该文件夹",
                                            "这个目录", "那个目录", "该目录",
                                            "文件夹", "目录", "文件目录", "云盘目录", "资料夹"
                                    ),
                                    true,
                                    true
                            ),
                            "target_name", RoleRules.defaults(),
                            "search_query", new RoleRules(
                                    "object",
                                    List.of("文件", "文档", "照片", "图片", "视频", "音频", "压缩包", "目录", "文件夹"),
                                    List.of("文件"),
                                    true,
                                    true
                            ),
                            "message", new RoleRules(
                                    "utterance",
                                    List.of("文件", "文档", "照片", "图片", "视频", "音频", "压缩包", "目录", "文件夹"),
                                    List.of("文件"),
                                    true,
                                    true
                            )
                    ),
                    true
            );
        }

        private static Map<String, RoleRules> roleMap(JsonNode node, Map<String, RoleRules> fallback) {
            if (node == null || !node.isObject()) {
                return fallback;
            }
            Map<String, RoleRules> roles = new LinkedHashMap<>(fallback);
            node.fields().forEachRemaining(entry -> roles.put(
                    entry.getKey(),
                    RoleRules.from(entry.getValue(), fallback.getOrDefault(entry.getKey(), RoleRules.defaults()))
            ));
            return Collections.unmodifiableMap(roles);
        }

        private static List<String> stringList(JsonNode node, List<String> fallback) {
            if (node == null || !node.isArray()) {
                return fallback;
            }
            List<String> values = new ArrayList<>();
            for (JsonNode item : node) {
                String value = item.asText("").trim();
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
            return values.isEmpty() ? fallback : List.copyOf(values);
        }
    }

    private record RoleRules(
            String phraseRole,
            List<String> candidateHeadNouns,
            List<String> removableDescriptors,
            boolean includeHeadNounAlone,
            boolean includeModifierAlone
    ) {
        private static RoleRules from(JsonNode node, RoleRules fallback) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                return fallback;
            }
            return new RoleRules(
                    node.path("phrase_role").asText(fallback.phraseRole()),
                    Rules.stringList(node.path("candidate_head_nouns"), fallback.candidateHeadNouns()),
                    Rules.stringList(node.path("removable_descriptors"), fallback.removableDescriptors()),
                    node.path("include_head_noun_alone").asBoolean(fallback.includeHeadNounAlone()),
                    node.path("include_modifier_alone").asBoolean(fallback.includeModifierAlone())
            );
        }

        private static RoleRules defaults() {
            return new RoleRules(
                    "object",
                    List.of("文件", "文档", "照片", "图片", "视频", "音频", "压缩包"),
                    List.of("这个文件", "那个文件", "该文件", "文件"),
                    false,
                    true
            );
        }
    }
}
