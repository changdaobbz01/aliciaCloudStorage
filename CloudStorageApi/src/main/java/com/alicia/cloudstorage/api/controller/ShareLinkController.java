package com.alicia.cloudstorage.api.controller;

import com.alicia.cloudstorage.api.principal.PrincipalRequestAttributes;
import com.alicia.cloudstorage.api.principal.CurrentPrincipal;
import com.alicia.cloudstorage.api.dto.CreateShareLinkRequest;
import com.alicia.cloudstorage.api.dto.SaveShareLinkRequest;
import com.alicia.cloudstorage.api.dto.ShareLinkDetailResponse;
import com.alicia.cloudstorage.api.dto.ShareLinkSummaryResponse;
import com.alicia.cloudstorage.api.dto.SignedUrlResponse;
import com.alicia.cloudstorage.api.dto.StorageNodeSummaryResponse;
import com.alicia.cloudstorage.api.service.ShareLinkService;
import com.alicia.cloudstorage.api.service.StorageCommandService;
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
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/share-links")
public class ShareLinkController {

    private static final String SHARE_ACCESS_HEADER = "X-Share-Access-Token";
    private static final String VERSIONED_PRIVATE_FILE_CACHE_CONTROL = "private, max-age=2592000, immutable";

    private final ShareLinkService shareLinkService;

    public ShareLinkController(ShareLinkService shareLinkService) {
        this.shareLinkService = shareLinkService;
    }

    @PostMapping
    public ShareLinkSummaryResponse createShareLink(
            @RequestAttribute(PrincipalRequestAttributes.CURRENT_PRINCIPAL) CurrentPrincipal principal,
            @Valid @RequestBody CreateShareLinkRequest request
    ) {
        return shareLinkService.createShareLink(principal.userId(), request);
    }

    @GetMapping("/my")
    public List<ShareLinkSummaryResponse> listMyShareLinks(
            @RequestAttribute(PrincipalRequestAttributes.CURRENT_PRINCIPAL) CurrentPrincipal principal
    ) {
        return shareLinkService.listMyShareLinks(principal.userId());
    }

    @DeleteMapping("/{shareId}")
    public ShareLinkSummaryResponse revokeShareLink(
            @RequestAttribute(PrincipalRequestAttributes.CURRENT_PRINCIPAL) CurrentPrincipal principal,
            @PathVariable Long shareId
    ) {
        return shareLinkService.revokeShareLink(principal.userId(), shareId);
    }

    @GetMapping("/{shareCode}/detail")
    public ShareLinkDetailResponse getShareDetail(
            @RequestAttribute(PrincipalRequestAttributes.CURRENT_PRINCIPAL) CurrentPrincipal principal,
            @PathVariable String shareCode,
            @RequestHeader(value = SHARE_ACCESS_HEADER, required = false) String shareAccessToken
    ) {
        return shareLinkService.getShareDetail(principal.userId(), shareCode, shareAccessToken);
    }

    @PostMapping("/{shareCode}/save")
    public List<StorageNodeSummaryResponse> saveShare(
            @RequestAttribute(PrincipalRequestAttributes.CURRENT_PRINCIPAL) CurrentPrincipal principal,
            @PathVariable String shareCode,
            @RequestHeader(value = SHARE_ACCESS_HEADER, required = false) String shareAccessToken,
            @Valid @RequestBody(required = false) SaveShareLinkRequest request
    ) {
        return shareLinkService.saveShare(principal.userId(), shareCode, shareAccessToken, request);
    }

    @GetMapping("/{shareCode}/files/{fileId}/access-url")
    public SignedUrlResponse getShareFileAccessUrl(
            @RequestAttribute(PrincipalRequestAttributes.CURRENT_PRINCIPAL) CurrentPrincipal principal,
            @PathVariable String shareCode,
            @PathVariable Long fileId,
            @RequestHeader(value = SHARE_ACCESS_HEADER, required = false) String shareAccessToken,
            @RequestParam(required = false, defaultValue = "inline") String disposition
    ) {
        boolean attachment = "attachment".equalsIgnoreCase(disposition) || "download".equalsIgnoreCase(disposition);
        StorageCommandService.StorageAccessUrlPayload payload = shareLinkService.createShareFileAccessUrl(
                principal.userId(),
                shareCode,
                fileId,
                shareAccessToken,
                attachment
        );
        return new SignedUrlResponse(
                payload.url(),
                payload.fileName(),
                payload.contentType(),
                payload.expiresAtEpochMillis()
        );
    }

    @GetMapping("/{shareCode}/files/{fileId}/download")
    public ResponseEntity<InputStreamResource> downloadShareFile(
            @RequestAttribute(PrincipalRequestAttributes.CURRENT_PRINCIPAL) CurrentPrincipal principal,
            @PathVariable String shareCode,
            @PathVariable Long fileId,
            @RequestHeader(value = SHARE_ACCESS_HEADER, required = false) String shareAccessToken
    ) {
        StorageCommandService.StorageDownloadPayload downloadPayload = shareLinkService.downloadShareFile(
                principal.userId(),
                shareCode,
                fileId,
                shareAccessToken
        );
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;

        if (downloadPayload.contentType() != null && !downloadPayload.contentType().isBlank()) {
            mediaType = MediaType.parseMediaType(downloadPayload.contentType());
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, VERSIONED_PRIVATE_FILE_CACHE_CONTROL)
                .header(HttpHeaders.VARY, HttpHeaders.AUTHORIZATION)
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
