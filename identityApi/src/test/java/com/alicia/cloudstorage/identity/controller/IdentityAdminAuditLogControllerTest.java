package com.alicia.cloudstorage.identity.controller;

import com.alicia.cloudstorage.identity.dto.IdentityAuditLogPageResponse;
import com.alicia.cloudstorage.identity.dto.IdentityAuditLogResponse;
import com.alicia.cloudstorage.identity.service.IdentityAuditLogQuery;
import com.alicia.cloudstorage.identity.service.IdentityAuditLogQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IdentityAdminAuditLogController.class)
class IdentityAdminAuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentityAuditLogQueryService identityAuditLogQueryService;

    @Test
    void listAuditLogsBindsFiltersAndReturnsPage() throws Exception {
        IdentityAuditLogResponse item = new IdentityAuditLogResponse(
                3L,
                "LOGIN",
                "SUCCESS",
                1L,
                2L,
                "13800000000",
                "issued by login",
                LocalDateTime.of(2026, 8, 21, 14, 47, 25)
        );
        IdentityAuditLogPageResponse page = new IdentityAuditLogPageResponse(List.of(item), 2, 10, 21L, 3);

        when(identityAuditLogQueryService.listAuditLogs(eq("Bearer admin-token"), any(IdentityAuditLogQuery.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/identity/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                        .param("eventType", "login")
                        .param("outcome", "success")
                        .param("actorUserId", "1")
                        .param("targetUserId", "2")
                        .param("identifier", "138")
                        .param("createdFrom", "2026-08-21T00:00:00")
                        .param("createdTo", "2026-08-21T23:59:59")
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(3))
                .andExpect(jsonPath("$.items[0].eventType").value("LOGIN"))
                .andExpect(jsonPath("$.items[0].outcome").value("SUCCESS"))
                .andExpect(jsonPath("$.items[0].identifier").value("13800000000"))
                .andExpect(jsonPath("$.items[0].detail").value("issued by login"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalItems").value(21))
                .andExpect(jsonPath("$.totalPages").value(3));

        ArgumentCaptor<IdentityAuditLogQuery> queryCaptor = ArgumentCaptor.forClass(IdentityAuditLogQuery.class);
        verify(identityAuditLogQueryService).listAuditLogs(eq("Bearer admin-token"), queryCaptor.capture());

        IdentityAuditLogQuery query = queryCaptor.getValue();
        assertThat(query.eventType()).isEqualTo("login");
        assertThat(query.outcome()).isEqualTo("success");
        assertThat(query.actorUserId()).isEqualTo(1L);
        assertThat(query.targetUserId()).isEqualTo(2L);
        assertThat(query.identifier()).isEqualTo("138");
        assertThat(query.createdFrom()).isEqualTo(LocalDateTime.of(2026, 8, 21, 0, 0));
        assertThat(query.createdTo()).isEqualTo(LocalDateTime.of(2026, 8, 21, 23, 59, 59));
        assertThat(query.page()).isEqualTo(2);
        assertThat(query.size()).isEqualTo(10);
    }
}
