package com.alicia.cloudstorage.rag.assistant;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses operation arguments into stable source and destination roles. */
final class OperationArgumentResolver {

    private static final Set<String> SUPPORTED_OPERATIONS = Set.of(
            "SHARE", "DELETE", "MOVE", "RENAME", "UPLOAD"
    );
    private static final Set<String> GENERIC_FILES = Set.of(
            "文件", "文档", "资料", "图片", "照片", "视频", "音频", "音乐", "压缩包", "压缩文件"
    );
    private static final Set<String> GENERIC_FOLDERS = Set.of("文件夹", "目录");
    private static final Set<String> GENERIC_ANY = Set.of(
            "内容", "东西", "文件和文件夹", "文件与文件夹", "文件及文件夹", "文件、文件夹"
    );
    private static final Pattern EXPLICIT_NAME_PREFIX = Pattern.compile(
            "^(?:(?:文件|文件夹|目录)?(?:名|名称|文件名)为|叫做|叫)(.+)$"
    );
    private static final Pattern QUOTED_NAME = Pattern.compile("^[“\"](.+)[”\"](?:的)?(?:文件|文件夹|目录)?$");
    private static final Pattern ROOT_SCOPE = Pattern.compile(
            "^(云盘根目录|根目录|根文件夹|我的云盘|顶层目录|最外层)(?:下|中|里|内)?(?:的)?(.*)$"
    );
    private static final Pattern CURRENT_SCOPE = Pattern.compile(
            "^(当前目录|当前文件夹)(?:下|中|里|内)?(?:的)?(.*)$"
    );
    private static final Pattern NAMED_SCOPE = Pattern.compile(
            "^(.+?(?:目录|文件夹))(?:下|中|里|内|的)(?:的)?(.+)$"
    );
    private static final Pattern LEADING_ALL = Pattern.compile(
            "^(?:(?:所有|全部|全都)(?:的)?|散着的|同类型(?:的)?|批量|统一)"
    );
    private static final Pattern TRAILING_ALL = Pattern.compile("(?:都|全部|全都|全)$");
    private static final Pattern REFERENCE = Pattern.compile(
            "^(?:它|它们|这个|那个|这些|那些|这个文件|那个文件|这个文件夹|那个文件夹|"
                    + "刚才那个|刚才的文件|刚才的文件夹|第[一二三四五六七八九十0-9]+个(?:文件|文件夹)?|"
                    + "另一个(?:文件|文件夹)?|剩下的(?:文件|文件夹)?)$"
    );
    private static final Pattern MOVE_SPLIT = Pattern.compile(
            "^(.*?)(?:移动到|移到|挪到|转移到|搬到|移动至|移至|挪至|转移至|搬至|放进)(.+)$"
    );
    private static final Pattern RENAME_SPLIT = Pattern.compile(
            "^(.*?)(?:重命名为|重命名成|改名为|改名成|改成|改为)(.+)$"
    );
    private static final Pattern UPLOAD_SPLIT = Pattern.compile(
            "^(.*?)(?:上传到|上传至|传到|传进|放到)(.+)$"
    );

    Optional<Resolution> resolve(String message, String operation) {
        String normalizedOperation = operation == null ? "" : operation.trim().toUpperCase(Locale.ROOT);
        String text = normalize(message);
        if (!SUPPORTED_OPERATIONS.contains(normalizedOperation) || text.isBlank()) {
            return Optional.empty();
        }
        if (!"SHARE".equals(normalizedOperation) && NamePredicateParser.parse(text).isPresent()) {
            return Optional.empty();
        }

        return switch (normalizedOperation) {
            case "SHARE" -> parseSingleSource(text, normalizedOperation, this::shareSource);
            case "DELETE" -> parseSingleSource(text, normalizedOperation, this::deleteSource);
            case "MOVE" -> parseMove(text);
            case "RENAME" -> parseRename(text);
            case "UPLOAD" -> parseUpload(text);
            default -> Optional.empty();
        };
    }

    private Optional<Resolution> parseSingleSource(
            String text,
            String operation,
            SourceExtractor extractor
    ) {
        String sourceSurface = extractor.extract(text);
        if (sourceSurface.isBlank()) {
            return Optional.empty();
        }
        NodeSelector source = parseNodeSelector(sourceSurface);
        return Optional.of(resolution(operation, source, Destination.empty(), "", false));
    }

    private Optional<Resolution> parseMove(String text) {
        Matcher matcher = MOVE_SPLIT.matcher(stripCourtesy(text));
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String sourceSurface = stripObjectPrefix(matcher.group(1));
        String destinationSurface = cleanDestination(matcher.group(2));
        if (sourceSurface.isBlank() || destinationSurface.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(resolution(
                "MOVE",
                parseNodeSelector(sourceSurface),
                destination(destinationSurface),
                "",
                false
        ));
    }

    private Optional<Resolution> parseRename(String text) {
        Matcher matcher = RENAME_SPLIT.matcher(stripCourtesy(text));
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String sourceSurface = stripObjectPrefix(matcher.group(1));
        String newName = cleanName(matcher.group(2));
        if (sourceSurface.isBlank() || newName.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(resolution(
                "RENAME",
                parseNodeSelector(sourceSurface),
                Destination.empty(),
                newName,
                false
        ));
    }

    private Optional<Resolution> parseUpload(String text) {
        Matcher matcher = UPLOAD_SPLIT.matcher(stripCourtesy(text));
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String sourceSurface = stripObjectPrefix(matcher.group(1));
        String destinationSurface = cleanDestination(matcher.group(2));
        if (destinationSurface.isBlank()) {
            return Optional.empty();
        }

        boolean cloudSource = false;
        if (!sourceSurface.isBlank() && !isClientInputReference(sourceSurface)) {
            NodeSelector possibleCloudSource = parseNodeSelector(sourceSurface);
            cloudSource = possibleCloudSource.nameKind() == NameKind.LEXICAL
                    || !"ALL".equals(possibleCloudSource.scopeType());
        }
        return Optional.of(resolution(
                "UPLOAD",
                NodeSelector.empty(),
                destination(destinationSurface),
                "",
                cloudSource
        ));
    }

    private Resolution resolution(
            String operation,
            NodeSelector source,
            Destination destination,
            String newName,
            boolean cloudUploadSource
    ) {
        if (cloudUploadSource) {
            return new Resolution(
                    operation,
                    source,
                    destination,
                    newName,
                    "upload_source_must_be_client_input",
                    "上传内容需要由手机本地文件选择器提供。请先选择本地文件或文件夹，再告诉我上传到哪个云盘目录。",
                    List.of("选择本地文件并上传到根目录", "选择本地文件夹并上传到项目资料")
            );
        }
        if (source.quantifier() == Quantifier.EXPLICIT_ALL && "SHARE".equals(operation)) {
            return new Resolution(
                    operation,
                    source,
                    destination,
                    newName,
                    "batch_share_unsupported",
                    "目前一次只能分享一个真实文件或文件夹。请告诉我要分享的单个名称，或先从候选中选择一个。",
                    List.of("分享合同.pdf", "分享第一个文件")
            );
        }
        if (List.of("SHARE", "DELETE", "MOVE", "RENAME").contains(operation)
                && source.nameKind() == NameKind.GENERIC) {
            return missingSource(operation, source, destination, newName);
        }
        return new Resolution(
                operation,
                source,
                destination,
                newName,
                "",
                "",
                List.of()
        );
    }

    private Resolution missingSource(
            String operation,
            NodeSelector source,
            Destination destination,
            String newName
    ) {
        String question = switch (operation) {
            case "SHARE" -> "请告诉我要分享的单个文件或文件夹名称，例如“分享合同.pdf”。";
            case "DELETE" -> "请告诉我要删除的单个文件或文件夹名称；如需批量删除，请明确目录范围和“所有”对象。";
            case "MOVE" -> "请告诉我要移动的单个文件或文件夹名称；目标目录“" + destination.surface() + "”已保留。";
            case "RENAME" -> "请告诉我要重命名的单个文件或文件夹名称；新名称“" + newName + "”已保留。";
            default -> "请补充要处理的单个文件或文件夹名称。";
        };
        return new Resolution(
                operation,
                source,
                destination,
                newName,
                "source_target_required",
                question,
                switch (operation) {
                    case "MOVE" -> List.of("把合同.pdf移动到" + destination.surface());
                    case "RENAME" -> List.of("把合同.pdf重命名为" + newName);
                    case "DELETE" -> List.of("删除合同.pdf", "删除测试目录下的所有文件");
                    default -> List.of("分享合同.pdf", "分享第一个文件");
                }
        );
    }

    private NodeSelector parseNodeSelector(String rawSurface) {
        String surface = cleanSourceSurface(rawSurface);
        if (REFERENCE.matcher(surface).matches()) {
            return new NodeSelector("", NameKind.REFERENCE, "ANY", "ALL", "", Quantifier.UNSPECIFIED);
        }

        ExplicitName explicitName = explicitName(surface);
        if (explicitName != null) {
            return new NodeSelector(
                    explicitName.name(),
                    NameKind.EXPLICIT,
                    explicitName.resultType(),
                    "ALL",
                    "",
                    Quantifier.SINGLE
            );
        }

        surface = surface.replaceFirst("^(.+?)(?:这个|那个|该)(?:文件夹|目录|文件)$", "$1");
        ScopedSurface scoped = scopedSurface(surface);
        QuantifiedSurface quantified = quantifiedSurface(scoped.objectSurface());
        String objectSurface = quantified.surface();
        boolean explicitAll = quantified.explicitAll();

        String genericType = genericResultType(objectSurface);
        if (!genericType.isBlank() || objectSurface.isBlank()) {
            String resultType = genericType.isBlank() ? "ANY" : genericType;
            Quantifier quantifier = explicitAll
                    ? Quantifier.EXPLICIT_ALL
                    : scoped.scoped() ? Quantifier.IMPLICIT_SET : Quantifier.UNSPECIFIED;
            return new NodeSelector(
                    "",
                    NameKind.GENERIC,
                    resultType,
                    scoped.scopeType(),
                    scoped.folderSurface(),
                    quantifier
            );
        }

        String lexicalName = cleanName(objectSurface);
        if (lexicalName.endsWith("文件夹") && lexicalName.length() > "文件夹".length()) {
            lexicalName = lexicalName.substring(0, lexicalName.length() - "文件夹".length());
        }
        return new NodeSelector(
                lexicalName,
                NameKind.LEXICAL,
                inferResultType(objectSurface),
                scoped.scopeType(),
                scoped.folderSurface(),
                Quantifier.SINGLE
        );
    }

    private ExplicitName explicitName(String surface) {
        Matcher quoted = QUOTED_NAME.matcher(surface);
        if (quoted.matches()) {
            String name = cleanName(quoted.group(1));
            return name.isBlank() ? null : new ExplicitName(name, inferResultType(name));
        }
        Matcher marker = EXPLICIT_NAME_PREFIX.matcher(surface);
        if (!marker.matches()) {
            return null;
        }
        String remainder = marker.group(1);
        String classifier = "";
        Matcher classified = Pattern.compile("^(.+?)的(文件夹|目录|文件)$").matcher(remainder);
        if (classified.matches()) {
            remainder = classified.group(1);
            classifier = classified.group(2);
        }
        String name = cleanName(remainder);
        return name.isBlank()
                ? null
                : new ExplicitName(name, classifier.isBlank() ? inferResultType(name) : inferResultType(classifier));
    }

    private ScopedSurface scopedSurface(String surface) {
        Matcher root = ROOT_SCOPE.matcher(surface);
        if (root.matches() && hasScopeEvidence(surface, root.group(1))) {
            return new ScopedSurface("ROOT", root.group(1), root.group(2), true);
        }
        Matcher current = CURRENT_SCOPE.matcher(surface);
        if (current.matches() && hasScopeEvidence(surface, current.group(1))) {
            return new ScopedSurface("CURRENT", current.group(1), current.group(2), true);
        }
        Matcher named = NAMED_SCOPE.matcher(surface);
        if (named.matches()) {
            return new ScopedSurface("NAMED_FOLDER", named.group(1), named.group(2), true);
        }
        return new ScopedSurface("ALL", "", surface, false);
    }

    private boolean hasScopeEvidence(String surface, String alias) {
        String suffix = surface.substring(Math.min(alias.length(), surface.length()));
        return suffix.isBlank()
                || suffix.matches("^(?:下|中|里|内)(?:的)?.*")
                || suffix.startsWith("的")
                || !genericResultType(suffix).isBlank();
    }

    private QuantifiedSurface quantifiedSurface(String rawSurface) {
        String surface = rawSurface == null ? "" : rawSurface.replaceFirst("^的", "");
        Matcher leading = LEADING_ALL.matcher(surface);
        if (leading.find()) {
            String remainder = surface.substring(leading.end());
            if (!genericResultType(remainder).isBlank()) {
                return new QuantifiedSurface(remainder, true);
            }
        }
        Matcher trailing = TRAILING_ALL.matcher(surface);
        if (trailing.find()) {
            String remainder = surface.substring(0, trailing.start());
            if (!genericResultType(remainder).isBlank()) {
                return new QuantifiedSurface(remainder, true);
            }
        }
        return new QuantifiedSurface(surface, false);
    }

    private Destination destination(String rawSurface) {
        String surface = cleanDestination(rawSurface);
        String scopeType = rootAlias(surface)
                ? "ROOT"
                : currentAlias(surface) ? "CURRENT" : "NAMED_FOLDER";
        return new Destination(scopeType, surface);
    }

    private String shareSource(String text) {
        String value = stripCourtesy(text);
        Matcher leading = Pattern.compile("^(?:分享|共享)(?:一下)?(.+)$").matcher(value);
        if (leading.matches()) {
            return stripObjectPrefix(leading.group(1));
        }
        Matcher generatedForMe = Pattern.compile("^给我(?:生成|创建)(?:一个)?(.+?)(?:的)?分享链接$").matcher(value);
        if (generatedForMe.matches()) {
            return stripObjectPrefix(generatedForMe.group(1));
        }
        Matcher generatedForTarget = Pattern.compile("^给(.+?)(?:生成|创建)(?:一个)?分享链接$").matcher(value);
        if (generatedForTarget.matches()) {
            return stripObjectPrefix(generatedForTarget.group(1));
        }
        Matcher oneLink = Pattern.compile("^给我一个(.+?)(?:的)?分享链接$").matcher(value);
        if (oneLink.matches()) {
            return stripObjectPrefix(oneLink.group(1));
        }
        Matcher makeShareable = Pattern.compile("^(?:把|将)?(.+?)做成(?:可)?分享链接$").matcher(value);
        if (makeShareable.matches()) {
            return stripObjectPrefix(makeShareable.group(1));
        }
        Matcher send = Pattern.compile("^(?:把|将)?(.+?)(?:分享|共享|发)给我$").matcher(value);
        if (send.matches()) {
            return stripObjectPrefix(send.group(1));
        }
        Matcher namedLink = Pattern.compile("^给(名为.+?的(?:文件|文件夹|目录))(?:建个|创建)(?:分享|共享)?链接$").matcher(value);
        if (namedLink.matches()) {
            return stripObjectPrefix(namedLink.group(1));
        }
        Matcher link = Pattern.compile("^(?:生成|创建)(.+?)(?:的)?(?:分享|共享)链接$").matcher(value);
        if (link.matches()) {
            return stripObjectPrefix(link.group(1));
        }
        Matcher simpleLink = Pattern.compile("^创建(.+?)(?:分享|共享)?链接$").matcher(value);
        if (simpleLink.matches()) {
            return stripObjectPrefix(simpleLink.group(1));
        }
        Matcher possessiveLink = Pattern.compile("^(.+?)的(?:分享|共享)链接$").matcher(value);
        if (possessiveLink.matches()) {
            return stripObjectPrefix(possessiveLink.group(1));
        }
        Matcher trailing = Pattern.compile("^(?:把|将)?(.+?)(?:进行)?(?:分享|共享)(?:一下)?$").matcher(value);
        return trailing.matches() ? stripObjectPrefix(trailing.group(1)) : "";
    }

    private String deleteSource(String text) {
        String value = stripCourtesy(text);
        Matcher leading = Pattern.compile("^(?:删除|删掉|删了|删除掉|移除|清理掉)(.+)$").matcher(value);
        if (leading.matches()) {
            return stripObjectPrefix(leading.group(1));
        }
        Matcher recycleBin = Pattern.compile("^(?:把|将)?(.+?)(?:扔到|移入|放进)回收站$").matcher(value);
        if (recycleBin.matches()) {
            return stripObjectPrefix(recycleBin.group(1));
        }
        Matcher trailing = Pattern.compile("^(?:把|将)?(.+?)(?:删除|删掉|删了|删除掉|移除|清理掉)$").matcher(value);
        return trailing.matches() ? stripObjectPrefix(trailing.group(1)) : "";
    }

    private boolean isClientInputReference(String source) {
        String value = cleanSourceSurface(source);
        return value.isBlank()
                || REFERENCE.matcher(value).matches()
                || value.matches("^(?:选择|选中|挑选)(?:本地)?(?:文件|文件夹|内容)(?:后)?$")
                || Set.of(
                "文件", "文件夹", "本地文件", "本地文件夹", "本地资料", "附件",
                "这个文件", "这些文件", "所选文件", "选中的文件", "刚选的文件", "刚才选择的文件",
                "设备里的文件", "手机里的文件"
        ).contains(value);
    }

    private String genericResultType(String value) {
        String normalized = value == null ? "" : value.trim();
        if (GENERIC_FILES.contains(normalized)) {
            return "FILE";
        }
        if (normalized.matches("^(?:图片|照片|视频|音频|音乐|文档|压缩包)(?:类型)?(?:的)?(?:文件|资料)?$")) {
            return "FILE";
        }
        if (normalized.endsWith("文件")
                && GENERIC_FILES.contains(normalized.substring(0, normalized.length() - "文件".length()))) {
            return "FILE";
        }
        if (GENERIC_FOLDERS.contains(normalized)) {
            return "FOLDER";
        }
        if (GENERIC_ANY.contains(normalized)) {
            return "ANY";
        }
        return "";
    }

    private String inferResultType(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith("文件夹") || normalized.endsWith("目录")) {
            return "FOLDER";
        }
        if (normalized.matches(".*\\.[a-z0-9]{1,16}$") || GENERIC_FILES.contains(normalized)) {
            return "FILE";
        }
        return "ANY";
    }

    private boolean rootAlias(String value) {
        return Set.of("根", "根目录", "根文件夹", "云盘根目录", "我的云盘", "顶层目录", "最外层", "/")
                .contains(value);
    }

    private boolean currentAlias(String value) {
        return Set.of("当前目录", "当前文件夹").contains(value);
    }

    private String stripCourtesy(String value) {
        return normalize(value).replaceFirst("^(?:请|麻烦|帮我)+", "");
    }

    private String stripObjectPrefix(String value) {
        return cleanSourceSurface(value)
                .replaceFirst("^(批量|统一)(?:把|将)", "$1")
                .replaceFirst("^(?:把|将)", "");
    }

    private String cleanSourceSurface(String value) {
        return normalize(value).replaceFirst("^(?:请)?(?:把|将)", "");
    }

    private String cleanDestination(String value) {
        return normalize(value)
                .replaceFirst("^(?:到|至|进|入)", "")
                .replaceFirst("(?:吧|中|里|下|内)$", "");
    }

    private String cleanName(String value) {
        return normalize(value)
                .replaceFirst("^[“\"']", "")
                .replaceFirst("[”\"']$", "");
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().replaceAll("[，。！？!?；;]+$", "").replaceAll("\\s+", "");
    }

    enum NameKind {
        NONE,
        EXPLICIT,
        LEXICAL,
        GENERIC,
        REFERENCE
    }

    enum Quantifier {
        SINGLE,
        EXPLICIT_ALL,
        IMPLICIT_SET,
        UNSPECIFIED
    }

    record NodeSelector(
            String name,
            NameKind nameKind,
            String resultType,
            String scopeType,
            String folderSurface,
            Quantifier quantifier
    ) {
        static NodeSelector empty() {
            return new NodeSelector("", NameKind.NONE, "ANY", "ALL", "", Quantifier.UNSPECIFIED);
        }
    }

    record Destination(String scopeType, String surface) {
        static Destination empty() {
            return new Destination("ALL", "");
        }
    }

    record Resolution(
            String operation,
            NodeSelector source,
            Destination destination,
            String newName,
            String clarificationReason,
            String clarificationQuestion,
            List<String> clarificationSuggestions
    ) {
        boolean needsClarification() {
            return clarificationQuestion != null && !clarificationQuestion.isBlank();
        }

        Resolution withSource(NodeSelector replacement) {
            return new Resolution(
                    operation,
                    replacement,
                    destination,
                    newName,
                    clarificationReason,
                    clarificationQuestion,
                    clarificationSuggestions
            );
        }
    }

    private record ScopedSurface(
            String scopeType,
            String folderSurface,
            String objectSurface,
            boolean scoped
    ) {
    }

    private record QuantifiedSurface(String surface, boolean explicitAll) {
    }

    private record ExplicitName(String name, String resultType) {
    }

    @FunctionalInterface
    private interface SourceExtractor {
        String extract(String text);
    }
}
