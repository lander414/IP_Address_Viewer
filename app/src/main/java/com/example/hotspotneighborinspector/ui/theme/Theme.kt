package com.example.hotspotneighborinspector.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val TerminalSlateColorScheme = darkColorScheme(
    primary = TerminalCyanBright,
    onPrimary = Slate950,
    primaryContainer = TerminalCyanDim,
    onPrimaryContainer = TerminalCyanBright,
    secondary = TerminalEmeraldBright,
    onSecondary = Slate950,
    secondaryContainer = TerminalEmeraldDim,
    onSecondaryContainer = TerminalEmeraldBright,
    tertiary = TerminalAmberBright,
    background = Slate950,
    onBackground = SlateTextPrimary,
    surface = Slate900,
    onSurface = SlateTextPrimary,
    surfaceVariant = Slate850,
    onSurfaceVariant = SlateTextSecondary,
    outline = Slate700,
    error = TerminalRoseBright
)

val TerminalTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun HotspotNeighborInspectorTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TerminalSlateColorScheme,
        typography = TerminalTypography,
        content = content
    )
}

