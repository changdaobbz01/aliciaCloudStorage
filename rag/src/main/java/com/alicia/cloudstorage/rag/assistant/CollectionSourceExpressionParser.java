package com.alicia.cloudstorage.rag.assistant;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

final class CollectionSourceExpressionParser {

    static final String SOURCE_ROOT = "ROOT_CHILDREN";
    static final String SOURCE_NAMED_FOLDER = "NAMED_FOLDER_CHILDREN";
    static final String SOURCE_CURRENT_FOLDER = "CURRENT_FOLDER_CHILDREN";
    static final String SOURCE_CONTEXT_FOLDER = "CONTEXT_FOLDER_CHILDREN";
    static final String SOURCE_PREVIOUS_RESULTS = "PREVIOUS_RESULTS";

    private static final Set<String> ROOT_ALIASES = Set.of(
            "根", "根目录", "根文件夹", "云盘根目录", "我的云盘", "顶层目录", "最外层", "/"
    );
    private static final Set<String> CURRENT_ALIASES = Set.of("当前目录", "当前文件夹");
    private static final Set<String> CONTEXT_ALIASES = Set.of(
            "这个目录", "这个文件夹", "该目录", "该文件夹"
    );
    private static final Pattern PREVIOUS_RESULTS = Pattern.compile(
            "^(?:请)?(?:把|将)?(?:(?:刚才|上面|之前|上一轮)(?:列出|找到|搜索到|查到|展示)(?:的)?(?:全部|所有)?(?:结果|文件|项目)?|这些|那些|它们|结果|文件|项目)(?:都|全部|全都|全)?$"
    );
    private static final Pattern LEADING_QUANTIFIER = Pattern.compile("^(?:所有|全部)(?:的)?");
    private static final Pattern TRAILING_QUANTIFIER = Pattern.compile("(?:都|全部|全都|全)$");
    private static final Pattern NODE_CONNECTOR = Pattern.compile("(?:以及|和|与|及|、)");

    Optional<SourceSelection> parse(String surface, AssistantConversationState conversation) {
        String text = normalize(surface);
        if (text.isBlank()) {
            return Optional.empty();
        }
        if (PREVIOUS_RESULTS.matcher(text).matches()) {
            return Optional.of(new SourceSelection(
                    SOURCE_PREVIOUS_RESULTS,
                    "",
                    inferPreviousNodeKinds(conversation),
                    "ALL",
                    false
            ));
        }

        String sourceExpression = stripSourcePrefix(text);
        Optional<SourceSelection> scoped = parseScopedContent(sourceExpression, conversation);
        if (scoped.isPresent()) {
            return scoped;
        }

        return parseNamedFolderContent(sourceExpression, conversation);
    }

    private Optional<SourceSelection> parseScopedContent(
            String expression,
            AssistantConversationState conversation
    ) {
        for (int index = 1; index < expression.length() - 1; index++) {
            if ("中里下内".indexOf(expression.charAt(index)) < 0) {
                continue;
            }
            String objectExpression = expression.substring(index + 1).replaceFirst("^的", "");
            Optional<SourceSelection> selection = selection(
                    expression.substring(0, index),
                    objectExpression,
                    conversation
            );
            if (selection.isPresent()) {
                return selection;
            }
        }
        return Optional.empty();
    }

    private Optional<SourceSelection> parseNamedFolderContent(
            String expression,
            AssistantConversationState conversation
    ) {
        for (int index = 2; index < expression.length(); index++) {
            String folder = expression.substring(0, index);
            if (!folder.endsWith("目录") && !folder.endsWith("文件夹")) {
                continue;
            }
            Optional<SourceSelection> selection = selection(
                    folder,
                    expression.substring(index),
                    conversation
            );
            if (selection.isPresent()) {
                return selection;
            }
        }
        return Optional.empty();
    }

    private Optional<SourceSelection> selection(
            String rawFolder,
            String rawObjectExpression,
            AssistantConversationState conversation
    ) {
        String folder = cleanFolder(rawFolder);
        Optional<EnumSet<NodeKind>> nodeKinds = parseNodeKinds(rawObjectExpression);
        if (folder.isBlank() || nodeKinds.isEmpty()) {
            return Optional.empty();
        }

        if (ROOT_ALIASES.contains(folder.toLowerCase(Locale.ROOT))) {
            return Optional.of(new SourceSelection(SOURCE_ROOT, folder, nodeKinds.get(), "ALL", false));
        }
        if (CURRENT_ALIASES.contains(folder)) {
            return Optional.of(new SourceSelection(SOURCE_CURRENT_FOLDER, folder, nodeKinds.get(), "ALL", false));
        }
        if (CONTEXT_ALIASES.contains(folder)) {
            if (!hasPreviousCandidates(conversation)) {
                return Optional.empty();
            }
            return Optional.of(new SourceSelection(SOURCE_CONTEXT_FOLDER, folder, nodeKinds.get(), "ALL", false));
        }
        return Optional.of(new SourceSelection(SOURCE_NAMED_FOLDER, folder, nodeKinds.get(), "ALL", false));
    }

    private Optional<EnumSet<NodeKind>> parseNodeKinds(String rawExpression) {
        String expression = normalize(rawExpression);
        expression = expression.replaceFirst("^的", "");
        expression = LEADING_QUANTIFIER.matcher(expression).replaceFirst("");
        expression = TRAILING_QUANTIFIER.matcher(expression).replaceFirst("");
        if (expression.isBlank()) {
            return Optional.empty();
        }

        EnumSet<NodeKind> kinds = EnumSet.noneOf(NodeKind.class);
        List<String> tokens = List.of(NODE_CONNECTOR.split(expression, -1));
        for (String rawToken : tokens) {
            String token = LEADING_QUANTIFIER.matcher(rawToken).replaceFirst("");
            switch (token) {
                case "文件" -> kinds.add(NodeKind.FILE);
                case "文件夹", "目录" -> kinds.add(NodeKind.FOLDER);
                case "内容", "东西" -> kinds.addAll(EnumSet.allOf(NodeKind.class));
                default -> {
                    return Optional.empty();
                }
            }
        }
        return kinds.isEmpty() ? Optional.empty() : Optional.of(kinds);
    }

    private EnumSet<NodeKind> inferPreviousNodeKinds(AssistantConversationState conversation) {
        if (!hasPreviousCandidates(conversation)) {
            return EnumSet.allOf(NodeKind.class);
        }
        EnumSet<NodeKind> kinds = EnumSet.noneOf(NodeKind.class);
        conversation.focus().candidateBinding().candidates().stream()
                .map(CandidateItem::type)
                .map(type -> type == null ? "" : type.toUpperCase(Locale.ROOT))
                .forEach(type -> {
                    if ("FILE".equals(type)) {
                        kinds.add(NodeKind.FILE);
                    } else if ("FOLDER".equals(type)) {
                        kinds.add(NodeKind.FOLDER);
                    }
                });
        return kinds.isEmpty() ? EnumSet.allOf(NodeKind.class) : kinds;
    }

    private boolean hasPreviousCandidates(AssistantConversationState conversation) {
        return conversation != null
                && conversation.focus() != null
                && conversation.focus().candidateBinding() != null
                && !conversation.focus().candidateBinding().candidates().isEmpty();
    }

    private String cleanFolder(String value) {
        String folder = normalize(value);
        if (List.of("这个", "该").contains(folder)) {
            return "这个文件夹";
        }
        return folder;
    }

    private String stripSourcePrefix(String value) {
        return value.replaceFirst("^请", "").replaceFirst("^(?:把|将)", "");
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().replaceAll("[，。！？?!]+$", "").replaceAll("\\s+", "");
    }

    enum NodeKind {
        FILE,
        FOLDER
    }

    record SourceSelection(
            String kind,
            String folder,
            Set<NodeKind> nodeKinds,
            String quantifier,
            boolean recursive
    ) {
        SourceSelection {
            nodeKinds = nodeKinds == null || nodeKinds.isEmpty()
                    ? Set.copyOf(EnumSet.allOf(NodeKind.class))
                    : Set.copyOf(nodeKinds);
        }

        List<String> nodeTypeNames() {
            return nodeKinds.stream().map(Enum::name).sorted().toList();
        }

        String legacyNodeType() {
            return nodeKinds.size() == 1 ? nodeKinds.iterator().next().name() : "ANY";
        }
    }
}
