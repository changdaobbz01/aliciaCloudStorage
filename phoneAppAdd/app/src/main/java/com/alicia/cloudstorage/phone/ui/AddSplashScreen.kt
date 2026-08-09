package com.alicia.cloudstorage.phone.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.alicia.cloudstorage.phone.R

private val SplashInk = Color(0xFF111827)
private val SplashMuted = Color(0xFF52657C)
private val SplashBlue = Color(0xFF087BF5)
private val SplashTrack = Color(0xFFDCE7F5)

@Composable
fun AddSplashContent(modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color.White, Color(0xFFF7FBFF), Color(0xFFEAF6FF)),
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        if (maxWidth > maxHeight) {
            LandscapeSplashContent(maxWidth = maxWidth, maxHeight = maxHeight)
        } else {
            PortraitSplashContent(maxWidth = maxWidth, maxHeight = maxHeight)
        }
    }
}

@Composable
private fun PortraitSplashContent(maxWidth: androidx.compose.ui.unit.Dp, maxHeight: androidx.compose.ui.unit.Dp) {
    val logoSize = minOf(maxWidth * 0.68f, maxHeight * 0.34f, 292.dp).coerceAtLeast(148.dp)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height((maxHeight * 0.07f).coerceIn(24.dp, 68.dp)))
        SplashLogo(size = logoSize)
        Spacer(modifier = Modifier.height(30.dp))
        SplashLoadingVisual(modifier = Modifier.fillMaxWidth(0.86f).widthIn(max = 350.dp))
        Spacer(modifier = Modifier.weight(1f))
        SplashCopy()
        Spacer(modifier = Modifier.height((maxHeight * 0.075f).coerceIn(30.dp, 72.dp)))
    }
}

@Composable
private fun LandscapeSplashContent(maxWidth: androidx.compose.ui.unit.Dp, maxHeight: androidx.compose.ui.unit.Dp) {
    val logoSize = minOf(maxWidth * 0.32f, maxHeight * 0.62f, 250.dp)
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(46.dp),
    ) {
        SplashLogo(size = logoSize)
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SplashLoadingVisual(modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp))
            Spacer(modifier = Modifier.height(30.dp))
            SplashCopy()
        }
    }
}

@Composable
private fun SplashLogo(size: androidx.compose.ui.unit.Dp) {
    Image(
        painter = painterResource(R.drawable.ic_alicia_cloud_logo),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f)),
    )
}

@Composable
private fun SplashLoadingVisual(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "splash loading")
    val progress by transition.animateFloat(
        initialValue = 0.34f,
        targetValue = 0.76f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splash progress",
    )
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(24.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(99.dp))
                    .background(SplashTrack),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(8.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(99.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF1599F7), Color(0xFF066AF5)),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .offset(x = (maxWidth * progress - 12.dp).coerceIn(0.dp, maxWidth - 24.dp))
                    .size(24.dp)
                    .shadow(7.dp, CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        SplashWaveform()
    }
}

@Composable
private fun SplashWaveform() {
    val barHeights = listOf(2, 3, 5, 8, 13, 20, 31, 18, 11, 7, 12, 22, 36, 48, 30, 19, 12, 22, 35, 25, 16, 10, 7, 5, 3, 2)
    Row(
        modifier = Modifier.fillMaxWidth().height(50.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        barHeights.forEachIndexed { index, height ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 1.dp)
                    .width(if (index % 3 == 0) 2.dp else 1.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(SplashBlue.copy(alpha = 0.38f + (height / 48f) * 0.42f)),
            )
        }
    }
}

@Composable
private fun SplashCopy() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "正在进入云盘",
            color = SplashInk,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "同步文件中，请稍候",
            color = SplashMuted,
            fontSize = 18.sp,
            lineHeight = 25.sp,
            textAlign = TextAlign.Center,
        )
    }
}
