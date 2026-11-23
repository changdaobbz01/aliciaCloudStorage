package com.alicia.cloudstorage.rag.assistant;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class SemanticKeywordIndex {
    private static final Pattern WORDS = Pattern.compile("\\p{IsHan}+|[\\p{L}\\p{N}&&[^\\p{IsHan}]]+");
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    private final Map<String, List<Posting>> postings = new HashMap<>();
    private final int documentCount;

    SemanticKeywordIndex(List<SemanticExampleRetriever.SemanticExample> corpus) {
        documentCount = corpus.size();
        List<List<String>> documents = corpus.stream().map(example -> terms(example.utterance())).toList();
        double averageLength = Math.max(1, documents.stream().mapToInt(List::size).average().orElse(1));
        for (int index = 0; index < documentCount; index++) {
            List<String> document = documents.get(index);
            double lengthWeight = K1 * (1 - B + B * document.size() / averageLength);
            Map<String, Integer> counts = new HashMap<>();
            document.forEach(term -> counts.merge(term, 1, Integer::sum));
            for (var entry : counts.entrySet()) {
                double frequency = entry.getValue();
                postings.computeIfAbsent(entry.getKey(), term -> new ArrayList<>())
                        .add(new Posting(index, frequency * (K1 + 1) / (frequency + lengthWeight)));
            }
        }
    }

    List<Integer> rank(String text, int limit) {
        Map<Integer, Double> scores = new HashMap<>();
        for (String term : terms(text).stream().distinct().sorted().toList()) {
            List<Posting> matches = postings.get(term);
            if (matches == null) {
                continue;
            }
            double idf = Math.log1p((documentCount - matches.size() + 0.5) / (matches.size() + 0.5));
            boolean singleHan = Character.UnicodeScript.of(term.codePointAt(0)) == Character.UnicodeScript.HAN
                    && term.length() == Character.charCount(term.codePointAt(0));
            double weight = singleHan ? 0.4 : 1;
            for (Posting match : matches) {
                scores.merge(match.index(), weight * idf * match.frequency(), Double::sum);
            }
        }
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static List<String> terms(String text) {
        List<String> tokens = new ArrayList<>();
        var matcher = WORDS.matcher(Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String term = matcher.group();
            if (Character.UnicodeScript.of(term.codePointAt(0)) == Character.UnicodeScript.HAN) {
                int[] points = term.codePoints().toArray();
                for (int index = 0; index < points.length; index++) {
                    tokens.add(new String(points, index, 1));
                    if (index + 1 < points.length) {
                        tokens.add(new String(points, index, 2));
                    }
                }
            } else {
                tokens.add(term);
            }
        }
        return tokens;
    }

    private record Posting(int index, double frequency) {}
}
