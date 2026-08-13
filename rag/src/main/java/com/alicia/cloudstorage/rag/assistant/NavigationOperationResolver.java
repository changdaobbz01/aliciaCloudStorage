package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NavigationOperationResolver {

    private static final Pattern CONTENT_SUFFIX = Pattern.compile(
            "^(.+?)(?:[，,]\s*)?(?:并且|并|然后)?(?:把)?(?:里面|其中|目录里|文件夹里|下|中|里|内)(?:的)?(?:所有|全部)?(文件夹|目录|文件|内容)(?:都)?(?:列出(?:来)?|列一下|展示|显示|给我看|看看|看下|有哪些)?$"
    );
    private static final Pattern AFTER_LOCATION_LIST_SUFFIX = Pattern.compile(
            "^(.+?)(?:[，,]\s*)?(?:并且|并|然后)?(?:把)?(?:里面|其中|目录里|文件夹里|下|中|里|内)(?:的)?(?:所有|全部)?(文件夹|目录|文件|内容)?(?:都)?(?:列出(?:来)?|列一下|展示|显示|给我看|看看|看下|有哪些)$"
    );
    private static final Pattern LIST_CONTENT_SUFFIX = Pattern.compile(
            "^(.+?)(?:[，,]\s*)?(?:并且|并|然后)?(?:列出|列一下|展示|显示|查看|看看|看下)(?:其中|里面|目录里|文件夹里|下|中|里|内)(?:的)?(?:所有|全部)?(文件夹|目录|文件|内容)?$"
    );
    private static final Pattern ORDINAL_REFERENCE = Pattern.compile(
            "^(?:第?[一二三四五六七八九十0-9]+(?:个|项|条|号)|上一个|下一个)$"
    );

    private final List<String> politePrefixes;
    private final List<String> locationQuestionPrefixes;
    private final List<String> locationQuestionSuffixes;
    private final List<String> verbs;
    private final List<String> leadingParticles;
    private final List<String> trailingParticles;
    private final List<String> genericReferences;
    private final List<String> rootReferences;
    private final List<String> parentReferences;
    private final List<String> fileDescriptors;
    private final List<String> folderDescriptors;
    private final Pattern fileExtensionPattern;

    public NavigationOperationResolver(RagConfigLoader configLoader) {
        JsonNode config = configLoader.loadJson("rag/conversation/query_rules.json")
                .path("navigation_resolution");
        this.politePrefixes = sortedStrings(config.path("polite_prefixes"));
        this.locationQuestionPrefixes = sortedStrings(config.path("location_question_prefixes"));
        this.locationQuestionSuffixes = sortedStrings(config.path("location_question_suffixes"));
        this.verbs = sortedStrings(config.path("verbs"));
        this.leadingParticles = sortedStrings(config.path("leading_particles"));
        this.trailingParticles = sortedStrings(config.path("trailing_particles"));
        this.genericReferences = strings(config.path("generic_references"));
        this.rootReferences = strings(config.path("root_references"));
        this.parentReferences = strings(config.path("parent_references"));
        this.fileDescriptors = strings(config.path("file_descriptors"));
        this.folderDescriptors = strings(config.path("folder_descriptors"));
        this.fileExtensionPattern = Pattern.compile(
                config.path("file_extension_pattern").asText("(?i).+\\.[a-z0-9]{1,12}$")
        );
    }

    public boolean handles(String message, AssistantConversationState conversation) {
        return resolve(message, conversation).isPresent();
    }

    public IntentRecognitionResponse apply(
            String message,
            AssistantConversationState conversation,
            IntentRecognitionResponse response
    ) {
        return resolve(message, conversation)
                .map(resolution -> override(response, resolution))
                .orElse(response);
    }

    private Optional<Resolution> resolve(String message, AssistantConversationState conversation) {
        String normalized = cleanTarget(normalize(message));
        Optional<Resolution> locationQuestion = parseLocationQuestion(normalized, conversation);
        if (locationQuestion.isPresent()) {
            return locationQuestion;
        }

        String text = stripPrefix(normalized, politePrefixes);
        String verb = matchingPrefix(text, verbs);
        if (verb.isBlank()) {
            return Optional.empty();
        }

        String target = text.substring(verb.length()).trim();
        target = stripPrefix(target, leadingParticles);
        target = stripSuffix(target, trailingParticles);
        target = cleanTarget(target);
        if (target.isBlank()
                || rootReferences.contains(target)
                || parentReferences.contains(target)
                || ORDINAL_REFERENCE.matcher(target).matches()) {
            return Optional.empty();
        }

        if (genericReferences.contains(target)) {
            if (hasCandidateContext(conversation)) {
                return Optional.empty();
            }
            return Optional.of(Resolution.needsClarification());
        }

        Optional<Resolution> contentResolution = parseContents(target);
        if (contentResolution.isPresent()) {
            return contentResolution;
        }

        if (List.of("进入", "进去", "跳转到", "前往").contains(verb)) {
            target = target.replaceFirst("(?:里面|里|中)$", "").trim();
        }

        String explicitType = explicitType(target);
        String cleanName = stripTypeWrapper(target);
        if (cleanName.isBlank() || genericReferences.contains(cleanName)) {
            return Optional.empty();
        }
        if ("FILE".equals(explicitType) || fileExtensionPattern.matcher(cleanName).matches()) {
            return Optional.of(Resolution.openFile(cleanName));
        }
        if ("FOLDER".equals(explicitType)
                || List.of("进入", "进去", "跳转到", "前往").contains(verb)
                || cleanName.endsWith("目录")
                || cleanName.endsWith("文件夹")) {
            return Optional.of(Resolution.navigate(cleanName));
        }
        return Optional.empty();
    }

    private Optional<Resolution> parseLocationQuestion(
            String message,
            AssistantConversationState conversation
    ) {
        String text = stripPrefix(message, locationQuestionPrefixes);
        String suffix = matchingSuffix(text, locationQuestionSuffixes);
        if (suffix.isBlank() || text.length() <= suffix.length()) {
            return Optional.empty();
        }

        String target = cleanTarget(text.substring(0, text.length() - suffix.length()));
        if (genericReferences.contains(target)) {
            return hasCandidateContext(conversation)
                    ? Optional.empty()
                    : Optional.of(Resolution.needsClarification());
        }
        target = stripExplicitNameWrapper(target);
        if (target.isBlank() || genericReferences.contains(target)) {
            return Optional.of(Resolution.needsClarification());
        }

        String resultType = explicitType(target);
        return Optional.of(Resolution.locate(target, resultType));
    }

    private Optional<Resolution> parseContents(String target) {
        Matcher matcher = LIST_CONTENT_SUFFIX.matcher(target);
        if (!matcher.matches()) {
            matcher = CONTENT_SUFFIX.matcher(target);
        }
        if (!matcher.matches()) {
            matcher = AFTER_LOCATION_LIST_SUFFIX.matcher(target);
        }
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String folder = stripTypeWrapper(matcher.group(1));
        if (folder.isBlank() || genericReferences.contains(folder)) {
            return Optional.empty();
        }
        String resultType = switch (safe(matcher.group(2))) {
            case "文件" -> "FILE";
            case "文件夹", "目录" -> "FOLDER";
            default -> "ANY";
        };
        return Optional.of(Resolution.listChildren(folder, resultType));
    }

    private IntentRecognitionResponse override(IntentRecognitionResponse response, Resolution resolution) {
        if (resolution.clarification()) {
            String question = "请告诉我具体是哪个文件或文件夹，例如“测试图片”或“合同.pdf”。";
            SemanticFrame frame = new SemanticFrame(
                    SemanticFrame.VERSION,
                    "NEW_TASK",
                    "UNKNOWN",
                    SemanticFrame.Query.empty(),
                    SemanticFrame.Scope.empty(),
                    SemanticFrame.Reference.empty(),
                    1.0,
                    List.of("missing_navigation_target"),
                    new SemanticFrame.Clarification(
                            "missing_navigation_target",
                            question,
                            List.of("测试图片在哪", "合同.pdf 在哪里")
                    )
            );
            return response.withCapabilityBoundary("missing_navigation_target", question, question)
                    .withSemanticFrame(frame, Map.of(), new ActionDraft("none", Map.of(), false))
                    .withAssistantText(question);
        }

        boolean listChildren = "LIST_CHILDREN".equals(resolution.queryMode());
        Map<String, Object> entities = new LinkedHashMap<>();
        entities.put("query_mode", listChildren ? "directory_list" : "name_search");
        entities.put("scope", listChildren ? "named_folder" : "all");
        entities.put("result_type", resolution.resultType());
        if (listChildren) {
            entities.put("target_folder", resolution.target());
        } else {
            entities.put("target_name", resolution.target());
        }
        Map<String, Object> immutableEntities = Map.copyOf(entities);
        SemanticFrame frame = new SemanticFrame(
                SemanticFrame.VERSION,
                "NEW_TASK",
                resolution.operation(),
                new SemanticFrame.Query(
                        resolution.queryMode(),
                        resolution.resultType(),
                        listChildren ? "" : resolution.target(),
                        listChildren ? "" : resolution.target().toLowerCase(Locale.ROOT),
                        Map.of()
                ),
                new SemanticFrame.Scope(
                        listChildren ? "NAMED_FOLDER" : "ALL",
                        listChildren ? resolution.target() : "",
                        listChildren ? resolution.target().toLowerCase(Locale.ROOT) : ""
                ),
                SemanticFrame.Reference.empty(),
                0.99,
                List.of(),
                SemanticFrame.Clarification.empty()
        );
        String assistantText = switch (resolution.operation()) {
            case "NAVIGATE" -> "我会先准确定位这个文件夹，找到后直接为你打开。";
            case "OPEN_FILE" -> "我会先准确定位这个文件，找到后直接为你打开。";
            case "SEARCH" -> listChildren
                    ? "我会先定位这个文件夹，再列出其中符合要求的内容。"
                    : "我先按名称定位这个文件或文件夹，找到后把它的位置展示给你。";
            default -> "我会先定位这个文件夹，再列出其中符合要求的内容。";
        };
        return response.withPlanningOverride(
                "file_search",
                "文件检索",
                "FILE_QUERY",
                immutableEntities,
                "wait_for_backend_binding",
                new SafetyDecision("none", false, false, "只读取云盘元数据，不修改文件。"),
                new ActionDraft("search", immutableEntities, true),
                assistantText,
                "已生成确定性的打开或导航语义。",
                frame
        );
    }

    private String explicitType(String target) {
        String normalized = safe(target);
        if (fileDescriptors.stream().anyMatch(normalized::endsWith)) {
            return "FILE";
        }
        if (folderDescriptors.stream().anyMatch(normalized::endsWith)) {
            return "FOLDER";
        }
        return "ANY";
    }

    private String stripTypeWrapper(String target) {
        String value = safe(target)
                .replaceFirst("^(?:名为|叫做|名称为|名字为)", "")
                .replaceFirst("(?:这个|那个|该)(?:文件夹|目录|文件)$", "")
                .trim();
        return value.replaceFirst("的(?:文件夹|目录|文件)$", "").trim();
    }

    private String stripExplicitNameWrapper(String target) {
        return safe(target)
                .replaceFirst("^(?:名为|叫做|名称为|名字为)", "")
                .replaceFirst("的(?:文件夹|目录|文件)$", "")
                .trim();
    }

    private String cleanTarget(String target) {
        return safe(target).replaceAll("^[：:，,]+|[。！？!?]+$", "").trim();
    }

    private String stripPrefix(String value, List<String> prefixes) {
        String result = safe(value);
        String prefix = matchingPrefix(result, prefixes);
        return prefix.isBlank() ? result : result.substring(prefix.length()).trim();
    }

    private String stripSuffix(String value, List<String> suffixes) {
        String result = safe(value);
        for (String suffix : suffixes) {
            if (result.endsWith(suffix) && result.length() > suffix.length()) {
                return result.substring(0, result.length() - suffix.length()).trim();
            }
        }
        return result;
    }

    private String matchingPrefix(String value, List<String> prefixes) {
        return prefixes.stream().filter(value::startsWith).findFirst().orElse("");
    }

    private String matchingSuffix(String value, List<String> suffixes) {
        return suffixes.stream().filter(value::endsWith).findFirst().orElse("");
    }

    private boolean hasCandidateContext(AssistantConversationState conversation) {
        return conversation != null
                && conversation.focus() != null
                && conversation.focus().candidateCount() > 0;
    }

    private static String normalize(String value) {
        return safe(value).replaceAll("\\s+", "");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                String value = item.asText("").trim();
                if (!value.isBlank()) {
                    values.add(value);
                }
            });
        }
        return List.copyOf(values);
    }

    private static List<String> sortedStrings(JsonNode node) {
        return strings(node).stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    private record Resolution(
            String operation,
            String queryMode,
            String resultType,
            String target,
            boolean clarification
    ) {
        private static Resolution navigate(String target) {
            return new Resolution("NAVIGATE", "NAME_EXACT", "FOLDER", target, false);
        }

        private static Resolution openFile(String target) {
            return new Resolution("OPEN_FILE", "NAME_EXACT", "FILE", target, false);
        }

        private static Resolution listChildren(String target, String resultType) {
            return new Resolution("SEARCH", "LIST_CHILDREN", resultType, target, false);
        }

        private static Resolution locate(String target, String resultType) {
            return new Resolution("SEARCH", "NAME_EXACT", resultType, target, false);
        }

        private static Resolution needsClarification() {
            return new Resolution("UNKNOWN", "NONE", "ANY", "", true);
        }
    }
}
