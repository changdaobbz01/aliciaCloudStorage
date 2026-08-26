package com.alicia.cloudstorage.api.controller;

import com.alicia.cloudstorage.api.dto.AdminCloudOperationsOverviewResponse;
import com.alicia.cloudstorage.api.service.AdminCloudOperationsOverviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminCloudOperationsControllerTest {

    @Mock
    private AdminCloudOperationsOverviewService adminCloudOperationsOverviewService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminCloudOperationsController(adminCloudOperationsOverviewService))
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
