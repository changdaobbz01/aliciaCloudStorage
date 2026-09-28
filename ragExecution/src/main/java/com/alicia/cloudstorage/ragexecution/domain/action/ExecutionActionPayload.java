package com.alicia.cloudstorage.ragexecution.domain.action;

import com.alicia.cloudstorage.ragexecution.domain.ExecutionActionType;

public sealed interface ExecutionActionPayload permits
        NodeRenameAction,
        NodeTrashAction,
        NodeMoveAction,
        FolderCreateAction,
        ShareCreateAction,
        NodeBatchTrashAction,
        NodeBatchMoveAction,
        NodeBatchRenameAction,
        UploadFilesAction {

    ExecutionActionType actionType();
}
