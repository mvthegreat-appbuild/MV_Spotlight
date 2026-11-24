package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val TradeSpotlightColorScheme = darkColorScheme(
    primary = EmeraldGreen,
    onPrimary = DeepNavyBg,
    primaryContainer = EmeraldPillBg,
    onPrimaryContainer = EmeraldPillText,
    secondary = LinkCyan,
    onSecondary = DeepNavyBg,
    secondaryContainer = NavySurfaceVariant,
    onSecondaryContainer = TextPrimary,
    tertiary = CoralRed,
    onTertiary = DeepNavyBg,
    tertiaryContainer = SellBadgeBg,
    onTertiaryContainer = SellBadgeText,
    background = DeepNavyBg,
    onBackground = TextPrimary,
    surface = NavySurface,
    onSurface = TextPrimary,
    surfaceVariant = NavySurfaceVariant,
    onSurfaceVariant = TextMuted,
    outline = BorderSlate,
    outlineVariant = TileBorderSlate,
    error = CoralRed,
    onError = DeepNavyBg,
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
)

@Composable
fun TradeSpotlightTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = TradeSpotlightColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
