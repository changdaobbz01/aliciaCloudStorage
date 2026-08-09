package com.alicia.cloudstorage.phone

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
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.alicia.cloudstorage.phone.ui.AliciaCloudApp
import com.alicia.cloudstorage.phone.ui.AliciaCloudTheme
import com.alicia.cloudstorage.phone.ui.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val appViewModel: MainViewModel by viewModels {
        MainViewModel.provideFactory(applicationContext)
    }
    private val clipboardManager: ClipboardManager by lazy {
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    private val clipboardChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        scheduleClipboardShareCheck()
    }
    private var clipboardShareCheckJob: Job? = null
    private var clipboardListenerRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        setContent {
            AliciaCloudTheme {
                AliciaCloudApp(viewModel = appViewModel)
            }
        }

        appViewModel.handleIncomingShareUri(intent?.data)
    }

    override fun onStart() {
        super.onStart()
        if (!clipboardListenerRegistered) {
            clipboardManager.addPrimaryClipChangedListener(clipboardChangedListener)
            clipboardListenerRegistered = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        appViewModel.handleIncomingShareUri(intent.data)
    }

    override fun onResume() {
        super.onResume()
        scheduleClipboardShareCheck()
    }

    override fun onPause() {
        clipboardShareCheckJob?.cancel()
        clipboardShareCheckJob = null
        super.onPause()
    }

    override fun onStop() {
        if (clipboardListenerRegistered) {
            clipboardManager.removePrimaryClipChangedListener(clipboardChangedListener)
            clipboardListenerRegistered = false
        }
        clipboardShareCheckJob?.cancel()
        clipboardShareCheckJob = null
        super.onStop()
    }

    private fun scheduleClipboardShareCheck() {
        clipboardShareCheckJob?.cancel()
        clipboardShareCheckJob = lifecycleScope.launch {
            delay(350)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                appViewModel.checkClipboardForShareLink()
            }
        }
    }
}
