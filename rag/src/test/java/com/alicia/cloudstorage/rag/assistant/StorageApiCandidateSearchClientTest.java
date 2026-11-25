package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StorageApiCandidateSearchClientTest {

    @ParameterizedTest
    @CsvSource({"false,single_candidate", "true,multiple_candidates"})
    void renamePrefersLiteralMatchesAndKeepsIdenticalNamesInDifferentFolders(boolean duplicate, String status) {
        String literal = "「流转  底册」.XLSX";
        var exact = new CandidateItem(21L, 10L, literal, "FILE", 0L, "", "", "");
        var neighbor = new CandidateItem(22L, 10L, "「流转 底册」.XLSX", "FILE", 0L, "", "", "");
        List<CandidateItem> matches = new ArrayList<>(List.of(neighbor, exact));
        if (duplicate) {
            matches.add(new CandidateItem(23L, 11L, literal, "FILE", 0L, "", "", ""));
        }
        var storage = FakeStorageApiNodeReadClient.withNodeResults(Map.of(literal, matches));
        var client = new StorageApiCandidateSearchClient(storage);
        var request = new CandidateSearchRequest("file_rename", "rename", "FILE", "target_name",
                literal, "name_search", "all", "", null, "/", 5, "Bearer test");

        var result = client.search(request);

        assertThat(result.status()).isEqualTo(status);
        assertThat(result.candidates()).extracting(CandidateItem::nodeId)
                .containsExactlyElementsOf(duplicate ? List.of(21L, 23L) : List.of(21L));
    }

    @Test
    void searchesForTheFullNameClueOnlyInTheNamedFolder() {
        var storage = new FakeStorageApiNodeReadClient(Map.of("年 结", List.of(
                new CandidateItem(12L, 9L, "第2批年 结.csv", "FILE", 0L, "", "", ""),
                new CandidateItem(13L, 9L, "第3批年结.csv", "FILE", 0L, "", "", ""),
                new CandidateItem(14L, 8L, "其他目录年 结.csv", "FILE", 0L, "", "", "")
        )), Map.of(), List.of(new CandidateItem(9L, null, "结算备档", "FOLDER", 0L, "", "", "")));
        var client = new StorageApiCandidateSearchClient(storage);
        var request = new CandidateSearchRequest("file_search", "search", "FILE", "target_name",
                "年 结", "name_contains", "named_folder", "结算备档", null, "/", 5, "Bearer test");

        var result = client.search(request);

        assertThat(result.candidates()).extracting(CandidateItem::nodeId).containsExactly(12L);
        assertThat(storage.nodeQueries()).singleElement().satisfies(query -> {
            assertThat(query.parentId()).isEqualTo(9L);
            assertThat(query.recursive()).isFalse();
            assertThat(query.keyword()).isEqualTo("年 结");
        });
    }

    @ParameterizedTest
    @CsvSource({"root,,false", "current,8,false", "all,,true"})
    void sendsTheActualRequestedScopeForNameContains(String scope, Long parentId, boolean recursive) {
        var storage = FakeStorageApiNodeReadClient.withNodeResults(Map.of());
        var client = new StorageApiCandidateSearchClient(storage);

        client.search(new CandidateSearchRequest("file_search", "search", "FOLDER", "target_folder",
                "会议", "name_contains", scope, "", 8L, "/本周", 5, "Bearer test"));

        assertThat(storage.nodeQueries()).singleElement().satisfies(query -> {
            assertThat(query.parentId()).isEqualTo(parentId);
            assertThat(query.recursive()).isEqualTo(recursive);
            assertThat(query.keyword()).isEqualTo("会议");
            assertThat(query.type()).isEqualTo("FOLDER");
        });
    }

    @Test
    void asksForAPathWhenTwoFoldersHaveTheRequestedName() {
        var storage = FakeStorageApiNodeReadClient.withFolders(List.of(
                new CandidateItem(7L, 3L, "合订材料", "FOLDER", 0L, "", "", ""),
                new CandidateItem(8L, 4L, "合订材料", "FOLDER", 0L, "", "", "")
        ));
        var client = new StorageApiCandidateSearchClient(storage);

        var result = client.search(new CandidateSearchRequest("file_search", "search", "FILE", "target_name",
                "附录", "name_contains", "named_folder", "合订材料", null, "/", 5, "Bearer test"));

        assertThat(result.status()).isEqualTo("ambiguous_folder_scope");
        assertThat(storage.nodeQueries()).isEmpty();
    }

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
    void fileLocationSearchFallsBackFromReferentialClassifierToActualName() {
        FakeStorageApiNodeReadClient storageApi = FakeStorageApiNodeReadClient.withNodeResults(Map.of(
                "测试图片", List.of(
                        new CandidateItem(8L, null, "测试图片", "FILE", 1L, "", "image/png", ""),
                        new CandidateItem(11L, null, "新名称：测试文件001", "FILE", 1L, "", "image/png", "")
                )
        ));
        StorageApiCandidateSearchClient client = new StorageApiCandidateSearchClient(storageApi);

        CandidateBindingResult result = client.search(new CandidateSearchRequest(
                "file_search",
                "search",
                "FILE",
                "target_name",
                "测试图片这个文件",
                5,
                "Bearer token"
        ));

        assertThat(storageApi.nodeQueries()).extracting(StorageApiNodeQuery::keyword)
                .contains("测试图片这个文件", "测试图片");
        assertThat(result.candidates()).extracting(CandidateItem::name).containsExactly("测试图片");
    }

    @Test
    void exactFileNameStillWinsWhenReferentialSurfaceIsLiteralName() {
        FakeStorageApiNodeReadClient storageApi = FakeStorageApiNodeReadClient.withNodeResults(Map.of(
                "测试图片这个文件", List.of(new CandidateItem(9L, null, "测试图片这个文件", "FILE", 1L, "", "image/png", "")),
                "测试图片", List.of(new CandidateItem(10L, null, "测试图片", "FILE", 1L, "", "image/png", ""))
        ));
        StorageApiCandidateSearchClient client = new StorageApiCandidateSearchClient(storageApi);

        CandidateBindingResult result = client.search(new CandidateSearchRequest(
                "file_search",
                "search",
                "FILE",
                "target_name",
                "测试图片这个文件",
                5,
                "Bearer token"
        ));

        assertThat(result.candidates()).extracting(CandidateItem::name).containsExactly("测试图片这个文件");
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
    void folderNavigationFallsBackFromClassifierSurfaceToActualFolderName() {
        StorageApiCandidateSearchClient client = new StorageApiCandidateSearchClient(
                FakeStorageApiNodeReadClient.withFolders(List.of(
                        new CandidateItem(26L, null, "文件记录", "FOLDER", 0L, "", "", "")
                ))
        );

        CandidateBindingResult result = client.search(new CandidateSearchRequest(
                "file_search",
                "search",
                "FOLDER",
                "target_folder",
                "文件记录文件夹",
                5,
                "Bearer token"
        ));

        assertThat(result.status()).isEqualTo("search_results_ready");
        assertThat(result.candidates()).extracting(CandidateItem::name).containsExactly("文件记录");
    }

    @Test
    void exactFolderNameStillWinsWhenClassifierSurfaceIsLiteralName() {
        StorageApiCandidateSearchClient client = new StorageApiCandidateSearchClient(
                FakeStorageApiNodeReadClient.withFolders(List.of(
                        new CandidateItem(27L, null, "文件记录文件夹", "FOLDER", 0L, "", "", ""),
                        new CandidateItem(28L, null, "文件记录", "FOLDER", 0L, "", "", "")
                ))
        );

        CandidateBindingResult result = client.search(new CandidateSearchRequest(
                "file_search",
                "search",
                "FOLDER",
                "target_folder",
                "文件记录文件夹",
                5,
                "Bearer token"
        ));

        assertThat(result.status()).isEqualTo("search_results_ready");
        assertThat(result.candidates()).extracting(CandidateItem::name).containsExactly("文件记录文件夹");
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

    @Test
    void categoryListSendsCanonicalFilterAndRejectsMismatchedNodes() {
        FakeStorageApiNodeReadClient storageApi = FakeStorageApiNodeReadClient.withNodeResults(Map.of(
                "", List.of(
                        new CandidateItem(51L, null, "封面.png", "FILE", 10L, "png", "image/png", ""),
                        new CandidateItem(52L, null, "演示.mp4", "FILE", 10L, "mp4", "video/mp4", ""),
                        new CandidateItem(53L, null, "说明.txt", "FILE", 10L, "txt", "text/plain", "")
                )
        ));
        StorageApiCandidateSearchClient client = new StorageApiCandidateSearchClient(storageApi);

        CandidateBindingResult result = client.search(new CandidateSearchRequest(
                "file_search",
                "search",
                "FILE",
                "directory_scope",
                "",
                "directory_list",
                "all",
                "",
                "图片",
                null,
                "",
                50,
                "Bearer token"
        ));

        assertThat(result.status()).isEqualTo("search_results_ready");
        assertThat(result.candidates()).extracting(CandidateItem::name).containsExactly("封面.png");
        assertThat(storageApi.nodeQueries()).singleElement().satisfies(query -> {
            assertThat(query.recursive()).isTrue();
            assertThat(query.type()).isEqualTo("FILE");
            assertThat(query.category()).isEqualTo("IMAGE");
        });
        assertThat(result.pageInfo().totalCount()).isNull();
        assertThat(result.pageInfo().returnedCount()).isEqualTo(1);
    }

    @Test
    void directoryListCarriesExactCountAndPartialPageMetadata() {
        FakeStorageApiNodeReadClient storageApi = FakeStorageApiNodeReadClient.withPagedNodeResults(
                Map.of(
                        "", List.of(
                                new CandidateItem(61L, null, "合同-1.pdf", "FILE", 10L, "pdf", "application/pdf", ""),
                                new CandidateItem(62L, null, "合同-2.pdf", "FILE", 10L, "pdf", "application/pdf", "")
                        )
                ),
                Map.of("", 12L)
        );
        StorageApiCandidateSearchClient client = new StorageApiCandidateSearchClient(storageApi);

        CandidateBindingResult result = client.search(new CandidateSearchRequest(
                "file_search",
                "search",
                "FILE",
                "directory_scope",
                "",
                "directory_list",
                "all",
                "",
                null,
                "",
                2,
                "Bearer token"
        ));

        assertThat(result.pageInfo().totalCount()).isEqualTo(12L);
        assertThat(result.pageInfo().returnedCount()).isEqualTo(2);
        assertThat(result.pageInfo().hasMore()).isTrue();
        assertThat(result.pageInfo().sortBy()).isEqualTo("updatedAt");
        assertThat(result.pageInfo().sortDirection()).isEqualTo("desc");
    }

    @Test
    void candidateSelectionKeepsResultPageMetadata() {
        CandidateResultPage pageInfo = CandidateResultPage.exact(2, 2, "relevance", "");
        CandidateBindingResult binding = new CandidateBindingResult(
                "multiple_candidates",
                "test",
                "合同",
                "FILE",
                List.of(
                        new CandidateItem(71L, null, "合同-1.pdf", "FILE", 10L, "pdf", "application/pdf", ""),
                        new CandidateItem(72L, null, "合同-2.pdf", "FILE", 10L, "pdf", "application/pdf", "")
                ),
                "请选择",
                null,
                null,
                pageInfo
        );

        CandidateBindingResult selected = binding.select(1);

        assertThat(selected.selectedCandidate().nodeId()).isEqualTo(72L);
        assertThat(selected.pageInfo()).isEqualTo(pageInfo);
    }

    private static class FakeStorageApiNodeReadClient extends StorageApiNodeReadClient {
        private final Map<String, List<CandidateItem>> candidatesByQuery;
        private final Map<String, Long> totalByQuery;
        private final List<CandidateItem> folders;
        private final List<StorageApiNodeQuery> nodeQueries = new ArrayList<>();

        private FakeStorageApiNodeReadClient(
                Map<String, List<CandidateItem>> candidatesByQuery,
                Map<String, Long> totalByQuery,
                List<CandidateItem> folders
        ) {
            super(new ObjectMapper(), "https://storage.example", 1);
            this.candidatesByQuery = new LinkedHashMap<>(candidatesByQuery);
            this.totalByQuery = new LinkedHashMap<>(totalByQuery);
            this.folders = List.copyOf(folders);
        }

        private static FakeStorageApiNodeReadClient withNodeResults(Map<String, List<CandidateItem>> candidatesByQuery) {
            return new FakeStorageApiNodeReadClient(candidatesByQuery, Map.of(), List.of());
        }

        private static FakeStorageApiNodeReadClient withPagedNodeResults(
                Map<String, List<CandidateItem>> candidatesByQuery,
                Map<String, Long> totalByQuery
        ) {
            return new FakeStorageApiNodeReadClient(candidatesByQuery, totalByQuery, List.of());
        }

        private static FakeStorageApiNodeReadClient withFolders(List<CandidateItem> folders) {
            return new FakeStorageApiNodeReadClient(Map.of(), Map.of(), folders);
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
            long totalItems = totalByQuery.getOrDefault(query.keyword(), (long) candidates.size());
            int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / query.size());
            return new StorageApiNodePage(candidates, totalItems, 1, query.size(), totalPages);
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
