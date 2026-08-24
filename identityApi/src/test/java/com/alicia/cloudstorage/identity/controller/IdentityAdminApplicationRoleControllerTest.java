package com.alicia.cloudstorage.identity.controller;

import com.alicia.cloudstorage.identity.dto.IdentityApplicationRoleResponse;
import com.alicia.cloudstorage.identity.dto.UpdateIdentityApplicationRoleRequest;
import com.alicia.cloudstorage.identity.service.IdentityApplicationRoleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IdentityAdminApplicationRoleController.class)
class IdentityAdminApplicationRoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentityApplicationRoleService identityApplicationRoleService;

    @Test
    void listUserRolesReturnsApplicationRoles() throws Exception {
        when(identityApplicationRoleService.listUserRoles("Bearer admin-token", 7L))
                .thenReturn(List.of(new IdentityApplicationRoleResponse("cloud", "CLOUD_ADMIN")));

        mockMvc.perform(get("/api/identity/admin/users/7/app-roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].appCode").value("cloud"))
                .andExpect(jsonPath("$[0].roleCode").value("CLOUD_ADMIN"));
    }

    @Test
    void updateUserRoleDelegatesToService() throws Exception {
        when(identityApplicationRoleService.updateUserRole(
                "Bearer admin-token",
                7L,
                "cloud",
                new UpdateIdentityApplicationRoleRequest("CLOUD_ADMIN")
        )).thenReturn(new IdentityApplicationRoleResponse("cloud", "CLOUD_ADMIN"));

        mockMvc.perform(put("/api/identity/admin/users/7/app-roles/cloud")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleCode": "CLOUD_ADMIN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appCode").value("cloud"))
                .andExpect(jsonPath("$.roleCode").value("CLOUD_ADMIN"));
    }
}
