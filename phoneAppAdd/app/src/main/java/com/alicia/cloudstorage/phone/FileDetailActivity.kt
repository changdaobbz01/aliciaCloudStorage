package com.alicia.cloudstorage.phone

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.alicia.cloudstorage.phone.data.StorageNode
import com.alicia.cloudstorage.phone.data.StorageNodeType
import com.alicia.cloudstorage.phone.ui.AliciaCloudTheme
import com.alicia.cloudstorage.phone.ui.FileDetailScreen
import com.alicia.cloudstorage.phone.ui.FileDetailViewModel

class FileDetailActivity : ComponentActivity() {
    private lateinit var detailArgs: FileDetailArgs
    private lateinit var detailViewModel: FileDetailViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = FileDetailArgs.fromIntent(intent) ?: run {
            finish()
            return
        }
        detailArgs = args

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        detailViewModel = ViewModelProvider(
            this,
            FileDetailViewModel.provideFactory(applicationContext, args),
        )[FileDetailViewModel::class.java]

        setContent {
            AliciaCloudTheme {
                FileDetailScreen(
                    args = args,
                    viewModel = detailViewModel,
                    onBack = ::finish,
                )
            }
        }
    }

    override fun finish() {
        if (this::detailArgs.isInitialized &&
            this::detailViewModel.isInitialized &&
            detailViewModel.uiState.value.contentChanged
        ) {
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(EXTRA_CONTENT_CHANGED, true),
            )
        }
        super.finish()
    }

    companion object {
        private const val EXTRA_CONTENT_CHANGED = "file_detail_content_changed"

        fun createIntent(
            context: Context,
            node: StorageNode,
            baseUrl: String,
            authToken: String,
            galleryNodes: List<StorageNode> = listOf(node),
        ): Intent = FileDetailArgs(
            node = node,
            baseUrl = baseUrl,
            authToken = authToken,
            galleryNodes = galleryNodes,
        ).writeTo(Intent(context, FileDetailActivity::class.java))

        fun contentChanged(data: Intent?): Boolean =
            data?.getBooleanExtra(EXTRA_CONTENT_CHANGED, false) == true
    }
}

data class FileDetailArgs(
    val node: StorageNode,
    val baseUrl: String,
    val authToken: String,
    val galleryNodes: List<StorageNode> = listOf(node),
) {
    fun writeTo(intent: Intent): Intent = intent.apply {
        writeStorageNode(intent = this, prefix = "", node = node)
        val safeGalleryNodes = normalizeGalleryNodes(node, galleryNodes)
        putExtra(EXTRA_GALLERY_COUNT, safeGalleryNodes.size)
        safeGalleryNodes.forEachIndexed { index, galleryNode ->
            writeStorageNode(intent = this, prefix = "$EXTRA_GALLERY_NODE_PREFIX$index.", node = galleryNode)
        }
        putExtra(EXTRA_BASE_URL, baseUrl)
        putExtra(EXTRA_AUTH_TOKEN, authToken)
    }

    companion object {
        private const val MAX_DETAIL_GALLERY_NODES = 80
        private const val EXTRA_NODE_ID = "file_detail_node_id"
        private const val EXTRA_PARENT_ID = "file_detail_parent_id"
        private const val EXTRA_HAS_PARENT_ID = "file_detail_has_parent_id"
        private const val EXTRA_NODE_NAME = "file_detail_node_name"
        private const val EXTRA_NODE_TYPE = "file_detail_node_type"
        private const val EXTRA_NODE_SIZE = "file_detail_node_size"
        private const val EXTRA_NODE_EXTENSION = "file_detail_node_extension"
        private const val EXTRA_NODE_MIME_TYPE = "file_detail_node_mime_type"
        private const val EXTRA_NODE_UPDATED_AT = "file_detail_node_updated_at"
        private const val EXTRA_NODE_DELETED_AT = "file_detail_node_deleted_at"
        private const val EXTRA_BASE_URL = "file_detail_base_url"
        private const val EXTRA_AUTH_TOKEN = "file_detail_auth_token"
        private const val EXTRA_GALLERY_COUNT = "file_detail_gallery_count"
        private const val EXTRA_GALLERY_NODE_PREFIX = "file_detail_gallery_node_"

        fun fromIntent(intent: Intent): FileDetailArgs? {
            val node = nodeFromIntent(intent) ?: return null
            val baseUrl = intent.getStringExtra(EXTRA_BASE_URL)?.takeIf(String::isNotBlank) ?: return null
            val authToken = intent.getStringExtra(EXTRA_AUTH_TOKEN)?.takeIf(String::isNotBlank) ?: return null
            return FileDetailArgs(
                node = node,
                baseUrl = baseUrl,
                authToken = authToken,
                galleryNodes = galleryNodesFromIntent(intent, node),
            )
        }

        fun nodeFromIntent(intent: Intent): StorageNode? =
            nodeFromIntent(intent, prefix = "")

        private fun galleryNodesFromIntent(intent: Intent, currentNode: StorageNode): List<StorageNode> {
            val count = intent.getIntExtra(EXTRA_GALLERY_COUNT, 0)
                .coerceIn(0, MAX_DETAIL_GALLERY_NODES)
            val nodes = (0 until count).mapNotNull { index ->
                nodeFromIntent(intent, prefix = "$EXTRA_GALLERY_NODE_PREFIX$index.")
            }
            return normalizeGalleryNodes(currentNode, nodes)
        }

        private fun nodeFromIntent(intent: Intent, prefix: String): StorageNode? {
            val id = intent.getLongExtra(prefix + EXTRA_NODE_ID, Long.MIN_VALUE)
            if (id == Long.MIN_VALUE) return null

            val name = intent.getStringExtra(prefix + EXTRA_NODE_NAME) ?: return null
            val type = intent.getStringExtra(prefix + EXTRA_NODE_TYPE)
                ?.let { runCatching { StorageNodeType.valueOf(it) }.getOrNull() }
                ?: return null
            return StorageNode(
                id = id,
                parentId = if (intent.getBooleanExtra(prefix + EXTRA_HAS_PARENT_ID, false)) {
                    intent.getLongExtra(prefix + EXTRA_PARENT_ID, 0L)
                } else {
                    null
                },
                name = name,
                type = type,
                size = intent.getLongExtra(prefix + EXTRA_NODE_SIZE, 0L),
                extension = intent.getStringExtra(prefix + EXTRA_NODE_EXTENSION),
                mimeType = intent.getStringExtra(prefix + EXTRA_NODE_MIME_TYPE),
                updatedAt = intent.getStringExtra(prefix + EXTRA_NODE_UPDATED_AT).orEmpty(),
                deletedAt = intent.getStringExtra(prefix + EXTRA_NODE_DELETED_AT),
            )
        }

        private fun writeStorageNode(intent: Intent, prefix: String, node: StorageNode) {
            intent.putExtra(prefix + EXTRA_NODE_ID, node.id)
            node.parentId?.let { intent.putExtra(prefix + EXTRA_PARENT_ID, it) }
            intent.putExtra(prefix + EXTRA_HAS_PARENT_ID, node.parentId != null)
            intent.putExtra(prefix + EXTRA_NODE_NAME, node.name)
            intent.putExtra(prefix + EXTRA_NODE_TYPE, node.type.name)
            intent.putExtra(prefix + EXTRA_NODE_SIZE, node.size)
            intent.putExtra(prefix + EXTRA_NODE_EXTENSION, node.extension)
            intent.putExtra(prefix + EXTRA_NODE_MIME_TYPE, node.mimeType)
            intent.putExtra(prefix + EXTRA_NODE_UPDATED_AT, node.updatedAt)
            intent.putExtra(prefix + EXTRA_NODE_DELETED_AT, node.deletedAt)
        }

        private fun normalizeGalleryNodes(
            currentNode: StorageNode,
            nodes: List<StorageNode>,
        ): List<StorageNode> {
            val byId = linkedMapOf<Long, StorageNode>()
            nodes
                .asSequence()
                .filter { it.type == StorageNodeType.FILE }
                .forEach { node ->
                    if (byId.size < MAX_DETAIL_GALLERY_NODES || byId.containsKey(node.id)) {
                        byId[node.id] = if (node.id == currentNode.id) currentNode else node
                    }
                }

            if (currentNode.type == StorageNodeType.FILE && !byId.containsKey(currentNode.id)) {
                val retained = byId.values.take(MAX_DETAIL_GALLERY_NODES - 1)
                return listOf(currentNode) + retained
            }

            return byId.values
                .take(MAX_DETAIL_GALLERY_NODES)
                .ifEmpty { listOf(currentNode) }
        }
    }
}
