package com.alicia.cloudstorage.rag.assistant;

import com.alicia.cloudstorage.rag.assistant.SemanticExampleRetriever.SemanticExample;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@Component
final class PromptSemanticExampleRetriever {
    enum Mode { LEGACY, HYBRID }

    private static final int RANK_WINDOW = 12;
    private static final int RRF_OFFSET = 10;
    private static final int INDEX_BATCH_SIZE = 32;
    private static final int MAX_QUERY_CODE_POINTS = 512;
    private static final double MIN_COSINE = 0.40;
    private static final Duration INDEX_BATCH_TIMEOUT = Duration.ofSeconds(15);
    private static final long EMBEDDING_RETRY_NANOS = Duration.ofSeconds(5).toNanos();

    private final CorpusSemanticExampleRetriever legacy;
    private final List<SemanticExample> corpus;
    private final SemanticKeywordIndex keywords;
    private final OllamaEmbeddingClient embeddings;
    private final Duration queryTimeout;
    private final ScheduledExecutorService warmup;
    private final Semaphore queries = new Semaphore(2);
    private volatile List<float[]> index = List.of();
    private volatile long retryQueryAtNanos;

    PromptSemanticExampleRetriever(
            CorpusSemanticExampleRetriever legacy,
            ObjectMapper json,
            @Value("${alicia.rag.example-retrieval.mode:LEGACY}") Mode mode,
            @Value("${alicia.rag.example-retrieval.embedding-url:}") String url,
            @Value("${alicia.rag.example-retrieval.embedding-model:granite-embedding:278m}") String model,
            @Value("${alicia.rag.example-retrieval.embedding-timeout-ms:350}") long timeoutMillis
    ) {
        this.legacy = legacy;
        this.corpus = mode == Mode.HYBRID ? legacy.catalog() : List.of();
        this.keywords = mode == Mode.HYBRID ? new SemanticKeywordIndex(corpus) : null;
        this.embeddings = mode == Mode.HYBRID && !url.isBlank()
                ? new OllamaEmbeddingClient(url.strip(), model.strip(), json) : null;
        this.queryTimeout = Duration.ofMillis(Math.clamp(timeoutMillis, 50, 1_500));
        this.warmup = embeddings == null ? null : Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon(true).name("rag-example-embedding-index").factory());
    }

    @EventListener(ApplicationReadyEvent.class)
    void startIndexing() {
        if (warmup != null) {
            warmup.scheduleWithFixedDelay(this::buildIndex, 0, 30, TimeUnit.SECONDS);
        }
    }

    @PreDestroy
    void close() {
        if (warmup != null) {
            warmup.shutdownNow();
        }
    }

    List<SemanticExample> retrieve(String message, int limit) {
        int safeLimit = Math.clamp(limit, 0, 8);
        if (safeLimit == 0 || message == null || message.isBlank()) {
            return List.of();
        }
        if (keywords == null || message.codePointCount(0, message.length()) > MAX_QUERY_CODE_POINTS) {
            return legacy.retrieve(message, safeLimit);
        }

        List<SemanticExample> literal = legacy.retrieve(message, 8);
        Map<String, Double> scores = new HashMap<>();
        for (int rank = 0; rank < literal.size(); rank++) {
            SemanticExample example = literal.get(rank);
            // Keep a literal exact match ahead of paraphrases.
            double weight = example.score() == 1.0 ? 4.0 : 1.0;
            addRank(scores, example, rank, weight);
        }
        addRanking(scores, keywords.rank(message, RANK_WINDOW), 1.0);
        addRanking(scores, semanticRanking(message), 2.0);
        return corpus.stream()
                .filter(example -> scores.containsKey(example.id()))
                .sorted(Comparator.<SemanticExample>comparingDouble(example -> scores.get(example.id())).reversed()
                        .thenComparing(SemanticExample::id))
                .limit(safeLimit)
                .map(example -> new SemanticExample(example.id(), example.utterance(), example.intentId(), example.operation(),
                        example.entities(), scores.get(example.id()) * (RRF_OFFSET + 1) / 7))
                .toList();
    }

    // Publish a whole index at once. Failed batches leave the prompt on lexical retrieval until the next retry.
    boolean buildIndex() {
        if (!index.isEmpty()) {
            return true;
        }
        if (embeddings == null || corpus.isEmpty()) {
            return false;
        }
        List<float[]> vectors = new ArrayList<>(corpus.size());
        int dimensions = 0;
        for (int start = 0; start < corpus.size(); start += INDEX_BATCH_SIZE) {
            List<String> inputs = corpus.subList(start, Math.min(start + INDEX_BATCH_SIZE, corpus.size()))
                    .stream().map(SemanticExample::utterance).toList();
            List<float[]> batch = embeddings.encode(inputs, INDEX_BATCH_TIMEOUT);
            if (batch.isEmpty() || (dimensions != 0 && dimensions != batch.getFirst().length)) {
                return false;
            }
            dimensions = batch.getFirst().length;
            vectors.addAll(batch);
        }
        index = List.copyOf(vectors);
        return true;
    }

    private List<Integer> semanticRanking(String text) {
        long started = RecognitionTrace.tick();
        List<float[]> vectors = index;
        if (embeddings == null) {
            RecognitionTrace.mark(RecognitionTrace.Stage.EMBEDDING, RecognitionTrace.Outcome.DISABLED, started);
            return List.of();
        }
        long retryAt = retryQueryAtNanos;
        if (vectors.isEmpty() || (retryAt != 0 && System.nanoTime() - retryAt < 0) || !queries.tryAcquire()) {
            RecognitionTrace.mark(RecognitionTrace.Stage.EMBEDDING, RecognitionTrace.Outcome.UNAVAILABLE, started);
            return List.of();
        }
        try {
            List<float[]> encoded = embeddings.encode(List.of(text), queryTimeout);
            if (encoded.isEmpty() || encoded.getFirst().length != vectors.getFirst().length) {
                retryQueryAtNanos = System.nanoTime() + EMBEDDING_RETRY_NANOS;
                RecognitionTrace.mark(RecognitionTrace.Stage.EMBEDDING, RecognitionTrace.Outcome.UNAVAILABLE, started);
                return List.of();
            }
            float[] query = encoded.getFirst();
            List<Integer> matches = IntStream.range(0, vectors.size())
                    .mapToObj(position -> new VectorMatch(position, cosine(query, vectors.get(position))))
                    .filter(match -> match.cosine() >= MIN_COSINE)
                    .sorted(Comparator.comparingDouble(VectorMatch::cosine).reversed()
                            .thenComparingInt(VectorMatch::position))
                    .limit(RANK_WINDOW)
                    .map(VectorMatch::position)
                    .toList();
            RecognitionTrace.mark(RecognitionTrace.Stage.EMBEDDING,
                    matches.isEmpty() ? RecognitionTrace.Outcome.NO_MATCH : RecognitionTrace.Outcome.SUCCESS, started);
            return matches;
        } finally {
            queries.release();
        }
    }

    private void addRanking(Map<String, Double> scores, List<Integer> ranking, double weight) {
        for (int rank = 0; rank < ranking.size(); rank++) {
            addRank(scores, corpus.get(ranking.get(rank)), rank, weight);
        }
    }

    private static void addRank(Map<String, Double> scores, SemanticExample example, int rank, double weight) {
        scores.merge(example.id(), weight / (RRF_OFFSET + rank + 1), Double::sum);
    }

    private static double cosine(float[] query, float[] example) {
        double score = 0;
        for (int index = 0; index < query.length; index++) {
            score += (double) query[index] * example[index];
        }
        return score;
    }

    private record VectorMatch(int position, double cosine) {}
}
