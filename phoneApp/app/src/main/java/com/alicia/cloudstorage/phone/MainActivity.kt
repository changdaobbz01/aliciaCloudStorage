package com.alicia.cloudstorage.phone

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.alicia.cloudstorage.phone.ui.AliciaCloudApp
import com.alicia.cloudstorage.phone.ui.AliciaCloudTheme
import com.alicia.cloudstorage.phone.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val appViewModel: MainViewModel by viewModels {
        MainViewModel.provideFactory(applicationContext)
    }

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
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        setContent {
            AliciaCloudTheme {
                AliciaCloudApp(viewModel = appViewModel)
            }
        }

        appViewModel.handleIncomingShareUri(intent?.data)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        appViewModel.handleIncomingShareUri(intent.data)
    }

    override fun onResume() {
        super.onResume()
        appViewModel.checkClipboardForShareLink()
    }
}
