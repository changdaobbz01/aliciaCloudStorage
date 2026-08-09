package com.alicia.cloudstorage.phone

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsetsController
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout

class SplashActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val openMainRunnable = Runnable(::openMainActivity)
    private var progressAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isTaskRoot && intent?.action == Intent.ACTION_MAIN) {
            openMainActivity()
            return
        }

        setContentView(R.layout.activity_splash)
        configureSystemBars()
        startProgressAnimation()
        mainHandler.postDelayed(openMainRunnable, MINIMUM_BRAND_DURATION_MILLIS)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(openMainRunnable)
        progressAnimator?.cancel()
        progressAnimator = null
        super.onDestroy()
    }

    private fun configureSystemBars() {
        window.statusBarColor = Color.rgb(247, 251, 255)
        window.navigationBarColor = Color.rgb(234, 246, 255)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    private fun startProgressAnimation() {
        val container = findViewById<FrameLayout>(R.id.splash_progress_container)
        val fill = findViewById<View>(R.id.splash_progress_fill)
        val thumb = findViewById<View>(R.id.splash_progress_thumb)
        container.post {
            progressAnimator = ValueAnimator.ofFloat(0.34f, 0.76f).apply {
                duration = 1_150L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animator ->
                    val progress = animator.animatedValue as Float
                    fill.pivotX = 0f
                    fill.scaleX = progress
                    thumb.translationX = (container.width * progress - thumb.width / 2f)
                        .coerceIn(0f, (container.width - thumb.width).toFloat())
                }
                start()
            }
        }
    }

    private fun openMainActivity() {
        if (isFinishing || isDestroyed) return
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
        )
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        finish()
    }

    private companion object {
        const val MINIMUM_BRAND_DURATION_MILLIS = 850L
    }
}
