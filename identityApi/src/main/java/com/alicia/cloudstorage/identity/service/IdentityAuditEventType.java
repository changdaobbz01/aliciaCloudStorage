package com.alicia.cloudstorage.identity.service;

public enum IdentityAuditEventType {
    LOGIN,
    TOKEN_REFRESH,
    LOGOUT,
    PROFILE_UPDATE,
    PASSWORD_CHANGE,
    ADMIN_USER_CREATE,
    ADMIN_PASSWORD_RESET,
    EMAIL_REGISTRATION_CODE_REQUEST,
    EMAIL_REGISTRATION_VERIFY
}
