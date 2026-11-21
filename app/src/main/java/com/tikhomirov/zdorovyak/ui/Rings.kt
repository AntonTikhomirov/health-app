package com.tikhomirov.zdorovyak.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tikhomirov.zdorovyak.ui.theme.Manrope
import com.tikhomirov.zdorovyak.ui.theme.Z

/** Сколько съедено и сколько нужно по каждому показателю. */
data class Macros(val kcal: Double, val protein: Double, val fat: Double, val carbs: Double)

/** Как раскрашивать перебор: белок сверх нормы — не страшно, калории/жиры/углеводы — предупреждаем. */
enum class Over { FINE, WARN }

/**
 * Кольцо прогресса. Первый круг — до нормы. Перебор рисуется вторым кругом поверх, другим цветом,
 * поэтому 130% видно сразу, а не «просто полное кольцо».
 */
@Composable
fun Ring(progress: Float, color: Color, size: Dp, stroke: Dp, over: Over = Over.WARN, content: @Composable () -> Unit = {}) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(progress) { anim.animateTo(progress.coerceIn(0f, 2f), tween(900, easing = FastOutSlowInEasing)) }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val d = this.size.minDimension - sw
            val tl = Offset((this.size.width - d) / 2, (this.size.height - d) / 2)
            val arc = Size(d, d)
            drawArc(color.copy(alpha = 0.14f), 0f, 360f, false, tl, arc, style = Stroke(sw))
            val p = anim.value
            if (p > 0f) {
                rotate(-90f) {
                    val first = (p.coerceAtMost(1f) * 360f).coerceAtLeast(2f)
                    drawArc(
                        Brush.sweepGradient(listOf(color.copy(alpha = 0.75f), color, color.copy(alpha = 0.75f))),
                        0f, first, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round),
                    )
                    if (p > 1f) {
                        val overColor = if (over == Over.WARN) Z.Red else lerpWhite(color)
                        // тень под вторым кругом, чтобы начало перебора читалось
                        drawArc(Color.Black.copy(alpha = 0.35f), 0f, (p - 1f).coerceAtMost(1f) * 360f + 4f, false, tl, arc,
                            style = Stroke(sw + 2.dp.toPx(), cap = StrokeCap.Round))
                        drawArc(overColor, 0f, (p - 1f).coerceAtMost(1f) * 360f, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
                    }
                }
            }
        }
        content()
    }
}

private fun lerpWhite(c: Color) = androidx.compose.ui.graphics.lerp(c, Color.White, 0.45f)

private fun pct(v: Double, t: Double): Float = if (t > 0) (v / t).toFloat() else 0f

/**
 * Инфографика КБЖУ: большое кольцо калорий с цифрой в центре и три кольца макронутриентов.
 * [compact] — для «Сегодня»: чуть меньше и без подсказок «ещё N г».
 */
@Composable
fun NutritionRings(eaten: Macros, target: Macros, compact: Boolean = false) {
    val big = if (compact) 132.dp else 156.dp
    val left = target.kcal - eaten.kcal
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Ring(pct(eaten.kcal, target.kcal), Z.Kcal, big, if (compact) 13.dp else 15.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(eaten.kcal.r0(), fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = if (compact) 26.sp else 30.sp,
                    letterSpacing = (-0.5).sp)
                Text("из ${target.kcal.r0()} ккал", color = Z.Muted, fontSize = 11.sp)
                Text(
                    if (left >= 0) "осталось ${left.r0()}" else "сверх ${(-left).r0()}",
                    color = if (left >= 0) Z.Kcal else Z.Red, fontSize = 11.sp, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(if (compact) 14.dp else 18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)) {
            MacroRow("Белок", eaten.protein, target.protein, Z.Protein, Over.FINE, compact)
            MacroRow("Жиры", eaten.fat, target.fat, Z.Fat, Over.WARN, compact)
            MacroRow("Углеводы", eaten.carbs, target.carbs, Z.Carbs, Over.WARN, compact)
        }
    }
}

@Composable
private fun MacroRow(label: String, v: Double, t: Double, color: Color, over: Over, compact: Boolean) {
    val p = pct(v, t)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Ring(p, color, if (compact) 36.dp else 42.dp, if (compact) 5.dp else 6.dp, over) {
            Text("${(p * 100).toInt().coerceAtMost(999)}", fontSize = if (compact) 10.sp else 11.sp, fontFamily = Manrope,
                fontWeight = FontWeight.ExtraBold, color = if (p > 1.1f && over == Over.WARN) Z.Red else Z.Text)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(label, color = Z.Muted, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(v.r0(), fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = color)
                Text(" / ${t.r0()} г", color = Z.Muted, fontSize = 12.sp)
            }
            if (!compact) {
                val rest = t - v
                Text(
                    when {
                        rest > 0.5 -> "ещё ${rest.r0()} г"
                        rest > -t * 0.1 -> "норма"
                        else -> "+${(-rest).r0()} г сверх"
                    },
                    color = if (rest < -t * 0.1 && over == Over.WARN) Z.Red else Z.Faint, fontSize = 11.sp,
                )
            }
        }
    }
}

/**
 * Неделя по калориям: столбики по дням против нормы (пунктир). Сегодняшний день — ярче.
 * [days] — 7 пар (подпись дня, ккал), [target] — норма.
 */
@Composable
fun WeekKcalBars(days: List<Pair<String, Double>>, target: Double, modifier: Modifier = Modifier) {
    val max = maxOf(target * 1.25, days.maxOfOrNull { it.second } ?: 0.0, 1.0)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(84.dp)) {
            val n = days.size.coerceAtLeast(1)
            val slot = size.width / n
            val bw = slot * 0.46f
            val ty = size.height - (target / max * size.height).toFloat()
            days.forEachIndexed { i, (_, k) ->
                val h = (k / max * size.height).toFloat()
                val x = i * slot + (slot - bw) / 2
                val last = i == days.lastIndex
                val c = when {
                    k <= 0.0 -> Z.Surface3
                    k > target * 1.1 -> Z.Red
                    k >= target * 0.85 -> Z.Kcal
                    else -> Z.Kcal.copy(alpha = 0.55f)
                }
                drawRoundRect(Z.Surface3, Offset(x, 0f), Size(bw, size.height), androidx.compose.ui.geometry.CornerRadius(bw / 2))
                if (h > 0) drawRoundRect(if (last) c else c.copy(alpha = c.alpha * 0.8f), Offset(x, size.height - h), Size(bw, h),
                    androidx.compose.ui.geometry.CornerRadius(bw / 2))
            }
            // линия нормы
            var x = 0f
            while (x < size.width) {
                drawLine(Z.Text.copy(alpha = 0.45f), Offset(x, ty), Offset((x + 6.dp.toPx()).coerceAtMost(size.width), ty), 1.2.dp.toPx())
                x += 10.dp.toPx()
            }
        }
        Row(Modifier.fillMaxWidth()) {
            days.forEachIndexed { i, (label, _) ->
                Text(label, Modifier.weight(1f), color = if (i == days.lastIndex) Z.Text else Z.Muted, fontSize = 11.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

/** Доля калорий по приёмам пищи — одна сегментированная полоска. */
@Composable
fun MealSplitBar(parts: List<Triple<String, Double, Color>>, modifier: Modifier = Modifier) {
    val total = parts.sumOf { it.second }
    if (total <= 0) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            var x = 0f
            val gap = 3.dp.toPx()
            val usable = size.width - gap * (parts.count { it.second > 0 } - 1).coerceAtLeast(0)
            parts.filter { it.second > 0 }.forEach { (_, v, c) ->
                val w = (v / total * usable).toFloat()
                drawRoundRect(c, Offset(x, 0f), Size(w, size.height), androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                x += w + gap
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            parts.filter { it.second > 0 }.forEach { (l, v, c) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(8.dp)) { drawCircle(c) }
                    Text(" $l ${(v / total * 100).toInt()}%", color = Z.Muted, fontSize = 11.sp)
                }
            }
        }
    }
}
