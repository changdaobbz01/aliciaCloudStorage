package com.alicia.cloudstorage.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alicia.cloudstorage.phone.ui.AliciaCloudApp
import com.alicia.cloudstorage.phone.ui.AliciaCloudTheme
import com.alicia.cloudstorage.phone.ui.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AliciaCloudTheme {
                val appViewModel: MainViewModel = viewModel(
                    factory = MainViewModel.provideFactory(applicationContext),
                )
                AliciaCloudApp(viewModel = appViewModel)
            }
        }
    }
}
