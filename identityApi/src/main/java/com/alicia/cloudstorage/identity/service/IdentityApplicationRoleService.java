package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.IdentityApplicationRoleResponse;
import com.alicia.cloudstorage.identity.dto.UpdateIdentityApplicationRoleRequest;
import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.entity.IdentityUserAppRole;
import com.alicia.cloudstorage.identity.entity.IdentityUserRole;
import com.alicia.cloudstorage.identity.repository.IdentityUserAppRoleRepository;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class IdentityApplicationRoleService {

    public static final String CLOUD_APP_CODE = "cloud";
    public static final String CLOUD_ADMIN_ROLE = "CLOUD_ADMIN";
    public static final String CLOUD_USER_ROLE = "CLOUD_USER";
    public static final String RAG_APP_CODE = "rag";
    public static final String RAG_ADMIN_ROLE = "RAG_ADMIN";
    public static final String RAG_USER_ROLE = "RAG_USER";

    private static final int MAX_APP_CODE_LENGTH = 64;
    private static final int MAX_ROLE_CODE_LENGTH = 64;
    private static final Map<String, String> DEFAULT_USER_APP_ROLES = Map.of(
            CLOUD_APP_CODE, CLOUD_USER_ROLE,
            RAG_APP_CODE, RAG_USER_ROLE
    );
    private static final Map<String, String> DEFAULT_ADMIN_APP_ROLES = Map.of(
            CLOUD_APP_CODE, CLOUD_ADMIN_ROLE,
            RAG_APP_CODE, RAG_ADMIN_ROLE
    );

    private final IdentityPrincipalService identityPrincipalService;
    private final IdentityUserRepository identityUserRepository;
    private final IdentityUserAppRoleRepository identityUserAppRoleRepository;
    private final IdentityAuditLogService identityAuditLogService;

    public IdentityApplicationRoleService(
            IdentityPrincipalService identityPrincipalService,
            IdentityUserRepository identityUserRepository,
            IdentityUserAppRoleRepository identityUserAppRoleRepository,
            IdentityAuditLogService identityAuditLogService
    ) {
        this.identityPrincipalService = identityPrincipalService;
        this.identityUserRepository = identityUserRepository;
        this.identityUserAppRoleRepository = identityUserAppRoleRepository;
        this.identityAuditLogService = identityAuditLogService;
    }

    public Map<String, String> effectiveRolesForUser(IdentityUser user) {
        if (user == null || user.getId() == null) {
            return Map.of();
        }

        Map<String, String> roles = new LinkedHashMap<>();
        identityUserAppRoleRepository.findByUser_IdOrderByAppCodeAsc(user.getId())
                .forEach(role -> roles.put(role.getAppCode(), role.getRoleCode()));
        DEFAULT_USER_APP_ROLES.forEach(roles::putIfAbsent);
        if (user.getRole() == IdentityUserRole.ADMIN) {
            DEFAULT_ADMIN_APP_ROLES.forEach(roles::put);
        }

        return Map.copyOf(roles);
    }

    public List<IdentityApplicationRoleResponse> listUserRoles(String authorizationHeader, Long userId) {
        identityPrincipalService.requireAdminUser(authorizationHeader);
        IdentityUser user = requireUser(userId);
        return effectiveRolesForUser(user).entrySet().stream()
                .map(entry -> new IdentityApplicationRoleResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Transactional
    public IdentityApplicationRoleResponse updateUserRole(
            String authorizationHeader,
            Long userId,
            String rawAppCode,
            UpdateIdentityApplicationRoleRequest request
    ) {
        IdentityUser adminUser = null;
        String appCode = normalizeAppCode(rawAppCode);
        String roleCode = request == null ? null : request.roleCode();
        try {
            adminUser = identityPrincipalService.requireAdminUser(authorizationHeader);
            IdentityUser targetUser = requireUser(userId);
            String normalizedRoleCode = normalizeRoleCode(appCode, roleCode);

            if (targetUser.getRole() == IdentityUserRole.ADMIN && normalizedRoleCode.endsWith("_USER")) {
                throw new IllegalArgumentException("全局管理员始终拥有应用管理员权限。");
            }

            IdentityUserAppRole appRole = identityUserAppRoleRepository
                    .findByUser_IdAndAppCode(targetUser.getId(), appCode)
                    .orElseGet(IdentityUserAppRole::new);
            appRole.setUser(targetUser);
            appRole.setAppCode(appCode);
            appRole.setRoleCode(normalizedRoleCode);

            IdentityUserAppRole savedRole = identityUserAppRoleRepository.save(appRole);
            identityAuditLogService.record(
                    IdentityAuditEventType.ADMIN_APP_ROLE_UPDATE,
                    IdentityAuditOutcome.SUCCESS,
                    adminUser.getId(),
                    targetUser.getId(),
                    appCode,
                    normalizedRoleCode
            );
            return toResponse(savedRole);
        } catch (RuntimeException ex) {
            identityAuditLogService.record(
                    IdentityAuditEventType.ADMIN_APP_ROLE_UPDATE,
                    IdentityAuditOutcome.FAILURE,
                    adminUser == null ? null : adminUser.getId(),
                    userId,
                    appCode,
                    failureDetail(roleCode, ex)
            );
            throw ex;
        }
    }

    private IdentityUser requireUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户不存在。");
        }

        return identityUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在。"));
    }

    private IdentityApplicationRoleResponse toResponse(IdentityUserAppRole appRole) {
        return new IdentityApplicationRoleResponse(appRole.getAppCode(), appRole.getRoleCode());
    }

    private String normalizeAppCode(String rawAppCode) {
        if (rawAppCode == null || rawAppCode.isBlank()) {
            throw new IllegalArgumentException("应用编码不能为空。");
        }

        String appCode = rawAppCode.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_');
        if (appCode.length() > MAX_APP_CODE_LENGTH || !appCode.matches("^[a-z0-9][a-z0-9_]*$")) {
            throw new IllegalArgumentException("应用编码不合法。");
        }

        return appCode;
    }

    private String normalizeRoleCode(String appCode, String rawRoleCode) {
        if (rawRoleCode == null || rawRoleCode.isBlank()) {
            throw new IllegalArgumentException("应用角色不能为空。");
        }

        String roleCode = rawRoleCode.trim().toUpperCase(Locale.ROOT);
        if (roleCode.length() > MAX_ROLE_CODE_LENGTH || !roleCode.matches("^[A-Z0-9_]+$")) {
            throw new IllegalArgumentException("应用角色不合法。");
        }

        String rolePrefix = appCode.toUpperCase(Locale.ROOT) + "_";
        if (!roleCode.equals(rolePrefix + "USER") && !roleCode.equals(rolePrefix + "ADMIN")) {
            throw new IllegalArgumentException("应用角色必须匹配应用编码，例如 " + rolePrefix + "USER 或 " + rolePrefix + "ADMIN。");
        }

        return roleCode;
    }

    private String failureDetail(String roleCode, RuntimeException ex) {
        String normalizedRoleCode = roleCode == null ? "" : roleCode.trim();
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (normalizedRoleCode.isEmpty()) {
            return message;
        }

        return normalizedRoleCode + ": " + message;
    }
}
