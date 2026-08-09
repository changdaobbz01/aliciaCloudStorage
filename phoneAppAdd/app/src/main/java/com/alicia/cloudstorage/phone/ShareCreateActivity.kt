package com.alicia.cloudstorage.phone

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
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
import com.alicia.cloudstorage.phone.ui.ShareCreateScreen
import com.alicia.cloudstorage.phone.ui.ShareCreateViewModel
import com.alicia.cloudstorage.phone.ui.ShareSelection

class ShareCreateActivity : ComponentActivity() {
    private lateinit var shareViewModel: ShareCreateViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = ShareCreateArgs.fromIntent(intent) ?: run {
            finish()
            return
        }

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

        shareViewModel = ViewModelProvider(
            this,
            ShareCreateViewModel.provideFactory(args),
        )[ShareCreateViewModel::class.java]

        setContent {
            AliciaCloudTheme {
                ShareCreateScreen(
                    args = args,
                    viewModel = shareViewModel,
                    onBack = ::finish,
                    onCopy = ::copyShareInfo,
                    onSystemShare = ::openSystemShare,
                )
            }
        }
    }

    override fun finish() {
        if (this::shareViewModel.isInitialized && shareViewModel.uiState.value.createdShare != null) {
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_SHARE_CREATED, true))
        }
        super.finish()
    }

    private fun copyShareInfo(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Alicia 云盘分享", text))
        shareViewModel.showMessage("分享信息已复制。")
    }

    private fun openSystemShare(text: String) {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "分享 Alicia 云盘链接",
            ),
        )
    }

    companion object {
        private const val EXTRA_SHARE_CREATED = "share_create_completed"

        fun createIntent(
            context: Context,
            nodes: List<StorageNode>,
            baseUrl: String,
            authToken: String,
        ): Intent = ShareCreateArgs(
            selection = ShareSelection(nodes.distinctBy(StorageNode::id).map { it.copy() }),
            baseUrl = baseUrl,
            authToken = authToken,
        ).writeTo(Intent(context, ShareCreateActivity::class.java))

        fun shareCreated(data: Intent?): Boolean =
            data?.getBooleanExtra(EXTRA_SHARE_CREATED, false) == true
    }
}

data class ShareCreateArgs(
    val selection: ShareSelection,
    val baseUrl: String,
    val authToken: String,
) {
    fun writeTo(intent: Intent): Intent = intent.apply {
        putExtra(EXTRA_BASE_URL, baseUrl)
        putExtra(EXTRA_AUTH_TOKEN, authToken)
        putParcelableArrayListExtra(
            EXTRA_NODES,
            ArrayList(selection.nodes.map(StorageNode::toShareBundle)),
        )
    }

    companion object {
        private const val EXTRA_BASE_URL = "share_create_base_url"
        private const val EXTRA_AUTH_TOKEN = "share_create_auth_token"
        private const val EXTRA_NODES = "share_create_nodes"

        fun fromIntent(intent: Intent): ShareCreateArgs? {
            val baseUrl = intent.getStringExtra(EXTRA_BASE_URL)?.takeIf(String::isNotBlank) ?: return null
            val authToken = intent.getStringExtra(EXTRA_AUTH_TOKEN)?.takeIf(String::isNotBlank) ?: return null
            val bundles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(EXTRA_NODES, Bundle::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(EXTRA_NODES)
            } ?: return null
            val nodes = bundles.mapNotNull(Bundle::toShareNode).distinctBy(StorageNode::id)
            val selection = runCatching { ShareSelection(nodes) }.getOrNull() ?: return null
            return ShareCreateArgs(selection, baseUrl, authToken)
        }
    }
}

private fun StorageNode.toShareBundle(): Bundle = Bundle().apply {
    putLong("id", id)
    putBoolean("hasParentId", parentId != null)
    parentId?.let { putLong("parentId", it) }
    putString("name", name)
    putString("type", type.name)
    putLong("size", size)
    putString("extension", extension)
    putString("mimeType", mimeType)
    putString("updatedAt", updatedAt)
    putString("deletedAt", deletedAt)
}

private fun Bundle.toShareNode(): StorageNode? {
    val id = getLong("id", Long.MIN_VALUE)
    if (id == Long.MIN_VALUE) return null
    val name = getString("name") ?: return null
    val type = getString("type")
        ?.let { runCatching { StorageNodeType.valueOf(it) }.getOrNull() }
        ?: return null
    return StorageNode(
        id = id,
        parentId = if (getBoolean("hasParentId")) getLong("parentId") else null,
        name = name,
        type = type,
        size = getLong("size"),
        extension = getString("extension"),
        mimeType = getString("mimeType"),
        updatedAt = getString("updatedAt").orEmpty(),
        deletedAt = getString("deletedAt"),
    )
}
