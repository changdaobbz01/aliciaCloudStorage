package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StorageApiCollectionPreviewClientTest {

    @Test
    void scopedTrashPreviewUsesBackendImpactSnapshotInsteadOfGenericNodeScan() {
        FakeStorageApiNodeReadClient storageApi = new FakeStorageApiNodeReadClient();
        StorageApiCollectionPreviewClient client = new StorageApiCollectionPreviewClient(storageApi);

        CollectionPreviewResult result = client.preview(new CollectionPreviewRequest(
                "collection.trash_scoped",
                Map.of(
                        "selectorVersion", "source_selector_v2",
                        "root", true,
                        "sourcePath", "/",
                        "directChildren", true,
                        "recursive", false,
                        "nodeTypes", List.of("FILE", "FOLDER"),
                        "includeFolders", true
                ),
                500,
                500,
                "Bearer token"
        ));

        assertThat(result.status()).isEqualTo("preview_ready");
        assertThat(result.exactCount()).isTrue();
        assertThat(result.candidates()).extracting(CandidateItem::nodeId).containsExactly(11L, 12L);
        assertThat(result.filter())
                .containsEntry("scopeFingerprint", "scope-hash")
                .containsEntry("impactFingerprint", "impact-hash")
                .containsEntry("selectedFileCount", 1)
                .containsEntry("selectedFolderCount", 1)
                .containsEntry("descendantCount", 3)
                .containsEntry("expectedImpactCount", 5)
                .containsEntry("sourceRoot", true);
        assertThat(storageApi.requestedNodeTypes).containsExactly("FILE", "FOLDER");
        assertThat(storageApi.genericSearchCalls).isZero();
        assertThat(storageApi.folderMapCalls).isZero();
        assertThat(result.candidates()).extracting(CandidateItem::path)
                .containsExactly("/说明.txt", "/资料");
    }

    private static class FakeStorageApiNodeReadClient extends StorageApiNodeReadClient {
        private int genericSearchCalls;
        private int folderMapCalls;
        private List<String> requestedNodeTypes = List.of();

        private FakeStorageApiNodeReadClient() {
            super(new ObjectMapper(), "https://storage.example", 1);
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public StorageApiNodePage searchNodes(StorageApiNodeQuery query, String authorizationHeader) {
            genericSearchCalls++;
            return new StorageApiNodePage(List.of(), 0, 1, 100, 0);
        }

        @Override
        public StorageApiScopedTrashPreview previewScopedTrash(
                Long sourceParentId,
                boolean root,
                List<String> nodeTypes,
                String authorizationHeader
        ) {
            requestedNodeTypes = List.copyOf(nodeTypes);
            return new StorageApiScopedTrashPreview(
                    List.of(
                            new CandidateItem(11L, null, "说明.txt", "FILE", 10L, "txt", "text/plain", ""),
                            new CandidateItem(12L, null, "资料", "FOLDER", 0L, "", "", "")
                    ),
                    1,
                    1,
                    3,
                    5,
                    "scope-hash",
                    "impact-hash",
                    true,
                    "已生成完整影响预览。"
            );
        }

        @Override
        public Map<Long, CandidateItem> safeFolderMap(String authorizationHeader) {
            folderMapCalls++;
            return Map.of();
        }

        @Override
        public List<CandidateItem> enrichWithPaths(List<CandidateItem> candidates, Map<Long, CandidateItem> folderById) {
            return candidates;
        }
    }
}
