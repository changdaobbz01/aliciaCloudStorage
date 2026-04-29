package com.alicia.cloudstorage.api.controller;

import com.alicia.cloudstorage.api.auth.AuthRequestAttributes;
import com.alicia.cloudstorage.api.dto.ApiMessageResponse;
import com.alicia.cloudstorage.api.dto.BatchMoveNodeRequest;
import com.alicia.cloudstorage.api.dto.BatchNodeRequest;
import com.alicia.cloudstorage.api.dto.CreateFolderRequest;
import com.alicia.cloudstorage.api.dto.CreateMultipartUploadRequest;
import com.alicia.cloudstorage.api.dto.DriveOverviewResponse;
import com.alicia.cloudstorage.api.dto.MoveNodeRequest;
import com.alicia.cloudstorage.api.dto.MultipartUploadPartResponse;
import com.alicia.cloudstorage.api.dto.MultipartUploadStatusResponse;
import com.alicia.cloudstorage.api.dto.PageResponse;
import com.alicia.cloudstorage.api.dto.RenameNodeRequest;
import com.alicia.cloudstorage.api.dto.StorageNodeSummaryResponse;
import com.alicia.cloudstorage.api.dto.UsageHistoryPointResponse;
import com.alicia.cloudstorage.api.service.StorageCommandService;
import com.alicia.cloudstorage.api.service.StorageMultipartUploadService;
import com.alicia.cloudstorage.api.service.StorageQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/storage")
public class StorageNodeController {

    private final StorageQueryService storageQueryService;
    private final StorageCommandService storageCommandService;
    private final StorageMultipartUploadService storageMultipartUploadService;

    public StorageNodeController(
            StorageQueryService storageQueryService,
            StorageCommandService storageCommandService,
            StorageMultipartUploadService storageMultipartUploadService
    ) {
        this.storageQueryService = storageQueryService;
        this.storageCommandService = storageCommandService;
        this.storageMultipartUploadService = storageMultipartUploadService;
    }

    /**
     * 查询当前目录下的文件和文件夹，并支持关键字和类型筛选。
     */
    @GetMapping("/nodes")
    public PageResponse<StorageNodeSummaryResponse> listNodes(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection
    ) {
        return storageQueryService.listNodes(userId, parentId, keyword, type, page, size, sortBy, sortDirection);
    }

    /**
     * 查询当前用户所有可作为移动目标的文件夹。
     */
    @GetMapping("/folders")
    public List<StorageNodeSummaryResponse> listFolders(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId
    ) {
        return storageQueryService.listFolders(userId);
    }

    /**
     * 查询回收站根节点，并支持关键字和类型筛选。
     */
    @GetMapping("/trash")
    public PageResponse<StorageNodeSummaryResponse> listTrashNodes(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection
    ) {
        return storageQueryService.listTrashNodes(userId, keyword, type, page, size, sortBy, sortDirection);
    }

    /**
     * 返回当前用户云盘首页需要展示的统计概览数据。
     */
    @GetMapping("/overview")
    public DriveOverviewResponse getOverview(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId
    ) {
        return storageQueryService.getOverview(userId);
    }

    /**
     * 返回当前用户近一段时间的空间占用趋势。
     */
    @GetMapping("/usage-history")
    public List<UsageHistoryPointResponse> getUsageHistory(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId,
            @RequestParam(required = false) Integer days
    ) {
        return storageQueryService.getUsageHistory(userId, days);
    }

    /**
     * 在当前目录下新建一个文件夹。
     */
    @PostMapping("/folders")
    public StorageNodeSummaryResponse createFolder(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId,
            @Valid @RequestBody CreateFolderRequest request
    ) {
        return storageCommandService.createFolder(userId, request);
    }

    /**
     * 接收前端上传文件，将文件写入 COS 并登记到元数据表。
     */
    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StorageNodeSummaryResponse uploadFile(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId,
            @RequestParam(required = false) Long parentId,
            @RequestPart("file") MultipartFile file
    ) {
        return storageCommandService.uploadFile(userId, parentId, file);
    }

    /**
     * 初始化或复用分片上传会话，返回已上传分片列表供前端断点续传。
     */
    @PostMapping("/files/multipart")
    public MultipartUploadStatusResponse createMultipartUpload(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId,
            @Valid @RequestBody CreateMultipartUploadRequest request
    ) {
        return storageMultipartUploadService.createMultipartUpload(userId, request);
    }

    /**
     * 查询分片上传会话状态。
     */
    @GetMapping("/files/multipart/{uploadToken}")
    public MultipartUploadStatusResponse getMultipartUploadStatus(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId,
            @PathVariable String uploadToken
    ) {
        return storageMultipartUploadService.getMultipartUploadStatus(userId, uploadToken);
    }

    /**
     * 上传指定编号的文件分片。
     */
    @PostMapping(value = "/files/multipart/{uploadToken}/parts/{partNumber}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public MultipartUploadPartResponse uploadMultipartPart(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId,
            @PathVariable String uploadToken,
            @PathVariable int partNumber,
            HttpServletRequest servletRequest
    ) {
        return storageMultipartUploadService.uploadPart(userId, uploadToken, partNumber, servletRequest);
    }

    /**
     * 合并所有分片并登记正式文件节点。
     */
    @PostMapping("/files/multipart/{uploadToken}/complete")
    public StorageNodeSummaryResponse completeMultipartUpload(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId,
            @PathVariable String uploadToken
    ) {
        return storageMultipartUploadService.completeMultipartUpload(userId, uploadToken);
    }

    /**
     * 取消未完成的分片上传会话。
     */
    @DeleteMapping("/files/multipart/{uploadToken}")
    public ApiMessageResponse abortMultipartUpload(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId,
            @PathVariable String uploadToken
    ) {
        return storageMultipartUploadService.abortMultipartUpload(userId, uploadToken);
    }

    /**
     * 重命名当前用户自己的文件或文件夹。
     */
    @PutMapping("/nodes/{nodeId}/rename")
    public StorageNodeSummaryResponse renameNode(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId,
            @PathVariable Long nodeId,
            @Valid @RequestBody RenameNodeRequest request
    ) {
        return storageCommandService.renameNode(userId, nodeId, request);
    }

    /**
     * 移动当前用户自己的文件或文件夹到新的父目录。
     */
    @PutMapping("/nodes/{nodeId}/move")
    public StorageNodeSummaryResponse moveNode(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId,
            @PathVariable Long nodeId,
            @Valid @RequestBody MoveNodeRequest request
    ) {
        return storageCommandService.moveNode(userId, nodeId, request);
    }

    /**
     * 批量移动当前用户自己的文件或文件夹到新的父目录。
     */
    @PutMapping("/nodes/batch/move")
    public List<StorageNodeSummaryResponse> moveNodes(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId,
            @Valid @RequestBody BatchMoveNodeRequest request
    ) {
        return storageCommandService.moveNodes(userId, request);
    }

    /**
     * 将当前用户自己的文件或文件夹移入回收站。
     */
    @DeleteMapping("/nodes/{nodeId}")
    public ApiMessageResponse moveNodeToTrash(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId,
            @PathVariable Long nodeId
    ) {
        return storageCommandService.moveNodeToTrash(userId, nodeId);
    }

    /**
     * 批量将当前用户自己的文件或文件夹移入回收站。
     */
    @PostMapping("/nodes/batch/trash")
    public ApiMessageResponse moveNodesToTrash(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId,
            @Valid @RequestBody BatchNodeRequest request
    ) {
        return storageCommandService.moveNodesToTrash(userId, request);
    }

    /**
     * 从回收站恢复当前用户自己的文件或文件夹。
     */
    @PostMapping("/trash/{nodeId}/restore")
    public StorageNodeSummaryResponse restoreNode(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId,
            @PathVariable Long nodeId
    ) {
        return storageCommandService.restoreNode(userId, nodeId);
    }

    /**
     * 批量从回收站恢复当前用户自己的文件或文件夹。
     */
    @PostMapping("/trash/batch/restore")
    public List<StorageNodeSummaryResponse> restoreNodes(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId,
            @Valid @RequestBody BatchNodeRequest request
    ) {
        return storageCommandService.restoreNodes(userId, request);
    }

    /**
     * 从回收站彻底删除当前用户自己的文件或文件夹。
     */
    @DeleteMapping("/trash/{nodeId}")
    public ApiMessageResponse permanentlyDeleteNode(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId,
            @PathVariable Long nodeId
    ) {
        return storageCommandService.permanentlyDeleteNode(userId, nodeId);
    }

    /**
     * 批量从回收站彻底删除当前用户自己的文件或文件夹。
     */
    @PostMapping("/trash/batch/delete")
    public ApiMessageResponse permanentlyDeleteNodes(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId,
            @Valid @RequestBody BatchNodeRequest request
    ) {
        return storageCommandService.permanentlyDeleteNodes(userId, request);
    }

    /**
     * 根据文件节点编号下载当前用户自己的文件内容。
     */
    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<InputStreamResource> downloadFile(
            @RequestAttribute(AuthRequestAttributes.CURRENT_USER_ID) Long userId,
            @PathVariable Long fileId
    ) {
        StorageCommandService.StorageDownloadPayload downloadPayload = storageCommandService.downloadFile(userId, fileId);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;

        if (downloadPayload.contentType() != null && !downloadPayload.contentType().isBlank()) {
            mediaType = MediaType.parseMediaType(downloadPayload.contentType());
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(downloadPayload.contentLength())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(downloadPayload.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(new InputStreamResource(downloadPayload.inputStream()));
    }
}
