package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntityExtractorTest {

    private final EntityExtractor extractor = new EntityExtractor(
            new RagConfigLoader(new ObjectMapper())
    );

    @Test
    void extractsAChineseFolderNameDeclaredAfterTheGenericObject() {
        assertThat(extractor.extract("创建一个文件夹，名称是 rag-active-cn-20261021-03"))
                .containsEntry("new_folder_name", "rag-active-cn-20261021-03");
    }

    @Test
    void acceptsCommonPostposedChineseNameMarkers() {
        assertThat(extractor.extract("新建个目录，名字为 周报归档"))
                .containsEntry("new_folder_name", "周报归档");
    }

    @Test
    void genericFolderObjectBeforePunctuationIsNeverTreatedAsTheName() {
        assertThat(extractor.extract("创建一个文件夹，然后告诉我下一步"))
                .doesNotContainKey("new_folder_name");
    }
}
