package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.BatchNodeRequest;
import com.alicia.cloudstorage.api.entity.NodeType;
import com.alicia.cloudstorage.api.entity.StorageNode;
import com.alicia.cloudstorage.api.repository.StorageNodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageArchiveServiceTest {

    @Mock
    private StorageNodeRepository storageNodeRepository;

    @Mock
    private CosFileStorageService cosFileStorageService;

    @InjectMocks
    private StorageArchiveService storageArchiveService;

    @Test
    void createArchiveRejectsNodeOutsideCurrentUserScope() {
        when(storageNodeRepository.findByOwnerIdAndIdInAndDeletedFalse(7L, List.of(99L))).thenReturn(List.of());

        assertThatThrownBy(() -> storageArchiveService.createArchive(7L, new BatchNodeRequest(List.of(99L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("选择的项目不存在或已被删除。");

        verify(cosFileStorageService, never()).openFileStream(anyString());
    }

    @Test
    void createArchiveDeduplicatesSelectedIdsBeforeLoadingNodes() throws Exception {
        StorageNode file = fileNode(41L, 7L, null, "report.txt", "cos/report.txt", 5L);

        when(storageNodeRepository.findByOwnerIdAndIdInAndDeletedFalse(7L, List.of(41L))).thenReturn(List.of(file));
        when(cosFileStorageService.openFileStream("cos/report.txt"))
                .thenReturn(new CosFileStorageService.DownloadedCosFile(
                        new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)),
                        "text/plain",
                        5L
                ));

        StorageArchiveService.StorageArchivePayload payload =
                storageArchiveService.createArchive(7L, new BatchNodeRequest(List.of(41L, 41L)));

        assertThat(payload.fileName()).isEqualTo("report.txt.zip");
        verify(storageNodeRepository).findByOwnerIdAndIdInAndDeletedFalse(7L, List.of(41L));
    }

    @Test
    void createArchiveCollapsesSelectedDescendantsAndStreamsFolderZip() throws Exception {
        StorageNode folder = folderNode(11L, 7L, null, "docs");
        StorageNode file = fileNode(12L, 7L, 11L, "a.txt", "cos/a.txt", 5L);

        when(storageNodeRepository.findByOwnerIdAndIdInAndDeletedFalse(7L, List.of(11L, 12L)))
                .thenReturn(List.of(file, folder));
        when(storageNodeRepository.findByOwnerIdAndParentIdAndDeletedFalse(7L, 11L)).thenReturn(List.of(file));
        when(cosFileStorageService.openFileStream("cos/a.txt"))
                .thenReturn(new CosFileStorageService.DownloadedCosFile(
                        new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)),
                        "text/plain",
                        5L
                ));

        StorageArchiveService.StorageArchivePayload payload =
                storageArchiveService.createArchive(7L, new BatchNodeRequest(List.of(11L, 12L)));

        assertThat(payload.fileName()).isEqualTo("docs.zip");
        assertThat(payload.contentLength()).isPositive();
        ZipSnapshot zipSnapshot = writeAndReadZip(payload);
        assertThat(zipSnapshot.entryNames()).containsExactly("docs/", "docs/a.txt");
        assertThat(zipSnapshot.fileContents()).containsEntry("docs/a.txt", "hello");
    }

    @Test
    void createArchiveSanitizesUnsafeZipEntryNames() throws Exception {
        StorageNode file = fileNode(21L, 7L, null, "report/..\\2026?.txt", "cos/report.txt", 4L);

        when(storageNodeRepository.findByOwnerIdAndIdInAndDeletedFalse(7L, List.of(21L))).thenReturn(List.of(file));
        when(cosFileStorageService.openFileStream("cos/report.txt"))
                .thenReturn(new CosFileStorageService.DownloadedCosFile(
                        new ByteArrayInputStream("safe".getBytes(StandardCharsets.UTF_8)),
                        "text/plain",
                        4L
                ));

        StorageArchiveService.StorageArchivePayload payload =
                storageArchiveService.createArchive(7L, new BatchNodeRequest(List.of(21L)));

        assertThat(payload.fileName()).isEqualTo("report_.._2026_.txt.zip");
        assertThat(payload.contentLength()).isPositive();
        ZipSnapshot zipSnapshot = writeAndReadZip(payload);
        assertThat(zipSnapshot.entryNames()).containsExactly("report_.._2026_.txt");
        assertThat(zipSnapshot.fileContents()).containsEntry("report_.._2026_.txt", "safe");
    }

    @Test
    void createArchiveFailsBeforeReturningPayloadWhenCosStreamCannotOpen() {
        StorageNode file = fileNode(31L, 7L, null, "broken.txt", "cos/broken.txt", 6L);

        when(storageNodeRepository.findByOwnerIdAndIdInAndDeletedFalse(7L, List.of(31L))).thenReturn(List.of(file));
        when(cosFileStorageService.openFileStream("cos/broken.txt"))
                .thenThrow(new IllegalStateException("COS down"));

        assertThatThrownBy(() -> storageArchiveService.createArchive(7L, new BatchNodeRequest(List.of(31L))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("COS down");
    }

    @Test
    void createArchiveRejectsFileWithoutStorageObject() {
        StorageNode file = fileNode(42L, 7L, null, "lost.txt", "   ", 6L);

        when(storageNodeRepository.findByOwnerIdAndIdInAndDeletedFalse(7L, List.of(42L))).thenReturn(List.of(file));

        assertThatThrownBy(() -> storageArchiveService.createArchive(7L, new BatchNodeRequest(List.of(42L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("文件未关联云端存储对象。");

        verify(cosFileStorageService, never()).openFileStream(anyString());
    }

    private ZipSnapshot writeAndReadZip(StorageArchiveService.StorageArchivePayload payload) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        payload.body().writeTo(outputStream);

        List<String> entryNames = new ArrayList<>();
        Map<String, String> fileContents = new java.util.LinkedHashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(
                new ByteArrayInputStream(outputStream.toByteArray()),
                StandardCharsets.UTF_8
        )) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                entryNames.add(zipEntry.getName());
                if (!zipEntry.isDirectory()) {
                    fileContents.put(zipEntry.getName(), new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8));
                }
                zipInputStream.closeEntry();
            }
        }

        return new ZipSnapshot(entryNames, fileContents);
    }

    private StorageNode folderNode(Long id, Long ownerId, Long parentId, String name) {
        StorageNode node = new StorageNode();
        ReflectionTestUtils.setField(node, "id", id);
        ReflectionTestUtils.setField(node, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(node, "updatedAt", LocalDateTime.now());
        node.setOwnerId(ownerId);
        node.setParentId(parentId);
        node.setNodeName(name);
        node.setNodeType(NodeType.FOLDER);
        node.setFileSize(0L);
        node.setDeleted(false);
        return node;
    }

    private StorageNode fileNode(Long id, Long ownerId, Long parentId, String name, String storagePath, long fileSize) {
        StorageNode node = folderNode(id, ownerId, parentId, name);
        node.setNodeType(NodeType.FILE);
        node.setFileSize(fileSize);
        node.setStoragePath(storagePath);
        node.setMimeType("text/plain");
        node.setFileExtension("txt");
        return node;
    }

    private record ZipSnapshot(List<String> entryNames, Map<String, String> fileContents) {
    }
}
