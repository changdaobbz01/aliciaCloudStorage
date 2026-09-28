package com.alicia.cloudstorage.api.ragexecution.domain;

public record FolderCreateCloudAction(
        Long parentId,
        String folderName
) implements CloudActionPayload {

    public FolderCreateCloudAction {
        CloudActionPayloadRules.optionalPositive(parentId, "parentId");
        folderName = CloudActionPayloadRules.requiredName(folderName, "folderName");
    }

    @Override
    public CloudActionType actionType() {
        return CloudActionType.FOLDER_CREATE;
    }
}
