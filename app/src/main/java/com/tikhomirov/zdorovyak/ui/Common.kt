package com.tikhomirov.zdorovyak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import com.tikhomirov.zdorovyak.ui.theme.Manrope
import com.tikhomirov.zdorovyak.ui.theme.Z
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val ru: Locale = Locale.forLanguageTag("ru")

fun dayLabel(day: Long): String {
    val d = LocalDate.ofEpochDay(day)
    val today = LocalDate.now()
    return when (d) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> d.format(DateTimeFormatter.ofPattern("d MMM", ru))
    }
}

fun today(): Long = LocalDate.now().toEpochDay()

fun Double.r0(): String = Math.round(this).toString()
fun Double.r1(): String = ("%.1f".format(ru, this)).replace(',', '.').removeSuffix(".0")

@Composable
fun ZCard(modifier: Modifier = Modifier, accent: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    // 0.5.0: плоские слои без градиентов и цветных рамок. Карточку отделяет светлота фона и тонкая «волосяная» линия;
    // акцент живёт в иконке заголовка и в цифрах, а не в обводке — так экран спокойнее и современнее.
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Z.Surface)
            .border(1.dp, Z.Line.copy(alpha = 0.55f), shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Заголовок карточки: иконка в цветном кружке, название и что-нибудь справа (кнопка, подпись). */
@Composable
fun CardHeader(title: String, icon: ImageVector?, accent: Color, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(11.dp)).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(title, Modifier.weight(1f), fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, letterSpacing = (-0.2).sp)
        trailing?.invoke()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), modifier.padding(top = 10.dp, bottom = 2.dp), color = Z.Muted, fontSize = 11.sp, fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)

@Composable
fun Big(text: String, color: Color = Z.Text) = Text(text, color = color, fontSize = 34.sp, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
    letterSpacing = (-0.8).sp, style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = com.tikhomirov.zdorovyak.ui.theme.TNUM))

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) = Text(text, modifier, color = Z.Muted, fontSize = 13.sp, lineHeight = 18.sp)

@Composable
fun MacroBar(label: String, value: Double, target: Double, color: Color) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Z.Text, fontSize = 13.sp)
            Text("${value.r0()} / ${target.r0()}", color = Z.Muted, fontSize = 13.sp)
        }
        LinearProgressIndicator(
            progress = { if (target > 0) (value / target).toFloat().coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = if (value > target * 1.1) Z.Red else color,
            trackColor = Z.Surface2,
        )
    }
}

@Composable
fun NumField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, decimal: Boolean = true) =
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.replace(',', '.').filter { it.isDigit() || (decimal && it == '.') }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )

@Composable
fun TextField1(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, single: Boolean = true, password: Boolean = false) =
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = single,
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = if (password) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        modifier = modifier.fillMaxWidth(),
    )

@Composable
fun Pill(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = color, fontSize = 12.sp, fontFamily = Manrope, fontWeight = FontWeight.Bold) }
}

/** Простой линейный график (вес, сон и т.п.). Точки: (x по порядку, y). */
@Composable
fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.min(); val max = values.max()
        val span = (max - min).takeIf { it > 1e-6 } ?: 1.0
        val stepX = size.width / (values.size - 1)
        val path = androidx.compose.ui.graphics.Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = (size.height - ((v - min) / span * size.height)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
        val lx = (values.size - 1) * stepX
        val ly = (size.height - ((values.last() - min) / span * size.height)).toFloat()
        drawCircle(color, 4.dp.toPx(), androidx.compose.ui.geometry.Offset(lx, ly))
    }
}
