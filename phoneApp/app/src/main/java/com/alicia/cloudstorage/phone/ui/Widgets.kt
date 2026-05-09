package com.alicia.cloudstorage.phone.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import com.alicia.cloudstorage.phone.R
import com.alicia.cloudstorage.phone.data.StorageNode
import com.alicia.cloudstorage.phone.data.StorageNodeFilter
import com.alicia.cloudstorage.phone.data.StorageNodeType
import com.alicia.cloudstorage.phone.data.User
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.delay

data class AliciaQuickAction(
    val label: String,
    val hint: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

data class AliciaBottomNavItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
    val prominent: Boolean = false,
    val enabled: Boolean = true,
    val shellResIdOverride: Int? = null,
    val showShellContent: Boolean = true,
)

private enum class AliciaBottomShellSlot {
    Left,
    Center,
    Right,
}

private data class AliciaBottomShellSlotSpec(
    val slot: AliciaBottomShellSlot,
    val xRatio: Float,
    val yRatio: Float,
    val widthRatio: Float,
    val heightRatio: Float,
)

private val MechaDeep = Color(0xFF071120)
private val MechaShell = Color(0xFF111B2B)
private val MechaArmor = Color(0xFFF8FBFF)
private val MechaArmorRaised = Color(0xFFFFFFFF)
private val MechaBorder = Color(0xFFD7E3F4)
private val MechaInk = Color(0xFF101626)
private val MechaMuted = Color(0xFF748094)
private val MechaBlue = Color(0xFF1E63FF)
private val MechaCyan = Color(0xFF48D5FF)
private val MechaOrange = Color(0xFFFF9D24)
private val AliciaLightningFrameResIds = intArrayOf(
    R.drawable.alicia_fx_lightning_01,
    R.drawable.alicia_fx_lightning_02,
    R.drawable.alicia_fx_lightning_03,
    R.drawable.alicia_fx_lightning_04,
    R.drawable.alicia_fx_lightning_05,
    R.drawable.alicia_fx_lightning_06,
    R.drawable.alicia_fx_lightning_07,
    R.drawable.alicia_fx_lightning_08,
)
val AliciaMechaFontFamily = FontFamily.SansSerif

enum class AliciaMechaActionButtonTone {
    Primary,
    Secondary,
    Danger,
}

object AliciaMechaDesignSpec {
    val screenWidth = 393.dp
    val screenHeight = 829.dp
    val pagePadding = 11.dp
    val contentGap = 9.dp
    val heroHeight = 132.dp
    val quickActionHeight = 86.dp
    val metricCardHeight = 60.dp
    val bottomBarHeight = 74.dp
    val compactBottomBarHeight = 62.dp
}

private val AliciaMi11BottomShellSlots = listOf(
    AliciaBottomShellSlotSpec(
        slot = AliciaBottomShellSlot.Left,
        xRatio = 0.1398f,
        yRatio = 0.2459f,
        widthRatio = 0.2309f,
        heightRatio = 0.5967f,
    ),
    AliciaBottomShellSlotSpec(
        slot = AliciaBottomShellSlot.Center,
        xRatio = 0.3765f,
        yRatio = 0.2652f,
        widthRatio = 0.2509f,
        heightRatio = 0.5773f,
    ),
    AliciaBottomShellSlotSpec(
        slot = AliciaBottomShellSlot.Right,
        xRatio = 0.6332f,
        yRatio = 0.2459f,
        widthRatio = 0.2304f,
        heightRatio = 0.5967f,
    ),
)

@Composable
private fun AliciaNineSliceBackground(
    resId: Int,
    slice: Dp,
    modifier: Modifier = Modifier,
) {
    val image = ImageBitmap.imageResource(id = resId)
    Canvas(modifier = modifier) {
        val dstWidth = size.width.roundToInt().coerceAtLeast(1)
        val dstHeight = size.height.roundToInt().coerceAtLeast(1)
        val requestedSlice = slice.value.roundToInt()
        val srcSliceX = requestedSlice.coerceIn(1, (image.width / 2 - 1).coerceAtLeast(1))
        val srcSliceY = requestedSlice.coerceIn(1, (image.height / 2 - 1).coerceAtLeast(1))
        val dstSliceX = requestedSlice.coerceAtMost((dstWidth / 2 - 1).coerceAtLeast(1))
        val dstSliceY = requestedSlice.coerceAtMost((dstHeight / 2 - 1).coerceAtLeast(1))

        val srcX = intArrayOf(0, srcSliceX, image.width - srcSliceX)
        val srcY = intArrayOf(0, srcSliceY, image.height - srcSliceY)
        val srcW = intArrayOf(srcSliceX, image.width - srcSliceX * 2, srcSliceX)
        val srcH = intArrayOf(srcSliceY, image.height - srcSliceY * 2, srcSliceY)
        val dstX = intArrayOf(0, dstSliceX, dstWidth - dstSliceX)
        val dstY = intArrayOf(0, dstSliceY, dstHeight - dstSliceY)
        val dstW = intArrayOf(dstSliceX, dstWidth - dstSliceX * 2, dstSliceX)
        val dstH = intArrayOf(dstSliceY, dstHeight - dstSliceY * 2, dstSliceY)

        for (row in 0..2) {
            for (column in 0..2) {
                if (srcW[column] <= 0 || srcH[row] <= 0 || dstW[column] <= 0 || dstH[row] <= 0) {
                    continue
                }
                drawImage(
                    image = image,
                    srcOffset = IntOffset(srcX[column], srcY[row]),
                    srcSize = IntSize(srcW[column], srcH[row]),
                    dstOffset = IntOffset(dstX[column], dstY[row]),
                    dstSize = IntSize(dstW[column], dstH[row]),
                    filterQuality = FilterQuality.High,
                )
            }
        }
    }
}

private fun mechaPlateShape(cutRatio: Float = 0.16f): Shape = GenericShape { size, _ ->
    val cut = size.height * cutRatio
    val notch = size.width * 0.08f
    moveTo(cut, 0f)
    lineTo(size.width * 0.44f, 0f)
    lineTo(size.width * 0.47f, cut * 0.42f)
    lineTo(size.width * 0.53f, cut * 0.42f)
    lineTo(size.width * 0.56f, 0f)
    lineTo(size.width - cut, 0f)
    lineTo(size.width, cut)
    lineTo(size.width, size.height - cut)
    lineTo(size.width - cut, size.height)
    lineTo(size.width * 0.58f + notch, size.height)
    lineTo(size.width * 0.56f + notch * 0.55f, size.height - cut * 0.38f)
    lineTo(size.width * 0.44f - notch * 0.55f, size.height - cut * 0.38f)
    lineTo(size.width * 0.42f - notch, size.height)
    lineTo(cut, size.height)
    lineTo(0f, size.height - cut)
    lineTo(0f, cut)
    close()
}

private fun mechaSegmentShape(cutRatio: Float = 0.22f): Shape = GenericShape { size, _ ->
    val cut = size.height * cutRatio
    moveTo(cut, 0f)
    lineTo(size.width - cut, 0f)
    lineTo(size.width, cut)
    lineTo(size.width, size.height - cut)
    lineTo(size.width - cut, size.height)
    lineTo(cut, size.height)
    lineTo(0f, size.height - cut)
    lineTo(0f, cut)
    close()
}

private fun mechaSegmentOutline(
    width: Float,
    height: Float,
    cutRatio: Float = 0.22f,
    inset: Float = 0f,
): Path = Path().apply {
    val left = inset
    val top = inset
    val right = (width - inset).coerceAtLeast(left)
    val bottom = (height - inset).coerceAtLeast(top)
    val cut = (height * cutRatio - inset * 0.55f).coerceAtLeast(0f)

    moveTo(left + cut, top)
    lineTo(right - cut, top)
    lineTo(right, top + cut)
    lineTo(right, bottom - cut)
    lineTo(right - cut, bottom)
    lineTo(left + cut, bottom)
    lineTo(left, bottom - cut)
    lineTo(left, top + cut)
    close()
}

@Composable
fun AliciaSectionCard(
    modifier: Modifier = Modifier,
    contentPadding: Int = 16,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
fun AliciaMechaBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(MechaDeep, MechaShell),
            ),
        ),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val verticalStep = 39.dp.toPx()
            val horizontalStep = 60.dp.toPx()
            var x = verticalStep
            while (x < size.width) {
                drawLine(
                    color = MechaCyan.copy(alpha = 0.1f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
                x += verticalStep
            }
            var y = horizontalStep
            while (y < size.height) {
                drawLine(
                    color = MechaCyan.copy(alpha = 0.08f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
                y += horizontalStep
            }
            drawLine(
                color = MechaCyan.copy(alpha = 0.42f),
                start = Offset(size.width * 0.74f, 0f),
                end = Offset(size.width * 0.92f, 120.dp.toPx()),
                strokeWidth = 1.4.dp.toPx(),
            )
            drawLine(
                color = MechaOrange.copy(alpha = 0.36f),
                start = Offset(0f, size.height - 140.dp.toPx()),
                end = Offset(42.dp.toPx(), size.height - 196.dp.toPx()),
                strokeWidth = 1.4.dp.toPx(),
            )
        }
        content()
    }
}

@Composable
fun AliciaMechaPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    backgroundResId: Int = R.drawable.alicia_9_panel,
    backgroundSlice: Dp = 72.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = Color.Transparent,
        shadowElevation = 0.dp,
    ) {
        Box {
            AliciaNineSliceBackground(
                resId = backgroundResId,
                slice = backgroundSlice,
                modifier = Modifier.matchParentSize(),
            )
            Column(
                modifier = Modifier.padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun AliciaMechaArmorTexture(
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val topRail = 4.dp.toPx().coerceAtMost(size.height * 0.08f)
        val bottomRail = 5.dp.toPx().coerceAtMost(size.height * 0.08f)
        drawRect(
            color = Color.White.copy(alpha = 0.78f),
            topLeft = Offset(size.width * 0.08f, 0f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.28f, topRail),
        )
        drawRect(
            color = Color.White.copy(alpha = 0.68f),
            topLeft = Offset(size.width * 0.64f, 0f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.24f, topRail),
        )
        drawLine(
            color = Color(0xFFAFC0D8).copy(alpha = 0.42f),
            start = Offset(size.width * 0.08f, size.height - bottomRail),
            end = Offset(size.width * 0.38f, size.height - bottomRail),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color(0xFFAFC0D8).copy(alpha = 0.42f),
            start = Offset(size.width * 0.62f, size.height - bottomRail),
            end = Offset(size.width * 0.92f, size.height - bottomRail),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawPath(
            path = Path().apply {
                moveTo(0f, size.height * 0.26f)
                lineTo(5.dp.toPx(), size.height * 0.34f)
                lineTo(5.dp.toPx(), size.height * 0.66f)
                lineTo(0f, size.height * 0.74f)
                close()
            },
            color = Color(0xFFE1EAF7).copy(alpha = 0.7f),
        )
        drawPath(
            path = Path().apply {
                moveTo(size.width, size.height * 0.26f)
                lineTo(size.width - 5.dp.toPx(), size.height * 0.34f)
                lineTo(size.width - 5.dp.toPx(), size.height * 0.66f)
                lineTo(size.width, size.height * 0.74f)
                close()
            },
            color = Color(0xFFD9E4F4).copy(alpha = 0.68f),
        )
        drawLine(
            color = Color(0xFFC9D8EE),
            start = Offset(size.width * 0.12f, 1.5.dp.toPx()),
            end = Offset(size.width * 0.36f, 1.5.dp.toPx()),
            strokeWidth = 1.2.dp.toPx(),
        )
        drawLine(
            color = Color(0xFFC9D8EE),
            start = Offset(size.width * 0.64f, 1.5.dp.toPx()),
            end = Offset(size.width * 0.88f, 1.5.dp.toPx()),
            strokeWidth = 1.2.dp.toPx(),
        )
        drawRoundRect(
            color = accent.copy(alpha = 0.95f),
            topLeft = Offset(size.width * 0.44f, size.height - 5.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(size.width * 0.12f, 4.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        )
        drawRoundRect(
            color = MechaOrange.copy(alpha = 0.9f),
            topLeft = Offset(size.width * 0.405f, size.height - 4.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(size.width * 0.028f, 3.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        )
    }
}

@Composable
private fun AliciaMechaFrameDetails(
    accent: Color,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
    centerNotch: Boolean = true,
) {
    Canvas(modifier = modifier) {
        val inset = 8.dp.toPx()
        val cut = 18.dp.toPx().coerceAtMost(size.height * 0.22f)
        val notchWidth = (size.width * 0.12f).coerceAtLeast(38.dp.toPx())
        val notchStart = size.width * 0.5f - notchWidth / 2f
        val notchEnd = notchStart + notchWidth
        val notchDepth = 6.dp.toPx().coerceAtMost(size.height * 0.08f)
        val inner = Path().apply {
            moveTo(inset + cut, inset)
            if (centerNotch && size.width > 220.dp.toPx()) {
                lineTo(notchStart - 14.dp.toPx(), inset)
                lineTo(notchStart, inset + notchDepth)
                lineTo(notchEnd, inset + notchDepth)
                lineTo(notchEnd + 14.dp.toPx(), inset)
            }
            lineTo(size.width - inset - cut, inset)
            lineTo(size.width - inset, inset + cut)
            lineTo(size.width - inset, size.height - inset - cut)
            lineTo(size.width - inset - cut, size.height - inset)
            if (centerNotch && size.width > 220.dp.toPx()) {
                lineTo(notchEnd + 10.dp.toPx(), size.height - inset)
                lineTo(notchEnd - 8.dp.toPx(), size.height - inset - notchDepth * 0.75f)
                lineTo(notchStart + 8.dp.toPx(), size.height - inset - notchDepth * 0.75f)
                lineTo(notchStart - 10.dp.toPx(), size.height - inset)
            }
            lineTo(inset + cut, size.height - inset)
            lineTo(inset, size.height - inset - cut)
            lineTo(inset, inset + cut)
            close()
        }

        drawPath(
            path = inner,
            color = Color(0xFFC6D5EA),
            style = Stroke(width = 1.2.dp.toPx()),
        )
        if (dense) {
            val secondaryInset = inset + 5.dp.toPx()
            val secondaryCut = (cut - 5.dp.toPx()).coerceAtLeast(8.dp.toPx())
            val secondary = Path().apply {
                moveTo(secondaryInset + secondaryCut, secondaryInset)
                lineTo(size.width - secondaryInset - secondaryCut, secondaryInset)
                lineTo(size.width - secondaryInset, secondaryInset + secondaryCut)
                lineTo(size.width - secondaryInset, size.height - secondaryInset - secondaryCut)
                lineTo(size.width - secondaryInset - secondaryCut, size.height - secondaryInset)
                lineTo(secondaryInset + secondaryCut, size.height - secondaryInset)
                lineTo(secondaryInset, size.height - secondaryInset - secondaryCut)
                lineTo(secondaryInset, secondaryInset + secondaryCut)
                close()
            }
            drawPath(
                path = secondary,
                color = Color.White.copy(alpha = 0.86f),
                style = Stroke(width = 0.9.dp.toPx()),
            )
            drawPath(
                path = secondary,
                color = accent.copy(alpha = 0.13f),
                style = Stroke(width = 2.2.dp.toPx()),
            )
            drawLine(
                color = accent.copy(alpha = 0.52f),
                start = Offset(secondaryInset + 3.dp.toPx(), secondaryInset + secondaryCut + 2.dp.toPx()),
                end = Offset(secondaryInset + 3.dp.toPx(), size.height - secondaryInset - secondaryCut - 2.dp.toPx()),
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = accent.copy(alpha = 0.48f),
                start = Offset(size.width - secondaryInset - 3.dp.toPx(), secondaryInset + secondaryCut + 2.dp.toPx()),
                end = Offset(size.width - secondaryInset - 3.dp.toPx(), size.height - secondaryInset - secondaryCut - 2.dp.toPx()),
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        drawLine(
            color = Color.White.copy(alpha = 0.85f),
            start = Offset(inset + cut + 8.dp.toPx(), inset + 2.dp.toPx()),
            end = Offset((size.width * 0.38f).coerceAtLeast(inset + cut + 24.dp.toPx()), inset + 2.dp.toPx()),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White.copy(alpha = 0.85f),
            start = Offset((size.width * 0.62f).coerceAtMost(size.width - inset - cut - 24.dp.toPx()), inset + 2.dp.toPx()),
            end = Offset(size.width - inset - cut - 8.dp.toPx(), inset + 2.dp.toPx()),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawRoundRect(
            color = accent.copy(alpha = 0.95f),
            topLeft = Offset(size.width * 0.46f, size.height - 6.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(size.width * 0.08f, 4.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        )
        drawRoundRect(
            color = MechaOrange.copy(alpha = 0.95f),
            topLeft = Offset(size.width * 0.425f, size.height - 5.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(size.width * 0.025f, 3.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        )
        drawLine(
            color = accent.copy(alpha = 0.78f),
            start = Offset(inset + 5.dp.toPx(), inset + cut + 1.dp.toPx()),
            end = Offset(inset + cut + 1.dp.toPx(), inset + 5.dp.toPx()),
            strokeWidth = 1.4.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = accent.copy(alpha = 0.78f),
            start = Offset(size.width - inset - cut - 1.dp.toPx(), inset + 5.dp.toPx()),
            end = Offset(size.width - inset - 5.dp.toPx(), inset + cut + 1.dp.toPx()),
            strokeWidth = 1.4.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = MechaOrange.copy(alpha = 0.82f),
            start = Offset(inset + 5.dp.toPx(), size.height - inset - cut - 1.dp.toPx()),
            end = Offset(inset + cut + 1.dp.toPx(), size.height - inset - 5.dp.toPx()),
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round,
        )

        val railHeight = (size.height * 0.28f).coerceAtMost(52.dp.toPx())
        if (size.height > 88.dp.toPx()) {
            drawRoundRect(
                color = accent.copy(alpha = 0.32f),
                topLeft = Offset(3.dp.toPx(), size.height * 0.36f),
                size = androidx.compose.ui.geometry.Size(2.dp.toPx(), railHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            )
            drawRoundRect(
                color = accent.copy(alpha = 0.32f),
                topLeft = Offset(size.width - 5.dp.toPx(), size.height * 0.36f),
                size = androidx.compose.ui.geometry.Size(2.dp.toPx(), railHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            )
        }

        val boltColor = Color(0xFF8EA5C8).copy(alpha = 0.45f)
        val boltRadius = if (dense) 1.8.dp.toPx() else 1.3.dp.toPx()
        listOf(
            Offset(inset + cut * 0.62f, inset + cut * 0.45f),
            Offset(size.width - inset - cut * 0.62f, inset + cut * 0.45f),
            Offset(inset + cut * 0.62f, size.height - inset - cut * 0.45f),
            Offset(size.width - inset - cut * 0.62f, size.height - inset - cut * 0.45f),
        ).forEach { center ->
            drawCircle(color = boltColor, radius = boltRadius, center = center)
            if (dense) {
                drawCircle(color = Color.White.copy(alpha = 0.7f), radius = boltRadius * 0.45f, center = center)
            }
        }

        if (dense && size.width > 170.dp.toPx()) {
            val ventTop = inset + 4.dp.toPx()
            repeat(4) { index ->
                val x = size.width - inset - cut - 34.dp.toPx() + index * 6.dp.toPx()
                drawLine(
                    color = accent.copy(alpha = 0.38f),
                    start = Offset(x, ventTop),
                    end = Offset(x + 3.dp.toPx(), ventTop),
                    strokeWidth = 1.4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun AliciaMechaCornerAccents(
    accent: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 2.2.dp,
    inset: Dp = 10.dp,
    length: Dp = 12.dp,
    showBottomNotch: Boolean = true,
) {
    Canvas(modifier = modifier) {
        val stroke = strokeWidth.toPx()
        val insetPx = inset.toPx()
        val lengthPx = length.toPx()
        drawLine(
            color = accent,
            start = Offset(insetPx, insetPx + lengthPx),
            end = Offset(insetPx + lengthPx, insetPx),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = accent,
            start = Offset(size.width - insetPx - lengthPx, insetPx),
            end = Offset(size.width - insetPx, insetPx + lengthPx),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = accent,
            start = Offset(insetPx, size.height - insetPx - lengthPx),
            end = Offset(insetPx + lengthPx, size.height - insetPx),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = accent,
            start = Offset(size.width - insetPx - lengthPx, size.height - insetPx),
            end = Offset(size.width - insetPx, size.height - insetPx - lengthPx),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        if (showBottomNotch && size.height > 120.dp.toPx()) {
            val notchTop = size.height - 9.dp.toPx()
            val notchBottom = size.height - 1.dp.toPx()
            val notchStart = size.width * 0.38f
            val notchEnd = size.width * 0.62f
            drawPath(
                path = Path().apply {
                    moveTo(notchStart, notchBottom)
                    lineTo(size.width * 0.43f, notchTop)
                    lineTo(size.width * 0.57f, notchTop)
                    lineTo(notchEnd, notchBottom)
                    close()
                },
                color = MechaDeep.copy(alpha = 0.92f),
            )
            drawLine(
                color = Color(0xFF526174).copy(alpha = 0.75f),
                start = Offset(notchStart + 2.dp.toPx(), notchBottom - 1.dp.toPx()),
                end = Offset(notchEnd - 2.dp.toPx(), notchBottom - 1.dp.toPx()),
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = accent.copy(alpha = 0.75f),
                start = Offset(size.width * 0.46f, notchTop + 1.dp.toPx()),
                end = Offset(size.width * 0.54f, notchTop + 1.dp.toPx()),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawRoundRect(
                color = MechaOrange.copy(alpha = 0.94f),
                topLeft = Offset(size.width * 0.415f, notchTop + 1.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(size.width * 0.025f, 2.5.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            )
        }
    }
}

@Composable
fun AliciaMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
            )
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
fun AliciaUserAvatar(
    label: String,
    imageUrl: String? = null,
    contentDescription: String? = label,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 46.dp,
    shape: Shape = MaterialTheme.shapes.small,
) {
    Box(
        modifier = modifier
            .size(avatarSize)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                        MaterialTheme.colorScheme.primaryContainer,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl.isNullOrBlank()) {
            AliciaAvatarFallback(
                label = label,
                avatarSize = avatarSize,
            )
        } else {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    AliciaAvatarFallback(
                        label = label,
                        avatarSize = avatarSize,
                    )
                },
                error = {
                    AliciaAvatarFallback(
                        label = label,
                        avatarSize = avatarSize,
                    )
                },
            )
        }
    }
}

@Composable
fun AliciaMechaAvatarFrame(
    label: String,
    imageUrl: String?,
    contentDescription: String? = label,
    modifier: Modifier = Modifier,
    frameSize: Dp = 60.dp,
    avatarSize: Dp = 42.dp,
    onClick: (() -> Unit)? = null,
) {
    val containerModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Box(
        modifier = containerModifier.size(frameSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val outerRadius = size.minDimension * 0.485f
            val ringOuter = outerRadius * 0.94f
            val ringMid = outerRadius * 0.80f
            val ringInner = outerRadius * 0.66f

            drawCircle(
                color = Color(0xFF071120).copy(alpha = 0.3f),
                radius = outerRadius * 0.99f,
                center = center.copy(y = center.y + size.minDimension * 0.03f),
            )
            drawCircle(
                color = MechaBlue.copy(alpha = 0.16f),
                radius = outerRadius * 1.03f,
                center = center,
                style = Stroke(width = 8.dp.toPx()),
            )
            drawCircle(
                color = MechaCyan.copy(alpha = 0.22f),
                radius = outerRadius * 0.99f,
                center = center,
                style = Stroke(width = 5.dp.toPx()),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFCFEFF), Color(0xFFE1E9F8), Color(0xFFA4B5D3)),
                    center = center,
                    radius = ringOuter,
                ),
                radius = ringOuter,
                center = center,
            )
            drawCircle(
                color = Color(0xFFF7FAFF),
                radius = ringOuter * 0.87f,
                center = center,
            )
            drawCircle(
                color = Color(0xFFA1B4D5),
                radius = ringMid,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1E3046), Color(0xFF0A1321), Color(0xFF050B13)),
                    center = center,
                    radius = ringInner,
                ),
                radius = ringInner,
                center = center,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.36f),
                radius = ringOuter,
                center = center,
                style = Stroke(width = 1.2.dp.toPx()),
            )
            drawCircle(
                color = MechaBlue.copy(alpha = 0.78f),
                radius = ringInner * 1.03f,
                center = center,
                style = Stroke(width = 2.4.dp.toPx()),
            )
            drawCircle(
                color = MechaCyan.copy(alpha = 0.46f),
                radius = ringInner * 1.09f,
                center = center,
                style = Stroke(width = 4.6.dp.toPx()),
            )
            drawCircle(
                color = MechaBlue.copy(alpha = 0.18f),
                radius = ringInner * 1.14f,
                center = center,
                style = Stroke(width = 8.dp.toPx()),
            )

            val arcSize = androidx.compose.ui.geometry.Size(ringOuter * 1.74f, ringOuter * 1.74f)
            val arcTopLeft = Offset(center.x - ringOuter * 0.85f, center.y - ringOuter * 0.85f)
            drawArc(
                color = MechaCyan.copy(alpha = 0.85f),
                startAngle = 206f,
                sweepAngle = 46f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = MechaBlue.copy(alpha = 0.95f),
                startAngle = 12f,
                sweepAngle = 44f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = 3.4.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = MechaOrange,
                startAngle = 276f,
                sweepAngle = 26f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
            )

            repeat(8) { index ->
                val angleDegrees = -90f + index * 45f
                val angle = Math.toRadians(angleDegrees.toDouble())
                val chipCenter = Offset(
                    x = center.x + kotlin.math.cos(angle).toFloat() * ringOuter * 0.79f,
                    y = center.y + kotlin.math.sin(angle).toFloat() * ringOuter * 0.79f,
                )
                val chipWidth = size.minDimension * 0.165f
                val chipHeight = size.minDimension * 0.085f
                rotate(degrees = angleDegrees + 90f, pivot = chipCenter) {
                    drawRoundRect(
                        color = Color(0xFF90A6C9).copy(alpha = 0.42f),
                        topLeft = Offset(chipCenter.x - chipWidth * 0.52f, chipCenter.y - chipHeight * 0.24f),
                        size = androidx.compose.ui.geometry.Size(chipWidth * 1.04f, chipHeight * 0.92f),
                        cornerRadius = CornerRadius(chipHeight * 0.34f, chipHeight * 0.34f),
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFFFFFFF), Color(0xFFE7EEF9), Color(0xFFB9C7DD)),
                            startY = chipCenter.y - chipHeight * 0.5f,
                            endY = chipCenter.y + chipHeight * 0.5f,
                        ),
                        topLeft = Offset(chipCenter.x - chipWidth * 0.5f, chipCenter.y - chipHeight * 0.5f),
                        size = androidx.compose.ui.geometry.Size(chipWidth, chipHeight),
                        cornerRadius = CornerRadius(chipHeight * 0.34f, chipHeight * 0.34f),
                    )
                    drawRoundRect(
                        color = Color(0xFF9DB0D1),
                        topLeft = Offset(chipCenter.x - chipWidth * 0.5f, chipCenter.y - chipHeight * 0.5f),
                        size = androidx.compose.ui.geometry.Size(chipWidth, chipHeight),
                        cornerRadius = CornerRadius(chipHeight * 0.34f, chipHeight * 0.34f),
                        style = Stroke(width = 1.1.dp.toPx()),
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.82f),
                        topLeft = Offset(chipCenter.x - chipWidth * 0.28f, chipCenter.y - chipHeight * 0.36f),
                        size = androidx.compose.ui.geometry.Size(chipWidth * 0.56f, chipHeight * 0.14f),
                        cornerRadius = CornerRadius(chipHeight * 0.08f, chipHeight * 0.08f),
                    )
                    drawRoundRect(
                        color = MechaBlue.copy(alpha = if (index % 2 == 0) 0.95f else 0.75f),
                        topLeft = Offset(chipCenter.x - chipWidth * 0.20f, chipCenter.y + chipHeight * 0.18f),
                        size = androidx.compose.ui.geometry.Size(chipWidth * 0.40f, chipHeight * 0.12f),
                        cornerRadius = CornerRadius(chipHeight * 0.08f, chipHeight * 0.08f),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(avatarSize + 6.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF0F253C), Color(0xFF091320)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            AliciaUserAvatar(
                label = label,
                imageUrl = imageUrl,
                contentDescription = contentDescription,
                avatarSize = avatarSize,
                shape = CircleShape,
            )
        }
    }
}

@Composable
private fun AliciaMechaRefreshRingBackdrop(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val outerRadius = size.minDimension * 0.485f
        val ringOuter = outerRadius * 0.94f
        val ringMid = outerRadius * 0.80f
        val ringInner = outerRadius * 0.66f

        drawCircle(
            color = Color(0xFF071120).copy(alpha = 0.3f),
            radius = outerRadius * 0.99f,
            center = center.copy(y = center.y + size.minDimension * 0.03f),
        )
        drawCircle(
            color = MechaBlue.copy(alpha = 0.16f),
            radius = outerRadius * 1.03f,
            center = center,
            style = Stroke(width = 8.dp.toPx()),
        )
        drawCircle(
            color = MechaCyan.copy(alpha = 0.22f),
            radius = outerRadius * 0.99f,
            center = center,
            style = Stroke(width = 5.dp.toPx()),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFCFEFF), Color(0xFFE1E9F8), Color(0xFFA4B5D3)),
                center = center,
                radius = ringOuter,
            ),
            radius = ringOuter,
            center = center,
        )
        drawCircle(
            color = Color(0xFFF7FAFF),
            radius = ringOuter * 0.87f,
            center = center,
        )
        drawCircle(
            color = Color(0xFFA1B4D5),
            radius = ringMid,
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF1E3046), Color(0xFF0A1321), Color(0xFF050B13)),
                center = center,
                radius = ringInner,
            ),
            radius = ringInner,
            center = center,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.36f),
            radius = ringOuter,
            center = center,
            style = Stroke(width = 1.2.dp.toPx()),
        )
        drawCircle(
            color = MechaBlue.copy(alpha = 0.78f),
            radius = ringInner * 1.03f,
            center = center,
            style = Stroke(width = 2.4.dp.toPx()),
        )
        drawCircle(
            color = MechaCyan.copy(alpha = 0.46f),
            radius = ringInner * 1.09f,
            center = center,
            style = Stroke(width = 4.6.dp.toPx()),
        )
        drawCircle(
            color = MechaBlue.copy(alpha = 0.18f),
            radius = ringInner * 1.14f,
            center = center,
            style = Stroke(width = 8.dp.toPx()),
        )

        val arcSize = androidx.compose.ui.geometry.Size(ringOuter * 1.74f, ringOuter * 1.74f)
        val arcTopLeft = Offset(center.x - ringOuter * 0.85f, center.y - ringOuter * 0.85f)
        drawArc(
            color = MechaCyan.copy(alpha = 0.85f),
            startAngle = 206f,
            sweepAngle = 46f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round),
        )
        drawArc(
            color = MechaBlue.copy(alpha = 0.95f),
            startAngle = 12f,
            sweepAngle = 44f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = 3.4.dp.toPx(), cap = StrokeCap.Round),
        )
        drawArc(
            color = MechaOrange,
            startAngle = 276f,
            sweepAngle = 26f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
        )

        repeat(8) { index ->
            val angleDegrees = -90f + index * 45f
            val angle = Math.toRadians(angleDegrees.toDouble())
            val chipCenter = Offset(
                x = center.x + kotlin.math.cos(angle).toFloat() * ringOuter * 0.79f,
                y = center.y + kotlin.math.sin(angle).toFloat() * ringOuter * 0.79f,
            )
            val chipWidth = size.minDimension * 0.165f
            val chipHeight = size.minDimension * 0.085f
            rotate(degrees = angleDegrees + 90f, pivot = chipCenter) {
                drawRoundRect(
                    color = Color(0xFF90A6C9).copy(alpha = 0.42f),
                    topLeft = Offset(chipCenter.x - chipWidth * 0.52f, chipCenter.y - chipHeight * 0.24f),
                    size = androidx.compose.ui.geometry.Size(chipWidth * 1.04f, chipHeight * 0.92f),
                    cornerRadius = CornerRadius(chipHeight * 0.34f, chipHeight * 0.34f),
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFFFFFFF), Color(0xFFE7EEF9), Color(0xFFB9C7DD)),
                        startY = chipCenter.y - chipHeight * 0.5f,
                        endY = chipCenter.y + chipHeight * 0.5f,
                    ),
                    topLeft = Offset(chipCenter.x - chipWidth * 0.5f, chipCenter.y - chipHeight * 0.5f),
                    size = androidx.compose.ui.geometry.Size(chipWidth, chipHeight),
                    cornerRadius = CornerRadius(chipHeight * 0.34f, chipHeight * 0.34f),
                )
                drawRoundRect(
                    color = Color(0xFF9DB0D1),
                    topLeft = Offset(chipCenter.x - chipWidth * 0.5f, chipCenter.y - chipHeight * 0.5f),
                    size = androidx.compose.ui.geometry.Size(chipWidth, chipHeight),
                    cornerRadius = CornerRadius(chipHeight * 0.34f, chipHeight * 0.34f),
                    style = Stroke(width = 1.1.dp.toPx()),
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.82f),
                    topLeft = Offset(chipCenter.x - chipWidth * 0.28f, chipCenter.y - chipHeight * 0.36f),
                    size = androidx.compose.ui.geometry.Size(chipWidth * 0.56f, chipHeight * 0.14f),
                    cornerRadius = CornerRadius(chipHeight * 0.08f, chipHeight * 0.08f),
                )
                drawRoundRect(
                    color = MechaBlue.copy(alpha = if (index % 2 == 0) 0.95f else 0.75f),
                    topLeft = Offset(chipCenter.x - chipWidth * 0.20f, chipCenter.y + chipHeight * 0.18f),
                    size = androidx.compose.ui.geometry.Size(chipWidth * 0.40f, chipHeight * 0.12f),
                    cornerRadius = CornerRadius(chipHeight * 0.08f, chipHeight * 0.08f),
                )
            }
        }
    }
}

@Composable
private fun AliciaAvatarFallback(
    label: String,
    avatarSize: Dp,
) {
    Text(
        text = label.take(1).uppercase(),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        fontSize = (avatarSize.value * 0.42f).sp,
    )
}

@Composable
fun AliciaBadge(text: String) {
    val shape = mechaSegmentShape(cutRatio = 0.26f)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF4A91FF), MechaBlue, Color(0xFF1552D8)),
                ),
                shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 3.dp),
            color = Color.White,
            fontFamily = AliciaMechaFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
    }
}

@Composable
fun AliciaRoundedBadge(
    text: String,
    modifier: Modifier = Modifier,
    minWidth: Dp = 0.dp,
) {
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier = modifier
            .widthIn(min = minWidth)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF4A91FF), MechaBlue, Color(0xFF1552D8)),
                ),
                shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.dp),
            color = Color.White,
            fontFamily = AliciaMechaFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

@Composable
fun AliciaMechaBadgeAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color(0xFF5A94FF)),
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .background(Color.Transparent, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 0.dp),
                color = MechaBlue,
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun AliciaPageHeader(
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = actions,
        )
    }
}

@Composable
fun AliciaHeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun AliciaHeaderAvatarButton(
    label: String,
    imageUrl: String?,
    contentDescription: String = label,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = Modifier.clickable(onClick = onClick),
        shadowElevation = 1.dp,
    ) {
        AliciaUserAvatar(
            label = label,
            imageUrl = imageUrl,
            contentDescription = contentDescription,
            avatarSize = 40.dp,
            shape = CircleShape,
            modifier = Modifier.padding(3.dp),
        )
    }
}

@Composable
fun AliciaSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        placeholder = {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            TextButton(onClick = onSearch) {
                Text("搜索", fontWeight = FontWeight.SemiBold)
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = { onSearch() },
        ),
    )
}

@Composable
fun AliciaMechaSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(49.dp),
        shape = RoundedCornerShape(0.dp),
        color = Color.Transparent,
        shadowElevation = 0.dp,
    ) {
        Box {
            AliciaNineSliceBackground(
                resId = R.drawable.alicia_9_search,
                slice = 54.dp,
                modifier = Modifier.matchParentSize(),
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = Color(0xFF50617D),
                    modifier = Modifier.size(22.dp),
                )
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                textStyle = TextStyle(
                    color = MechaInk,
                    fontSize = 12.5.sp,
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Medium,
                ),
                    cursorBrush = SolidColor(MechaBlue),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isBlank()) {
                                Text(
                                    text = placeholder,
                                    color = MechaMuted,
                                    fontSize = 15.sp,
                                    fontFamily = AliciaMechaFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                AliciaMechaBlueButton(
                    label = "搜索",
                    onClick = onSearch,
                    modifier = Modifier.width(65.dp),
                    height = 28.dp,
                )
            }
        }
    }
}

@Composable
fun AliciaMechaHomeSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val shellShape = mechaPlateShape(cutRatio = 0.14f)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 5.dp)
            .height(50.dp),
        shape = shellShape,
        color = Color.Transparent,
        shadowElevation = 10.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shellShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFFFFFFF), Color(0xFFF4F8FF)),
                    ),
                    shellShape,
                ),
        ) {
            AliciaMechaHomeSearchShell(modifier = Modifier.matchParentSize())
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 22.dp, end = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = Color(0xFF617491),
                    modifier = Modifier.size(20.dp),
                )
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MechaInk,
                        fontSize = 15.sp,
                        fontFamily = AliciaMechaFontFamily,
                        fontWeight = FontWeight.Medium,
                    ),
                    cursorBrush = SolidColor(MechaBlue),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isBlank()) {
                                Text(
                                    text = placeholder,
                                    color = Color(0xFF8794AA),
                                    fontSize = 12.5.sp,
                                    fontFamily = AliciaMechaFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                AliciaMechaBlueButton(
                    label = "搜索",
                    onClick = onSearch,
                    modifier = Modifier.width(50.dp),
                    height = 25.dp,
                    fontSizeSp = 10
                )
            }
        }
    }
}

@Composable
private fun AliciaMechaHomeSearchShell(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val shellPath = mechaSegmentOutline(size.width, size.height, cutRatio = 0.17f, inset = 1.2.dp.toPx())
        drawPath(
            path = shellPath,
            color = Color(0xFFD4E0F1),
            style = Stroke(width = 1.2.dp.toPx()),
        )
        val innerInset = 7.dp.toPx()
        val innerPath = mechaSegmentOutline(
            width = size.width - innerInset * 2,
            height = size.height - innerInset * 2,
            cutRatio = 0.16f,
            inset = 0.8.dp.toPx(),
        )
        translate(left = innerInset, top = innerInset) {
            drawPath(
                path = innerPath,
                color = Color(0xFFE8EFF9),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        drawLine(
            color = MechaBlue.copy(alpha = 0.72f),
            start = Offset(22.dp.toPx(), size.height - 5.dp.toPx()),
            end = Offset(size.width * 0.30f, size.height - 5.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = MechaOrange.copy(alpha = 0.78f),
            start = Offset(size.width * 0.56f, 4.dp.toPx()),
            end = Offset(size.width * 0.74f, 4.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun AliciaMechaHomeAdminPanel(
    title: String,
    badgeText: String,
    usedBytes: Long,
    totalBytes: Long?,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String = "查看文件",
) {
    val isTight = totalBytes != null && totalBytes > 0L && usedBytes * 100 >= totalBytes * 85
    val accent = if (isTight) Color(0xFFFF8A59) else MechaBlue
    val statusText = if (isTight) "空间已接近上限" else "云端空间运行正常"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(126.dp),
        shape = RoundedCornerShape(0.dp),
        color = Color.Transparent,
        shadowElevation = 0.dp,
    ) {
        Box {
            AliciaNineSliceBackground(
                resId = R.drawable.alicia_9_team_summary,
                slice = 58.dp,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 18.dp, top = 16.dp),
            ) {
                AliciaRoundedBadge(text = badgeText)
            }
            Text(
                text = title,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 18.dp),
                color = MechaInk,
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                lineHeight = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
            AliciaMechaInlineLightning(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = 20.dp, top = 46.dp, end = 20.dp)
                    .fillMaxWidth()
                    .height(12.dp),
                tint = MechaBlue,
                highlightTint = Color(0xFF5A93FF),
            )
            AliciaMechaHomeStatusCard(
                statusText = statusText,
                accent = accent,
                onPrimaryAction = onPrimaryAction,
                actionLabel = actionLabel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 18.dp, end = 18.dp, bottom = 19.dp),
            )
        }
    }
}

@Composable
private fun AliciaMechaInlineLightning(
    modifier: Modifier = Modifier,
    tint: Color,
    highlightTint: Color,
) {
    val lightningTransition = rememberInfiniteTransition(label = "home_inline_lightning")
    val frameTicker by lightningTransition.animateFloat(
        initialValue = 0f,
        targetValue = AliciaLightningFrameResIds.size.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "home_inline_lightning_frame",
    )
    val pulse by lightningTransition.animateFloat(
        initialValue = 0.84f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 280, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "home_inline_lightning_pulse",
    )
    val frameIndex = positiveMod(frameTicker.toInt(), AliciaLightningFrameResIds.size)
    val frame = ImageBitmap.imageResource(id = AliciaLightningFrameResIds[frameIndex])

    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        drawAliciaLightningFrame(
            image = frame,
            left = 0f,
            top = 0f,
            width = size.width,
            height = size.height,
            tint = tint,
            highlightTint = highlightTint,
            baseAlpha = 0.9f,
            highlightAlpha = 0.44f + 0.12f * pulse,
        )
    }
}

@Composable
private fun AliciaMechaHomeStatusCard(
    statusText: String,
    accent: Color,
    onPrimaryAction: () -> Unit,
    actionLabel: String,
    modifier: Modifier = Modifier,
) {
    val shellShape = mechaSegmentShape(cutRatio = 0.17f)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = shellShape,
        color = Color.Transparent,
        shadowElevation = 9.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shellShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFFFFFFF), Color(0xFFF5F8FE)),
                    ),
                    shellShape,
                ),
        ) {
            AliciaMechaHomeStatusShell(modifier = Modifier.matchParentSize())
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 29.dp, top = 5.dp, end = 10.dp, bottom = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AliciaMechaHomeStatusOrb(
                    accent = accent,
                    modifier = Modifier
                        .size(30.dp)
                        .offset(x = (-15).dp),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .offset(x = (-20).dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (accent == MechaBlue) Color(0xFF49C95A) else accent),
                        )
                        Text(
                            text = statusText,
                            color = MechaInk,
                            fontFamily = AliciaMechaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                AliciaMechaBlueButton(
                    label = actionLabel,
                    onClick = onPrimaryAction,
                    modifier = Modifier.width(72.dp),
                    height = 26.dp,
                    fontSizeSp = 9,
                )
            }
        }
    }
}

@Composable
private fun AliciaMechaHomeStatusShell(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val outline = mechaSegmentOutline(size.width, size.height, cutRatio = 0.17f, inset = 1.2.dp.toPx())
        drawPath(
            path = outline,
            color = Color(0xFFD7E1F0),
            style = Stroke(width = 1.2.dp.toPx()),
        )
    }
}

@Composable
private fun AliciaMechaHomeStatusOrb(
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.24f),
                        Color(0xFFEAF3FF),
                    ),
                ),
                radius = size.minDimension * 0.5f,
                center = center,
            )
            drawCircle(
                color = accent.copy(alpha = 0.16f),
                radius = size.minDimension * 0.42f,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
            drawCircle(
                color = accent,
                radius = size.minDimension * 0.34f,
                center = center,
            )
        }
        Icon(
            imageVector = Icons.Rounded.CloudQueue,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun AliciaMechaBlueButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
    fontSizeSp: Int = 12,
) {
    Surface(
        modifier = modifier
            .height(height)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(0.dp),
        color = Color.Transparent,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AliciaNineSliceBackground(
                resId = R.drawable.alicia_9_button_blue,
                slice = 24.dp,
                modifier = Modifier.matchParentSize(),
            )
            Text(
                text = label,
                color = Color.White,
                fontSize = fontSizeSp.sp,
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun AliciaMechaActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: AliciaMechaActionButtonTone = AliciaMechaActionButtonTone.Primary,
    enabled: Boolean = true,
    height: Dp = 50.dp,
) {
    val shellShape = mechaSegmentShape(cutRatio = 0.24f)
    val textColor = when (tone) {
        AliciaMechaActionButtonTone.Primary -> Color.White
        AliciaMechaActionButtonTone.Secondary -> MechaMuted
        AliciaMechaActionButtonTone.Danger -> Color.White
    }.copy(alpha = if (enabled) 1f else 0.55f)
    val outerBorder = when (tone) {
        AliciaMechaActionButtonTone.Primary -> Color(0xFF86B8FF)
        AliciaMechaActionButtonTone.Secondary -> Color(0xFFD7E1F0)
        AliciaMechaActionButtonTone.Danger -> Color(0xFFFFB181)
    }.copy(alpha = if (enabled) 1f else 0.55f)
    val innerBorder = when (tone) {
        AliciaMechaActionButtonTone.Primary -> Color(0xFF1352D5)
        AliciaMechaActionButtonTone.Secondary -> Color(0xFFE9F0F9)
        AliciaMechaActionButtonTone.Danger -> Color(0xFFE25A2C)
    }.copy(alpha = if (enabled) 1f else 0.55f)
    val topAccent = when (tone) {
        AliciaMechaActionButtonTone.Primary -> Color(0xFFB9D6FF)
        AliciaMechaActionButtonTone.Secondary -> MechaOrange.copy(alpha = 0.85f)
        AliciaMechaActionButtonTone.Danger -> Color(0xFFFFD4A2)
    }.copy(alpha = if (enabled) 1f else 0.5f)
    val bottomAccent = when (tone) {
        AliciaMechaActionButtonTone.Primary -> MechaCyan
        AliciaMechaActionButtonTone.Secondary -> MechaCyan.copy(alpha = 0.75f)
        AliciaMechaActionButtonTone.Danger -> Color(0xFFFFF2E8)
    }.copy(alpha = if (enabled) 1f else 0.5f)

    Surface(
        modifier = modifier
            .height(height)
            .clip(shellShape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = shellShape,
        color = Color.Transparent,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            when (tone) {
                AliciaMechaActionButtonTone.Primary -> {
                    AliciaNineSliceBackground(
                        resId = R.drawable.alicia_9_button_blue,
                        slice = 24.dp,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                AliciaMechaActionButtonTone.Secondary,
                AliciaMechaActionButtonTone.Danger,
                -> {
                    val backgroundBrush = when (tone) {
                        AliciaMechaActionButtonTone.Secondary -> Brush.verticalGradient(
                            colors = listOf(Color(0xFFFFFFFF), Color(0xFFF0F5FF)),
                        )
                        AliciaMechaActionButtonTone.Danger -> Brush.verticalGradient(
                            colors = listOf(Color(0xFFFF925A), Color(0xFFF46C37), Color(0xFFE75522)),
                        )
                        AliciaMechaActionButtonTone.Primary -> Brush.verticalGradient(
                            colors = listOf(Color(0xFF4A91FF), MechaBlue, Color(0xFF1655D8)),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(shellShape)
                            .background(backgroundBrush, shellShape),
                    )
                    Canvas(modifier = Modifier.matchParentSize()) {
                        drawPath(
                            path = mechaSegmentOutline(size.width, size.height, cutRatio = 0.24f, inset = 1.dp.toPx()),
                            color = outerBorder,
                            style = Stroke(width = 1.2.dp.toPx()),
                        )
                        drawPath(
                            path = mechaSegmentOutline(size.width, size.height, cutRatio = 0.24f, inset = 3.dp.toPx()),
                            color = innerBorder.copy(alpha = 0.42f),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                        drawLine(
                            color = topAccent,
                            start = Offset(size.width * 0.18f, 5.dp.toPx()),
                            end = Offset(size.width * 0.42f, 5.dp.toPx()),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                        drawLine(
                            color = bottomAccent,
                            start = Offset(size.width * 0.60f, size.height - 4.dp.toPx()),
                            end = Offset(size.width * 0.84f, size.height - 4.dp.toPx()),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
            Text(
                text = label,
                color = textColor,
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun AliciaMechaTeamCompactButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    minWidth: Dp = 74.dp,
    height: Dp = 30.dp,
) {
    val clickableModifier = if (onClick != null) {
        modifier.clickable(enabled = enabled, onClick = onClick)
    } else {
        modifier
    }
    Box(
        modifier = clickableModifier
            .widthIn(min = minWidth)
            .height(height)
            .graphicsLayer { alpha = if (enabled) 1f else 0.52f },
        contentAlignment = Alignment.Center,
    ) {
        AliciaNineSliceBackground(
            resId = R.drawable.alicia_9_team_button,
            slice = 22.dp,
            modifier = Modifier.matchParentSize(),
        )
        Text(
            text = label,
            color = Color(0xFF2B67E7),
            fontFamily = AliciaMechaFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = 11.sp,
            lineHeight = 12.sp,
            maxLines = 1,
        )
    }
}

@Composable
fun AliciaMechaDialogShell(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissEnabled: Boolean = true,
    supporting: (@Composable ColumnScope.() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
    footer: @Composable RowScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = {
            if (dismissEnabled) {
                onDismissRequest()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
        ) {
            AliciaMechaPanel(
                modifier = modifier.widthIn(max = 380.dp),
                contentPadding = PaddingValues(start = 22.dp, top = 18.dp, end = 22.dp, bottom = 18.dp),
                backgroundResId = R.drawable.alicia_9_dialog_panel,
                backgroundSlice = 72.dp,
            ) {
                Text(
                    text = title,
                    color = MechaInk,
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp,
                    lineHeight = 28.sp,
                )
                if (supporting != null) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        content = supporting,
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = body,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    content = footer,
                )
            }
        }
    }
}

@Composable
fun AliciaMechaDialogActionRow(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String,
    modifier: Modifier = Modifier,
    dismissLabel: String = "取消",
    enabled: Boolean = true,
    confirmLoading: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AliciaMechaActionButton(
            label = dismissLabel,
            onClick = onDismiss,
            tone = AliciaMechaActionButtonTone.Secondary,
            enabled = enabled && !confirmLoading,
            modifier = Modifier.weight(1f),
            height = 42.dp,
        )
        AliciaMechaActionButton(
            label = confirmLabel,
            onClick = onConfirm,
            tone = AliciaMechaActionButtonTone.Primary,
            enabled = enabled && !confirmLoading,
            modifier = Modifier.weight(1f),
            height = 42.dp,
        )
    }
}

@Composable
fun AliciaMechaInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            color = Color(0xFF7C879A),
            fontFamily = AliciaMechaFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            lineHeight = 14.sp,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            AliciaNineSliceBackground(
                resId = R.drawable.alicia_9_dialog_input,
                slice = 28.dp,
                modifier = Modifier.matchParentSize(),
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxSize(),
                singleLine = singleLine,
                textStyle = TextStyle(
                    color = MechaInk,
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                ),
                cursorBrush = SolidColor(MechaBlue),
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isBlank() && !placeholder.isNullOrBlank()) {
                            Text(
                                text = placeholder,
                                color = Color(0xFF9AA6BA),
                                fontFamily = AliciaMechaFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                lineHeight = 16.sp,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

@Composable
fun AliciaInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFFF7F8FC),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxSize(),
                singleLine = singleLine,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isBlank() && !placeholder.isNullOrBlank()) {
                            Text(
                                text = placeholder,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

@Composable
fun AliciaFolderSummary(
    currentLabel: String,
    breadcrumbs: List<String>,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.White, Color(0xFFEAF3FF), Color(0xFFDCEBFF)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = MechaBlue,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "当前位置",
                    color = MechaMuted,
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                )
                Text(
                    text = currentLabel,
                    color = MechaInk,
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    lineHeight = 24.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (breadcrumbs.size > 1) {
            AliciaBreadCrumbs(
                labels = breadcrumbs,
                onTap = onTap,
            )
        }
    }
}

@Composable
fun AliciaSegmentTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    Surface(
        color = Color(0xFFF5F7FB),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            labels.forEachIndexed { index, label ->
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onSelected(index) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (selectedIndex == index) {
                        Color.White
                    } else {
                        Color.Transparent
                    },
                    shadowElevation = if (selectedIndex == index) 1.dp else 0.dp,
                ) {
                    Text(
                        text = label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 11.dp),
                        color = if (selectedIndex == index) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (selectedIndex == index) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Medium
                        },
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun AliciaMechaSegmentTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
    ) {
        val shellShape = mechaPlateShape(cutRatio = 0.18f)
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shellShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFF6FAFF), Color(0xFFEBF2FD)),
                    ),
                    shellShape,
                ),
        )
        Canvas(modifier = Modifier.matchParentSize()) {
            val outer = mechaSegmentOutline(size.width, size.height, cutRatio = 0.18f, inset = 1.dp.toPx())
            drawPath(
                path = outer,
                color = Color(0xFFD6E2F2),
                style = Stroke(width = 1.1.dp.toPx()),
            )
            drawLine(
                color = MechaBlue.copy(alpha = 0.45f),
                start = Offset(size.width * 0.12f, size.height - 4.dp.toPx()),
                end = Offset(size.width * 0.38f, size.height - 4.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = MechaOrange.copy(alpha = 0.78f),
                start = Offset(size.width * 0.62f, 4.dp.toPx()),
                end = Offset(size.width * 0.84f, 4.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 7.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            labels.forEachIndexed { index, label ->
                AliciaMechaSegmentTab(
                    label = label,
                    selected = selectedIndex == index,
                    onClick = { onSelected(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AliciaMechaSegmentTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shellShape = mechaSegmentShape(cutRatio = 0.21f)
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .clip(shellShape)
            .clickable(onClick = onClick),
        shape = shellShape,
        color = Color.Transparent,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                AliciaNineSliceBackground(
                    resId = R.drawable.alicia_9_button_blue,
                    slice = 24.dp,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(shellShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFFFFFFFF), Color(0xFFF0F5FF)),
                            ),
                            shellShape,
                        ),
                )
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawPath(
                        path = mechaSegmentOutline(size.width, size.height, inset = 1.dp.toPx()),
                        color = Color(0xFFD3DFEF),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                    drawLine(
                        color = MechaCyan.copy(alpha = 0.72f),
                        start = Offset(size.width * 0.18f, size.height - 3.dp.toPx()),
                        end = Offset(size.width * 0.55f, size.height - 3.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            Text(
                text = label,
                color = if (selected) Color.White else MechaMuted,
                fontFamily = AliciaMechaFontFamily,
                fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                fontSize = 15.sp,
                lineHeight = 17.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun AliciaFilterRow(
    selected: StorageNodeFilter,
    onSelected: (StorageNodeFilter) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            StorageNodeFilter.ALL to "全部",
            StorageNodeFilter.FOLDER to "文件夹",
            StorageNodeFilter.FILE to "文件",
        ).forEach { (filter, label) ->
            AliciaActionChip(
                label = label,
                highlighted = selected == filter,
                onClick = { onSelected(filter) },
            )
        }
    }
}

@Composable
fun AliciaActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val background = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        danger -> Color(0xFFFFF1EE)
        highlighted -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        danger -> Color(0xFFD84B2A)
        highlighted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = when {
        danger -> Color(0xFFFFD4CC)
        highlighted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    Surface(
        modifier = modifier
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = background,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
fun AliciaTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier
            .widthIn(min = 72.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            Color(0xFFF2F4F8)
        },
        border = BorderStroke(
            1.dp,
            if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        ),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
fun AliciaPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(enabled = enabled && !loading, onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
        shadowElevation = 4.dp,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun AliciaDialogActionRow(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String,
    modifier: Modifier = Modifier,
    dismissLabel: String = "取消",
    enabled: Boolean = true,
    confirmLoading: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .clickable(enabled = enabled && !confirmLoading, onClick = onDismiss),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = dismissLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
        }
        AliciaPrimaryButton(
            label = confirmLabel,
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            loading = confirmLoading,
        )
    }
}

@Composable
fun AliciaBreadCrumbs(
    labels: List<String>,
    onTap: (Int) -> Unit,
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(labels.lastOrNull(), scrollState.maxValue) {
        if (labels.size > 1 && scrollState.maxValue > 0) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    Row(
        modifier = Modifier.horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val highlighted = index == labels.lastIndex
            Box(
                modifier = Modifier
                    .width(82.dp)
                    .height(36.dp)
                    .clickable(onClick = { onTap(index) }),
            ) {
                Image(
                    painter = painterResource(
                        id = if (highlighted) {
                            R.drawable.alicia_9_breadcrumb_chip_active
                        } else {
                            R.drawable.alicia_9_breadcrumb_chip
                        },
                    ),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.FillBounds,
                )
                Text(
                    text = label,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 12.dp),
                    color = if (highlighted) Color.White else MechaMuted,
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun AliciaQuickActionGrid(actions: List<AliciaQuickAction>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.chunked(4).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { action ->
                    AliciaQuickActionTile(
                        action = action,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
fun AliciaQuickActionTile(
    action: AliciaQuickAction,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = action.onClick),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = action.label,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = action.label,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Text(
                text = action.hint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun AliciaMechaQuickActionGrid(actions: List<AliciaQuickAction>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (actions.size >= 4) 8.dp else 12.dp),
    ) {
        actions.forEach { action ->
            AliciaMechaQuickActionTile(
                action = action,
                compact = actions.size >= 4,
                modifier = Modifier
                    .weight(1f)
                    .height(AliciaMechaDesignSpec.quickActionHeight),
            )
        }
    }
}

@Composable
private fun AliciaMechaQuickActionTile(
    action: AliciaQuickAction,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clickable(onClick = action.onClick),
        shape = RoundedCornerShape(0.dp),
        color = Color.Transparent,
        shadowElevation = 0.dp,
    ) {
        Box {
            AliciaNineSliceBackground(
                resId = R.drawable.alicia_9_quick,
                slice = 42.dp,
                modifier = Modifier.matchParentSize(),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFFFFFFFF), Color(0xFFEAF2FF), Color(0xFFDCEAFF)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    AliciaMechaActionGlyph(
                        label = action.label,
                        fallback = action.icon,
                        color = MechaBlue,
                        modifier = Modifier.size(21.dp),
                    )
                }
                Text(
                    text = action.label,
                    color = MechaInk,
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 12.sp else 13.sp,
                    lineHeight = if (compact) 14.sp else 15.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun AliciaMechaActionGlyph(
    label: String,
    fallback: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        when (label) {
            "全部文件" -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.12f, size.height * 0.36f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.44f),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                )
                val tab = Path().apply {
                    moveTo(size.width * 0.15f, size.height * 0.38f)
                    lineTo(size.width * 0.30f, size.height * 0.22f)
                    lineTo(size.width * 0.50f, size.height * 0.22f)
                    lineTo(size.width * 0.60f, size.height * 0.36f)
                    close()
                }
                drawPath(tab, color)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.9f),
                    topLeft = Offset(size.width * 0.22f, size.height * 0.47f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.21f),
                    cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
                )
            }
            "上传文件" -> {
                val document = Path().apply {
                    moveTo(size.width * 0.25f, size.height * 0.10f)
                    lineTo(size.width * 0.61f, size.height * 0.10f)
                    lineTo(size.width * 0.79f, size.height * 0.28f)
                    lineTo(size.width * 0.79f, size.height * 0.88f)
                    lineTo(size.width * 0.25f, size.height * 0.88f)
                    close()
                }
                drawPath(document, color)
                drawPath(
                    path = Path().apply {
                        moveTo(size.width * 0.61f, size.height * 0.11f)
                        lineTo(size.width * 0.78f, size.height * 0.28f)
                        lineTo(size.width * 0.61f, size.height * 0.28f)
                        close()
                    },
                    color = Color.White.copy(alpha = 0.85f),
                )
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * 0.52f, size.height * 0.72f),
                    end = Offset(size.width * 0.52f, size.height * 0.36f),
                    strokeWidth = 2.3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * 0.38f, size.height * 0.48f),
                    end = Offset(size.width * 0.52f, size.height * 0.34f),
                    strokeWidth = 2.3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * 0.66f, size.height * 0.48f),
                    end = Offset(size.width * 0.52f, size.height * 0.34f),
                    strokeWidth = 2.3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            "回收站" -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.30f, size.height * 0.32f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.40f, size.height * 0.50f),
                    cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.22f, size.height * 0.22f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.10f),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.39f, size.height * 0.12f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.22f, size.height * 0.09f),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                )
                repeat(2) { index ->
                    val x = size.width * (0.43f + index * 0.14f)
                    drawLine(
                        color = Color.White.copy(alpha = 0.86f),
                        start = Offset(x, size.height * 0.40f),
                        end = Offset(x, size.height * 0.72f),
                        strokeWidth = 1.3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            "账号管理" -> {
                drawCircle(
                    color = color,
                    radius = size.width * 0.17f,
                    center = Offset(size.width * 0.43f, size.height * 0.30f),
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.20f, size.height * 0.50f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.46f, size.height * 0.28f),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                )
                drawCircle(
                    color = color,
                    radius = size.width * 0.13f,
                    center = Offset(size.width * 0.72f, size.height * 0.62f),
                )
                drawCircle(
                    color = Color.White,
                    radius = size.width * 0.045f,
                    center = Offset(size.width * 0.72f, size.height * 0.62f),
                )
                repeat(6) { index ->
                    val angle = index * 60f
                    val radians = Math.toRadians(angle.toDouble()).toFloat()
                    val center = Offset(size.width * 0.72f, size.height * 0.62f)
                    val inner = Offset(
                        center.x + kotlin.math.cos(radians) * size.width * 0.12f,
                        center.y + kotlin.math.sin(radians) * size.width * 0.12f,
                    )
                    val outer = Offset(
                        center.x + kotlin.math.cos(radians) * size.width * 0.18f,
                        center.y + kotlin.math.sin(radians) * size.width * 0.18f,
                    )
                    drawLine(color, inner, outer, strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
                }
            }
            else -> Unit
        }
    }
    if (label !in setOf("全部文件", "上传文件", "回收站", "账号管理")) {
        Icon(
            imageVector = fallback,
            contentDescription = label,
            tint = color,
            modifier = modifier,
        )
    }
}

@Composable
fun AliciaPullRefreshContainer(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val refreshThresholdPx = with(density) { 108.dp.toPx() }
    val refreshHoldOffsetPx = with(density) { 66.dp.toPx() }
    val maxPullOffsetPx = with(density) { 132.dp.toPx() }
    val maxIndicatorOffsetPx = with(density) { 78.dp.toPx() }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var refreshHoldVisible by remember { mutableStateOf(false) }

    LaunchedEffect(refreshing) {
        if (refreshing) {
            refreshHoldVisible = true
        } else if (refreshHoldVisible) {
            delay(220)
            refreshHoldVisible = false
        }
    }

    val nestedScrollConnection = remember(refreshing, refreshThresholdPx, maxPullOffsetPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.Drag || refreshing || refreshHoldVisible) {
                    return Offset.Zero
                }
                if (available.y >= 0f || dragOffsetPx <= 0f) {
                    return Offset.Zero
                }
                val previous = dragOffsetPx
                dragOffsetPx = max(0f, dragOffsetPx + available.y * 0.72f)
                return Offset(x = 0f, y = dragOffsetPx - previous)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.Drag || refreshing || refreshHoldVisible) {
                    return Offset.Zero
                }
                if (available.y <= 0f) {
                    return Offset.Zero
                }
                dragOffsetPx = (dragOffsetPx + available.y * 0.34f).coerceAtMost(maxPullOffsetPx)
                return Offset(x = 0f, y = available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragOffsetPx <= 0f) {
                    return Velocity.Zero
                }
                val shouldRefresh = dragOffsetPx >= refreshThresholdPx && !refreshing
                dragOffsetPx = 0f
                if (shouldRefresh) {
                    refreshHoldVisible = true
                    onRefresh()
                }
                return available
            }
        }
    }

    val indicatorDragFactor = refreshHoldOffsetPx / refreshThresholdPx
    val dragIndicatorOffsetPx = (dragOffsetPx * indicatorDragFactor).coerceAtMost(maxIndicatorOffsetPx)
    val settledIndicatorOffsetPx by animateFloatAsState(
        targetValue = if (refreshing || refreshHoldVisible) refreshHoldOffsetPx else 0f,
        animationSpec = tween(durationMillis = if (refreshing || refreshHoldVisible) 160 else 190),
        label = "pull_refresh_indicator_offset",
    )
    val indicatorOffsetPx = if (dragOffsetPx > 0f && !refreshing && !refreshHoldVisible) {
        dragIndicatorOffsetPx
    } else {
        settledIndicatorOffsetPx
    }

    Box(
        modifier = modifier.nestedScroll(nestedScrollConnection),
    ) {
        content()
        AliciaMechaPullRefreshIndicator(
            refreshing = refreshing,
            progress = (dragOffsetPx / refreshThresholdPx).coerceIn(0f, 1f),
            offsetPx = indicatorOffsetPx,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun AliciaMechaPullRefreshIndicator(
    refreshing: Boolean,
    progress: Float,
    offsetPx: Float,
    modifier: Modifier = Modifier,
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val revealTarget = if (offsetPx > 0.5f || refreshing) 1f else 0f
    val pulseTransition = rememberInfiniteTransition(label = "pull_refresh_pulse")
    val visible by animateFloatAsState(
        targetValue = revealTarget,
        animationSpec = tween(durationMillis = 140),
        label = "pull_refresh_visible",
    )
    val dragScale by animateFloatAsState(
        targetValue = if (refreshing) 1f else 0.8f + clampedProgress * 0.2f,
        animationSpec = tween(durationMillis = 130),
        label = "pull_refresh_drag_scale",
    )
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 780),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pull_refresh_pulse_scale",
    )
    val finalScale = if (refreshing) pulseScale else dragScale

    Box(
        modifier = modifier
            .padding(top = 4.dp)
            .offset { IntOffset(x = 0, y = offsetPx.roundToInt() - 74.dp.roundToPx()) }
            .size(74.dp)
            .graphicsLayer {
                alpha = visible
                scaleX = finalScale
                scaleY = finalScale
            },
        contentAlignment = Alignment.Center,
    ) {
        AliciaMechaRefreshRingBackdrop(
            modifier = Modifier
                .size(66.dp)
                .offset(y = 2.dp),
        )
        Image(
            painter = painterResource(id = R.drawable.alicia_pull_refresh_mecha),
            contentDescription = "刷新中",
            modifier = Modifier
                .size(58.dp)
                .offset(y = (-1).dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
fun AliciaQuotaBanner(
    usedBytes: Long,
    totalBytes: Long?,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val isTight = totalBytes != null && totalBytes > 0L && usedBytes * 100 >= totalBytes * 85
    val background = if (isTight) Color(0xFFFFFCFA) else Color(0xFFFFFFFF)
    val accent = if (isTight) Color(0xFFFF6D4A) else MaterialTheme.colorScheme.primary

    Surface(
        color = background,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            width = 1.dp,
            color = if (isTight) {
                Color(0xFFFFE5D9)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudQueue,
                        contentDescription = null,
                        tint = accent,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isTight) "空间已接近上限" else "云端空间运行正常",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = "${formatBytes(usedBytes)} / ${formatOptionalBytes(totalBytes)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
                AliciaTextAction(
                    label = actionLabel,
                    onClick = onAction,
                )
            }
        }
    }
}

@Composable
fun AliciaMechaQuotaBanner(
    usedBytes: Long,
    totalBytes: Long?,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isTight = totalBytes != null && totalBytes > 0L && usedBytes * 100 >= totalBytes * 85
    val accent = if (isTight) Color(0xFFFF6D4A) else MechaBlue

    Box(modifier = modifier) {
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(0.dp),
            shadowElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier,
            ) {
                AliciaNineSliceBackground(
                    resId = R.drawable.alicia_9_quota_banner,
                    slice = 44.dp,
                    modifier = Modifier.matchParentSize(),
                )
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 13.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(35.dp)
                            .clip(CircleShape)
                        ,
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudQueue,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isTight) Color(0xFFFF6D4A) else Color(0xFF47C956)),
                            )
                            Text(
                                text = if (isTight) "空间已接近上限" else "云端空间运行正常",
                                color = MechaInk,
                                fontFamily = AliciaMechaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                lineHeight = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (actionLabel != null && onAction != null) {
                        AliciaMechaBlueButton(
                            label = actionLabel,
                            onClick = onAction,
                            modifier = Modifier.width(82.dp),
                        )
                    }
                }
            }
        }
        AliciaMechaQuotaLightningOverlay(
            isTight = isTight,
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
private fun AliciaMechaQuotaLightningOverlay(
    isTight: Boolean,
    modifier: Modifier = Modifier,
) {
    val stormTransition = rememberInfiniteTransition(label = "quota_lightning_frames")
    val topTicker by stormTransition.animateFloat(
        initialValue = 0f,
        targetValue = AliciaLightningFrameResIds.size.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 620, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "quota_lightning_top",
    )
    val bottomTicker by stormTransition.animateFloat(
        initialValue = 2f,
        targetValue = AliciaLightningFrameResIds.size.toFloat() + 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "quota_lightning_bottom",
    )
    val leftTicker by stormTransition.animateFloat(
        initialValue = 4f,
        targetValue = AliciaLightningFrameResIds.size.toFloat() + 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 760, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "quota_lightning_left",
    )
    val rightTicker by stormTransition.animateFloat(
        initialValue = 1f,
        targetValue = AliciaLightningFrameResIds.size.toFloat() + 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 670, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "quota_lightning_right",
    )
    val pulse by stormTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 360, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "quota_lightning_pulse",
    )
    val flicker by stormTransition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 220, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "quota_lightning_flicker",
    )

    val topIndex = positiveMod(topTicker.toInt(), AliciaLightningFrameResIds.size)
    val bottomIndex = positiveMod(bottomTicker.toInt(), AliciaLightningFrameResIds.size)
    val leftIndex = positiveMod(leftTicker.toInt(), AliciaLightningFrameResIds.size)
    val rightIndex = positiveMod(rightTicker.toInt(), AliciaLightningFrameResIds.size)
    val core = if (isTight) Color(0xFF1655D8) else MechaBlue
    val highlight = if (isTight) Color(0xFF5F97FF) else Color(0xFF5A93FF)
    val topFrame = ImageBitmap.imageResource(id = AliciaLightningFrameResIds[topIndex])
    val bottomFrame = ImageBitmap.imageResource(id = AliciaLightningFrameResIds[bottomIndex])
    val leftFrame = ImageBitmap.imageResource(id = AliciaLightningFrameResIds[leftIndex])
    val rightFrame = ImageBitmap.imageResource(id = AliciaLightningFrameResIds[rightIndex])

    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        val expandX = 7.5.dp.toPx()
        val expandY = 2.dp.toPx()
        val frameLeft = -expandX
        val frameTop = -expandY
        val frameWidth = size.width + expandX * 2
        val frameHeight = size.height + expandY * 2
        val edgeThickness = 14.dp.toPx()
        val horizontalShift = 5.dp.toPx()
        val verticalTopOverlap = 5.dp.toPx()
        val verticalBottomOverlap = 0.dp.toPx()
        val verticalLength = frameHeight + verticalTopOverlap + verticalBottomOverlap

        drawAliciaLightningFrame(
            image = topFrame,
            left = frameLeft + horizontalShift,
            top = frameTop - edgeThickness * 0.5f,
            width = frameWidth,
            height = edgeThickness,
            tint = core,
            highlightTint = highlight,
            baseAlpha = 0.9f + 0.1f * flicker,
            highlightAlpha = 0.48f + 0.1f * pulse,
        )
        drawAliciaLightningFrame(
            image = bottomFrame,
            left = frameLeft,
            top = frameTop + frameHeight - edgeThickness * 0.5f,
            width = frameWidth,
            height = edgeThickness,
            rotation = 180f,
            tint = core,
            highlightTint = highlight,
            baseAlpha = 0.88f + 0.12f * pulse,
            highlightAlpha = 0.46f + 0.1f * flicker,
        )
        drawAliciaLightningFrame(
            image = leftFrame,
            left = frameLeft - edgeThickness * 0.5f + horizontalShift,
            top = frameTop - verticalTopOverlap,
            width = edgeThickness,
            height = verticalLength,
            rotation = -90f,
            tint = core,
            highlightTint = highlight,
            baseAlpha = 0.9f + 0.1f * pulse,
            highlightAlpha = 0.46f + 0.1f * flicker,
        )
        drawAliciaLightningFrame(
            image = rightFrame,
            left = frameLeft + frameWidth - edgeThickness * 0.5f - horizontalShift,
            top = frameTop - verticalTopOverlap,
            width = edgeThickness,
            height = verticalLength,
            rotation = 90f,
            mirrorX = true,
            tint = core,
            highlightTint = highlight,
            baseAlpha = 0.9f + 0.1f * flicker,
            highlightAlpha = 0.46f + 0.1f * pulse,
        )
    }
}

private fun DrawScope.drawAliciaLightningFrame(
    image: ImageBitmap,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    tint: Color,
    highlightTint: Color,
    baseAlpha: Float,
    highlightAlpha: Float,
    rotation: Float = 0f,
    mirrorX: Boolean = false,
) {
    val targetWidth = if (rotation == 90f || rotation == -90f) height else width
    val targetHeight = if (rotation == 90f || rotation == -90f) width else height
    val center = Offset(left + width * 0.5f, top + height * 0.5f)

    rotate(degrees = rotation, pivot = center) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(
                x = (center.x - targetWidth * 0.5f).roundToInt(),
                y = (center.y - targetHeight * 0.5f).roundToInt(),
            ),
            dstSize = IntSize(
                width = targetWidth.roundToInt().coerceAtLeast(1),
                height = targetHeight.roundToInt().coerceAtLeast(1),
            ),
            colorFilter = ColorFilter.tint(tint.copy(alpha = baseAlpha), BlendMode.SrcIn),
            filterQuality = FilterQuality.High,
        )
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(
                x = (center.x - targetWidth * 0.5f).roundToInt(),
                y = (center.y - targetHeight * 0.5f).roundToInt(),
            ),
            dstSize = IntSize(
                width = targetWidth.roundToInt().coerceAtLeast(1),
                height = targetHeight.roundToInt().coerceAtLeast(1),
            ),
            colorFilter = ColorFilter.tint(highlightTint.copy(alpha = highlightAlpha), BlendMode.SrcIn),
            filterQuality = FilterQuality.High,
        )
    }
}

private fun positiveMod(value: Int, modulus: Int): Int {
    if (modulus == 0) return 0
    val result = value % modulus
    return if (result < 0) result + modulus else result
}

@Composable
fun AliciaMechaMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MechaBlue,
    icon: ImageVector? = null,
) {
    val backgroundRes = if (accent == MechaBlue) {
        R.drawable.alicia_9_metric_blue
    } else {
        R.drawable.alicia_9_metric_orange
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = Color.Transparent,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .height(AliciaMechaDesignSpec.metricCardHeight)
        ) {
            AliciaNineSliceBackground(
                resId = backgroundRes,
                slice = 38.dp,
                modifier = Modifier.matchParentSize(),
            )
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(
                    color = accent.copy(alpha = 0.09f),
                    radius = 19.dp.toPx(),
                    center = Offset(size.width - 46.dp.toPx(), size.height * 0.5f),
                )
            }
            icon?.let { imageVector ->
                Icon(
                    imageVector = imageVector,
                    contentDescription = null,
                    tint = accent.copy(alpha = 0.58f),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 34.dp)
                        .size(24.dp),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = 14.dp, top = 8.dp, end = 66.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    color = Color(0xFF6D7688),
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                )
                Text(
                    text = value,
                    color = MechaInk,
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun AliciaFloatingFileDock(
    onUpload: () -> Unit,
    onCreateFolder: () -> Unit,
    uploading: Boolean,
    creating: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AliciaFloatingFileAction(
            icon = Icons.Rounded.UploadFile,
            label = if (uploading) "上传中" else "上传",
            loading = uploading,
            onClick = onUpload,
            modifier = Modifier.size(width = 76.dp, height = 84.dp),
        )
        AliciaFloatingFileAction(
            icon = Icons.Rounded.CreateNewFolder,
            label = if (creating) "创建中" else "新建",
            loading = creating,
            onClick = onCreateFolder,
            modifier = Modifier.size(width = 76.dp, height = 84.dp),
        )
    }
}

@Composable
private fun AliciaFloatingFileAction(
    icon: ImageVector,
    label: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.clickable(enabled = !loading, onClick = onClick),
    ) {
        AliciaNineSliceBackground(
            resId = R.drawable.alicia_9_quick,
            slice = 42.dp,
            modifier = Modifier.matchParentSize(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.White, Color(0xFFEAF2FF), Color(0xFFDCEAFF)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MechaBlue,
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = MechaBlue,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(
                text = label,
                color = MechaInk,
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun AliciaMiniTrendCard(
    usedBytes: Long,
    totalBytes: Long?,
    values: List<Long>,
) {
    val chartValues = if (values.size > 7) values.takeLast(7) else values

    AliciaSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "空间趋势",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(y = (5).dp),
                )
                Text(
                    text = "${formatBytes(usedBytes)} / ${formatOptionalBytes(totalBytes)}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.offset(y = (-5).dp),
                )
            }
            AliciaMiniSparkline(
                values = chartValues,
                modifier = Modifier
                    .width(112.dp)
                    .height(56.dp),
            )
        }

    }
}

@Composable
fun AliciaMechaTrendCard(
    usedBytes: Long,
    totalBytes: Long?,
    values: List<Long>,
) {
    val chartValues = if (values.size > 7) values.takeLast(7) else values

    AliciaMechaPanel(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        backgroundResId = R.drawable.alicia_9_trend,
        backgroundSlice = 56.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "空间趋势",
                    color = MechaInk,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                )
                Text(
                    text = "${formatBytes(usedBytes)} / ${formatOptionalBytes(totalBytes)}",
                    color = MechaInk,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .width(152.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFFEAF2FF), Color(0xFFD4E2FF)),
                        ),
                    )
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                AliciaMechaSparkline(
                    values = chartValues,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun AliciaMiniSparkline(
    values: List<Long>,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface

    if (values.isEmpty()) {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "暂无数据",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }
        return
    }

    Canvas(modifier = modifier) {
        val max = values.maxOrNull()?.coerceAtLeast(1L)?.toFloat() ?: 1f
        val stepX = size.width / values.lastIndex.coerceAtLeast(1)
        val top = 12.dp.toPx()
        val bottom = size.height - 10.dp.toPx()
        val height = bottom - top
        val linePath = Path()
        val fillPath = Path()

        values.forEachIndexed { index, value ->
            val x = stepX * index
            val y = bottom - height * (value / max)
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, bottom)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        fillPath.lineTo(size.width, bottom)
        fillPath.close()

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    primary.copy(alpha = 0.14f),
                    primary.copy(alpha = 0.02f),
                ),
            ),
            cornerRadius = CornerRadius(24.dp.toPx()),
        )
        drawPath(
            path = fillPath,
            color = primary.copy(alpha = 0.14f),
        )
        drawPath(
            path = linePath,
            color = primary,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
        )

        values.forEachIndexed { index, value ->
            val x = stepX * index
            val y = bottom - height * (value / max)
            drawCircle(
                color = surface,
                radius = 4.dp.toPx(),
                center = Offset(x, y),
            )
            drawCircle(
                color = primary,
                radius = 2.5.dp.toPx(),
                center = Offset(x, y),
            )
        }
    }
}

@Composable
private fun AliciaMechaSparkline(
    values: List<Long>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas
        val max = values.maxOrNull()?.coerceAtLeast(1L)?.toFloat() ?: 1f
        val stepX = size.width / values.lastIndex.coerceAtLeast(1)
        val top = 6.dp.toPx()
        val bottom = size.height - 6.dp.toPx()
        val height = bottom - top
        val path = Path()
        val fillPath = Path()

        values.forEachIndexed { index, value ->
            val x = stepX * index
            val y = bottom - height * (value / max)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        repeat(5) { index ->
            val y = size.height * index / 4f
            drawLine(
                color = MechaBlue.copy(alpha = 0.12f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 0.8.dp.toPx(),
            )
        }
        repeat(7) { index ->
            val x = size.width * index / 6f
            drawLine(
                color = MechaBlue.copy(alpha = 0.08f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 0.8.dp.toPx(),
            )
        }

        values.forEachIndexed { index, value ->
            val x = stepX * index
            val y = bottom - height * (value / max)
            if (index == 0) fillPath.moveTo(x, bottom) else Unit
            fillPath.lineTo(x, y)
        }
        fillPath.lineTo(size.width, bottom)
        fillPath.close()
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(MechaBlue.copy(alpha = 0.18f), MechaBlue.copy(alpha = 0.02f)),
                startY = top,
                endY = bottom,
            ),
        )
        drawPath(
            path = path,
            color = MechaBlue,
            style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round),
        )
        values.forEachIndexed { index, value ->
            val x = stepX * index
            val y = bottom - height * (value / max)
            drawCircle(Color.White, radius = 4.2.dp.toPx(), center = Offset(x, y))
            drawCircle(MechaBlue, radius = 2.4.dp.toPx(), center = Offset(x, y))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AliciaCompactNodeRow(
    node: StorageNode,
    busy: Boolean,
    selected: Boolean = false,
    highlighted: Boolean = false,
    selectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    onToggleSelect: () -> Unit = {},
    onMore: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (selectionMode) {
                        onToggleSelect()
                    } else {
                        onClick()
                    }
                },
                onLongClick = onLongPress,
            )
            .height(76.dp),
    ) {
        AliciaNineSliceBackground(
            resId = R.drawable.alicia_9_file_row,
            slice = 44.dp,
            modifier = Modifier.matchParentSize(),
        )
        if (selected || highlighted) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawPath(
                    path = mechaSegmentOutline(size.width, size.height, inset = 2.dp.toPx()),
                    color = MechaBlue.copy(alpha = if (selected) 0.8f else 0.56f),
                    style = Stroke(width = 1.7.dp.toPx()),
                )
                if (selected) {
                    drawLine(
                        color = MechaCyan.copy(alpha = 0.9f),
                        start = Offset(size.width * 0.67f, size.height - 5.dp.toPx()),
                        end = Offset(size.width * 0.92f, size.height - 5.dp.toPx()),
                        strokeWidth = 2.2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, top = 6.dp, end = 10.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = if (node.type == StorageNodeType.FOLDER) {
                                listOf(Color.White, Color(0xFFEAF3FF), Color(0xFFD9E8FF))
                            } else {
                                listOf(Color.White, Color(0xFFF2F6FD), Color(0xFFE4EDF8))
                            },
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (node.type == StorageNodeType.FOLDER) {
                        Icons.Rounded.Folder
                    } else {
                        Icons.Rounded.Description
                    },
                    contentDescription = null,
                    tint = if (node.type == StorageNodeType.FOLDER) MechaBlue else MechaMuted,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = node.name,
                    color = MechaInk,
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatNodeMeta(node),
                    color = MechaMuted,
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MechaBlue,
                )
            } else if (selectionMode) {
                AliciaMechaNodeActionOrb(
                    selected = selected,
                    onClick = onToggleSelect,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = if (selected) "已选中" else "未选中",
                        tint = if (selected) MechaBlue else MechaMuted.copy(alpha = 0.65f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                AliciaMechaNodeActionOrb(
                    selected = false,
                    onClick = onMore,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = "更多操作",
                        tint = MechaBlue,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AliciaMechaNodeActionOrb(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.Transparent,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    brush = if (selected) {
                        Brush.radialGradient(
                            colors = listOf(Color.White, Color(0xFFE6EEFF), Color(0xFFD6E4FF)),
                        )
                    } else {
                        Brush.radialGradient(
                            colors = listOf(Color.White, Color(0xFFF3F7FD), Color(0xFFE5EDF7)),
                        )
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

@Composable
fun AliciaCompactUserRow(
    user: User,
    imageUrl: String? = null,
    isCurrentUser: Boolean = false,
    isUpdatingQuota: Boolean = false,
    isResettingPassword: Boolean = false,
    onEditQuota: (() -> Unit)? = null,
    onResetPassword: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = Color.Transparent,
        shadowElevation = 0.dp,
    ) {
        Box {
            AliciaNineSliceBackground(
                resId = R.drawable.alicia_9_team_user,
                slice = 48.dp,
                modifier = Modifier.matchParentSize(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 12.dp, end = 14.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AliciaMechaAvatarFrame(
                    label = user.nickname,
                    imageUrl = imageUrl,
                    frameSize = 60.dp,
                    avatarSize = 42.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = user.nickname,
                        color = MechaInk,
                        fontFamily = AliciaMechaFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        lineHeight = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${formatRole(user.role)} · ${user.phoneNumber}",
                        color = MechaMuted,
                        fontFamily = AliciaMechaFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "占用 ${userUsageLabel(user)}",
                        color = Color(0xFF778397),
                        fontFamily = AliciaMechaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AliciaMechaTeamCompactButton(
                            label = if (isCurrentUser) "当前账号" else formatRole(user.role),
                            minWidth = 66.dp,
                            height = 28.dp,
                        )
                        if (onResetPassword != null) {
                            AliciaMechaTeamCompactButton(
                                label = if (isResettingPassword) "重置中..." else "重置密码",
                                onClick = onResetPassword,
                                enabled = !isResettingPassword && !isUpdatingQuota,
                                minWidth = 72.dp,
                                height = 28.dp,
                            )
                        }
                        if (onEditQuota != null) {
                            AliciaMechaTeamCompactButton(
                                label = if (isUpdatingQuota) "提交中..." else "修改额度",
                                onClick = onEditQuota,
                                enabled = !isUpdatingQuota && !isResettingPassword,
                                minWidth = 72.dp,
                                height = 28.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AliciaInlineState(
    title: String,
    description: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.CloudQueue,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = AliciaMechaFontFamily,
            fontWeight = FontWeight.Bold,
        )
        if (description.isNotBlank()) {
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
fun AliciaMechaRecentEmptyState(
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(4.dp)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = Color(0xFFF6F9FE),
        border = BorderStroke(1.dp, Color(0xFFD4DEEE)),
    ) {
        Box(
            modifier = Modifier
                .height(38.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = Color(0xFFB5C0D3),
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(9.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = "暂无最近文件",
                        color = Color(0xFF9BA6B9),
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        maxLines = 1,
                    )
                    Text(
                        text = "上传或访问文件后会显示在这里",
                        color = Color(0xFF9BA6B9),
                        fontWeight = FontWeight.Medium,
                        fontSize = 7.sp,
                        lineHeight = 9.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun AliciaMechaRecentFiles(
    nodes: List<StorageNode>,
    onOpen: (StorageNode) -> Unit,
    onMore: (StorageNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (nodes.isEmpty()) {
        AliciaMechaRecentEmptyState(modifier = modifier)
        return
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFFF6F9FE),
        border = BorderStroke(1.dp, Color(0xFFD4DEEE)),
    ) {
        Column(
            modifier = Modifier
                .height(74.dp)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            nodes.take(2).forEachIndexed { index, node ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFE2E9F4)),
                    )
                }
                AliciaMechaRecentFileRow(
                    node = node,
                    onOpen = { onOpen(node) },
                    onMore = { onMore(node) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AliciaMechaRecentFileRow(
    node: StorageNode,
    onOpen: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFFEAF1FF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (node.type == StorageNodeType.FOLDER) {
                    Icons.Rounded.Folder
                } else {
                    Icons.Rounded.Description
                },
                contentDescription = null,
                tint = MechaBlue,
                modifier = Modifier.size(17.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = node.name,
                color = MechaInk,
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatNodeMeta(node),
                color = Color(0xFF7D8799),
                fontFamily = AliciaMechaFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 8.sp,
                lineHeight = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onMore),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.72f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.MoreHoriz,
                    contentDescription = "更多操作",
                    tint = MechaBlue,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
fun AliciaEmptyState(
    title: String,
    description: String,
) {
    AliciaSectionCard {
        AliciaInlineState(
            title = title,
            description = description,
        )
    }
}

@Composable
fun AliciaLoadingCard(message: String) {
    AliciaSectionCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AliciaBottomBar(items: List<AliciaBottomNavItem>) {
    if (items.isEmpty()) {
        return
    }

    val prominentItem = items.firstOrNull { it.prominent }
    val sideItems = items.filterNot { it.prominent }
    val useMi11Shell = prominentItem == null && items.size == 3

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .height(
                when {
                    useMi11Shell -> 78.dp
                    prominentItem != null -> AliciaMechaDesignSpec.bottomBarHeight
                    else -> 82.dp
                },
            ),
    ) {
        if (useMi11Shell) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(70.dp),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.alicia_9_bottom_mi11_hollow_sidecut),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )

                AliciaMi11BottomShellSlots.zip(items).forEach { (slotSpec, item) ->
                    AliciaBottomShellImageItem(
                        item = item,
                        slot = slotSpec.slot,
                        modifier = Modifier
                            .offset(
                                x = maxWidth * slotSpec.xRatio,
                                y = maxHeight * slotSpec.yRatio,
                            )
                            .width(maxWidth * slotSpec.widthRatio)
                            .height(maxHeight * slotSpec.heightRatio),
                    )
                }
            }
            return@Box
        } else {
            Image(
                painter = painterResource(id = R.drawable.alicia_9_bottom),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(64.dp),
                contentScale = ContentScale.FillBounds,
            )
        }

        if (prominentItem != null && sideItems.size == 2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(start = 32.dp, end = 32.dp, bottom = 8.dp)
                    .height(38.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AliciaBottomSideItem(
                    item = sideItems[0],
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(86.dp))
                AliciaBottomSideItem(
                    item = sideItems[1],
                    modifier = Modifier.weight(1f),
                )
            }

            AliciaBottomCenterItem(
                item = prominentItem,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-2).dp),
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = if (useMi11Shell) 38.dp else 34.dp,
                        end = if (useMi11Shell) 38.dp else 34.dp,
                        bottom = if (useMi11Shell) 7.dp else 5.dp,
                    )
                    .height(if (useMi11Shell) 47.dp else 46.dp),
                horizontalArrangement = Arrangement.spacedBy(if (useMi11Shell) 0.dp else 4.dp),
            ) {
                items.forEach { item ->
                    AliciaBottomSegmentItem(
                        item = item,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        embeddedInShell = useMi11Shell,
                    )
                }
            }
        }
    }
}

private fun aliciaBottomShellResId(
    slot: AliciaBottomShellSlot,
    selected: Boolean,
): Int {
    return when (slot) {
        AliciaBottomShellSlot.Left -> if (selected) {
            R.drawable.alicia_bottom_left_active
        } else {
            R.drawable.alicia_bottom_left_idle
        }

        AliciaBottomShellSlot.Center -> if (selected) {
            R.drawable.alicia_bottom_center_active
        } else {
            R.drawable.alicia_bottom_center_idle
        }

        AliciaBottomShellSlot.Right -> if (selected) {
            R.drawable.alicia_bottom_right_active
        } else {
            R.drawable.alicia_bottom_right_idle
        }
    }
}

@Composable
private fun AliciaBottomShellImageItem(
    item: AliciaBottomNavItem,
    slot: AliciaBottomShellSlot,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (item.selected) Color.White else Color(0xFF59667B)
    val shellResId = item.shellResIdOverride ?: aliciaBottomShellResId(slot, item.selected)
    val containerModifier = if (item.enabled) {
        modifier.clickable(onClick = item.onClick)
    } else {
        modifier
    }

    Box(
        modifier = containerModifier,
    ) {
        Image(
            painter = painterResource(id = shellResId),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )

        if (item.showShellContent) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 0.dp)
                    .padding(horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = contentColor,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    text = item.label,
                    modifier = Modifier.offset(y = (-2).dp),
                    color = contentColor,
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 9.sp,
                    lineHeight = 8.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AliciaBottomSegmentItem(
    item: AliciaBottomNavItem,
    modifier: Modifier = Modifier,
    embeddedInShell: Boolean = false,
) {
    val segmentShape = mechaSegmentShape(cutRatio = 0.24f)
    val backgroundBrush = if (item.selected) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF4B94FF),
                MechaBlue,
                Color(0xFF063DBA),
            ),
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                if (embeddedInShell) Color(0xFFF5F8FE) else Color(0xFFF9FCFF),
                if (embeddedInShell) Color(0xFFE7EEF9) else Color(0xFFEAF1FB),
                if (embeddedInShell) Color(0xFFDEE8F6) else Color(0xFFDCE7F7),
            ),
        )
    }
    val contentColor = if (item.selected) Color.White else Color(0xFF59667B)
    val borderColor = if (item.selected) Color(0xFF7FC7FF) else Color(0xFFC9D8ED)
    val innerStrokeColor = if (item.selected) {
        Color.White.copy(alpha = 0.46f)
    } else {
        Color.White.copy(alpha = 0.92f)
    }
    val topAccentColor = if (item.selected) {
        Color(0xFF8AF0FF)
    } else {
        Color(0xFFD3E6FF)
    }
    val containerModifier = if (embeddedInShell) {
        modifier.padding(horizontal = 7.dp, vertical = 4.dp)
    } else {
        modifier
    }

    Surface(
        modifier = containerModifier
            .clip(segmentShape)
            .offset(y = if (embeddedInShell) 0.dp else if (item.selected) 1.dp else 2.dp)
            .clickable(onClick = item.onClick),
        shape = segmentShape,
        color = if (embeddedInShell) Color.Transparent else Color.Transparent,
        border = if (embeddedInShell) {
            null
        } else {
            BorderStroke(if (item.selected) 1.6.dp else 1.2.dp, borderColor)
        },
        shadowElevation = if (embeddedInShell) 0.dp else if (item.selected) 8.dp else 2.dp,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundBrush),
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val innerPath = mechaSegmentOutline(
                    width = size.width,
                    height = size.height,
                    cutRatio = 0.24f,
                    inset = 2.dp.toPx(),
                )
                drawPath(
                    path = innerPath,
                    color = innerStrokeColor.copy(alpha = if (embeddedInShell) 0.72f else 1f),
                    style = Stroke(width = if (embeddedInShell) 0.8.dp.toPx() else 1.dp.toPx()),
                )

                if (item.selected) {
                    drawPath(
                        path = Path().apply {
                            moveTo(0f, size.height * 0.72f)
                            lineTo(size.width * 0.22f, size.height)
                            lineTo(0f, size.height)
                            close()
                        },
                        color = Color(0xFF184FC3).copy(alpha = 0.48f),
                    )
                    drawPath(
                        path = Path().apply {
                            moveTo(size.width, size.height * 0.72f)
                            lineTo(size.width * 0.78f, size.height)
                            lineTo(size.width, size.height)
                            close()
                        },
                        color = Color(0xFF184FC3).copy(alpha = 0.48f),
                    )
                }

                val topY = 4.dp.toPx()
                drawLine(
                    color = topAccentColor.copy(
                        alpha = when {
                            item.selected -> 0.92f
                            embeddedInShell -> 0.58f
                            else -> 0.78f
                        },
                    ),
                    start = Offset(size.width * 0.12f, topY),
                    end = Offset(size.width * 0.24f, topY),
                    strokeWidth = 1.1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = topAccentColor.copy(
                        alpha = when {
                            item.selected -> 0.92f
                            embeddedInShell -> 0.58f
                            else -> 0.78f
                        },
                    ),
                    start = Offset(size.width * 0.76f, topY),
                    end = Offset(size.width * 0.88f, topY),
                    strokeWidth = 1.1.dp.toPx(),
                    cap = StrokeCap.Round,
                )

                val bottomY = size.height - 4.dp.toPx()
                drawLine(
                    color = if (item.selected) {
                        MechaCyan.copy(alpha = 0.96f)
                    } else {
                        Color(0xFFA8CFFF).copy(alpha = if (embeddedInShell) 0.42f else 0.72f)
                    },
                    start = Offset(size.width * 0.18f, bottomY),
                    end = Offset(size.width * 0.70f, bottomY),
                    strokeWidth = if (item.selected) 1.4.dp.toPx() else if (embeddedInShell) 0.9.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 4.dp)
                    .padding(horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = contentColor,
                    modifier = Modifier.size(19.dp),
                )
                Text(
                    text = item.label,
                    color = contentColor,
                    fontFamily = AliciaMechaFontFamily,
                    fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 9.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AliciaBottomSideItem(
    item: AliciaBottomNavItem,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = item.onClick)
            .padding(horizontal = 8.dp, vertical = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = if (item.selected) {
                MechaBlue
            } else {
                MechaMuted
            },
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = item.label,
            color = if (item.selected) {
                MechaBlue
            } else {
                MechaMuted
            },
            fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 9.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun AliciaBottomCenterItem(
    item: AliciaBottomNavItem,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (item.selected) MechaBlue else MechaArmor
    val contentColor = if (item.selected) Color.White else MechaBlue
    val borderColor = if (item.selected) {
        Color.White.copy(alpha = 0.96f)
    } else {
        Color(0xFFD6E0F0)
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = item.onClick),
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        border = BorderStroke(3.dp, borderColor),
        shadowElevation = if (item.selected) 10.dp else 5.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = item.label,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun LoginBackdrop(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(Color(0xFFF8FAFF), Color(0xFFF3F6FD), Color(0xFFF7F8FB)),
            ),
        ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFDEE9FF), Color.Transparent),
                ),
                radius = size.minDimension * 0.45f,
                center = Offset(size.width * 0.18f, size.height * 0.16f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFE8DA), Color.Transparent),
                ),
                radius = size.minDimension * 0.42f,
                center = Offset(size.width * 0.84f, size.height * 0.28f),
            )
        }
    }
}

@Composable
fun TwoUpRow(
    start: @Composable () -> Unit,
    end: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            start()
        }
        Box(modifier = Modifier.weight(1f)) {
            end()
        }
    }
}

private fun trendDescription(values: List<Long>): String {
    if (values.size < 2) {
        return "最近暂无明显波动"
    }

    val delta = values.last() - values.first()
    if (delta == 0L) {
        return "最近保持平稳"
    }

    val firstValue = values.first().coerceAtLeast(1L)
    val percent = abs((delta.toDouble() / firstValue.toDouble()) * 100).roundToInt()
    return if (delta > 0) {
        "较首日增加 ${percent}%"
    } else {
        "较首日回落 ${percent}%"
    }
}
