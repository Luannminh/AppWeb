package com.twchat.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Rose,
    onPrimary = Color.White,
    primaryContainer = RoseContainer,
    onPrimaryContainer = OnRoseContainer,
    secondary = Color(0xFF2F7F86),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEBEF),
    onSecondaryContainer = Color(0xFF07363B),
    background = Blush,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = BubbleMate,
    onSurfaceVariant = InkMuted,
    outline = OutlineSoft,
    outlineVariant = OutlineSoft,
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = RoseNight,
    onPrimary = Color(0xFF4A0C1E),
    primaryContainer = RoseContainerNight,
    onPrimaryContainer = OnRoseContainerNight,
    secondary = Color(0xFF7FD0D6),
    onSecondary = Color(0xFF063539),
    secondaryContainer = Color(0xFF1D4E54),
    onSecondaryContainer = Color(0xFFCDEBEF),
    background = NightBackground,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightInkMuted,
    outline = NightOutline,
    outlineVariant = NightOutline,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val TwoChatShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val TwoChatTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 17.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
    ),
)

@Composable
fun TwoChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = TwoChatShapes,
        typography = TwoChatTypography,
        content = content,
    )
}

/** Picks a readable foreground for an arbitrary accent colour. */
fun Color.readableOn(): Color = if (luminance() > 0.55f) Color(0xFF231A1B) else Color.White
