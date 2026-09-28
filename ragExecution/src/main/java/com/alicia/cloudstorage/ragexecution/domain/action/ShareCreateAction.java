package com.alicia.cloudstorage.ragexecution.domain.action;

import com.alicia.cloudstorage.ragexecution.domain.ExecutionActionType;

import java.util.List;

public record ShareCreateAction(
        List<Long> nodeIds,
        String title,
        String password,
        Integer expiresInDays,
        boolean allowDownload,
        boolean allowSave
) implements ExecutionActionPayload {

    public ShareCreateAction {
        nodeIds = ActionPayloadRules.requireNodeIds(nodeIds, 20);
        title = normalizeOptional(title, 255, "title");
        password = normalizeOptional(password, 32, "password");
        if (password != null && password.length() < 4) {
            throw new IllegalArgumentException("password must contain 4-32 characters when present.");
        }
        if (expiresInDays != null && (expiresInDays <= 0 || expiresInDays > 365)) {
            throw new IllegalArgumentException("expiresInDays must be between 1 and 365 when present.");
        }
    }

    @Override
    public ExecutionActionType actionType() {
        return ExecutionActionType.SHARE_CREATE;
    }

    private static String normalizeOptional(String value, int maximumLength, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maximumLength + " characters.");
        }
        return normalized;
    }
}
