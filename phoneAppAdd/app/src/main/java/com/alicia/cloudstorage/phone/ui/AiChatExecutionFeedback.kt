package com.alicia.cloudstorage.phone.ui

internal object AiChatExecutionFeedback {
    private val policies = buildMap {
        register(
            actionTypes = listOf("rename", "collection.rename_add_prefix"),
            progressMessage = "正在为你重命名，请稍等...",
            mutationScope = AiChatFileMutationScope.FILES_ONLY,
        )
        register(
            actionTypes = listOf("delete", "collection.trash_by_name_contains", "collection.trash_by_category"),
            progressMessage = "正在将文件移入回收站，请稍等...",
            mutationScope = AiChatFileMutationScope.FILES_AND_TRASH,
        )
        register(
            actionTypes = listOf("collection.move_by_extension", "collection.move_by_name_contains"),
            progressMessage = "正在为你移动文件，请稍等...",
            mutationScope = AiChatFileMutationScope.FILES_ONLY,
        )
        register(listOf("share"), "正在为你创建分享，请稍等...")
        register(listOf("file.upload", "upload_target"), "正在上传文件，请稍等...")
        register(listOf("composite.create_folder_then_upload"), "正在创建文件夹并上传，请稍等...")
    }

    fun progressMessage(actionType: String?): String =
        policies[actionType.normalizedActionType()]?.progressMessage ?: "正在执行中，请稍等..."

    fun mutationScope(actionType: String?): AiChatFileMutationScope? =
        policies[actionType.normalizedActionType()]?.mutationScope

    fun uploadSelectionPrompt(request: AiChatClientUploadRequest): String {
        val targetText = request.targetName?.takeIf { it.isNotBlank() }
        val createFolderName = request.createFolderName?.takeIf { it.isNotBlank() }
        return if (createFolderName != null) {
            "请选择要上传到「${targetText ?: createFolderName}」的文件或文件夹，选好后我会接着处理。"
        } else {
            "请选择要上传${targetText?.let { "到「$it」" }.orEmpty()}的文件或文件夹。"
        }
    }

    fun uploadActionType(request: AiChatClientUploadRequest): String =
        if (request.createFolderName.isNullOrBlank()) {
            "file.upload"
        } else {
            "composite.create_folder_then_upload"
        }

    private fun MutableMap<String, ActionPolicy>.register(
        actionTypes: List<String>,
        progressMessage: String,
        mutationScope: AiChatFileMutationScope? = null,
    ) {
        actionTypes.forEach { actionType ->
            put(actionType, ActionPolicy(progressMessage, mutationScope))
        }
    }

    private fun String?.normalizedActionType(): String = this?.trim()?.lowercase().orEmpty()

    private data class ActionPolicy(
        val progressMessage: String,
        val mutationScope: AiChatFileMutationScope?,
    )
}
