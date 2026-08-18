package com.alicia.cloudstorage.api.controller;

import com.alicia.cloudstorage.api.auth.AuthRequestAttributes;
import com.alicia.cloudstorage.api.auth.CurrentPrincipal;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.service.CloudProfileManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

@RestController
@RequestMapping("/api/cloud-profile")
public class CloudProfileController {

    private static final String SIGNED_MEDIA_REDIRECT_CACHE_CONTROL = "private, max-age=240";

    private final CloudProfileManagementService cloudProfileManagementService;

    public CloudProfileController(CloudProfileManagementService cloudProfileManagementService) {
        this.cloudProfileManagementService = cloudProfileManagementService;
    }

    @PostMapping(value = "/background", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserProfileResponse uploadHomeBackground(
            @RequestAttribute(AuthRequestAttributes.CURRENT_PRINCIPAL) CurrentPrincipal principal,
            @Valid @RequestPart("file") MultipartFile file
    ) {
        return cloudProfileManagementService.uploadCurrentUserHomeBackground(principal.userId(), file);
    }

    @GetMapping("/background/{userId}")
    public ResponseEntity<Void> getHomeBackground(@PathVariable Long userId) {
        String accessUrl = cloudProfileManagementService.resolveUserHomeBackgroundAccessUrl(userId).url();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.CACHE_CONTROL, SIGNED_MEDIA_REDIRECT_CACHE_CONTROL)
                .location(URI.create(accessUrl))
                .build();
    }

    @DeleteMapping("/background")
    public UserProfileResponse clearHomeBackground(
            @RequestAttribute(AuthRequestAttributes.CURRENT_PRINCIPAL) CurrentPrincipal principal
    ) {
        return cloudProfileManagementService.clearCurrentUserHomeBackground(principal.userId());
    }
}
