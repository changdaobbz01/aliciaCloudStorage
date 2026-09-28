package com.alicia.cloudstorage.api.ragexecution.domain;

import java.util.List;

public record ShareCreateCloudAction(
        List<Long> nodeIds,
        String title,
        Integer expiresInDays,
        boolean allowDownload,
        boolean allowSave
) implements CloudActionPayload {

    public ShareCreateCloudAction {
        nodeIds = CloudActionPayloadRules.nodeIds(nodeIds);
        title = CloudActionPayloadRules.optionalText(title, 255, "title");
        if (expiresInDays != null && (expiresInDays <= 0 || expiresInDays > 365)) {
            throw new IllegalArgumentException("expiresInDays must be between 1 and 365 when present.");
        }
    }

    @Override
    public CloudActionType actionType() {
        return CloudActionType.SHARE_CREATE;
    }
}
