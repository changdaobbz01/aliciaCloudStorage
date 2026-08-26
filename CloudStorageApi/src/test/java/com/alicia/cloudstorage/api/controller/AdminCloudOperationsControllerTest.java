package com.alicia.cloudstorage.api.controller;

import com.alicia.cloudstorage.api.dto.AdminCloudOperationsOverviewResponse;
import com.alicia.cloudstorage.api.dto.AdminCloudShareLinkResponse;
import com.alicia.cloudstorage.api.dto.AdminCloudStorageUserUsageResponse;
import com.alicia.cloudstorage.api.dto.AdminCloudTrashNodeResponse;
import com.alicia.cloudstorage.api.dto.PageResponse;
import com.alicia.cloudstorage.api.service.AdminCloudOperationsDetailService;
import com.alicia.cloudstorage.api.service.AdminCloudOperationsOverviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminCloudOperationsControllerTest {

    @Mock
    private AdminCloudOperationsOverviewService adminCloudOperationsOverviewService;

    @Mock
    private AdminCloudOperationsDetailService adminCloudOperationsDetailService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminCloudOperationsController(
                        adminCloudOperationsOverviewService,
                        adminCloudOperationsDetailService
                ))
                .build();
    }

    @Test
    void getOverviewUsesCloudOperationsAdminRoute() throws Exception {
        when(adminCloudOperationsOverviewService.getOverview()).thenReturn(overview());

        mockMvc.perform(get("/api/admin/cloud-operations/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity.systemTotalSpaceBytes").value(10_000))
                .andExpect(jsonPath("$.activeNodes.totalItems").value(30))
                .andExpect(jsonPath("$.trash.totalItems").value(6))
                .andExpect(jsonPath("$.shares.totalLinks").value(12))
                .andExpect(jsonPath("$.multipartUploads.inProgressSessions").value(4));

        verify(adminCloudOperationsOverviewService).getOverview();
    }

    @Test
    void listShareLinksDelegatesDetailQuery() throws Exception {
        when(adminCloudOperationsDetailService.listShareLinks(
                7L,
                "expired",
                true,
                2,
                20,
                "viewCount",
                "desc"
        )).thenReturn(new PageResponse<>(
                List.of(new AdminCloudShareLinkResponse(
                        101L,
                        7L,
                        "项目资料",
                        "ACTIVE",
                        "EXPIRED",
                        true,
                        true,
                        false,
                        18L,
                        2L,
                        LocalDateTime.of(2026, 8, 20, 9, 0),
                        LocalDateTime.of(2026, 8, 21, 9, 0),
                        LocalDateTime.of(2026, 8, 19, 9, 0),
                        LocalDateTime.of(2026, 8, 21, 10, 0)
                )),
                2,
                20,
                31,
                2,
                "viewCount",
                "desc"
        ));

        mockMvc.perform(get("/api/admin/cloud-operations/shares")
                        .param("ownerId", "7")
                        .param("status", "expired")
                        .param("passwordProtected", "true")
                        .param("page", "2")
                        .param("size", "20")
                        .param("sortBy", "viewCount")
                        .param("sortDirection", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(101))
                .andExpect(jsonPath("$.items[0].effectiveStatus").value("EXPIRED"))
                .andExpect(jsonPath("$.items[0].itemCount").value(2))
                .andExpect(jsonPath("$.totalItems").value(31));

        verify(adminCloudOperationsDetailService).listShareLinks(
                7L,
                "expired",
                true,
                2,
                20,
                "viewCount",
                "desc"
        );
    }

    @Test
    void listTrashNodesDelegatesDetailQuery() throws Exception {
        when(adminCloudOperationsDetailService.listTrashNodes(
                8L,
                "报告",
                "FILE",
                false,
                1,
                10,
                "deletedAt",
                "desc"
        )).thenReturn(new PageResponse<>(
                List.of(new AdminCloudTrashNodeResponse(
                        201L,
                        8L,
                        88L,
                        66L,
                        "报告.pdf",
                        "FILE",
                        2048L,
                        8L,
                        false,
                        LocalDateTime.of(2026, 8, 21, 11, 0),
                        LocalDateTime.of(2026, 8, 21, 11, 1)
                )),
                1,
                10,
                1,
                1,
                "deletedAt",
                "desc"
        ));

        mockMvc.perform(get("/api/admin/cloud-operations/trash")
                        .param("ownerId", "8")
                        .param("keyword", "报告")
                        .param("type", "FILE")
                        .param("rootOnly", "false")
                        .param("page", "1")
                        .param("size", "10")
                        .param("sortBy", "deletedAt")
                        .param("sortDirection", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(201))
                .andExpect(jsonPath("$.items[0].name").value("报告.pdf"))
                .andExpect(jsonPath("$.items[0].rootItem").value(false));

        verify(adminCloudOperationsDetailService).listTrashNodes(
                8L,
                "报告",
                "FILE",
                false,
                1,
                10,
                "deletedAt",
                "desc"
        );
    }

    @Test
    void listStorageUsersPassesAuthorizationToDetailQuery() throws Exception {
        when(adminCloudOperationsDetailService.listStorageUsers("Bearer admin-token", 1, 10, "usageRatio", "desc"))
                .thenReturn(new PageResponse<>(
                        List.of(new AdminCloudStorageUserUsageResponse(
                                9L,
                                "13800000000",
                                "admin@example.com",
                                "青空",
                                "ADMIN",
                                "ACTIVE",
                                10_000L,
                                4_500L,
                                5_500L,
                                0.45,
                                12L,
                                3L,
                                9L,
                                2L,
                                4L,
                                LocalDateTime.of(2026, 8, 20, 8, 0)
                        )),
                        1,
                        10,
                        1,
                        1,
                        "usageRatio",
                        "desc"
                ));

        mockMvc.perform(get("/api/admin/cloud-operations/users/storage")
                        .header("Authorization", "Bearer admin-token")
                        .param("page", "1")
                        .param("size", "10")
                        .param("sortBy", "usageRatio")
                        .param("sortDirection", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].userId").value(9))
                .andExpect(jsonPath("$.items[0].usedBytes").value(4_500))
                .andExpect(jsonPath("$.items[0].usageRatio").value(0.45));

        verify(adminCloudOperationsDetailService).listStorageUsers("Bearer admin-token", 1, 10, "usageRatio", "desc");
    }

    private AdminCloudOperationsOverviewResponse overview() {
        return new AdminCloudOperationsOverviewResponse(
                LocalDateTime.of(2026, 8, 26, 14, 0),
                new AdminCloudOperationsOverviewResponse.CapacityOverview(
                        10_000L,
                        4_000L,
                        1_250L,
                        6_000L,
                        0.4,
                        0.125
                ),
                new AdminCloudOperationsOverviewResponse.NodeOverview(30L, 8L, 22L),
                new AdminCloudOperationsOverviewResponse.TrashOverview(
                        6L,
                        2L,
                        1L,
                        5L,
                        900L,
                        LocalDateTime.of(2026, 8, 26, 9, 30)
                ),
                new AdminCloudOperationsOverviewResponse.ShareOverview(
                        12L,
                        9L,
                        7L,
                        2L,
                        3L,
                        5L,
                        10L,
                        8L,
                        88L,
                        LocalDateTime.of(2026, 8, 26, 10, 30),
                        LocalDateTime.of(2026, 8, 26, 11, 30)
                ),
                new AdminCloudOperationsOverviewResponse.MultipartUploadOverview(
                        14L,
                        4L,
                        1L,
                        7L,
                        3L,
                        LocalDateTime.of(2026, 8, 26, 12, 30),
                        24L
                )
        );
    }
}
