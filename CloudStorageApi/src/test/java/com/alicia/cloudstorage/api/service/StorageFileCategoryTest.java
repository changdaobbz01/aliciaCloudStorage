package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.entity.NodeType;
import com.alicia.cloudstorage.api.entity.StorageNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageFileCategoryTest {

    @Test
    void matchesByMimeTypePrefix() {
        StorageNode image = file("capture.bin", null, "image/png");
        StorageNode video = file("movie.bin", null, "video/mp4");
        StorageNode audio = file("sound.bin", null, "audio/mpeg");

        assertThat(StorageFileCategory.IMAGE.matches(image)).isTrue();
        assertThat(StorageFileCategory.VIDEO.matches(video)).isTrue();
        assertThat(StorageFileCategory.AUDIO.matches(audio)).isTrue();
    }

    @Test
    void matchesByExtensionWhenMimeTypeIsMissing() {
        assertThat(StorageFileCategory.DOCUMENT.matches(file("report", "docx", null))).isTrue();
        assertThat(StorageFileCategory.ARCHIVE.matches(file("package", "7z", null))).isTrue();
        assertThat(StorageFileCategory.IMAGE.matches(file("photo", ".webp", null))).isTrue();
    }

    @Test
    void foldersNeverMatchFileCategory() {
        StorageNode folder = new StorageNode();
        folder.setNodeName("photos");
        folder.setNodeType(NodeType.FOLDER);
        folder.setFileSize(0L);

        assertThat(StorageFileCategory.IMAGE.matches(folder)).isFalse();
    }

    @Test
    void rejectsUnknownCategory() {
        assertThatThrownBy(() -> StorageFileCategory.fromRaw("photo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid file category");
    }

    private StorageNode file(String name, String extension, String mimeType) {
        StorageNode node = new StorageNode();
        node.setNodeName(name);
        node.setNodeType(NodeType.FILE);
        node.setFileSize(1L);
        node.setFileExtension(extension);
        node.setMimeType(mimeType);
        return node;
    }
}
