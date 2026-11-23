package com.alicia.cloudstorage.rag.assistant;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

final class CandidateExpressionRedactor {

    static final int MAX_LENGTH = 400;

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final List<Rule> RULES = List.of(
            rule("\\b(?:authorization|proxy-authorization|cookie|set-cookie)[ \\t]*[:=][ \\t]*[^\\r\\n。！？]+", "凭据"),
            rule("(?:api[ _-]?key|access[ _-]?token|refresh[ _-]?token|password|secret|密码|口令)"
                    + "\\s*[:=]\\s*[^\\s，。；;]+", "凭据"),
            rule("\\b(?:Bearer|Basic)\\s+[A-Za-z0-9._~+/-]+=*|\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"
                    + "|\\bsk-[A-Za-z0-9_-]{10,}", "凭据"),
            rule("\\b[a-z][a-z0-9+.-]*://[^\\s，。；、！？\"'）)]+|\\bwww\\.[^\\s，。；、！？\"'）)]+", "链接"),
            rule("[\\p{L}0-9._%+-]+@[\\p{L}0-9.-]+\\.[a-z]{2,}", "邮箱"),
            rule("(?<![0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![0-9])", "地址"),
            rule("(?<![0-9])(?:\\+?86[ -]?)?1[3-9](?:[ -]?[0-9]){9}(?![0-9])", "手机号"),
            rule("\\b[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\b"
                    + "|\\b(?:node|file|folder|user|owner|plan|execution|conversation)[ _-]?id\\s*[:=]\\s*[A-Za-z0-9_-]+"
                    + "|(?<![0-9])[0-9]{5,}(?![0-9])", "编号"),
            rule("\"[^\"\\r\\n]*\"|'[^'\\r\\n]*'|“[^”\\r\\n]*”|‘[^’\\r\\n]*’|「[^」\\r\\n]*」|《[^》\\r\\n]*》", "名称"),
            rule("(?:[A-Za-z]:[\\\\/]|\\\\\\\\|(?:\\.{0,2}/))[^\\s，。；、！？\"')）【】]*"
                    + "|[\\p{L}0-9_.-]+[\\\\/][^\\s，。；、！？\"')）【】]+", "路径"),
            rule("[\\p{L}0-9_+.-]+\\.[A-Za-z0-9]{1,10}(?![A-Za-z0-9])", "文件"),
            rule("\\b[A-Za-z0-9_-]{24,}\\b", "标识")
    );

    private CandidateExpressionRedactor() {}

    static String preview(String expression) {
        if (expression == null || expression.isBlank() || expression.length() > MAX_LENGTH) {
            throw invalid();
        }
        String cleaned = Normalizer.normalize(expression, Normalizer.Form.NFKC).replaceAll("\\p{Cf}+", "");
        for (Rule rule : RULES) {
            cleaned = rule.pattern().matcher(cleaned).replaceAll(rule.replacement());
        }
        cleaned = cleaned.replaceAll("[\\p{Cc}]+", " ").replaceAll("(?U)\\s+", " ").strip();
        String meaningful = cleaned.replaceAll("【[^】]*】", "");
        if (cleaned.length() > MAX_LENGTH || meaningful.codePoints().noneMatch(Character::isLetter)) {
            throw invalid();
        }
        return cleaned;
    }

    private static Rule rule(String regex, String replacement) {
        return new Rule(Pattern.compile(regex, FLAGS), "【" + replacement + "】");
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "invalid_candidate_expression");
    }

    private record Rule(Pattern pattern, String replacement) {}
}
