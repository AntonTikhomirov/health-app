package com.tikhomirov.zdorovyak.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tikhomirov.zdorovyak.nutrition.ActivityBurn
import com.tikhomirov.zdorovyak.ui.theme.Manrope
import com.tikhomirov.zdorovyak.ui.theme.Z

/** Норма дня: базовая из профиля + расход на активность (если включено). */
data class DayTarget(val target: Macros, val burn: ActivityBurn.Day, val base: Macros, val added: Boolean)

@Composable
fun rememberDayTarget(day: Long): DayTarget {
    val app = LocalApp.current
    val profile by app.settings.profile.collectAsState()
    val burnFlow = remember(day) { app.nutrition.dayBurn(day) }
    val burn by burnFlow.collectAsState(initial = ActivityBurn.Day(emptyList()))
    val base = Macros(profile.kcalTarget.toDouble(), profile.proteinTarget.toDouble(), profile.fatTarget.toDouble(), profile.carbsTarget.toDouble())
    val on = profile.activityToTarget && profile.autoTargets
    val add = if (on) burn.kcal else 0
    return DayTarget(base.copy(kcal = base.kcal + add, carbs = base.carbs + add / 4.0), burn, base, on && add > 0)
}

/** Строка «+N ккал за активность» с раскрытием: что именно и откуда цифры. */
@Composable
fun ActivityBurnRow(t: DayTarget) {
    if (!t.added) return
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocalFireDepartment, null, tint = Z.Orange, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("+${t.burn.kcal} ккал за активность", color = Z.Orange, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.weight(1f))
            Text(if (open) "▴" else "▾", color = Z.Muted)
        }
        if (open) {
            t.burn.items.forEach { i ->
                Row(Modifier.fillMaxWidth()) {
                    Muted("${i.title} · ${i.minutes} мин", Modifier.weight(1f))
                    Muted("+${i.kcal} · ${i.source.ru}")
                }
            }
            Muted("База ${t.base.kcal.r0()} ккал + активность = ${t.target.kcal.r0()}. Прибавка идёт в углеводы. " +
                "Считается чистый расход сверх покоя: (MET − 1) × вес × время; данные Garmin важнее оценки.")
        }
    }
}
