package com.alicia.cloudstorage.ragexecution.domain.action;

import com.alicia.cloudstorage.ragexecution.domain.ExecutionActionType;

public record FolderCreateAction(
        Long parentId,
        String folderName
) implements ExecutionActionPayload {

    public FolderCreateAction {
        ActionPayloadRules.optionalPositive(parentId, "parentId");
        folderName = ActionPayloadRules.requireName(folderName, "folderName");
    }

    @Override
    public ExecutionActionType actionType() {
        return ExecutionActionType.FOLDER_CREATE;
    }
}
