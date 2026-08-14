package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.ApiMessageResponse;
import com.alicia.cloudstorage.api.dto.BatchNodeRequest;
import com.alicia.cloudstorage.api.dto.ScopedTrashPreviewResponse;
import com.alicia.cloudstorage.api.dto.ScopedTrashRequest;
import com.alicia.cloudstorage.api.dto.StorageNodeSummaryResponse;
import com.alicia.cloudstorage.api.entity.NodeType;
import com.alicia.cloudstorage.api.entity.StorageNode;
import com.alicia.cloudstorage.api.repository.StorageNodeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class ScopedCollectionTrashService {

    public static final String SELECTOR_VERSION = "source_selector_v2";
    private static final int HARD_MAX_IMPACT_NODES = 10_000;

    private final StorageNodeRepository storageNodeRepository;
    private final StorageCommandService storageCommandService;
    private final int maxSelectedNodes;
    private final int maxImpactNodes;

    public ScopedCollectionTrashService(
            StorageNodeRepository storageNodeRepository,
            StorageCommandService storageCommandService,
            @Value("${alicia.storage.ai-trash.max-selected-nodes:500}") int maxSelectedNodes,
            @Value("${alicia.storage.ai-trash.max-impact-nodes:2000}") int maxImpactNodes
    ) {
        this.storageNodeRepository = storageNodeRepository;
        this.storageCommandService = storageCommandService;
        this.maxSelectedNodes = Math.max(1, Math.min(500, maxSelectedNodes));
        this.maxImpactNodes = Math.max(
                this.maxSelectedNodes,
                Math.min(HARD_MAX_IMPACT_NODES, maxImpactNodes)
        );
    }

    @Transactional(readOnly = true)
    public ScopedTrashPreviewResponse preview(
            Long userId,
            Long sourceParentId,
            boolean root,
            List<String> rawNodeTypes
    ) {
        SourceScope scope = validateScope(userId, sourceParentId, root, rawNodeTypes);
        List<StorageNode> selected = selectedNodes(userId, scope);
        Impact impact = selected.size() > maxSelectedNodes
                ? new Impact(List.copyOf(selected), false)
                : collectImpact(userId, selected);
        return response(scope, selected, impact);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ApiMessageResponse execute(Long userId, ScopedTrashRequest request) {
        if (!SELECTOR_VERSION.equals(request.selectorVersion())) {
            throw new IllegalArgumentException("不支持的集合选择器版本。");
        }
        SourceScope scope = validateScope(userId, request.sourceParentId(), request.root(), request.nodeTypes());
        List<StorageNode> selected = selectedNodes(userId, scope);
        Impact impact = selected.size() > maxSelectedNodes
                ? new Impact(List.copyOf(selected), false)
                : collectImpact(userId, selected);
        ScopedTrashPreviewResponse current = response(scope, selected, impact);

        List<Long> requestedIds = request.nodeIds().stream().distinct().sorted().toList();
        List<Long> currentIds = selected.stream().map(StorageNode::getId).sorted().toList();
        boolean unchanged = current.executable()
                && requestedIds.equals(currentIds)
                && request.expectedImpactCount() == current.impactCount()
                && request.scopeFingerprint().equals(current.scopeFingerprint())
                && request.impactFingerprint().equals(current.impactFingerprint());
        if (!unchanged) {
            throw new ScopedTrashSnapshotStaleException("目录内容在确认前后发生了变化，请重新预览后再确认删除。");
        }
        return storageCommandService.moveNodesToTrash(userId, new BatchNodeRequest(currentIds));
    }

    private SourceScope validateScope(Long userId, Long sourceParentId, boolean root, List<String> rawNodeTypes) {
        if (root == (sourceParentId != null)) {
            throw new IllegalArgumentException("根目录范围与父目录编号不一致。");
        }
        StorageNode sourceFolder = null;
        if (!root) {
            sourceFolder = storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(sourceParentId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("源目录不存在。"));
            if (sourceFolder.getNodeType() != NodeType.FOLDER) {
                throw new IllegalArgumentException("源节点不是文件夹。");
            }
        }
        EnumSet<NodeType> nodeTypes = EnumSet.noneOf(NodeType.class);
        if (rawNodeTypes != null) {
            for (String value : rawNodeTypes) {
                try {
                    NodeType type = NodeType.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
                    nodeTypes.add(type);
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("集合节点类型只能是 FILE 或 FOLDER。");
                }
            }
        }
        if (nodeTypes.isEmpty()) {
            throw new IllegalArgumentException("集合节点类型不能为空。");
        }
        return new SourceScope(sourceParentId, root, Set.copyOf(nodeTypes), sourceFolder);
    }

    private List<StorageNode> selectedNodes(Long userId, SourceScope scope) {
        NodeType type = scope.nodeTypes().size() == 1 ? scope.nodeTypes().iterator().next() : null;
        Page<StorageNode> page = storageNodeRepository.searchNodes(
                userId,
                scope.parentId(),
                null,
                type,
                PageRequest.of(0, maxSelectedNodes + 1, Sort.by(Sort.Direction.ASC, "id"))
        );
        return page.getContent().stream()
                .filter(node -> scope.nodeTypes().contains(node.getNodeType()))
                .sorted(Comparator.comparing(StorageNode::getId))
                .toList();
    }

    private Impact collectImpact(Long userId, List<StorageNode> selected) {
        List<StorageNode> nodes = new ArrayList<>();
        ArrayDeque<StorageNode> pending = new ArrayDeque<>(selected);
        Set<Long> visited = new LinkedHashSet<>();
        while (!pending.isEmpty()) {
            StorageNode node = pending.removeFirst();
            if (node.getId() == null || !visited.add(node.getId())) {
                continue;
            }
            nodes.add(node);
            if (nodes.size() > maxImpactNodes) {
                return new Impact(List.copyOf(nodes), true);
            }
            if (node.getNodeType() == NodeType.FOLDER) {
                int remaining = maxImpactNodes - nodes.size() - pending.size();
                Page<StorageNode> childPage = storageNodeRepository.searchNodes(
                        userId,
                        node.getId(),
                        null,
                        null,
                        PageRequest.of(0, Math.max(1, remaining + 1), Sort.by(Sort.Direction.ASC, "id"))
                );
                if (childPage.getTotalElements() > remaining) {
                    return new Impact(List.copyOf(nodes), true);
                }
                pending.addAll(childPage.getContent());
            }
        }
        nodes.sort(Comparator.comparing(StorageNode::getId));
        return new Impact(List.copyOf(nodes), false);
    }

    private ScopedTrashPreviewResponse response(SourceScope scope, List<StorageNode> selected, Impact impact) {
        int selectedFileCount = (int) selected.stream().filter(node -> node.getNodeType() == NodeType.FILE).count();
        int selectedFolderCount = selected.size() - selectedFileCount;
        boolean executable = !selected.isEmpty()
                && selected.size() <= maxSelectedNodes
                && !impact.limitExceeded();
        String message;
        if (selected.isEmpty()) {
            message = "这个目录范围内没有匹配的项目。";
        } else if (selected.size() > maxSelectedNodes) {
            message = "直属项目超过 " + maxSelectedNodes + " 个，请缩小范围后再试。";
        } else if (impact.limitExceeded()) {
            message = "文件夹子树的实际影响节点超过 " + maxImpactNodes + " 个，请缩小范围后再试。";
        } else {
            message = "已核对直属项目和文件夹子树的完整影响范围。";
        }
        return new ScopedTrashPreviewResponse(
                SELECTOR_VERSION,
                scope.parentId(),
                scope.root(),
                scope.nodeTypes().stream().map(Enum::name).sorted().toList(),
                selected.stream().limit(maxSelectedNodes + 1L).map(this::toSummary).toList(),
                selectedFileCount,
                selectedFolderCount,
                Math.max(0, impact.nodes().size() - selected.size()),
                impact.nodes().size(),
                fingerprint(scope, selected),
                fingerprint(scope, impact.nodes()),
                executable,
                message
        );
    }

    private String fingerprint(SourceScope scope, List<StorageNode> nodes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String header = encodedField(scope.root())
                    + encodedField(scope.parentId())
                    + encodedField(scope.nodeTypes().stream().map(Enum::name).sorted().toList())
                    + encodedField(nodeState(scope.sourceFolder()));
            digest.update(header.getBytes(StandardCharsets.UTF_8));
            nodes.stream().sorted(Comparator.comparing(StorageNode::getId)).forEach(node -> {
                String row = "\n" + nodeState(node);
                digest.update(row.getBytes(StandardCharsets.UTF_8));
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成集合删除快照指纹。", exception);
        }
    }

    private String nodeState(StorageNode node) {
        if (node == null) {
            return "ROOT";
        }
        return encodedField(node.getId())
                + encodedField(node.getParentId())
                + encodedField(node.getNodeType())
                + encodedField(node.getNodeName())
                + encodedField(node.getUpdatedAt());
    }

    private String encodedField(Object value) {
        String text = Objects.toString(value, "");
        return text.length() + ":" + text;
    }

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

    private record SourceScope(
            Long parentId,
            boolean root,
            Set<NodeType> nodeTypes,
            StorageNode sourceFolder
    ) {
    }

    private record Impact(List<StorageNode> nodes, boolean limitExceeded) {
    }
}
