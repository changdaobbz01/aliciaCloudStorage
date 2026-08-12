package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StorageApiCandidateSearchClientTest {

    @Test
    void searchReturnsNoCandidatesWhenStorageApiFindsNothing() {
        StorageApiCandidateSearchClient client = new StorageApiCandidateSearchClient(
                FakeStorageApiNodeReadClient.withNodeResults(Map.of())
        );

        CandidateBindingResult result = client.search(new CandidateSearchRequest(
                "file_search",
                "search",
                "NODE",
                "project",
                5,
                "Bearer token"
        ));

        assertThat(result.status()).isEqualTo("no_candidates");
        assertThat(result.candidates()).isEmpty();
        assertThat(result.message()).contains("未匹配到候选文件或目录");
    }

    @Test
    void searchReturnsReadyOnlyWhenCandidatesExist() {
        StorageApiCandidateSearchClient client = new StorageApiCandidateSearchClient(
                FakeStorageApiNodeReadClient.withNodeResults(Map.of(
                        "project", List.of(new CandidateItem(1L, null, "project.docx", "FILE", 1L, "docx", "application/docx", ""))
                ))
        );

        CandidateBindingResult result = client.search(new CandidateSearchRequest(
                "file_search",
                "search",
                "NODE",
                "project",
                5,
                "Bearer token"
        ));

        assertThat(result.status()).isEqualTo("search_results_ready");
        assertThat(result.candidates()).hasSize(1);
    }

    @Test
    void nodeSearchFallsBackToSemanticPrefixWhenExactQueryMisses() {
        FakeStorageApiNodeReadClient storageApi = FakeStorageApiNodeReadClient.withNodeResults(Map.of(
                "旅行", List.of(new CandidateItem(7L, null, "旅行-2026.jpeg", "FILE", 1L, "jpeg", "image/jpeg", ""))
        ));
        StorageApiCandidateSearchClient client = new StorageApiCandidateSearchClient(storageApi);

        CandidateBindingResult result = client.search(new CandidateSearchRequest(
                "file_delete",
                "delete",
                "FILE",
                "旅行照片",
                5,
                "Bearer token"
        ));

        assertThat(storageApi.nodeQueries()).extracting(StorageApiNodeQuery::keyword)
                .contains("旅行照片", "旅行");
        assertThat(result.status()).isEqualTo("single_candidate");
        assertThat(result.candidates().getFirst().name()).isEqualTo("旅行-2026.jpeg");
    }

    @Test
    void nodeSearchSendsFileTypeFilterForFileMutations() {
        FakeStorageApiNodeReadClient storageApi = FakeStorageApiNodeReadClient.withNodeResults(Map.of(
                "临时截图", List.of(
                        new CandidateItem(11L, null, "临时截图", "FOLDER", 0L, "", "", ""),
                        new CandidateItem(12L, null, "临时截图.png", "FILE", 1L, "png", "image/png", "")
                )
        ));
        StorageApiCandidateSearchClient client = new StorageApiCandidateSearchClient(storageApi);

        CandidateBindingResult result = client.search(new CandidateSearchRequest(
                "file_delete",
                "delete",
                "FILE",
                "临时截图",
                5,
                "Bearer token"
        ));

        assertThat(storageApi.nodeQueries()).extracting(StorageApiNodeQuery::type)
                .containsOnly("FILE");
        assertThat(result.candidates()).extracting(CandidateItem::type)
                .containsExactly("FILE");
    }

    @Test
    void folderSearchUsesConfiguredFolderDescriptorInsteadOfHardCodedBusinessTerms() {
        StorageApiCandidateSearchClient client = new StorageApiCandidateSearchClient(
                FakeStorageApiNodeReadClient.withFolders(List.of(
                        new CandidateItem(21L, null, "资料", "FOLDER", 0L, "", "", ""),
                        new CandidateItem(22L, null, "设计素材", "FOLDER", 0L, "", "", "")
                ))
        );

        CandidateBindingResult result = client.search(new CandidateSearchRequest(
                "file_upload",
                "upload_target",
                "FOLDER",
                "资料文件夹",
                5,
                "Bearer token"
        ));

        assertThat(result.status()).isEqualTo("single_candidate");
        assertThat(result.candidates().getFirst().name()).isEqualTo("资料");
    }

    @Test
    void exactFolderNameWinsOverFuzzyAndNestedMatches() {
        StorageApiCandidateSearchClient client = new StorageApiCandidateSearchClient(
                FakeStorageApiNodeReadClient.withFolders(List.of(
                        new CandidateItem(23L, null, "测试目录", "FOLDER", 0L, "", "", ""),
                        new CandidateItem(24L, 23L, "测试目录2", "FOLDER", 0L, "", "", ""),
                        new CandidateItem(25L, null, "旧测试目录", "FOLDER", 0L, "", "", "")
                ))
        );

        CandidateBindingResult result = client.search(new CandidateSearchRequest(
                "file_upload",
                "upload_target",
                "FOLDER",
                "测试目录",
                5,
                "Bearer token"
        ));

        assertThat(result.status()).isEqualTo("single_candidate");
        assertThat(result.candidates()).extracting(CandidateItem::name).containsExactly("测试目录");
    }

    @Test
    void folderSearchDoesNotSplitArbitraryUserDefinedNounsByDefault() {
        StorageApiCandidateSearchClient client = new StorageApiCandidateSearchClient(
                FakeStorageApiNodeReadClient.withFolders(List.of(
                        new CandidateItem(21L, null, "资料", "FOLDER", 0L, "", "", ""),
                        new CandidateItem(22L, null, "项目", "FOLDER", 0L, "", "", "")
                ))
        );

        CandidateBindingResult result = client.search(new CandidateSearchRequest(
                "file_upload",
                "upload_target",
                "FOLDER",
                "项目资料",
                5,
                "Bearer token"
        ));

        assertThat(result.status()).isEqualTo("no_candidates");
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void listsRootFoldersWithoutTurningTheSentenceIntoAKeyword() {
        FakeStorageApiNodeReadClient storageApi = FakeStorageApiNodeReadClient.withNodeResults(Map.of(
                "", List.of(
                        new CandidateItem(31L, null, "项目资料", "FOLDER", 0L, "", "", ""),
                        new CandidateItem(32L, null, "说明.txt", "FILE", 10L, "txt", "text/plain", "")
                )
        ));
        StorageApiCandidateSearchClient client = new StorageApiCandidateSearchClient(storageApi);

        CandidateBindingResult result = client.search(new CandidateSearchRequest(
                "file_search",
                "search",
                "FOLDER",
                "directory_scope",
                "",
                "directory_list",
                "root",
                "",
                99L,
                "根目录/其他位置",
                20,
                "Bearer token"
        ));

        assertThat(result.status()).isEqualTo("search_results_ready");
        assertThat(result.query()).isEqualTo("根目录");
        assertThat(result.candidates()).extracting(CandidateItem::name).containsExactly("项目资料");
        assertThat(storageApi.nodeQueries()).singleElement().satisfies(query -> {
            assertThat(query.parentId()).isNull();
            assertThat(query.recursive()).isFalse();
            assertThat(query.keyword()).isBlank();
            assertThat(query.type()).isEqualTo("FOLDER");
        });
    }

    @Test
    void listsFilesFromTheClientCurrentFolder() {
        FakeStorageApiNodeReadClient storageApi = FakeStorageApiNodeReadClient.withNodeResults(Map.of(
                "", List.of(new CandidateItem(41L, 77L, "合同.pdf", "FILE", 10L, "pdf", "application/pdf", ""))
        ));
        StorageApiCandidateSearchClient client = new StorageApiCandidateSearchClient(storageApi);

        CandidateBindingResult result = client.search(new CandidateSearchRequest(
                "file_search",
                "search",
                "FILE",
                "directory_scope",
                "",
                "directory_list",
                "current",
                "",
                77L,
                "根目录/合同",
                20,
                "Bearer token"
        ));

        assertThat(result.candidates()).extracting(CandidateItem::name).containsExactly("合同.pdf");
        assertThat(storageApi.nodeQueries()).singleElement().satisfies(query -> {
            assertThat(query.parentId()).isEqualTo(77L);
            assertThat(query.recursive()).isFalse();
            assertThat(query.keyword()).isBlank();
            assertThat(query.type()).isEqualTo("FILE");
        });
    }

    private static class FakeStorageApiNodeReadClient extends StorageApiNodeReadClient {
        private final Map<String, List<CandidateItem>> candidatesByQuery;
        private final List<CandidateItem> folders;
        private final List<StorageApiNodeQuery> nodeQueries = new ArrayList<>();

        private FakeStorageApiNodeReadClient(
                Map<String, List<CandidateItem>> candidatesByQuery,
                List<CandidateItem> folders
        ) {
            super(new ObjectMapper(), "https://storage.example", 1);
            this.candidatesByQuery = new LinkedHashMap<>(candidatesByQuery);
            this.folders = List.copyOf(folders);
        }

        private static FakeStorageApiNodeReadClient withNodeResults(Map<String, List<CandidateItem>> candidatesByQuery) {
            return new FakeStorageApiNodeReadClient(candidatesByQuery, List.of());
        }

        private static FakeStorageApiNodeReadClient withFolders(List<CandidateItem> folders) {
            return new FakeStorageApiNodeReadClient(Map.of(), folders);
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public StorageApiNodePage searchNodes(StorageApiNodeQuery query, String authorizationHeader) {
            nodeQueries.add(query);
            List<CandidateItem> candidates = candidatesByQuery.getOrDefault(query.keyword(), List.of()).stream()
                    .filter(candidate -> query.type().isBlank() || query.type().equalsIgnoreCase(candidate.type()))
                    .toList();
            return new StorageApiNodePage(candidates, candidates.size(), 1, 5, candidates.isEmpty() ? 0 : 1);
        }

        @Override
        public List<CandidateItem> fetchAllFolders(String authorizationHeader) {
            return folders;
        }

        @Override
        public Map<Long, CandidateItem> safeFolderMap(String authorizationHeader) {
            return Map.of();
        }

        @Override
        public List<CandidateItem> enrichWithPaths(List<CandidateItem> candidates, Map<Long, CandidateItem> folderById) {
            return candidates;
        }

        private List<StorageApiNodeQuery> nodeQueries() {
            return List.copyOf(nodeQueries);
        }
    }
}
