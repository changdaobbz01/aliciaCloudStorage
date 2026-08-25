package com.alicia.cloudstorage.rag.assistant;

import com.alicia.cloudstorage.rag.health.RagDependencyOperationSnapshot;
import com.alicia.cloudstorage.rag.health.RagDependencyTelemetry;
import com.alicia.cloudstorage.rag.health.StorageApiHealthProbe;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class StorageApiNodeReadClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final int timeoutSeconds;
    private final RagDependencyTelemetry telemetry;

    @Autowired
    public StorageApiNodeReadClient(
            ObjectMapper objectMapper,
            @Value("${alicia.rag.storage-api.base-url:}") String baseUrl,
            @Value("${alicia.rag.storage-api.timeout-seconds:4}") int timeoutSeconds,
            RagDependencyTelemetry telemetry
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                .build();
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
        this.telemetry = telemetry;
    }

    protected StorageApiNodeReadClient(
            ObjectMapper objectMapper,
            String baseUrl,
            int timeoutSeconds
    ) {
        this(objectMapper, baseUrl, timeoutSeconds, new RagDependencyTelemetry());
    }

    public boolean isConfigured() {
        return !baseUrl.isBlank();
    }

    public StorageApiHealthProbe checkHealth() {
        if (!isConfigured()) {
            return StorageApiHealthProbe.notConfigured();
        }

        URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/api/health")
                .build()
                .encode()
                .toUri();
        JsonNode root = send("storage.health", uri, "");
        if ("ok".equalsIgnoreCase(root.path("status").asText(""))) {
            return StorageApiHealthProbe.available(root.path("service").asText(""));
        }
        return StorageApiHealthProbe.unavailable(root.path("service").asText(""));
    }

    public List<RagDependencyOperationSnapshot> dependencyOperations() {
        return telemetry.snapshots("storage.");
    }

    public StorageApiNodePage searchNodes(StorageApiNodeQuery query, String authorizationHeader) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + "/api/storage/nodes")
                .queryParam("recursive", query.recursive())
                .queryParam("page", query.page())
                .queryParam("size", query.size())
                .queryParam("sortBy", query.sortBy())
                .queryParam("sortDirection", query.sortDirection());

        if (query.parentId() != null) {
            builder.queryParam("parentId", query.parentId());
        }
        if (!query.keyword().isBlank()) {
            builder.queryParam("keyword", query.keyword());
        }
        if (!query.type().isBlank()) {
            builder.queryParam("type", query.type());
        }
        if (!query.category().isBlank()) {
            builder.queryParam("category", query.category());
        }

        JsonNode root = send("storage.nodes", builder.build().encode().toUri(), authorizationHeader);
        List<CandidateItem> items = parseNodeItems(root.path("items"));
        return new StorageApiNodePage(
                items,
                root.path("totalItems").asLong(items.size()),
                root.path("page").asInt(query.page()),
                root.path("size").asInt(query.size()),
                root.path("totalPages").asInt(0)
        );
    }

    public List<CandidateItem> fetchAllFolders(String authorizationHeader) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/api/storage/folders")
                .build()
                .encode()
                .toUri();
        return parseNodeItems(send("storage.folders", uri, authorizationHeader));
    }

    public StorageApiScopedTrashPreview previewScopedTrash(
            Long sourceParentId,
            boolean root,
            List<String> nodeTypes,
            String authorizationHeader
    ) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(baseUrl + "/api/storage/nodes/batch/trash/scoped/preview")
                .queryParam("root", root);
        if (sourceParentId != null) {
            builder.queryParam("sourceParentId", sourceParentId);
        }
        nodeTypes.forEach(type -> builder.queryParam("nodeTypes", type));
        JsonNode response = send("storage.trash.preview", builder.build().encode().toUri(), authorizationHeader);
        return new StorageApiScopedTrashPreview(
                parseNodeItems(response.path("items")),
                response.path("selectedFileCount").asInt(0),
                response.path("selectedFolderCount").asInt(0),
                response.path("descendantCount").asInt(0),
                response.path("impactCount").asInt(0),
                response.path("scopeFingerprint").asText(""),
                response.path("impactFingerprint").asText(""),
                response.path("executable").asBoolean(false),
                response.path("message").asText("")
        );
    }

    public Map<Long, CandidateItem> safeFolderMap(String authorizationHeader) {
        try {
            return folderMap(fetchAllFolders(authorizationHeader));
        } catch (RuntimeException exception) {
            return Map.of();
        }
    }

    public Map<Long, CandidateItem> folderMap(List<CandidateItem> folders) {
        Map<Long, CandidateItem> folderById = new LinkedHashMap<>();
        folders.stream()
                .filter(folder -> folder.nodeId() != null)
                .forEach(folder -> folderById.put(folder.nodeId(), folder));
        return Map.copyOf(folderById);
    }

    public List<CandidateItem> enrichWithPaths(List<CandidateItem> candidates, Map<Long, CandidateItem> folderById) {
        return candidates.stream()
                .map(candidate -> {
                    CandidatePath path = buildCandidatePath(candidate, folderById);
                    return candidate.withPath(path.path(), path.breadcrumbs());
                })
                .toList();
    }

    private JsonNode send(String operation, URI uri, String authorizationHeader) {
        return telemetry.observe(operation, () -> sendUnobserved(uri, authorizationHeader));
    }

    private JsonNode sendUnobserved(URI uri, String authorizationHeader) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Accept", "application/json")
                    .GET();
            String authorization = authorizationHeader == null ? "" : authorizationHeader.trim();
            if (!authorization.isBlank()) {
                requestBuilder.header("Authorization", authorization);
            }
            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .join();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Storage API node read failed with status " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (Exception exception) {
            throw new IllegalStateException("Storage API node read failed.", exception);
        }
    }

    private List<CandidateItem> parseNodeItems(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<CandidateItem> candidates = new ArrayList<>();
        for (JsonNode item : node) {
            candidates.add(new CandidateItem(
                    longValue(item.path("id")),
                    longValue(item.path("parentId")),
                    item.path("name").asText(""),
                    item.path("type").asText(""),
                    longValue(item.path("size")),
                    item.path("extension").asText(""),
                    item.path("mimeType").asText(""),
                    item.path("updatedAt").asText("")
            ));
        }
        return List.copyOf(candidates);
    }

    private CandidatePath buildCandidatePath(CandidateItem candidate, Map<Long, CandidateItem> folderById) {
        LinkedList<CandidateBreadcrumb> breadcrumbs = new LinkedList<>();
        Set<Long> visited = new java.util.HashSet<>();
        Long cursor = candidate.parentId();

        while (cursor != null && visited.add(cursor)) {
            CandidateItem folder = folderById.get(cursor);
            if (folder == null) {
                break;
            }
            breadcrumbs.addFirst(new CandidateBreadcrumb(folder.nodeId(), folder.name()));
            cursor = folder.parentId();
        }

        if (candidate.nodeId() != null) {
            breadcrumbs.addLast(new CandidateBreadcrumb(candidate.nodeId(), candidate.name()));
        }

        String path = breadcrumbs.isEmpty()
                ? "/" + candidate.name()
                : breadcrumbs.stream()
                .map(CandidateBreadcrumb::name)
                .filter(name -> name != null && !name.isBlank())
                .collect(java.util.stream.Collectors.joining("/", "/", ""));
        return new CandidatePath(path, List.copyOf(breadcrumbs));
    }

    private Long longValue(JsonNode node) {
        return node.isNumber() ? node.asLong() : null;
    }

    private record CandidatePath(
            String path,
            List<CandidateBreadcrumb> breadcrumbs
    ) {
    }
}
