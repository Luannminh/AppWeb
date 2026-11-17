package com.twchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twchat.app.ui.theme.readableOn

/** Round gradient avatar showing the first letter of a name. */
@Composable
fun Avatar(
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    fontSize: TextUnit = (size.value * 0.42f).sp,
) {
    val initial = name.trim().take(1).uppercase().ifEmpty { "?" }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(color, color.copy(alpha = 0.72f)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = color.readableOn(),
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
