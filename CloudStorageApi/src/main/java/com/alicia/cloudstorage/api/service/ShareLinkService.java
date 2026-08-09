package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.CreateShareLinkRequest;
import com.alicia.cloudstorage.api.dto.SaveShareLinkRequest;
import com.alicia.cloudstorage.api.dto.ShareLinkDetailResponse;
import com.alicia.cloudstorage.api.dto.ShareLinkStatusResponse;
import com.alicia.cloudstorage.api.dto.ShareLinkSummaryResponse;
import com.alicia.cloudstorage.api.dto.StorageNodeSummaryResponse;
import com.alicia.cloudstorage.api.dto.VerifySharePasswordRequest;
import com.alicia.cloudstorage.api.dto.VerifySharePasswordResponse;
import com.alicia.cloudstorage.api.entity.NodeType;
import com.alicia.cloudstorage.api.entity.ShareLink;
import com.alicia.cloudstorage.api.entity.ShareLinkItem;
import com.alicia.cloudstorage.api.entity.ShareLinkStatus;
import com.alicia.cloudstorage.api.entity.StorageNode;
import com.alicia.cloudstorage.api.entity.SysUser;
import com.alicia.cloudstorage.api.repository.ShareLinkItemRepository;
import com.alicia.cloudstorage.api.repository.ShareLinkRepository;
import com.alicia.cloudstorage.api.repository.StorageNodeRepository;
import com.alicia.cloudstorage.api.repository.SysUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
public class ShareLinkService {

    private static final int SHARE_CODE_RANDOM_BYTES = 12;
    private static final int SHARE_CODE_MAX_ATTEMPTS = 12;
    private static final int MAX_SHARE_ITEMS = 20;
    private static final int MAX_SAVE_SELECTED_ITEMS = 500;
    private static final int SHARE_NODE_QUERY_BATCH_SIZE = 500;
    private static final int SHARE_ACCESS_TOKEN_EXPIRE_MINUTES = 120;
    private static final int MAX_PASSWORD_FAILURES = 5;
    private static final int PASSWORD_FAILURE_WINDOW_MINUTES = 10;
    private static final int PASSWORD_LOCK_MINUTES = 5;

    private final ShareLinkRepository shareLinkRepository;
    private final ShareLinkItemRepository shareLinkItemRepository;
    private final StorageNodeRepository storageNodeRepository;
    private final SysUserRepository sysUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final StorageCommandService storageCommandService;
    private final CosFileStorageService cosFileStorageService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, PasswordAttemptState> passwordAttemptStates = new ConcurrentHashMap<>();
    private final String tokenSecret;
    private final int maxExpandedNodes;

    public ShareLinkService(
            ShareLinkRepository shareLinkRepository,
            ShareLinkItemRepository shareLinkItemRepository,
            StorageNodeRepository storageNodeRepository,
            SysUserRepository sysUserRepository,
            PasswordEncoder passwordEncoder,
            StorageCommandService storageCommandService,
            CosFileStorageService cosFileStorageService,
            @Value("${alicia.auth.token-secret}") String tokenSecret,
            @Value("${alicia.share.max-expanded-nodes:5000}") int maxExpandedNodes
    ) {
        this.shareLinkRepository = shareLinkRepository;
        this.shareLinkItemRepository = shareLinkItemRepository;
        this.storageNodeRepository = storageNodeRepository;
        this.sysUserRepository = sysUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.storageCommandService = storageCommandService;
        this.cosFileStorageService = cosFileStorageService;
        this.tokenSecret = tokenSecret;
        this.maxExpandedNodes = maxExpandedNodes;
    }

    public ShareLinkSummaryResponse createShareLink(Long ownerId, CreateShareLinkRequest request) {
        List<Long> nodeIds = normalizeNodeIds(request.nodeIds());
        List<StorageNode> rootNodes = collapseSelectedRoots(ownerId, loadOwnedActiveNodes(ownerId, nodeIds));
        collectActiveSharedNodes(rootNodes);
        String passwordHash = normalizePassword(request.password());
        LocalDateTime expiresAt = resolveExpiresAt(request.expiresInDays());

        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode(generateShareCode());
        shareLink.setOwnerId(ownerId);
        shareLink.setTitle(resolveShareTitle(request.title(), rootNodes));
        shareLink.setPasswordHash(passwordHash);
        shareLink.setExpiresAt(expiresAt);
        shareLink.setAllowDownload(request.allowDownload() == null || request.allowDownload());
        shareLink.setAllowSave(request.allowSave() == null || request.allowSave());
        shareLink.setStatus(ShareLinkStatus.ACTIVE);
        shareLink.setViewCount(0L);

        ShareLink savedShareLink = shareLinkRepository.save(shareLink);
        List<ShareLinkItem> shareItems = new ArrayList<>();

        for (int index = 0; index < rootNodes.size(); index += 1) {
            ShareLinkItem item = new ShareLinkItem();
            item.setShareId(savedShareLink.getId());
            item.setNodeId(rootNodes.get(index).getId());
            item.setSortOrder(index);
            shareItems.add(item);
        }

        shareLinkItemRepository.saveAll(shareItems);
        return toSummary(savedShareLink, shareItems.size());
    }

    @Transactional(readOnly = true)
    public List<ShareLinkSummaryResponse> listMyShareLinks(Long ownerId) {
        List<ShareLink> shareLinks = shareLinkRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
        if (shareLinks.isEmpty()) {
            return List.of();
        }

        List<Long> shareIds = shareLinks.stream().map(ShareLink::getId).toList();
        Map<Long, Long> itemCounts = new HashMap<>();
        shareLinkItemRepository.findByShareIdIn(shareIds)
                .forEach(item -> itemCounts.merge(item.getShareId(), 1L, Long::sum));

        return shareLinks.stream()
                .map(shareLink -> toSummary(shareLink, itemCounts.getOrDefault(shareLink.getId(), 0L)))
                .toList();
    }

    public ShareLinkSummaryResponse revokeShareLink(Long ownerId, Long shareId) {
        ShareLink shareLink = shareLinkRepository.findByIdAndOwnerId(shareId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("分享链接不存在。"));
        shareLink.setStatus(ShareLinkStatus.REVOKED);
        ShareLink savedShareLink = shareLinkRepository.save(shareLink);
        return toSummary(savedShareLink, shareLinkItemRepository.countByShareId(savedShareLink.getId()));
    }

    @Transactional(readOnly = true)
    public ShareLinkStatusResponse getPublicStatus(String shareCode) {
        ShareLink shareLink = findByShareCode(shareCode);

        if (shareLink.getStatus() != ShareLinkStatus.ACTIVE) {
            return new ShareLinkStatusResponse(shareLink.getShareCode(), null, false, false, shareLink.getExpiresAt(), "REVOKED");
        }

        if (isExpired(shareLink)) {
            return new ShareLinkStatusResponse(shareLink.getShareCode(), null, false, false, shareLink.getExpiresAt(), "EXPIRED");
        }

        boolean requiresPassword = hasPassword(shareLink);
        return new ShareLinkStatusResponse(
                shareLink.getShareCode(),
                requiresPassword ? null : shareLink.getTitle(),
                true,
                requiresPassword,
                shareLink.getExpiresAt(),
                null
        );
    }

    public VerifySharePasswordResponse verifyPassword(
            String shareCode,
            VerifySharePasswordRequest request,
            String clientAddress
    ) {
        ShareLink shareLink = requireActiveShare(shareCode);

        if (!hasPassword(shareLink)) {
            return new VerifySharePasswordResponse(null, null);
        }

        String attemptKey = buildPasswordAttemptKey(shareLink, clientAddress);
        validatePasswordAttemptAllowed(attemptKey);

        String password = request.password() == null ? "" : request.password().trim();
        if (!passwordEncoder.matches(password, shareLink.getPasswordHash())) {
            recordPasswordFailure(attemptKey);
            throw new IllegalArgumentException("提取码不正确。");
        }

        clearPasswordAttemptState(attemptKey);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(SHARE_ACCESS_TOKEN_EXPIRE_MINUTES);
        return new VerifySharePasswordResponse(createShareAccessToken(shareLink, expiresAt), expiresAt);
    }

    public ShareLinkDetailResponse getShareDetail(Long visitorUserId, String shareCode, String shareAccessToken) {
        ShareLink shareLink = requireActiveShare(shareCode);
        validateShareAccessTokenIfNeeded(shareLink, shareAccessToken);

        List<StorageNode> rootNodes = loadActiveSharedRootNodes(shareLink);
        List<StorageNode> sharedNodes = collectActiveSharedNodes(rootNodes);
        SysUser owner = sysUserRepository.findById(shareLink.getOwnerId())
                .orElseThrow(() -> new IllegalArgumentException("分享者不存在。"));

        shareLink.setViewCount((shareLink.getViewCount() == null ? 0L : shareLink.getViewCount()) + 1L);
        shareLink.setLastAccessedAt(LocalDateTime.now());
        shareLinkRepository.save(shareLink);

        return new ShareLinkDetailResponse(
                shareLink.getShareCode(),
                shareLink.getTitle(),
                owner.getNickname(),
                shareLink.getExpiresAt(),
                shareLink.isAllowDownload(),
                shareLink.isAllowSave(),
                rootNodes.stream().map(StorageNode::getId).toList(),
                sharedNodes.stream().map(this::toNodeSummary).toList()
        );
    }

    public List<StorageNodeSummaryResponse> saveShare(
            Long visitorUserId,
            String shareCode,
            String shareAccessToken,
            SaveShareLinkRequest request
    ) {
        ShareLink shareLink = requireActiveShare(shareCode);
        validateShareAccessTokenIfNeeded(shareLink, shareAccessToken);

        if (!shareLink.isAllowSave()) {
            throw new IllegalArgumentException("分享者未允许保存到网盘。");
        }

        List<StorageNode> rootNodes = loadActiveSharedRootNodes(shareLink);
        Long parentId = request == null ? null : request.parentId();
        List<Long> selectedNodeIds = request == null ? null : request.selectedNodeIds();
        if (selectedNodeIds == null) {
            return storageCommandService.copySharedNodesToUser(visitorUserId, rootNodes, parentId);
        }

        List<StorageNode> selectedRootNodes = resolveSelectedSharedRootNodes(shareLink.getOwnerId(), rootNodes, selectedNodeIds);
        return storageCommandService.copySharedNodesToUser(visitorUserId, selectedRootNodes, parentId);
    }

    @Transactional(readOnly = true)
    public StorageCommandService.StorageAccessUrlPayload createShareFileAccessUrl(
            Long visitorUserId,
            String shareCode,
            Long fileId,
            String shareAccessToken,
            boolean attachment
    ) {
        ShareLink shareLink = requireActiveShare(shareCode);
        validateShareAccessTokenIfNeeded(shareLink, shareAccessToken);
        validateDownloadAllowed(shareLink);
        StorageNode fileNode = requireSharedFile(shareLink, fileId);
        CosFileStorageService.PresignedCosUrl presignedUrl = attachment
                ? cosFileStorageService.createAttachmentDownloadUrl(
                        fileNode.getStoragePath(),
                        fileNode.getMimeType(),
                        fileNode.getNodeName()
                )
                : cosFileStorageService.createInlineDownloadUrl(
                        fileNode.getStoragePath(),
                        fileNode.getMimeType(),
                        fileNode.getNodeName()
                );

        return new StorageCommandService.StorageAccessUrlPayload(
                presignedUrl.url(),
                fileNode.getNodeName(),
                fileNode.getMimeType(),
                presignedUrl.expiresAtEpochMillis()
        );
    }

    @Transactional(readOnly = true)
    public StorageCommandService.StorageDownloadPayload downloadShareFile(
            Long visitorUserId,
            String shareCode,
            Long fileId,
            String shareAccessToken
    ) {
        ShareLink shareLink = requireActiveShare(shareCode);
        validateShareAccessTokenIfNeeded(shareLink, shareAccessToken);
        validateDownloadAllowed(shareLink);
        StorageNode fileNode = requireSharedFile(shareLink, fileId);
        CosFileStorageService.DownloadedCosFile downloadedCosFile = cosFileStorageService.openFileStream(fileNode.getStoragePath());

        return new StorageCommandService.StorageDownloadPayload(
                fileNode.getNodeName(),
                fileNode.getMimeType() == null || fileNode.getMimeType().isBlank()
                        ? downloadedCosFile.contentType()
                        : fileNode.getMimeType(),
                downloadedCosFile.contentLength(),
                downloadedCosFile.inputStream()
        );
    }

    private ShareLink findByShareCode(String rawShareCode) {
        String shareCode = normalizeShareCode(rawShareCode);
        return shareLinkRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new IllegalArgumentException("分享链接不存在。"));
    }

    private ShareLink requireActiveShare(String shareCode) {
        ShareLink shareLink = findByShareCode(shareCode);

        if (shareLink.getStatus() != ShareLinkStatus.ACTIVE) {
            throw new IllegalArgumentException("分享链接已取消。");
        }

        if (isExpired(shareLink)) {
            throw new IllegalArgumentException("分享链接已过期。");
        }

        return shareLink;
    }

    private List<StorageNode> loadActiveSharedRootNodes(ShareLink shareLink) {
        List<ShareLinkItem> shareItems = shareLinkItemRepository.findByShareIdOrderBySortOrderAsc(shareLink.getId());
        List<StorageNode> rootNodes = new ArrayList<>();

        for (ShareLinkItem shareItem : shareItems) {
            storageNodeRepository.findByIdAndOwnerIdAndDeletedFalse(shareItem.getNodeId(), shareLink.getOwnerId())
                    .ifPresent(rootNodes::add);
        }

        if (rootNodes.isEmpty()) {
            throw new IllegalArgumentException("分享内容不再可用。");
        }

        return rootNodes;
    }

    private List<StorageNode> collectActiveSharedNodes(List<StorageNode> rootNodes) {
        List<StorageNode> nodes = new ArrayList<>();
        Set<Long> visitedIds = new HashSet<>();
        List<Long> folderIds = new ArrayList<>();

        for (StorageNode rootNode : rootNodes) {
            addExpandedNode(rootNode, nodes, visitedIds, folderIds);
        }

        if (rootNodes.isEmpty()) {
            return nodes;
        }

        Long ownerId = rootNodes.get(0).getOwnerId();
        while (!folderIds.isEmpty()) {
            List<StorageNode> children = new ArrayList<>();
            for (int start = 0; start < folderIds.size(); start += SHARE_NODE_QUERY_BATCH_SIZE) {
                int end = Math.min(start + SHARE_NODE_QUERY_BATCH_SIZE, folderIds.size());
                children.addAll(storageNodeRepository
                        .findByOwnerIdAndParentIdInAndDeletedFalseOrderByParentIdAscIdAsc(
                                ownerId,
                                folderIds.subList(start, end)
                        ));
            }

            Map<Long, List<StorageNode>> childrenByParentId = new HashMap<>();
            children.forEach(child -> childrenByParentId
                    .computeIfAbsent(child.getParentId(), ignored -> new ArrayList<>())
                    .add(child));

            List<Long> nextFolderIds = new ArrayList<>();
            for (Long folderId : folderIds) {
                for (StorageNode child : childrenByParentId.getOrDefault(folderId, List.of())) {
                    addExpandedNode(child, nodes, visitedIds, nextFolderIds);
                }
            }
            folderIds = nextFolderIds;
        }

        return nodes;
    }

    private void addExpandedNode(
            StorageNode node,
            List<StorageNode> nodes,
            Set<Long> visitedIds,
            List<Long> folderIds
    ) {
        if (!visitedIds.add(node.getId())) {
            return;
        }

        if (nodes.size() >= maxExpandedNodes) {
            throw new IllegalArgumentException("分享内容过多，请拆分后重新分享。");
        }

        nodes.add(node);
        if (node.getNodeType() == NodeType.FOLDER) {
            folderIds.add(node.getId());
        }
    }

    private StorageNode requireSharedFile(ShareLink shareLink, Long fileId) {
        if (fileId == null) {
            throw new IllegalArgumentException("文件编号不能为空。");
        }

        Map<Long, StorageNode> sharedNodeMap = new HashMap<>();
        collectActiveSharedNodes(loadActiveSharedRootNodes(shareLink))
                .forEach(node -> sharedNodeMap.put(node.getId(), node));
        StorageNode fileNode = sharedNodeMap.get(fileId);

        if (fileNode == null || fileNode.getNodeType() != NodeType.FILE) {
            throw new IllegalArgumentException("分享文件不存在。");
        }

        if (fileNode.getStoragePath() == null || fileNode.getStoragePath().isBlank()) {
            throw new IllegalArgumentException("分享文件不再可用。");
        }

        return fileNode;
    }

    private List<StorageNode> resolveSelectedSharedRootNodes(
            Long ownerId,
            List<StorageNode> rootNodes,
            List<Long> rawSelectedNodeIds
    ) {
        List<Long> selectedNodeIds = normalizeSaveSelectedNodeIds(rawSelectedNodeIds);
        Map<Long, StorageNode> sharedNodeMap = new HashMap<>();
        collectActiveSharedNodes(rootNodes).forEach(node -> sharedNodeMap.put(node.getId(), node));

        List<StorageNode> selectedNodes = new ArrayList<>();
        for (Long selectedNodeId : selectedNodeIds) {
            StorageNode selectedNode = sharedNodeMap.get(selectedNodeId);
            if (selectedNode == null) {
                throw new IllegalArgumentException("Selected share item is not available.");
            }
            selectedNodes.add(selectedNode);
        }

        return collapseSelectedRoots(ownerId, selectedNodes);
    }

    private List<Long> normalizeSaveSelectedNodeIds(List<Long> rawSelectedNodeIds) {
        if (rawSelectedNodeIds == null || rawSelectedNodeIds.isEmpty()) {
            throw new IllegalArgumentException("Please select at least one share item to save.");
        }

        if (rawSelectedNodeIds.size() > MAX_SAVE_SELECTED_ITEMS) {
            throw new IllegalArgumentException("Too many selected share items.");
        }

        LinkedHashSet<Long> uniqueNodeIds = new LinkedHashSet<>();
        for (Long rawSelectedNodeId : rawSelectedNodeIds) {
            if (rawSelectedNodeId == null) {
                throw new IllegalArgumentException("Selected share item id must not be null.");
            }
            uniqueNodeIds.add(rawSelectedNodeId);
        }

        return List.copyOf(uniqueNodeIds);
    }

    private void validateDownloadAllowed(ShareLink shareLink) {
        if (!shareLink.isAllowDownload()) {
            throw new IllegalArgumentException("分享者未允许下载。");
        }
    }

    private void validateShareAccessTokenIfNeeded(ShareLink shareLink, String shareAccessToken) {
        if (!hasPassword(shareLink)) {
            return;
        }

        if (shareAccessToken == null || shareAccessToken.isBlank()) {
            throw new IllegalArgumentException("请先输入分享提取码。");
        }

        String[] parts = shareAccessToken.trim().split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("分享提取码校验已失效，请重新输入。");
        }

        String encodedPayload = parts[0];
        String signature = parts[1];
        String expectedSignature = sign(encodedPayload);

        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new IllegalArgumentException("分享提取码校验已失效，请重新输入。");
        }

        String payload = base64UrlDecode(encodedPayload);
        String[] payloadParts = payload.split(":");
        if (payloadParts.length != 3) {
            throw new IllegalArgumentException("分享提取码校验已失效，请重新输入。");
        }

        long shareId = parseLong(payloadParts[0], "分享编号不正确。");
        long expiresAtEpochSecond = parseLong(payloadParts[2], "分享提取码校验已失效，请重新输入。");

        if (!shareLink.getId().equals(shareId) || !shareLink.getShareCode().equals(payloadParts[1])) {
            throw new IllegalArgumentException("分享提取码校验已失效，请重新输入。");
        }

        if (System.currentTimeMillis() / 1000L >= expiresAtEpochSecond) {
            throw new IllegalArgumentException("分享提取码校验已过期，请重新输入。");
        }
    }

    private String createShareAccessToken(ShareLink shareLink, LocalDateTime expiresAt) {
        long epochSecond = expiresAt.atZone(ZoneId.systemDefault()).toEpochSecond();
        String payload = shareLink.getId() + ":" + shareLink.getShareCode() + ":" + epochSecond;
        String encodedPayload = base64UrlEncode(payload);

        return encodedPayload + "." + sign(encodedPayload);
    }

    private String generateShareCode() {
        for (int attempt = 0; attempt < SHARE_CODE_MAX_ATTEMPTS; attempt += 1) {
            byte[] randomBytes = new byte[SHARE_CODE_RANDOM_BYTES];
            secureRandom.nextBytes(randomBytes);
            String shareCode = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

            if (!shareLinkRepository.existsByShareCode(shareCode)) {
                return shareCode;
            }
        }

        throw new IllegalStateException("生成分享链接失败，请稍后重试。");
    }

    private String normalizeShareCode(String rawShareCode) {
        if (rawShareCode == null || rawShareCode.isBlank()) {
            throw new IllegalArgumentException("分享链接不存在。");
        }

        String shareCode = rawShareCode.trim();
        if (shareCode.length() > 40) {
            throw new IllegalArgumentException("分享链接不存在。");
        }

        return shareCode;
    }

    private List<Long> normalizeNodeIds(List<Long> rawNodeIds) {
        if (rawNodeIds == null || rawNodeIds.isEmpty()) {
            throw new IllegalArgumentException("请选择要分享的文件或文件夹。");
        }

        if (rawNodeIds.size() > MAX_SHARE_ITEMS) {
            throw new IllegalArgumentException("单个分享最多包含 20 个项目。");
        }

        LinkedHashSet<Long> uniqueNodeIds = new LinkedHashSet<>();
        for (Long rawNodeId : rawNodeIds) {
            if (rawNodeId == null) {
                throw new IllegalArgumentException("分享项目编号不能为空。");
            }

            uniqueNodeIds.add(rawNodeId);
        }

        return List.copyOf(uniqueNodeIds);
    }

    private List<StorageNode> loadOwnedActiveNodes(Long ownerId, List<Long> nodeIds) {
        List<StorageNode> foundNodes = storageNodeRepository.findByOwnerIdAndIdInAndDeletedFalse(ownerId, nodeIds);
        if (foundNodes.size() != nodeIds.size()) {
            throw new IllegalArgumentException("分享项目不存在或已被删除。");
        }

        Map<Long, StorageNode> nodeMap = new HashMap<>();
        foundNodes.forEach(node -> nodeMap.put(node.getId(), node));

        List<StorageNode> orderedNodes = new ArrayList<>();
        for (Long nodeId : nodeIds) {
            StorageNode node = nodeMap.get(nodeId);
            if (node == null) {
                throw new IllegalArgumentException("分享项目不存在或已被删除。");
            }

            orderedNodes.add(node);
        }

        return orderedNodes;
    }

    private List<StorageNode> collapseSelectedRoots(Long ownerId, List<StorageNode> nodes) {
        Set<Long> selectedIds = new HashSet<>();
        nodes.forEach(node -> selectedIds.add(node.getId()));
        List<StorageNode> rootNodes = new ArrayList<>();

        for (StorageNode node : nodes) {
            if (!hasSelectedAncestor(ownerId, node.getParentId(), selectedIds)) {
                rootNodes.add(node);
            }
        }

        return rootNodes;
    }

    private boolean hasSelectedAncestor(Long ownerId, Long parentId, Set<Long> selectedIds) {
        Long cursorParentId = parentId;

        while (cursorParentId != null) {
            if (selectedIds.contains(cursorParentId)) {
                return true;
            }

            StorageNode parentNode = storageNodeRepository.findByIdAndOwnerId(cursorParentId, ownerId).orElse(null);
            cursorParentId = parentNode == null ? null : parentNode.getParentId();
        }

        return false;
    }

    private String resolveShareTitle(String rawTitle, List<StorageNode> rootNodes) {
        if (rawTitle != null && !rawTitle.isBlank()) {
            String title = rawTitle.trim();
            if (title.length() > 255) {
                throw new IllegalArgumentException("分享名称不能超过 255 个字符。");
            }

            return title;
        }

        if (rootNodes.size() == 1) {
            return rootNodes.get(0).getNodeName();
        }

        return "共 " + rootNodes.size() + " 项分享内容";
    }

    private String normalizePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return null;
        }

        String password = rawPassword.trim();
        if (password.length() < 4 || password.length() > 32) {
            throw new IllegalArgumentException("分享提取码长度必须在 4 到 32 个字符之间。");
        }

        return passwordEncoder.encode(password);
    }

    private LocalDateTime resolveExpiresAt(Integer expiresInDays) {
        if (expiresInDays == null || expiresInDays == 0) {
            return null;
        }

        if (expiresInDays < 0 || expiresInDays > 365) {
            throw new IllegalArgumentException("分享有效期必须介于 1 到 365 天之间。");
        }

        return LocalDateTime.now().plusDays(expiresInDays);
    }

    private boolean isExpired(ShareLink shareLink) {
        return shareLink.getExpiresAt() != null && LocalDateTime.now().isAfter(shareLink.getExpiresAt());
    }

    private boolean hasPassword(ShareLink shareLink) {
        return shareLink.getPasswordHash() != null && !shareLink.getPasswordHash().isBlank();
    }

    private String buildPasswordAttemptKey(ShareLink shareLink, String clientAddress) {
        String normalizedClientAddress = clientAddress == null || clientAddress.isBlank()
                ? "unknown"
                : clientAddress.trim();

        return shareLink.getShareCode() + ":" + normalizedClientAddress;
    }

    private synchronized void validatePasswordAttemptAllowed(String attemptKey) {
        PasswordAttemptState state = passwordAttemptStates.get(attemptKey);
        if (state == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (state.lockedUntil != null && now.isBefore(state.lockedUntil)) {
            throw new IllegalArgumentException("提取码错误次数过多，请稍后再试。");
        }

        if (now.isAfter(state.windowStartedAt.plusMinutes(PASSWORD_FAILURE_WINDOW_MINUTES))) {
            passwordAttemptStates.remove(attemptKey);
        }
    }

    private synchronized void recordPasswordFailure(String attemptKey) {
        LocalDateTime now = LocalDateTime.now();
        PasswordAttemptState state = passwordAttemptStates.get(attemptKey);

        if (state == null || now.isAfter(state.windowStartedAt.plusMinutes(PASSWORD_FAILURE_WINDOW_MINUTES))) {
            passwordAttemptStates.put(attemptKey, new PasswordAttemptState(now, 1, null));
            return;
        }

        state.failureCount += 1;
        if (state.failureCount >= MAX_PASSWORD_FAILURES) {
            state.lockedUntil = now.plusMinutes(PASSWORD_LOCK_MINUTES);
        }
    }

    private synchronized void clearPasswordAttemptState(String attemptKey) {
        passwordAttemptStates.remove(attemptKey);
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(tokenSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (Exception exception) {
            throw new IllegalStateException("生成分享访问凭证失败。", exception);
        }
    }

    private String base64UrlEncode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String base64UrlDecode(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("分享提取码校验已失效，请重新输入。");
        }
    }

    private long parseLong(String value, String message) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(message);
        }
    }

    private ShareLinkSummaryResponse toSummary(ShareLink shareLink, long itemCount) {
        return new ShareLinkSummaryResponse(
                shareLink.getId(),
                shareLink.getShareCode(),
                shareLink.getTitle(),
                hasPassword(shareLink),
                shareLink.getExpiresAt(),
                shareLink.isAllowDownload(),
                shareLink.isAllowSave(),
                isExpired(shareLink) && shareLink.getStatus() == ShareLinkStatus.ACTIVE
                        ? "EXPIRED"
                        : shareLink.getStatus().name(),
                shareLink.getViewCount(),
                shareLink.getLastAccessedAt(),
                shareLink.getCreatedAt(),
                shareLink.getUpdatedAt(),
                itemCount
        );
    }

    private StorageNodeSummaryResponse toNodeSummary(StorageNode node) {
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

    private static class PasswordAttemptState {

        private final LocalDateTime windowStartedAt;
        private int failureCount;
        private LocalDateTime lockedUntil;

        private PasswordAttemptState(LocalDateTime windowStartedAt, int failureCount, LocalDateTime lockedUntil) {
            this.windowStartedAt = windowStartedAt;
            this.failureCount = failureCount;
            this.lockedUntil = lockedUntil;
        }
    }
}
