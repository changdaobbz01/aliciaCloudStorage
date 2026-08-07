package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.BatchNodeRequest;
import com.alicia.cloudstorage.api.entity.NodeType;
import com.alicia.cloudstorage.api.entity.StorageNode;
import com.alicia.cloudstorage.api.repository.StorageNodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Transactional(readOnly = true)
public class StorageArchiveService {

    private static final int MAX_ARCHIVE_ROOTS = 100;
    private static final int MAX_ARCHIVE_FILES = 1000;
    private static final int MAX_ARCHIVE_DEPTH = 50;
    private static final int MAX_ZIP_SEGMENT_LENGTH = 180;
    private static final long MAX_ARCHIVE_BYTES = 1024L * 1024 * 1024;
    private static final DateTimeFormatter ARCHIVE_FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final StorageNodeRepository storageNodeRepository;
    private final CosFileStorageService cosFileStorageService;

    public StorageArchiveService(
            StorageNodeRepository storageNodeRepository,
            CosFileStorageService cosFileStorageService
    ) {
        this.storageNodeRepository = storageNodeRepository;
        this.cosFileStorageService = cosFileStorageService;
    }

    public StorageArchivePayload createArchive(Long userId, BatchNodeRequest request) {
        List<Long> nodeIds = normalizeNodeIds(request == null ? null : request.nodeIds());
        List<StorageNode> rootNodes = collapseSelectedRoots(userId, loadOwnedActiveNodes(userId, nodeIds));
        ArchivePlan archivePlan = buildArchivePlan(userId, rootNodes);

        return new StorageArchivePayload(
                resolveArchiveFileName(rootNodes),
                outputStream -> writeZipArchive(archivePlan.entries(), outputStream)
        );
    }

    private ArchivePlan buildArchivePlan(Long userId, List<StorageNode> rootNodes) {
        List<ArchiveEntryPlan> entries = new ArrayList<>();
        ArchiveLimitCounter limitCounter = new ArchiveLimitCounter();
        Set<String> reservedRootSegments = new HashSet<>();

        for (StorageNode rootNode : rootNodes) {
            String rootSegment = resolveUniqueSegment(rootNode.getNodeName(), reservedRootSegments);
            appendArchiveEntries(userId, rootNode, rootSegment, 0, entries, limitCounter);
        }

        if (entries.isEmpty()) {
            throw new IllegalArgumentException("No available items to archive.");
        }

        return new ArchivePlan(entries);
    }

    private void appendArchiveEntries(
            Long userId,
            StorageNode node,
            String zipPath,
            int depth,
            List<ArchiveEntryPlan> entries,
            ArchiveLimitCounter limitCounter
    ) {
        if (depth > MAX_ARCHIVE_DEPTH) {
            throw new IllegalArgumentException("Folder nesting is too deep to archive safely.");
        }

        if (node.getNodeType() == NodeType.FILE) {
            validateDownloadableFile(node);
            limitCounter.recordFile(node.getFileSize());
            entries.add(new ArchiveEntryPlan(node, zipPath, false));
            return;
        }

        entries.add(new ArchiveEntryPlan(node, ensureDirectoryPath(zipPath), true));
        List<StorageNode> childNodes = storageNodeRepository.findByOwnerIdAndParentIdAndDeletedFalse(userId, node.getId())
                .stream()
                .sorted(Comparator
                        .comparing((StorageNode child) -> child.getNodeType() != NodeType.FOLDER)
                        .thenComparing(child -> child.getNodeName().toLowerCase(Locale.ROOT)))
                .toList();
        Set<String> reservedChildSegments = new HashSet<>();

        for (StorageNode childNode : childNodes) {
            String childSegment = resolveUniqueSegment(childNode.getNodeName(), reservedChildSegments);
            appendArchiveEntries(
                    userId,
                    childNode,
                    zipPath + "/" + childSegment,
                    depth + 1,
                    entries,
                    limitCounter
            );
        }
    }

    private void writeZipArchive(List<ArchiveEntryPlan> entries, OutputStream outputStream) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, java.nio.charset.StandardCharsets.UTF_8)) {
            for (ArchiveEntryPlan entryPlan : entries) {
                ZipEntry zipEntry = new ZipEntry(entryPlan.zipPath());
                if (entryPlan.node().getUpdatedAt() != null) {
                    zipEntry.setTime(entryPlan.node().getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                }

                zipOutputStream.putNextEntry(zipEntry);
                try {
                    if (!entryPlan.directory()) {
                        CosFileStorageService.DownloadedCosFile downloadedFile =
                                cosFileStorageService.openFileStream(entryPlan.node().getStoragePath());
                        try (InputStream inputStream = downloadedFile.inputStream()) {
                            inputStream.transferTo(zipOutputStream);
                        }
                    }
                } finally {
                    zipOutputStream.closeEntry();
                }
            }
        }
    }

    private List<StorageNode> loadOwnedActiveNodes(Long userId, List<Long> nodeIds) {
        List<StorageNode> foundNodes = storageNodeRepository.findByOwnerIdAndIdInAndDeletedFalse(userId, nodeIds);
        if (foundNodes.size() != nodeIds.size()) {
            throw new IllegalArgumentException("Selected item does not exist or has been deleted.");
        }

        Map<Long, StorageNode> nodeMap = new HashMap<>();
        foundNodes.forEach(node -> nodeMap.put(node.getId(), node));

        List<StorageNode> orderedNodes = new ArrayList<>();
        for (Long nodeId : nodeIds) {
            StorageNode node = nodeMap.get(nodeId);
            if (node == null) {
                throw new IllegalArgumentException("Selected item does not exist or has been deleted.");
            }
            orderedNodes.add(node);
        }

        return orderedNodes;
    }

    private List<Long> normalizeNodeIds(List<Long> rawNodeIds) {
        if (rawNodeIds == null || rawNodeIds.isEmpty()) {
            throw new IllegalArgumentException("Please select at least one item.");
        }

        if (rawNodeIds.size() > MAX_ARCHIVE_ROOTS) {
            throw new IllegalArgumentException("Too many selected items for one archive.");
        }

        LinkedHashSet<Long> uniqueNodeIds = new LinkedHashSet<>();
        for (Long rawNodeId : rawNodeIds) {
            if (rawNodeId == null) {
                throw new IllegalArgumentException("Selected item id must not be null.");
            }
            uniqueNodeIds.add(rawNodeId);
        }

        return List.copyOf(uniqueNodeIds);
    }

    private List<StorageNode> collapseSelectedRoots(Long userId, List<StorageNode> nodes) {
        Set<Long> selectedIds = new HashSet<>();
        nodes.forEach(node -> selectedIds.add(node.getId()));
        List<StorageNode> rootNodes = new ArrayList<>();

        for (StorageNode node : nodes) {
            if (!hasSelectedAncestor(userId, node.getParentId(), selectedIds)) {
                rootNodes.add(node);
            }
        }

        return rootNodes;
    }

    private boolean hasSelectedAncestor(Long userId, Long parentId, Set<Long> selectedIds) {
        Long cursorParentId = parentId;

        while (cursorParentId != null) {
            if (selectedIds.contains(cursorParentId)) {
                return true;
            }

            StorageNode parentNode = storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(cursorParentId, userId)
                    .orElse(null);
            cursorParentId = parentNode == null ? null : parentNode.getParentId();
        }

        return false;
    }

    private void validateDownloadableFile(StorageNode node) {
        if (node.getStoragePath() == null || node.getStoragePath().isBlank()) {
            throw new IllegalArgumentException("Selected file is not linked to cloud storage.");
        }
    }

    private String resolveArchiveFileName(List<StorageNode> rootNodes) {
        String baseName = rootNodes.size() == 1
                ? stripZipSuffix(sanitizeZipPathSegment(rootNodes.get(0).getNodeName()))
                : "AliciaCloud-" + java.time.LocalDateTime.now().format(ARCHIVE_FILE_NAME_FORMATTER);

        if (baseName.isBlank()) {
            baseName = "AliciaCloud";
        }

        return baseName + ".zip";
    }

    private String stripZipSuffix(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".zip")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
    }

    private String ensureDirectoryPath(String zipPath) {
        return zipPath.endsWith("/") ? zipPath : zipPath + "/";
    }

    private String resolveUniqueSegment(String rawSegment, Set<String> reservedSegments) {
        String segment = sanitizeZipPathSegment(rawSegment);

        for (int index = 0; index < 1000; index += 1) {
            String candidate = index == 0 ? segment : appendCopySuffix(segment, index);
            if (reservedSegments.add(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }

        throw new IllegalArgumentException("Too many items with the same name in one folder.");
    }

    private String appendCopySuffix(String segment, int index) {
        String suffix = " (" + index + ")";
        int dotIndex = segment.lastIndexOf('.');
        boolean hasExtension = dotIndex > 0 && dotIndex < segment.length() - 1;
        String baseName = hasExtension ? segment.substring(0, dotIndex) : segment;
        String extension = hasExtension ? segment.substring(dotIndex) : "";
        int maxBaseLength = Math.max(1, MAX_ZIP_SEGMENT_LENGTH - suffix.length() - extension.length());

        if (baseName.length() > maxBaseLength) {
            baseName = baseName.substring(0, maxBaseLength);
        }

        return baseName + suffix + extension;
    }

    private String sanitizeZipPathSegment(String rawSegment) {
        String value = rawSegment == null ? "" : rawSegment.trim();
        StringBuilder sanitized = new StringBuilder();

        for (int index = 0; index < value.length(); index += 1) {
            char current = value.charAt(index);
            if (current <= 31 || current == '/' || current == '\\' || current == ':' || current == '*'
                    || current == '?' || current == '"' || current == '<' || current == '>' || current == '|') {
                sanitized.append('_');
            } else {
                sanitized.append(current);
            }
        }

        String segment = sanitized.toString().trim();
        while (segment.endsWith(".") || segment.endsWith(" ")) {
            segment = segment.substring(0, segment.length() - 1);
        }

        if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
            segment = "untitled";
        }

        if (segment.length() > MAX_ZIP_SEGMENT_LENGTH) {
            segment = segment.substring(0, MAX_ZIP_SEGMENT_LENGTH);
        }

        return segment;
    }

    private static class ArchiveLimitCounter {
        private int fileCount;
        private long totalBytes;

        void recordFile(Long rawFileSize) {
            long fileSize = Math.max(0L, rawFileSize == null ? 0L : rawFileSize);
            if (fileCount + 1 > MAX_ARCHIVE_FILES) {
                throw new IllegalArgumentException("Archive contains too many files.");
            }

            if (totalBytes > MAX_ARCHIVE_BYTES - fileSize) {
                throw new IllegalArgumentException("Archive is too large. Please select fewer items.");
            }

            fileCount += 1;
            totalBytes += fileSize;
        }
    }

    private record ArchivePlan(List<ArchiveEntryPlan> entries) {
    }

    private record ArchiveEntryPlan(StorageNode node, String zipPath, boolean directory) {
    }

    public record StorageArchivePayload(
            String fileName,
            StreamingResponseBody body
    ) {
    }
}
