package com.alicia.cloudstorage.phone.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import coil.compose.SubcomposeAsyncImage

@Composable
internal fun UserAvatar(
    url: String?,
    fallback: String,
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                },
            ),
        shape = CircleShape,
        color = Color(0xFFEAF1FF),
    ) {
        if (url.isNullOrBlank()) {
            UserAvatarFallback(fallback)
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = "用户头像",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = { UserAvatarFallback(fallback) },
                error = { UserAvatarFallback(fallback) },
            )
        }
    }
}

@Composable
private fun UserAvatarFallback(fallback: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = fallback.trim().take(1).ifBlank { "A" },
            color = Color(0xFF0B6BFF),
            fontWeight = FontWeight.Bold,
        )
    }
}
