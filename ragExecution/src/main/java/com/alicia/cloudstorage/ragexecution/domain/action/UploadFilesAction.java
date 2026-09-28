package com.alicia.cloudstorage.ragexecution.domain.action;

import com.alicia.cloudstorage.ragexecution.domain.ExecutionActionType;

public record UploadFilesAction(
        ActionOutputReference parentIdReference
) implements ExecutionActionPayload {
    public UploadFilesAction {
        if (parentIdReference == null) {
            throw new IllegalArgumentException("parentIdReference is required.");
        }
    }

    @Override
    public ExecutionActionType actionType() {
        return ExecutionActionType.UPLOAD_FILES;
    }
}
