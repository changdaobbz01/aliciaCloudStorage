package com.alicia.cloudstorage.api.entity;

public enum CloudObjectCleanupSource {
    UPLOAD_METADATA_ROLLBACK,
    MULTIPART_METADATA_ROLLBACK,
    SHARE_SAVE_ROLLBACK,
    PERMANENT_DELETE
}
