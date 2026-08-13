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
        ): Intent = FileDetailArgs(
            node = node,
            baseUrl = baseUrl,
            authToken = authToken,
        ).writeTo(Intent(context, FileDetailActivity::class.java))

        fun contentChanged(data: Intent?): Boolean =
            data?.getBooleanExtra(EXTRA_CONTENT_CHANGED, false) == true
    }
}

data class FileDetailArgs(
    val node: StorageNode,
    val baseUrl: String,
    val authToken: String,
) {
    fun writeTo(intent: Intent): Intent = intent.apply {
        putExtra(EXTRA_NODE_ID, node.id)
        node.parentId?.let { putExtra(EXTRA_PARENT_ID, it) }
        putExtra(EXTRA_HAS_PARENT_ID, node.parentId != null)
        putExtra(EXTRA_NODE_NAME, node.name)
        putExtra(EXTRA_NODE_TYPE, node.type.name)
        putExtra(EXTRA_NODE_SIZE, node.size)
        putExtra(EXTRA_NODE_EXTENSION, node.extension)
        putExtra(EXTRA_NODE_MIME_TYPE, node.mimeType)
        putExtra(EXTRA_NODE_UPDATED_AT, node.updatedAt)
        putExtra(EXTRA_NODE_DELETED_AT, node.deletedAt)
        putExtra(EXTRA_BASE_URL, baseUrl)
        putExtra(EXTRA_AUTH_TOKEN, authToken)
    }

    companion object {
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

        fun fromIntent(intent: Intent): FileDetailArgs? {
            val node = nodeFromIntent(intent) ?: return null
            val baseUrl = intent.getStringExtra(EXTRA_BASE_URL)?.takeIf(String::isNotBlank) ?: return null
            val authToken = intent.getStringExtra(EXTRA_AUTH_TOKEN)?.takeIf(String::isNotBlank) ?: return null
            return FileDetailArgs(node = node, baseUrl = baseUrl, authToken = authToken)
        }

        fun nodeFromIntent(intent: Intent): StorageNode? {
            val id = intent.getLongExtra(EXTRA_NODE_ID, Long.MIN_VALUE)
            if (id == Long.MIN_VALUE) return null

            val name = intent.getStringExtra(EXTRA_NODE_NAME) ?: return null
            val type = intent.getStringExtra(EXTRA_NODE_TYPE)
                ?.let { runCatching { StorageNodeType.valueOf(it) }.getOrNull() }
                ?: return null
            return StorageNode(
                id = id,
                parentId = if (intent.getBooleanExtra(EXTRA_HAS_PARENT_ID, false)) {
                    intent.getLongExtra(EXTRA_PARENT_ID, 0L)
                } else {
                    null
                },
                name = name,
                type = type,
                size = intent.getLongExtra(EXTRA_NODE_SIZE, 0L),
                extension = intent.getStringExtra(EXTRA_NODE_EXTENSION),
                mimeType = intent.getStringExtra(EXTRA_NODE_MIME_TYPE),
                updatedAt = intent.getStringExtra(EXTRA_NODE_UPDATED_AT).orEmpty(),
                deletedAt = intent.getStringExtra(EXTRA_NODE_DELETED_AT),
            )
        }
    }
}
