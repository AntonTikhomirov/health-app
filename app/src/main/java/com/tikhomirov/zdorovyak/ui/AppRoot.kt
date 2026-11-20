package com.tikhomirov.zdorovyak.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tikhomirov.zdorovyak.App
import com.tikhomirov.zdorovyak.ui.theme.Z
import kotlinx.coroutines.launch

val LocalApp = staticCompositionLocalOf<App> { error("no app") }
val LocalSnack = staticCompositionLocalOf<(String) -> Unit> { {} }

enum class Tab(val title: String, val icon: ImageVector) {
    TODAY("Сегодня", Icons.Filled.Today),
    FOOD("Питание", Icons.Filled.Restaurant),
    BODY("Тело", Icons.Filled.MonitorWeight),
    TRAINING("Тренировки", Icons.Filled.FitnessCenter),
    COACH("Коуч", Icons.AutoMirrored.Filled.Chat),
}

/** Действия быстрого ввода, которые экраны подхватывают при открытии. */
enum class Quick { WEIGHT, FOOD_MANUAL, FOOD_BARCODE, FOOD_PHOTO, OTHER_ACTIVITY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(app: App) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var workoutOpen by rememberSaveable { mutableStateOf(false) }
    var quickOpen by remember { mutableStateOf(false) }
    var pendingQuick by remember { mutableStateOf<Quick?>(null) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showSnack: (String) -> Unit = { msg -> scope.launch { snack.showSnackbar(msg) } }

    CompositionLocalProvider(LocalApp provides app, LocalSnack provides showSnack) {
        if (settingsOpen) {
            BackHandler { settingsOpen = false }
            SettingsScreen(onClose = { settingsOpen = false })
            return@CompositionLocalProvider
        }
        if (workoutOpen) {
            BackHandler { workoutOpen = false }
            WorkoutScreen(onClose = { workoutOpen = false; tab = Tab.TRAINING })
            return@CompositionLocalProvider
        }
        Scaffold(
            containerColor = Z.Bg,
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                TopAppBar(
                    title = { Text(if (tab == Tab.TODAY) "Здоровяк" else tab.title, fontWeight = FontWeight.Bold) },
                    actions = { IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Filled.Settings, "Настройки") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Z.Bg),
                )
            },
            floatingActionButton = {
                if (tab != Tab.COACH) FloatingActionButton(onClick = { quickOpen = true }, containerColor = Z.Lime, contentColor = Z.Bg) {
                    Icon(Icons.Filled.Add, "Быстрый ввод")
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Z.Surface) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t, onClick = { tab = t },
                            icon = { Icon(t.icon, t.title) },
                            label = { Text(t.title, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Z.Bg, indicatorColor = Z.Lime,
                                selectedTextColor = Z.Lime, unselectedIconColor = Z.Muted, unselectedTextColor = Z.Muted),
                        )
                    }
                }
            },
        ) { pad ->
            val m = Modifier.padding(pad).fillMaxSize()
            when (tab) {
                Tab.TODAY -> TodayScreen(m, onOpenTab = { tab = it }, onStartWorkout = { workoutOpen = true })
                Tab.FOOD -> NutritionScreen(m, pendingQuick) { pendingQuick = null }
                Tab.BODY -> BodyScreen(m, pendingQuick) { pendingQuick = null }
                Tab.TRAINING -> TrainingScreen(m, pendingQuick, onStartWorkout = { workoutOpen = true }) { pendingQuick = null }
                Tab.COACH -> CoachScreen(m)
            }
        }
        if (quickOpen) {
            ModalBottomSheet(onDismissRequest = { quickOpen = false }, containerColor = Z.Surface) {
                Column(Modifier.fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Быстрый ввод", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    fun go(q: Quick, t: Tab) { pendingQuick = q; tab = t; quickOpen = false }
                    TextButton(onClick = { go(Quick.FOOD_PHOTO, Tab.FOOD) }) { Text("Еда по фото") }
                    TextButton(onClick = { go(Quick.FOOD_BARCODE, Tab.FOOD) }) { Text("Еда по штрихкоду") }
                    TextButton(onClick = { go(Quick.FOOD_MANUAL, Tab.FOOD) }) { Text("Еда вручную") }
                    TextButton(onClick = { go(Quick.WEIGHT, Tab.BODY) }) { Text("Вес") }
                    TextButton(onClick = { go(Quick.OTHER_ACTIVITY, Tab.TRAINING) }) { Text("Прочая активность (работа по дому)") }
                    TextButton(onClick = { quickOpen = false; tab = Tab.COACH }) { Text("Написать коучу") }
                }
            }
        }
    }
}
