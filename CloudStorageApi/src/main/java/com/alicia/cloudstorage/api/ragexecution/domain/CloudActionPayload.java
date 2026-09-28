package com.alicia.cloudstorage.api.ragexecution.domain;

public sealed interface CloudActionPayload permits
        NodeRenameCloudAction,
        NodeTrashCloudAction,
        NodeMoveCloudAction,
        FolderCreateCloudAction,
        ShareCreateCloudAction,
        NodeBatchTrashCloudAction,
        NodeBatchMoveCloudAction,
        NodeBatchRenameCloudAction,
        UploadFilesVerificationCloudAction {

    CloudActionType actionType();
}
