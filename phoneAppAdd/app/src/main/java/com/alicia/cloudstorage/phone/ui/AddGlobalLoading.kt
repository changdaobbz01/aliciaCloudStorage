package com.alicia.cloudstorage.phone.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alicia.cloudstorage.phone.R
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val LOADING_MIN_VISIBLE_MILLIS = 420L

private val LoadingOverlayScrim = Color(0x526B7890)
private val LoadingPanelLine = Color(0xFFE4EAF4)
private val LoadingTrack = Color(0xFFD9E8FB)
private val LoadingBlue = Color(0xFF0B6BFF)
private val LoadingInk = Color(0xFF111827)
private val LoadingMuted = Color(0xFF8993A6)

@Composable
internal fun AddGlobalLoadingOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    title: String = "正在加载",
    supporting: String = "请稍候",
) {
    var rendered by remember { mutableStateOf(false) }
    var shownAtMillis by remember { mutableLongStateOf(0L) }

    LaunchedEffect(visible) {
        if (visible) {
            if (!rendered) {
                rendered = true
                shownAtMillis = SystemClock.elapsedRealtime()
            }
        } else if (rendered) {
            val elapsed = SystemClock.elapsedRealtime() - shownAtMillis
            delay(max(0L, LOADING_MIN_VISIBLE_MILLIS - elapsed))
            rendered = false
        }
    }

    if (!rendered) return

    BackHandler(enabled = true) {}
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LoadingOverlayScrim)
            .semantics { contentDescription = "$title，$supporting" }
            .noRippleClickable(onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        val panelShape = RoundedCornerShape(20.dp)
        Column(
            modifier = Modifier
                .width(204.dp)
                .height(216.dp)
                .scale(0.75f)
                .shadow(
                    elevation = 18.dp,
                    shape = panelShape,
                    ambientColor = Color(0x1F4C6C99),
                    spotColor = Color(0x294C6C99),
                )
                .clip(panelShape)
                .background(Color(0xFCFFFFFF))
                .border(1.dp, LoadingPanelLine, panelShape),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            LoadingSquareTrack(modifier = Modifier.size(128.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                color = LoadingInk,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = supporting,
                color = LoadingMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LoadingSquareTrack(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "Alicia square loading")
    val activePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
        ),
        label = "active square segment",
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val middleX = width / 2f
            val middleY = height / 2f
            val gapX = width * 0.085f
            val gapY = height * 0.085f
            val radius = min(width, height) * 0.205f
            val stroke = Stroke(
                width = 5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )

            val topRight = Path().apply {
                moveTo(middleX + gapX, 0f)
                lineTo(width - radius, 0f)
                quadraticBezierTo(width, 0f, width, radius)
                lineTo(width, middleY - gapY)
            }
            val bottomRight = Path().apply {
                moveTo(width, middleY + gapY)
                lineTo(width, height - radius)
                quadraticBezierTo(width, height, width - radius, height)
                lineTo(middleX + gapX, height)
            }
            val bottomLeft = Path().apply {
                moveTo(middleX - gapX, height)
                lineTo(radius, height)
                quadraticBezierTo(0f, height, 0f, height - radius)
                lineTo(0f, middleY + gapY)
            }
            val topLeft = Path().apply {
                moveTo(0f, middleY - gapY)
                lineTo(0f, radius)
                quadraticBezierTo(0f, 0f, radius, 0f)
                lineTo(middleX - gapX, 0f)
            }
            val paths = listOf(topRight, bottomRight, bottomLeft, topLeft)

            paths.forEach { path ->
                drawPath(path = path, color = LoadingTrack, style = stroke)
            }
            paths.forEachIndexed { index, path ->
                val rawDistance = abs(activePhase - index.toFloat())
                val distance = min(rawDistance, 4f - rawDistance)
                val intensity = (1f - distance).coerceIn(0f, 1f)
                if (intensity > 0f) {
                    drawPath(
                        path = path,
                        color = LoadingBlue.copy(alpha = 0.28f + (0.72f * intensity)),
                        style = stroke,
                    )
                }
            }
        }

        Image(
            painter = painterResource(R.drawable.ic_alicia_cloud_loading_mark),
            contentDescription = null,
            modifier = Modifier.size(112.dp),
        )
    }
}
