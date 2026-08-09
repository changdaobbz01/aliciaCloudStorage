package com.alicia.cloudstorage.phone.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alicia.cloudstorage.phone.R

private val AddControlInk = Color(0xFF111827)
private val AddControlMuted = Color(0xFF8993A6)
private val AddControlLine = Color(0xFFE4E8F1)
private val AddControlBlue = Color(0xFF0B6BFF)
private val AddControlSoftBlue = Color(0xFFEAF2FF)
private val AddControlDisabled = Color(0xFFF0F2F7)

@Composable
internal fun AddTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = when {
        !enabled -> AddControlLine.copy(alpha = 0.7f)
        focused -> AddControlBlue
        else -> AddControlLine
    }

    Column(modifier = modifier) {
        Text(
            text = label,
            color = AddControlInk,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Box(modifier = Modifier.height(9.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(
                color = AddControlInk,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            ),
            cursorBrush = SolidColor(AddControlBlue),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (enabled) Color.White else AddControlDisabled)
                        .border(1.5.dp, borderColor, RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .alpha(if (enabled) 1f else 0.62f),
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = AddControlMuted,
                                fontSize = 15.sp,
                                modifier = Modifier.align(Alignment.CenterStart),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = if (trailingContent == null) 0.dp else 40.dp)
                                .align(Alignment.CenterStart),
                        ) {
                            innerTextField()
                        }
                        if (trailingContent != null) {
                            Box(
                                modifier = Modifier.align(Alignment.CenterEnd),
                                contentAlignment = Alignment.Center,
                            ) {
                                trailingContent()
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
internal fun AddActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean,
    enabled: Boolean = true,
) {
    val backgroundColor = when {
        !enabled -> AddControlDisabled
        primary -> AddControlBlue
        else -> AddControlSoftBlue
    }
    val contentColor = when {
        !enabled -> AddControlMuted
        primary -> Color.White
        else -> AddControlInk
    }

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .noRippleClickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun AddTopBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .noRippleClickable(
                role = Role.Button,
                onClickLabel = "返回",
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_add_back_black),
            contentDescription = "返回",
            modifier = Modifier
                .size(32.dp)
                .scale(1.55f),
        )
    }
}
