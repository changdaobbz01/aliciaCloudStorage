package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.ApiMessageResponse;
import com.alicia.cloudstorage.api.dto.BatchMoveNodeRequest;
import com.alicia.cloudstorage.api.dto.BatchNodeRequest;
import com.alicia.cloudstorage.api.dto.CreateFolderRequest;
import com.alicia.cloudstorage.api.dto.MoveNodeRequest;
import com.alicia.cloudstorage.api.dto.RenameNodeRequest;
import com.alicia.cloudstorage.api.dto.StorageNodeSummaryResponse;
import com.alicia.cloudstorage.api.entity.NodeType;
import com.alicia.cloudstorage.api.entity.StorageNode;
import com.alicia.cloudstorage.api.repository.StorageNodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class StorageCommandService {

    private final StorageNodeRepository storageNodeRepository;
    private final CosFileStorageService cosFileStorageService;
    private final StorageQuotaService storageQuotaService;

    public StorageCommandService(
            StorageNodeRepository storageNodeRepository,
            CosFileStorageService cosFileStorageService,
            StorageQuotaService storageQuotaService
    ) {
        this.storageNodeRepository = storageNodeRepository;
        this.cosFileStorageService = cosFileStorageService;
        this.storageQuotaService = storageQuotaService;
    }

    /**
     * 在当前目录下创建一个新的文件夹节点。
     */
    public StorageNodeSummaryResponse createFolder(Long userId, CreateFolderRequest request) {
        Long parentId = validateParentFolder(userId, request.parentId());
        String folderName = normalizeFolderName(request.folderName());
        validateSiblingNameUnique(userId, parentId, folderName);

        StorageNode folderNode = new StorageNode();
        folderNode.setOwnerId(userId);
        folderNode.setParentId(parentId);
        folderNode.setNodeName(folderName);
        folderNode.setNodeType(NodeType.FOLDER);
        folderNode.setFileSize(0L);
        clearTrashMetadata(folderNode);

        return toSummary(storageNodeRepository.save(folderNode));
    }

    /**
     * 接收前端上传文件，将文件写入 COS，并同步保存一条文件元数据记录。
     */
    public StorageNodeSummaryResponse uploadFile(Long userId, Long rawParentId, MultipartFile file) {
        Long parentId = validateParentFolder(userId, rawParentId);
        String fileName = extractFileName(file.getOriginalFilename());
        validateSiblingNameUnique(userId, parentId, fileName);
        storageQuotaService.validateUploadFits(userId, file.getSize());

        CosFileStorageService.StoredCosFile storedCosFile = cosFileStorageService.uploadUserFile(userId, file, fileName);

        try {
            StorageNode fileNode = new StorageNode();
            fileNode.setOwnerId(userId);
            fileNode.setParentId(parentId);
            fileNode.setNodeName(fileName);
            fileNode.setNodeType(NodeType.FILE);
            fileNode.setFileSize(storedCosFile.contentLength());
            fileNode.setFileExtension(extractExtension(fileName));
            fileNode.setMimeType(storedCosFile.contentType());
            fileNode.setStoragePath(storedCosFile.objectKey());
            clearTrashMetadata(fileNode);

            return toSummary(storageNodeRepository.save(fileNode));
        } catch (RuntimeException exception) {
            cosFileStorageService.deleteObjectQuietly(storedCosFile.objectKey());
            throw exception;
        }
    }

    /**
     * 根据文件节点元数据从 COS 打开下载流，供控制器回写给浏览器。
     */
    @Transactional(readOnly = true)
    public StorageDownloadPayload downloadFile(Long userId, Long fileId) {
        StorageNode fileNode = storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(fileId, userId)
                .orElseThrow(() -> new IllegalArgumentException("文件不存在。"));

        if (fileNode.getNodeType() != NodeType.FILE) {
            throw new IllegalArgumentException("当前节点不是文件，无法下载。");
        }

        if (fileNode.getStoragePath() == null || fileNode.getStoragePath().isBlank()) {
            throw new IllegalArgumentException("文件未关联云端存储对象。");
        }

        CosFileStorageService.DownloadedCosFile downloadedCosFile = cosFileStorageService.openFileStream(fileNode.getStoragePath());

        return new StorageDownloadPayload(
                fileNode.getNodeName(),
                fileNode.getMimeType() == null || fileNode.getMimeType().isBlank()
                        ? downloadedCosFile.contentType()
                        : fileNode.getMimeType(),
                downloadedCosFile.contentLength(),
                downloadedCosFile.inputStream()
        );
    }

    /**
     * 修改当前用户自己的文件或文件夹名称，并同步维护文件后缀元数据。
     */
    public StorageNodeSummaryResponse renameNode(Long userId, Long nodeId, RenameNodeRequest request) {
        StorageNode node = storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(nodeId, userId)
                .orElseThrow(() -> new IllegalArgumentException("文件或文件夹不存在。"));
        String nextName = normalizeNodeName(request.name(), "名称");

        validateSiblingNameUnique(userId, node.getParentId(), nextName, node.getId());
        node.setNodeName(nextName);

        if (node.getNodeType() == NodeType.FILE) {
            node.setFileExtension(extractExtension(nextName));
        }

        return toSummary(storageNodeRepository.save(node));
    }

    /**
     * 将单个文件或文件夹移动到新的父目录，并复用批量移动的校验逻辑。
     */
    public StorageNodeSummaryResponse moveNode(Long userId, Long nodeId, MoveNodeRequest request) {
        return moveNodesInternal(userId, List.of(nodeId), request.parentId()).get(0);
    }

    /**
     * 批量移动文件或文件夹到新的父目录，并阻止形成重名或目录循环。
     */
    public List<StorageNodeSummaryResponse> moveNodes(Long userId, BatchMoveNodeRequest request) {
        return moveNodesInternal(userId, request.nodeIds(), request.parentId());
    }

    /**
     * 将单个文件或文件夹移入回收站。
     */
    public ApiMessageResponse moveNodeToTrash(Long userId, Long nodeId) {
        int movedCount = moveNodesToTrashInternal(userId, List.of(nodeId));
        return new ApiMessageResponse(buildTrashMessage(movedCount));
    }

    /**
     * 批量将文件或文件夹移入回收站。
     */
    public ApiMessageResponse moveNodesToTrash(Long userId, BatchNodeRequest request) {
        int movedCount = moveNodesToTrashInternal(userId, request.nodeIds());
        return new ApiMessageResponse(buildTrashMessage(movedCount));
    }

    /**
     * 从回收站恢复单个文件或文件夹。
     */
    public StorageNodeSummaryResponse restoreNode(Long userId, Long nodeId) {
        return restoreNodesInternal(userId, List.of(nodeId)).get(0);
    }

    /**
     * 批量从回收站恢复文件或文件夹。
     */
    public List<StorageNodeSummaryResponse> restoreNodes(Long userId, BatchNodeRequest request) {
        return restoreNodesInternal(userId, request.nodeIds());
    }

    /**
     * 从回收站彻底删除单个文件或文件夹。
     */
    public ApiMessageResponse permanentlyDeleteNode(Long userId, Long nodeId) {
        int deletedCount = permanentlyDeleteNodesInternal(userId, List.of(nodeId));
        return new ApiMessageResponse(buildPermanentDeleteMessage(deletedCount));
    }

    /**
     * 批量从回收站彻底删除文件或文件夹，并清理关联的 COS 对象。
     */
    public ApiMessageResponse permanentlyDeleteNodes(Long userId, BatchNodeRequest request) {
        int deletedCount = permanentlyDeleteNodesInternal(userId, request.nodeIds());
        return new ApiMessageResponse(buildPermanentDeleteMessage(deletedCount));
    }

    /**
     * 执行批量移动，并把被选中的子孙节点折叠为根级操作项，避免重复处理。
     */
    private List<StorageNodeSummaryResponse> moveNodesInternal(Long userId, List<Long> rawNodeIds, Long rawParentId) {
        List<StorageNode> rootNodes = collapseSelectedRoots(
                userId,
                loadOwnedNodes(userId, rawNodeIds, false, "文件或文件夹不存在。")
        );
        Long nextParentId = validateParentFolder(userId, rawParentId);

        for (StorageNode node : rootNodes) {
            if (node.getId().equals(nextParentId)) {
                throw new IllegalArgumentException("不能将文件夹移动到自己内部。");
            }

            if (node.getNodeType() == NodeType.FOLDER) {
                validateFolderMoveTarget(userId, node.getId(), nextParentId);
            }
        }

        validatePlannedNameConflicts(
                rootNodes.stream()
                        .map(node -> new NodePlacement(node, nextParentId))
                        .toList(),
                "目标目录下已存在同名文件或文件夹。"
        );

        for (StorageNode node : rootNodes) {
            validateSiblingNameUnique(userId, nextParentId, node.getNodeName(), node.getId());
            node.setParentId(nextParentId);
        }

        storageNodeRepository.saveAll(rootNodes);
        return rootNodes.stream().map(this::toSummary).toList();
    }

    /**
     * 执行批量回收站删除，并对子树节点统一打上删除时间和删除人信息。
     */
    private int moveNodesToTrashInternal(Long userId, List<Long> rawNodeIds) {
        List<StorageNode> rootNodes = collapseSelectedRoots(
                userId,
                loadOwnedNodes(userId, rawNodeIds, false, "文件或文件夹不存在。")
        );
        LocalDateTime deletedAt = LocalDateTime.now();
        List<StorageNode> subtreeNodes = new ArrayList<>();

        for (StorageNode rootNode : rootNodes) {
            subtreeNodes.addAll(collectSubtree(userId, rootNode, true));
        }

        subtreeNodes.forEach(subtreeNode -> markNodeDeleted(subtreeNode, userId, deletedAt));
        storageNodeRepository.saveAll(subtreeNodes);
        return rootNodes.size();
    }

    /**
     * 执行批量恢复，并在原父目录无效时自动回退到根目录。
     */
    private List<StorageNodeSummaryResponse> restoreNodesInternal(Long userId, List<Long> rawNodeIds) {
        List<StorageNode> rootNodes = collapseSelectedRoots(
                userId,
                loadOwnedNodes(userId, rawNodeIds, true, "回收站中不存在该项目。")
        );
        List<RestorePlan> restorePlans = rootNodes.stream()
                .map(node -> new RestorePlan(node, resolveRestoreParentId(userId, node.getOriginalParentId())))
                .toList();

        validatePlannedNameConflicts(
                restorePlans.stream()
                        .map(plan -> new NodePlacement(plan.node(), plan.restoreParentId()))
                        .toList(),
                "目标目录下已存在同名文件或文件夹。"
        );

        for (RestorePlan restorePlan : restorePlans) {
            validateSiblingNameUnique(
                    userId,
                    restorePlan.restoreParentId(),
                    restorePlan.node().getNodeName(),
                    restorePlan.node().getId()
            );
        }

        List<StorageNode> subtreeNodes = new ArrayList<>();
        for (RestorePlan restorePlan : restorePlans) {
            List<StorageNode> subtree = collectSubtree(userId, restorePlan.node(), false);
            restorePlan.node().setParentId(restorePlan.restoreParentId());
            subtree.forEach(this::clearTrashMetadata);
            subtreeNodes.addAll(subtree);
        }

        storageNodeRepository.saveAll(subtreeNodes);
        return restorePlans.stream()
                .map(RestorePlan::node)
                .map(this::toSummary)
                .toList();
    }

    /**
     * 执行批量彻底删除，并从叶子节点到根节点依次清理元数据和 COS 文件对象。
     */
    private int permanentlyDeleteNodesInternal(Long userId, List<Long> rawNodeIds) {
        List<StorageNode> rootNodes = collapseSelectedRoots(
                userId,
                loadOwnedNodes(userId, rawNodeIds, true, "回收站中不存在该项目。")
        );
        List<StorageNode> subtreeNodes = new ArrayList<>();

        for (StorageNode rootNode : rootNodes) {
            subtreeNodes.addAll(collectSubtree(userId, rootNode, false));
        }

        subtreeNodes.stream()
                .filter(subtreeNode -> subtreeNode.getNodeType() == NodeType.FILE)
                .map(StorageNode::getStoragePath)
                .filter(storagePath -> storagePath != null && !storagePath.isBlank())
                .forEach(cosFileStorageService::deleteObjectQuietly);

        Collections.reverse(subtreeNodes);
        storageNodeRepository.deleteAll(subtreeNodes);
        return rootNodes.size();
    }

    /**
     * 批量加载用户自己的节点，并校验每个编号都真实存在于当前状态集合中。
     */
    private List<StorageNode> loadOwnedNodes(Long userId, List<Long> rawNodeIds, boolean deleted, String notFoundMessage) {
        List<Long> nodeIds = normalizeNodeIds(rawNodeIds);
        List<StorageNode> foundNodes = deleted
                ? storageNodeRepository.findByOwnerIdAndIdInAndDeletedTrue(userId, nodeIds)
                : storageNodeRepository.findByOwnerIdAndIdInAndDeletedFalse(userId, nodeIds);

        if (foundNodes.size() != nodeIds.size()) {
            throw new IllegalArgumentException(notFoundMessage);
        }

        Map<Long, StorageNode> nodeMap = new HashMap<>();
        for (StorageNode foundNode : foundNodes) {
            nodeMap.put(foundNode.getId(), foundNode);
        }

        List<StorageNode> orderedNodes = new ArrayList<>(nodeIds.size());
        for (Long nodeId : nodeIds) {
            StorageNode node = nodeMap.get(nodeId);
            if (node == null) {
                throw new IllegalArgumentException(notFoundMessage);
            }
            orderedNodes.add(node);
        }

        return orderedNodes;
    }

    /**
     * 规整批量请求里的节点编号，去重并拦截空值，避免后续流程重复处理。
     */
    private List<Long> normalizeNodeIds(List<Long> rawNodeIds) {
        if (rawNodeIds == null || rawNodeIds.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个项目。");
        }

        LinkedHashSet<Long> uniqueNodeIds = new LinkedHashSet<>();
        for (Long rawNodeId : rawNodeIds) {
            if (rawNodeId == null) {
                throw new IllegalArgumentException("项目编号不能为空。");
            }
            uniqueNodeIds.add(rawNodeId);
        }

        return List.copyOf(uniqueNodeIds);
    }

    /**
     * 折叠批量选择里的子孙节点，只保留最顶层的选中项，避免对子树重复操作。
     */
    private List<StorageNode> collapseSelectedRoots(Long userId, List<StorageNode> nodes) {
        Set<Long> selectedIds = new HashSet<>();
        for (StorageNode node : nodes) {
            selectedIds.add(node.getId());
        }

        List<StorageNode> rootNodes = new ArrayList<>();
        for (StorageNode node : nodes) {
            if (!hasSelectedAncestor(userId, node.getParentId(), selectedIds)) {
                rootNodes.add(node);
            }
        }

        return rootNodes;
    }

    /**
     * 沿着父级链向上检查，判断当前节点是否已经被更高层的已选祖先覆盖。
     */
    private boolean hasSelectedAncestor(Long userId, Long parentId, Set<Long> selectedIds) {
        Long cursorParentId = parentId;

        while (cursorParentId != null) {
            if (selectedIds.contains(cursorParentId)) {
                return true;
            }

            StorageNode parentNode = storageNodeRepository.findByIdAndOwnerId(cursorParentId, userId).orElse(null);
            if (parentNode == null) {
                return false;
            }

            cursorParentId = parentNode.getParentId();
        }

        return false;
    }

    /**
     * 预校验批量操作后会落到同一目录的节点名称，提前拦截请求内自带的重名冲突。
     */
    private void validatePlannedNameConflicts(List<NodePlacement> placements, String conflictMessage) {
        Set<String> placementKeys = new HashSet<>();

        for (NodePlacement placement : placements) {
            String placementKey = (placement.parentId() == null ? "ROOT" : placement.parentId()) + "\u0000" + placement.node().getNodeName();
            if (!placementKeys.add(placementKey)) {
                throw new IllegalArgumentException(conflictMessage);
            }
        }
    }

    /**
     * 校验父级目录是否合法，仅允许在当前用户自己的文件夹下创建内容。
     */
    private Long validateParentFolder(Long userId, Long parentId) {
        if (parentId == null) {
            return null;
        }

        StorageNode parentNode = storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(parentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("父级文件夹不存在。"));

        if (parentNode.getNodeType() != NodeType.FOLDER) {
            throw new IllegalArgumentException("只能在文件夹内创建内容。");
        }

        return parentId;
    }

    /**
     * 校验文件夹移动目标，防止把父目录放进自己的子孙目录形成循环。
     */
    private void validateFolderMoveTarget(Long userId, Long nodeId, Long targetParentId) {
        Long cursorParentId = targetParentId;

        while (cursorParentId != null) {
            if (nodeId.equals(cursorParentId)) {
                throw new IllegalArgumentException("不能将文件夹移动到自己或自己的子目录内。");
            }

            StorageNode parentNode = storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(cursorParentId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("目标文件夹不存在。"));
            cursorParentId = parentNode.getParentId();
        }
    }

    /**
     * 恢复回收站项目时优先回到原父级；原父级不可用时退回根目录。
     */
    private Long resolveRestoreParentId(Long userId, Long parentId) {
        if (parentId == null) {
            return null;
        }

        return storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(parentId, userId)
                .filter(parentNode -> parentNode.getNodeType() == NodeType.FOLDER)
                .map(StorageNode::getId)
                .orElse(null);
    }

    /**
     * 收集某个节点下的完整子树，供回收站和彻底删除流程批量处理。
     */
    private List<StorageNode> collectSubtree(Long userId, StorageNode rootNode, boolean activeOnly) {
        List<StorageNode> subtreeNodes = new ArrayList<>();
        appendSubtree(userId, rootNode, activeOnly, subtreeNodes);
        return subtreeNodes;
    }

    /**
     * 深度优先递归加入子节点，保留父节点在前、子节点在后的顺序。
     */
    private void appendSubtree(Long userId, StorageNode node, boolean activeOnly, List<StorageNode> subtreeNodes) {
        subtreeNodes.add(node);

        List<StorageNode> childNodes = activeOnly
                ? storageNodeRepository.findByOwnerIdAndParentIdAndDeletedFalse(userId, node.getId())
                : storageNodeRepository.findByOwnerIdAndParentId(userId, node.getId());

        for (StorageNode childNode : childNodes) {
            appendSubtree(userId, childNode, activeOnly, subtreeNodes);
        }
    }

    /**
     * 校验同一目录下是否已经存在同名文件或文件夹。
     */
    private void validateSiblingNameUnique(Long userId, Long parentId, String nodeName) {
        if (storageNodeRepository.existsActiveSiblingName(userId, parentId, nodeName)) {
            throw new IllegalArgumentException("当前目录下已存在同名文件或文件夹。");
        }
    }

    /**
     * 校验同名冲突时排除当前节点自身，供重命名、移动和恢复流程复用。
     */
    private void validateSiblingNameUnique(Long userId, Long parentId, String nodeName, Long excludedId) {
        if (storageNodeRepository.existsActiveSiblingNameExcludingId(userId, parentId, nodeName, excludedId)) {
            throw new IllegalArgumentException("目标目录下已存在同名文件或文件夹。");
        }
    }

    /**
     * 将节点标记为已删除，并补齐回收站所需的审计元数据。
     */
    private void markNodeDeleted(StorageNode node, Long deletedBy, LocalDateTime deletedAt) {
        node.setOriginalParentId(node.getParentId());
        node.setDeleted(true);
        node.setDeletedBy(deletedBy);
        node.setDeletedAt(deletedAt);
    }

    /**
     * 清理节点的回收站元数据，供创建、恢复和其他激活流程复用。
     */
    private void clearTrashMetadata(StorageNode node) {
        node.setDeleted(false);
        node.setDeletedAt(null);
        node.setDeletedBy(null);
        node.setOriginalParentId(null);
    }

    /**
     * 规范化文件夹名称，并拦截不合法的目录字符。
     */
    private String normalizeFolderName(String rawFolderName) {
        return normalizeNodeName(rawFolderName, "文件夹名称");
    }

    /**
     * 规范化文件或文件夹名称，并统一禁止空名称、超长名称和路径分隔符。
     */
    private String normalizeNodeName(String rawNodeName, String fieldName) {
        if (rawNodeName == null || rawNodeName.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空。");
        }

        String nodeName = rawNodeName.trim();
        if (nodeName.length() > 255) {
            throw new IllegalArgumentException(fieldName + "长度不能超过 255 个字符。");
        }

        if (nodeName.contains("/") || nodeName.contains("\\")) {
            throw new IllegalArgumentException(fieldName + "不能包含斜杠。");
        }

        return nodeName;
    }

    /**
     * 从上传请求里提取最终文件名，并移除客户端路径信息。
     */
    private String extractFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("上传文件缺少文件名。");
        }

        String normalized = originalFilename.replace("\\", "/");
        int slashIndex = normalized.lastIndexOf('/');
        String fileName = slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
        fileName = fileName.trim();

        if (fileName.isBlank()) {
            throw new IllegalArgumentException("上传文件缺少文件名。");
        }

        return normalizeNodeName(fileName, "文件名称");
    }

    /**
     * 从文件名中提取不带点号的小写后缀，便于前端列表展示。
     */
    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return null;
        }

        return fileName.substring(dotIndex + 1).toLowerCase();
    }

    /**
     * 将实体对象转换成前端需要的列表摘要结构。
     */
    private StorageNodeSummaryResponse toSummary(StorageNode node) {
        return new StorageNodeSummaryResponse(
                node.getId(),
                node.getParentId(),
                node.getNodeName(),
                node.getNodeType().name(),
                node.getFileSize(),
                node.getFileExtension(),
                node.getMimeType(),
                node.getUpdatedAt(),
                node.getDeletedAt()
        );
    }

    private String buildTrashMessage(int count) {
        return count == 1 ? "已移入回收站。" : "已将 " + count + " 项移入回收站。";
    }

    private String buildPermanentDeleteMessage(int count) {
        return count == 1 ? "已彻底删除。" : "已彻底删除 " + count + " 项。";
    }

    private record NodePlacement(StorageNode node, Long parentId) {
    }

    private record RestorePlan(StorageNode node, Long restoreParentId) {
    }

    public record StorageDownloadPayload(
            String fileName,
            String contentType,
            long contentLength,
            InputStream inputStream
    ) {
    }
}
