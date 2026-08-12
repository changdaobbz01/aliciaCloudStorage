package com.alicia.cloudstorage.phone.ui

import com.alicia.cloudstorage.phone.data.RagAssistantPlanResponse

internal fun RagAssistantPlanResponse.shouldRetainComposerAttachments(): Boolean {
    val intent = intentId.orEmpty()
    val draftType = actionDraft?.type.orEmpty()
    val planAction = actionPlan?.actionType.orEmpty()
    val backendAction = backendActionDraft?.actionType.orEmpty()
    val next = nextAction.orEmpty()

    return intent.equalsAnyUploadAction() ||
        draftType.equalsAnyUploadAction() ||
        planAction.equalsAnyUploadAction() ||
        backendAction.equalsAnyUploadAction() ||
        next.equals("handoff_to_client_upload", ignoreCase = true)
}

private fun String.equalsAnyUploadAction(): Boolean =
    equals("file_upload", ignoreCase = true) ||
        equals("folder_create_then_upload", ignoreCase = true) ||
        equals("upload_target", ignoreCase = true) ||
        equals("file.upload", ignoreCase = true) ||
        equals("composite.create_folder_then_upload", ignoreCase = true)
