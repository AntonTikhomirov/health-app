package com.tikhomirov.zdorovyak.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Z {
    val Bg = Color(0xFF0E110F)
    val Surface = Color(0xFF171B18)
    val Surface2 = Color(0xFF1F2420)
    val Line = Color(0xFF2B312C)
    val Text = Color(0xFFEDF1EC)
    val Muted = Color(0xFF9AA39B)
    val Lime = Color(0xFFC8F169)
    val Orange = Color(0xFFF2A65A)   // силовые
    val Blue = Color(0xFF6BB8FF)     // кардио
    val Sand = Color(0xFFD9C7A0)     // прочая активность
    val Red = Color(0xFFFF7A6B)
}

private val scheme = darkColorScheme(
    primary = Z.Lime, onPrimary = Color(0xFF13200A),
    secondary = Z.Orange, onSecondary = Color(0xFF2A1600),
    tertiary = Z.Blue,
    background = Z.Bg, onBackground = Z.Text,
    surface = Z.Surface, onSurface = Z.Text,
    surfaceVariant = Z.Surface2, onSurfaceVariant = Z.Muted,
    surfaceContainer = Z.Surface, surfaceContainerHigh = Z.Surface2, surfaceContainerHighest = Z.Surface2,
    surfaceContainerLow = Z.Surface, surfaceContainerLowest = Z.Bg,
    outline = Z.Line, outlineVariant = Z.Line,
    error = Z.Red,
)

@Composable
fun ZTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)
