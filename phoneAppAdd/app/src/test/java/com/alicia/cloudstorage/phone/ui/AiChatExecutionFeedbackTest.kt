package com.alicia.cloudstorage.phone.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AiChatExecutionFeedbackTest {

    @Test
    fun `progress messages cover supported operation families`() {
        assertEquals("正在为你重命名，请稍等...", AiChatExecutionFeedback.progressMessage("rename"))
        assertEquals(
            "正在将文件移入回收站，请稍等...",
            AiChatExecutionFeedback.progressMessage("collection.trash_by_category"),
        )
        assertEquals(
            "正在为你移动文件或文件夹，请稍等...",
            AiChatExecutionFeedback.progressMessage("collection.move_by_extension"),
        )
        assertEquals("正在为你创建分享，请稍等...", AiChatExecutionFeedback.progressMessage("share"))
        assertEquals("正在创建文件夹，请稍等...", AiChatExecutionFeedback.progressMessage("folder.create"))
        assertEquals("正在上传文件，请稍等...", AiChatExecutionFeedback.progressMessage("file.upload"))
        assertEquals(
            "正在创建文件夹并上传，请稍等...",
            AiChatExecutionFeedback.progressMessage("composite.create_folder_then_upload"),
        )
    }

    @Test
    fun `unknown execution type uses generic progress message`() {
        assertEquals("正在执行中，请稍等...", AiChatExecutionFeedback.progressMessage("future.action"))
    }

    @Test
    fun `action policy also owns refresh scope`() {
        assertEquals(AiChatFileMutationScope.FILES_ONLY, AiChatExecutionFeedback.mutationScope("rename"))
        assertEquals(
            AiChatFileMutationScope.FILES_AND_TRASH,
            AiChatExecutionFeedback.mutationScope("collection.trash_by_category"),
        )
        assertEquals(AiChatFileMutationScope.FILES_ONLY, AiChatExecutionFeedback.mutationScope("folder.create"))
        assertEquals(null, AiChatExecutionFeedback.mutationScope("share"))
    }

    @Test
    fun `upload prompt is derived from request instead of caller specific text`() {
        val request = AiChatClientUploadRequest(
            parentId = 8L,
            targetName = "项目资料/设计稿",
            createFolderName = "设计稿",
        )

        assertEquals(
            "请选择要上传到「项目资料/设计稿」的文件或文件夹，选好后我会接着处理。",
            AiChatExecutionFeedback.uploadSelectionPrompt(request),
        )
        assertEquals("composite.create_folder_then_upload", AiChatExecutionFeedback.uploadActionType(request))
    }
}
