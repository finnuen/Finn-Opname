package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// In light mode ink is the "primary" so plain buttons and text buttons stay readable on paper;
// yellow is reserved for the main actions and is exposed as `tertiary`.
private val LightColorScheme = lightColorScheme(
    primary = Ink,
    onPrimary = LabelStock,
    primaryContainer = ForkliftSoft,
    onPrimaryContainer = Ink,
    secondary = Steel,
    onSecondary = LabelStock,
    secondaryContainer = Color(0xFFE7E9E5),
    onSecondaryContainer = Ink,
    tertiary = Forklift,
    onTertiary = Ink,
    background = Concrete,
    onBackground = Ink,
    surface = LabelStock,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE8EAE6),
    onSurfaceVariant = Steel,
    surfaceTint = Color.Transparent,
    outline = Color(0xFF9AA1A8),
    outlineVariant = Rule,
    error = StopRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Forklift,
    onPrimary = Ink,
    primaryContainer = NightForkliftDeep,
    onPrimaryContainer = ForkliftSoft,
    secondary = NightSteel,
    onSecondary = NightFloor,
    secondaryContainer = NightLabelRaised,
    onSecondaryContainer = NightText,
    tertiary = Forklift,
    onTertiary = Ink,
    background = NightFloor,
    onBackground = NightText,
    surface = NightLabel,
    onSurface = NightText,
    surfaceVariant = NightLabelRaised,
    onSurfaceVariant = NightSteel,
    surfaceTint = Color.Transparent,
    outline = Color(0xFF5A6672),
    outlineVariant = NightRule,
    error = Color(0xFFFF6B52),
    onError = Color(0xFF1A0A06)
)

// Label stock has near-square corners; the default Material pill/bubble shapes would fight it.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp)
)

/** Corner radius for buttons and controls, so dialog buttons match the ones in the main screens. */
val ControlShape = RoundedCornerShape(6.dp)

@Composable
fun ScanCounterTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
