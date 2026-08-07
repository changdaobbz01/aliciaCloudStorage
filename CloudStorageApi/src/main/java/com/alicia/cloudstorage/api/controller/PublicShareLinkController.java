package com.alicia.cloudstorage.api.controller;

import com.alicia.cloudstorage.api.dto.ShareLinkStatusResponse;
import com.alicia.cloudstorage.api.dto.VerifySharePasswordRequest;
import com.alicia.cloudstorage.api.dto.VerifySharePasswordResponse;
import com.alicia.cloudstorage.api.service.ShareLinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/share-links")
public class PublicShareLinkController {

    private final ShareLinkService shareLinkService;

    public PublicShareLinkController(ShareLinkService shareLinkService) {
        this.shareLinkService = shareLinkService;
    }

    @GetMapping("/{shareCode}/status")
    public ShareLinkStatusResponse getPublicStatus(@PathVariable String shareCode) {
        return shareLinkService.getPublicStatus(shareCode);
    }

    @PostMapping("/{shareCode}/verify-password")
    public VerifySharePasswordResponse verifyPassword(
            @PathVariable String shareCode,
            @Valid @RequestBody VerifySharePasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        return shareLinkService.verifyPassword(shareCode, request, resolveClientAddress(servletRequest));
    }

    private String resolveClientAddress(HttpServletRequest servletRequest) {
        String forwardedFor = servletRequest.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = servletRequest.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return servletRequest.getRemoteAddr();
    }
}
