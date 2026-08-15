package com.alicia.cloudstorage.phone.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RagActionExecutorTest {
    private val context = RagActionExecutionContext(
        baseUrl = "https://storage.example",
        token = "token",
    )

    @Test
    fun `keeps execution disabled by default`() = runBlocking {
        val repository = FakeRagActionRepositoryPort()
        val executor = RagActionExecutor(repositoryPort = repository)

        val result = executor.execute(
            context = context,
            draft = draft(
                actionType = "rename",
                method = "PUT",
                pathTemplate = "/api/storage/nodes/{nodeId}/rename",
                body = mapOf("name" to "final.docx"),
            ),
        )

        assertEquals(RagActionExecutionStatus.DISABLED, result.status)
        assertFalse(result.succeeded)
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun `executes confirmed rename through repository port when enabled`() = runBlocking {
        val repository = FakeRagActionRepositoryPort()
        val executor = RagActionExecutor(repositoryPort = repository, executionEnabled = true)

        val result = executor.execute(
            context = context,
            draft = draft(
                actionType = "rename",
                method = "PUT",
                pathTemplate = "/api/storage/nodes/{nodeId}/rename",
                body = mapOf("name" to "final.docx"),
            ),
        )

        assertEquals(RagActionExecutionStatus.COMPLETED, result.status)
        assertEquals(listOf("rename:42:final.docx"), repository.calls)
        assertEquals(listOf(42L), result.affectedNodeIds)
    }

    @Test
    fun `rejects execution when validator rejects draft`() = runBlocking {
        val repository = FakeRagActionRepositoryPort()
        val executor = RagActionExecutor(repositoryPort = repository, executionEnabled = true)

        val result = executor.execute(
            context = context,
            draft = draft(
                actionType = "delete",
                method = "DELETE",
                pathTemplate = "/api/storage/nodes/{nodeId}",
                confirmedByUser = false,
            ),
        )

        assertEquals(RagActionExecutionStatus.VALIDATION_REJECTED, result.status)
        assertEquals(RagActionBridgeValidationCode.CONFIRMATION_REQUIRED, result.validation?.code)
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun `returns invalid draft when required action data is missing`() = runBlocking {
        val repository = FakeRagActionRepositoryPort()
        val executor = RagActionExecutor(repositoryPort = repository, executionEnabled = true)

        val result = executor.execute(
            context = context,
            draft = draft(
                actionType = "rename",
                method = "PUT",
                pathTemplate = "/api/storage/nodes/{nodeId}/rename",
                body = emptyMap(),
            ),
        )

        assertEquals(RagActionExecutionStatus.INVALID_DRAFT, result.status)
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun `executes confirmed delete through repository port when enabled`() = runBlocking {
        val repository = FakeRagActionRepositoryPort()
        val executor = RagActionExecutor(repositoryPort = repository, executionEnabled = true)

        val result = executor.execute(
            context = context,
            draft = draft(
                actionType = "delete",
                method = "DELETE",
                pathTemplate = "/api/storage/nodes/{nodeId}",
                body = emptyMap(),
            ),
        )

        assertEquals(RagActionExecutionStatus.COMPLETED, result.status)
        assertEquals(listOf("trash:42"), repository.calls)
        assertEquals(listOf(42L), result.affectedNodeIds)
    }

    @Test
    fun `executes confirmed create folder through repository port when enabled`() = runBlocking {
        val repository = FakeRagActionRepositoryPort()
        val executor = RagActionExecutor(repositoryPort = repository, executionEnabled = true)

        val result = executor.execute(
            context = context,
            draft = draft(
                actionType = "folder.create",
                method = "POST",
                pathTemplate = "/api/storage/folders",
                body = mapOf(
                    "parentId" to "",
                    "folderName" to "视频目录",
                ),
            ),
        )

        assertEquals(RagActionExecutionStatus.COMPLETED, result.status)
        assertEquals(listOf("create-folder:null:视频目录"), repository.calls)
        assertEquals(listOf(77L), result.affectedNodeIds)
        assertTrue(result.message.contains("视频目录"))
    }

    @Test
    fun `executes batch trash with node ids`() = runBlocking {
        val repository = FakeRagActionRepositoryPort()
        val executor = RagActionExecutor(repositoryPort = repository, executionEnabled = true)

        val result = executor.execute(
            context = context,
            draft = draft(
                actionType = "collection.trash_by_name_contains",
                method = "POST",
                pathTemplate = "/api/storage/nodes/batch/trash",
                body = mapOf("nodeIds" to listOf(7, "8", 9L)),
            ),
        )

        assertEquals(RagActionExecutionStatus.COMPLETED, result.status)
        assertEquals(listOf("batch-trash:[7, 8, 9]"), repository.calls)
        assertEquals(listOf(7L, 8L, 9L), result.affectedNodeIds)
    }

    @Test
    fun `executes scoped collection trash with snapshot node ids`() = runBlocking {
        val repository = FakeRagActionRepositoryPort()
        val executor = RagActionExecutor(repositoryPort = repository, executionEnabled = true)

        val result = executor.execute(
            context = context,
            draft = draft(
                actionType = "collection.trash",
                method = "POST",
                pathTemplate = "/api/storage/nodes/batch/trash",
                body = mapOf("nodeIds" to listOf(7L, 8L)),
            ),
        )

        assertEquals(RagActionExecutionStatus.COMPLETED, result.status)
        assertEquals(listOf("batch-trash:[7, 8]"), repository.calls)
    }

    @Test
    fun `executes revalidated scoped trash only with complete fingerprints`() = runBlocking {
        val repository = FakeRagActionRepositoryPort()
        val executor = RagActionExecutor(repositoryPort = repository, executionEnabled = true)

        val result = executor.execute(
            context = context,
            draft = draft(
                actionType = "collection.trash_scoped",
                method = "POST",
                pathTemplate = "/api/storage/nodes/batch/trash/scoped",
                body = mapOf(
                    "selectorVersion" to "source_selector_v2",
                    "sourceParentId" to "",
                    "root" to true,
                    "nodeTypes" to listOf("FILE", "FOLDER"),
                    "nodeIds" to listOf(7L, 8L),
                    "scopeFingerprint" to "scope-hash",
                    "impactFingerprint" to "impact-hash",
                    "expectedImpactCount" to 5,
                ),
            ),
        )

        assertEquals(RagActionExecutionStatus.COMPLETED, result.status)
        assertEquals(listOf("scoped-trash:[7, 8]:true:5"), repository.calls)
    }

    @Test
    fun `rejects scoped trash draft with missing impact fingerprint`() = runBlocking {
        val repository = FakeRagActionRepositoryPort()
        val executor = RagActionExecutor(repositoryPort = repository, executionEnabled = true)

        val result = executor.execute(
            context = context,
            draft = draft(
                actionType = "collection.trash_scoped",
                method = "POST",
                pathTemplate = "/api/storage/nodes/batch/trash/scoped",
                body = mapOf(
                    "selectorVersion" to "source_selector_v2",
                    "root" to true,
                    "nodeTypes" to listOf("FILE", "FOLDER"),
                    "nodeIds" to listOf(7L, 8L),
                    "scopeFingerprint" to "scope-hash",
                    "expectedImpactCount" to 5,
                ),
            ),
        )

        assertEquals(RagActionExecutionStatus.INVALID_DRAFT, result.status)
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun `executes batch move with node ids and target parent`() = runBlocking {
        val repository = FakeRagActionRepositoryPort()
        val executor = RagActionExecutor(repositoryPort = repository, executionEnabled = true)

        val result = executor.execute(
            context = context,
            draft = draft(
                actionType = "collection.move_by_name_contains",
                method = "PUT",
                pathTemplate = "/api/storage/nodes/batch/move",
                body = mapOf(
                    "nodeIds" to listOf(1, "2", 3L),
                    "parentId" to "9",
                ),
            ),
        )

        assertEquals(RagActionExecutionStatus.COMPLETED, result.status)
        assertEquals(listOf("move:[1, 2, 3]:9"), repository.calls)
        assertEquals(listOf(1L, 2L, 3L), result.affectedNodeIds)
    }

    @Test
    fun `executes scoped collection move with snapshot node ids`() = runBlocking {
        val repository = FakeRagActionRepositoryPort()
        val executor = RagActionExecutor(repositoryPort = repository, executionEnabled = true)

        val result = executor.execute(
            context = context,
            draft = draft(
                actionType = "collection.move",
                method = "PUT",
                pathTemplate = "/api/storage/nodes/batch/move",
                body = mapOf("nodeIds" to listOf(1L, 2L), "parentId" to 9L),
            ),
        )

        assertEquals(RagActionExecutionStatus.COMPLETED, result.status)
        assertEquals(listOf("move:[1, 2]:9"), repository.calls)
    }

    @Test
    fun `executes transactional batch rename items`() = runBlocking {
        val repository = FakeRagActionRepositoryPort()
        val executor = RagActionExecutor(repositoryPort = repository, executionEnabled = true)

        val result = executor.execute(
            context = context,
            draft = draft(
                actionType = "collection.rename_add_prefix",
                method = "PUT",
                pathTemplate = "/api/storage/nodes/batch/rename",
                body = mapOf(
                    "items" to listOf(
                        mapOf("nodeId" to 1L, "name" to "归档-合同.pdf"),
                        mapOf("nodeId" to "2", "name" to "归档-报告.docx"),
                    ),
                ),
            ),
        )

        assertEquals(RagActionExecutionStatus.COMPLETED, result.status)
        assertEquals(listOf(1L, 2L), result.affectedNodeIds)
        assertTrue(repository.calls.single().startsWith("batch-rename:"))
    }

    @Test
    fun `executes share and returns share code`() = runBlocking {
        val repository = FakeRagActionRepositoryPort()
        val executor = RagActionExecutor(repositoryPort = repository, executionEnabled = true)

        val result = executor.execute(
            context = context,
            draft = draft(
                actionType = "share",
                method = "POST",
                pathTemplate = "/api/share-links",
                body = mapOf(
                    "nodeIds" to listOf(42L),
                    "title" to "项目资料",
                    "allowDownload" to true,
                    "allowSave" to false,
                ),
            ),
        )

        assertEquals(RagActionExecutionStatus.COMPLETED, result.status)
        assertEquals("SHARE123", result.shareCode)
        assertEquals("https://storage.example/share/SHARE123", result.shareUrl)
        assertEquals(listOf("share:[42]:项目资料:true:false"), repository.calls)
    }

    @Test
    fun `completed share with invalid response does not expose unsafe link`() = runBlocking {
        val repository = FakeRagActionRepositoryPort(shareCode = "bad/code")
        val executor = RagActionExecutor(repositoryPort = repository, executionEnabled = true)

        val result = executor.execute(
            context = context,
            draft = draft(
                actionType = "share",
                method = "POST",
                pathTemplate = "/api/share-links",
                body = mapOf("nodeIds" to listOf(42L)),
            ),
        )

        assertEquals(RagActionExecutionStatus.COMPLETED, result.status)
        assertEquals(null, result.shareCode)
        assertEquals(null, result.shareUrl)
        assertTrue(result.message.contains("暂时无法复制链接"))
        assertEquals(listOf("share:[42]:null:true:true"), repository.calls)
    }

    private fun draft(
        actionType: String,
        method: String,
        pathTemplate: String?,
        path: String? = null,
        status: String = "backend_action_ready",
        confirmedByUser: Boolean = true,
        executableByBackend: Boolean = true,
        requiredClientFields: List<String>? = emptyList(),
        body: Map<String, Any>? = emptyMap(),
        pathVariables: Map<String, Any>? = null,
    ): RagBackendActionDraft {
        val resolvedPathVariables = pathVariables ?: when (actionType) {
            "rename",
            "delete",
            -> mapOf("nodeId" to 42L)
            else -> emptyMap()
        }

        return RagBackendActionDraft(
            status = status,
            bridgeVersion = "action_bridge_v2",
            actionType = actionType,
            nextAction = "handoff_to_backend",
            confirmedByUser = confirmedByUser,
            executableByBackend = executableByBackend,
            authorizationRequired = true,
            method = method,
            pathTemplate = pathTemplate,
            path = path,
            contentType = "application/json",
            pathVariables = resolvedPathVariables,
            queryParameters = emptyMap(),
            body = body,
            requiredClientFields = requiredClientFields,
            targetCandidate = null,
            message = null,
        )
    }
}

private class FakeRagActionRepositoryPort(
    private val shareCode: String = "SHARE123",
) : RagActionRepositoryPort {
    val calls = mutableListOf<String>()

    override suspend fun renameNode(baseUrl: String, token: String, nodeId: Long, name: String): StorageNode {
        calls += "rename:$nodeId:$name"
        return storageNode(nodeId, name)
    }

    override suspend fun renameNodes(
        baseUrl: String,
        token: String,
        items: List<BatchRenameNodeItemPayload>,
    ): List<StorageNode> {
        calls += "batch-rename:$items"
        return items.map { item -> storageNode(item.nodeId, item.name) }
    }

    override suspend fun moveNodeToTrash(baseUrl: String, token: String, nodeId: Long): ApiMessageResponse {
        calls += "trash:$nodeId"
        return ApiMessageResponse("ok")
    }

    override suspend fun moveNodesToTrash(
        baseUrl: String,
        token: String,
        nodeIds: List<Long>,
    ): ApiMessageResponse {
        calls += "batch-trash:$nodeIds"
        return ApiMessageResponse("ok")
    }

    override suspend fun moveScopedNodesToTrash(
        baseUrl: String,
        token: String,
        payload: ScopedTrashPayload,
    ): ApiMessageResponse {
        calls += "scoped-trash:${payload.nodeIds}:${payload.root}:${payload.expectedImpactCount}"
        return ApiMessageResponse("ok")
    }

    override suspend fun moveNodes(
        baseUrl: String,
        token: String,
        nodeIds: List<Long>,
        parentId: Long?,
    ): List<StorageNode> {
        calls += "move:$nodeIds:$parentId"
        return nodeIds.map { storageNode(it, "node-$it") }
    }

    override suspend fun createFolder(
        baseUrl: String,
        token: String,
        parentId: Long?,
        folderName: String,
    ): StorageNode {
        calls += "create-folder:$parentId:$folderName"
        return storageNode(77L, folderName, StorageNodeType.FOLDER)
    }

    override suspend fun createShareLink(
        baseUrl: String,
        token: String,
        nodeIds: List<Long>,
        title: String?,
        password: String?,
        expiresInDays: Int?,
        allowDownload: Boolean,
        allowSave: Boolean,
    ): ShareLinkSummaryResponse {
        calls += "share:$nodeIds:$title:$allowDownload:$allowSave"
        return ShareLinkSummaryResponse(
            id = 1L,
            shareCode = shareCode,
            title = title ?: "分享",
            hasPassword = password != null,
            expiresAt = null,
            allowDownload = allowDownload,
            allowSave = allowSave,
            status = "ACTIVE",
            viewCount = 0L,
            lastAccessedAt = null,
            createdAt = "2026-08-11T10:00:00",
            updatedAt = "2026-08-11T10:00:00",
            itemCount = nodeIds.size.toLong(),
        )
    }

    private fun storageNode(
        id: Long,
        name: String,
        type: StorageNodeType = StorageNodeType.FILE,
    ): StorageNode =
        StorageNode(
            id = id,
            parentId = null,
            name = name,
            type = type,
            size = 1L,
            extension = "docx",
            mimeType = "application/docx",
            updatedAt = "2026-08-11T10:00:00",
            deletedAt = null,
        )
}
