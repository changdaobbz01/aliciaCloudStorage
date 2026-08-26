package com.alicia.cloudstorage.api.controller;

import com.alicia.cloudstorage.api.principal.CurrentPrincipal;
import com.alicia.cloudstorage.api.principal.PrincipalRequestAttributes;
import com.alicia.cloudstorage.api.service.ScopedCollectionTrashService;
import com.alicia.cloudstorage.api.service.ShareLinkService;
import com.alicia.cloudstorage.api.service.StorageArchiveService;
import com.alicia.cloudstorage.api.service.StorageCommandService;
import com.alicia.cloudstorage.api.service.StorageMultipartUploadService;
import com.alicia.cloudstorage.api.service.StorageQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StorageDownloadControllerTest {

    @Mock
    private StorageQueryService storageQueryService;

    @Mock
    private StorageCommandService storageCommandService;

    @Mock
    private StorageArchiveService storageArchiveService;

    @Mock
    private StorageMultipartUploadService storageMultipartUploadService;

    @Mock
    private ScopedCollectionTrashService scopedCollectionTrashService;

    @Mock
    private ShareLinkService shareLinkService;

    private MockMvc storageMockMvc;

    private MockMvc shareMockMvc;

    @BeforeEach
    void setUp() {
        storageMockMvc = MockMvcBuilders
                .standaloneSetup(new StorageNodeController(
                        storageQueryService,
                        storageCommandService,
                        storageArchiveService,
                        storageMultipartUploadService,
                        scopedCollectionTrashService
                ))
                .build();
        shareMockMvc = MockMvcBuilders
                .standaloneSetup(new ShareLinkController(shareLinkService))
                .build();
    }

    @Test
    void downloadFileFallsBackToBinaryContentTypeWhenStoredMimeTypeIsInvalid() throws Exception {
        byte[] body = new byte[]{1, 2, 3};
        when(storageCommandService.downloadFile(7L, 11L))
                .thenReturn(new StorageCommandService.StorageDownloadPayload(
                        "report.bin",
                        "bad/type/extra",
                        body.length,
                        new ByteArrayInputStream(body)
                ));

        storageMockMvc.perform(get("/api/storage/files/11/download")
                        .requestAttr(PrincipalRequestAttributes.CURRENT_PRINCIPAL, new CurrentPrincipal(7L, null)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .andExpect(content().bytes(body));
    }

    @Test
    void downloadShareFileFallsBackToBinaryContentTypeWhenStoredMimeTypeIsInvalid() throws Exception {
        byte[] body = new byte[]{4, 5, 6};
        when(shareLinkService.downloadShareFile(7L, "share-code", 11L, "share-access-token"))
                .thenReturn(new StorageCommandService.StorageDownloadPayload(
                        "report.bin",
                        "bad/type/extra",
                        body.length,
                        new ByteArrayInputStream(body)
                ));

        shareMockMvc.perform(get("/api/share-links/share-code/files/11/download")
                        .requestAttr(PrincipalRequestAttributes.CURRENT_PRINCIPAL, new CurrentPrincipal(7L, null))
                        .header("X-Share-Access-Token", "share-access-token"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .andExpect(content().bytes(body));
    }
}
