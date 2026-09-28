package com.alicia.cloudstorage.ragexecution.domain;

public enum ExecutionActionType {
    NODE_RENAME(ExecutionLocation.SERVER),
    NODE_TRASH(ExecutionLocation.SERVER),
    NODE_MOVE(ExecutionLocation.SERVER),
    FOLDER_CREATE(ExecutionLocation.SERVER),
    SHARE_CREATE(ExecutionLocation.SERVER),
    NODE_BATCH_TRASH(ExecutionLocation.SERVER),
    NODE_BATCH_MOVE(ExecutionLocation.SERVER),
    NODE_BATCH_RENAME(ExecutionLocation.SERVER),
    UPLOAD_FILES(ExecutionLocation.CLIENT),
    DOWNLOAD_TO_DEVICE(ExecutionLocation.CLIENT),
    OPEN_PREVIEW(ExecutionLocation.CLIENT),
    NAVIGATE_UI(ExecutionLocation.CLIENT),
    SYSTEM_SHARE(ExecutionLocation.CLIENT);

    private final ExecutionLocation location;

    ExecutionActionType(ExecutionLocation location) {
        this.location = location;
    }

    public ExecutionLocation location() {
        return location;
    }

    public enum ExecutionLocation {
        SERVER,
        CLIENT
    }
}
