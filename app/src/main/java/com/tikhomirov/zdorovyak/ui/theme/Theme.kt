package com.tikhomirov.zdorovyak.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tikhomirov.zdorovyak.R

/**
 * Палитра 0.5.0. Тёмная «графитовая» тема: слои различаются светлотой, а не рамками.
 * Контраст по WCAG 2.1 (проверен скриптом, см. docs/design/v050-redesign.md):
 *  Text ≥ 13:1, Muted ≥ 6:1, Faint ≥ 4,5:1 на любом слое (Bg…Surface3) — годится для мелкого текста;
 *  акценты ≥ 6:1 на Surface; тёмный текст OnAccent на любом акценте ≥ 9:1.
 */
object Z {
    val Bg = Color(0xFF0B0D0C)
    val Surface = Color(0xFF141716)
    val Surface2 = Color(0xFF1C201E)
    val Surface3 = Color(0xFF262B28)
    val Line = Color(0xFF2A302C)
    val Text = Color(0xFFF2F5F2)
    val Muted = Color(0xFFA3ACA6)
    val Faint = Color(0xFF8A938D)
    val OnAccent = Color(0xFF0B0D0C)  // текст и иконки на ярких кнопках
    val Lime = Color(0xFFC8F169)
    val Orange = Color(0xFFF5A962)   // силовые
    val Blue = Color(0xFF74BEFF)     // кардио
    val Sand = Color(0xFFD9C7A0)     // прочая активность
    val Red = Color(0xFFFF8577)

    // КБЖУ: у каждого макронутриента свой цвет, один и тот же на всех экранах
    val Kcal = Lime
    val Protein = Color(0xFFFF94B6)
    val Fat = Color(0xFFFFC65C)
    val Carbs = Color(0xFF74C8FF)
}

/** Цифры одинаковой ширины: столбцы и счётчики не «прыгают». */
const val TNUM = "tnum"

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, w: Int) = Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

/** Manrope — заголовки и цифры, Inter — текст. Оба вариативные, с кириллицей (OFL). */
val Manrope = FontFamily(listOf(400, 500, 600, 700, 800).map { variable(R.font.manrope, it) })
val Inter = FontFamily(listOf(400, 500, 600, 700).map { variable(R.font.inter, it) })

private val type = Typography().let { t ->
    fun TextStyle.m() = copy(fontFamily = Manrope)
    fun TextStyle.i() = copy(fontFamily = Inter)
    Typography(
        displayLarge = t.displayLarge.m(), displayMedium = t.displayMedium.m(), displaySmall = t.displaySmall.m(),
        headlineLarge = t.headlineLarge.m().copy(fontWeight = FontWeight.ExtraBold),
        headlineMedium = t.headlineMedium.m().copy(fontWeight = FontWeight.ExtraBold),
        headlineSmall = t.headlineSmall.m().copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.m().copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.2).sp),
        titleMedium = t.titleMedium.m().copy(fontWeight = FontWeight.Bold),
        titleSmall = t.titleSmall.m().copy(fontWeight = FontWeight.Bold),
        bodyLarge = t.bodyLarge.i().copy(fontSize = 15.sp, lineHeight = 21.sp),
        bodyMedium = t.bodyMedium.i(), bodySmall = t.bodySmall.i(),
        labelLarge = t.labelLarge.m().copy(fontWeight = FontWeight.Bold),
        labelMedium = t.labelMedium.i().copy(fontWeight = FontWeight.Medium),
        labelSmall = t.labelSmall.i().copy(fontWeight = FontWeight.Medium),
    )
}

private val scheme = darkColorScheme(
    primary = Z.Lime, onPrimary = Z.OnAccent,
    primaryContainer = Color(0xFF2C3A15), onPrimaryContainer = Z.Lime,
    secondary = Z.Orange, onSecondary = Color(0xFF2A1600),
    // выбранные чипы (FilterChip) — в лаймовом тоне, как навигация
    secondaryContainer = Color(0xFF2C3A15), onSecondaryContainer = Z.Lime,
    tertiary = Z.Blue,
    background = Z.Bg, onBackground = Z.Text,
    surface = Z.Surface, onSurface = Z.Text,
    surfaceVariant = Z.Surface2, onSurfaceVariant = Z.Muted,
    surfaceContainer = Z.Surface, surfaceContainerHigh = Z.Surface2, surfaceContainerHighest = Z.Surface3,
    surfaceContainerLow = Z.Surface, surfaceContainerLowest = Z.Bg,
    outline = Z.Line, outlineVariant = Z.Line,
    error = Z.Red, onError = Z.OnAccent,
    inverseSurface = Z.Text, inverseOnSurface = Z.Bg,
)

@Composable
fun ZTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, typography = type, content = content)
