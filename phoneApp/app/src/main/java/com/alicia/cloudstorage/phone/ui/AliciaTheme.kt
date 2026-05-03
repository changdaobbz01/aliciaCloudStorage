package com.alicia.cloudstorage.phone.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF1F6FF),
    onPrimaryContainer = Color(0xFF1A49BF),
    secondary = Color(0xFF6F7B93),
    onSecondary = Color.White,
    tertiary = Color(0xFFFF8B5B),
    onTertiary = Color.White,
    background = Color(0xFFFAFBFD),
    onBackground = Color(0xFF161C2E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF161C2E),
    surfaceVariant = Color(0xFFF7F9FC),
    onSurfaceVariant = Color(0xFF7B8599),
    outline = Color(0xFFEDF1F6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB9FF),
    onPrimary = Color(0xFF0E2457),
    primaryContainer = Color(0xFF1D397A),
    onPrimaryContainer = Color(0xFFE3EBFF),
    secondary = Color(0xFFBAC4DB),
    onSecondary = Color(0xFF23304B),
    tertiary = Color(0xFFFFB38F),
    onTertiary = Color(0xFF52230F),
    background = Color(0xFF0F1420),
    onBackground = Color(0xFFF4F6FB),
    surface = Color(0xFF171D2A),
    onSurface = Color(0xFFF4F6FB),
    surfaceVariant = Color(0xFF222938),
    onSurfaceVariant = Color(0xFFB7C1D3),
    outline = Color(0xFF2B3448),
)

private val AliciaShapes = Shapes(
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(26.dp),
    large = RoundedCornerShape(32.dp),
)

private val AliciaTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
fun AliciaCloudTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AliciaTypography,
        shapes = AliciaShapes,
        content = content,
    )
}
