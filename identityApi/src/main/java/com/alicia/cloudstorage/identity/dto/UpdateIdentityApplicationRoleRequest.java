package com.alicia.cloudstorage.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateIdentityApplicationRoleRequest(
        @NotBlank(message = "应用角色不能为空。")
        String roleCode
) {
}
