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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
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
import com.tikhomirov.zdorovyak.ui.theme.Manrope
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

val LocalApp = staticCompositionLocalOf<App> { error("no app") }
val LocalSnack = staticCompositionLocalOf<(String) -> Unit> { {} }

enum class PhotoMode { CAPTURE, COMPARE }

enum class Tab(val title: String, val icon: ImageVector, val short: String = title) {
    TODAY("Сегодня", Icons.Filled.Today),
    FOOD("Питание", Icons.Filled.Restaurant, "Еда"),
    BODY("Тело", Icons.Filled.Accessibility),
    TRAINING("Тренировки", Icons.Filled.FitnessCenter, "Силовые"),
    COACH("Коуч", Icons.AutoMirrored.Filled.Chat),
}

/** Действия быстрого ввода, которые экраны подхватывают при открытии. */
enum class Quick { WEIGHT, FOOD_MANUAL, FOOD_BARCODE, FOOD_PHOTO, FOOD_PHRASE, OTHER_ACTIVITY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(app: App) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var workoutOpen by rememberSaveable { mutableStateOf(false) }
    var labsOpen by rememberSaveable { mutableStateOf(false) }
    var photoMode by rememberSaveable { mutableStateOf<PhotoMode?>(null) }
    var quickOpen by remember { mutableStateOf(false) }
    var pendingQuick by remember { mutableStateOf<Quick?>(null) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showSnack: (String) -> Unit = { msg -> scope.launch { snack.showSnackbar(msg) } }

    // Авто-нормы КБЖУ: пересчёт при изменении профиля и при новом взвешивании (и раз в неделю — по факту)
    val profileNow by app.settings.profile.collectAsState()
    val weightsNow by app.db.body().weights().collectAsState(initial = null)
    LaunchedEffect(profileNow, weightsNow) {
        if (weightsNow != null) runCatching { app.nutrition.refreshTargets() }
    }

    // Корневой Surface задаёт цвет текста по умолчанию для ВСЕХ экранов, включая полноэкранные (тренировка, настройки,
    // фото). Без него Text вне Scaffold получал чёрный LocalContentColor — «чёрное на чёрном» в режиме тренировки (0.4.0).
    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = Z.Bg, contentColor = Z.Text) {
    CompositionLocalProvider(LocalApp provides app, LocalSnack provides showSnack) {
        if (photoMode != null) {
            BackHandler { photoMode = null }
            when (photoMode) {
                PhotoMode.CAPTURE -> PhotoCaptureScreen(onClose = { photoMode = null })
                else -> PhotoCompareScreen(onClose = { photoMode = null }, onCapture = { photoMode = PhotoMode.CAPTURE })
            }
            return@CompositionLocalProvider
        }
        if (settingsOpen) {
            BackHandler { settingsOpen = false }
            SettingsScreen(onClose = { settingsOpen = false })
            return@CompositionLocalProvider
        }
        if (labsOpen) {
            BackHandler { labsOpen = false }
            Scaffold(containerColor = Z.Bg, snackbarHost = { SnackbarHost(snack) }) { pad ->
                Column(Modifier.padding(pad)) { LabsScreen(onClose = { labsOpen = false }) }
            }
            return@CompositionLocalProvider
        }
        if (workoutOpen) {
            BackHandler { workoutOpen = false }
            WorkoutScreen(onClose = { workoutOpen = false; tab = Tab.TRAINING })
            return@CompositionLocalProvider
        }
        // Фон: тёмный, с едва заметным свечением цвета текущей вкладки в левом верхнем углу
        val glow = when (tab) { Tab.FOOD -> Z.Lime; Tab.TRAINING -> Z.Orange; Tab.BODY -> Z.Carbs; Tab.COACH -> Z.Protein; Tab.TODAY -> Z.Lime }
        Scaffold(
            modifier = Modifier.background(Z.Bg).drawBehind {
                drawRect(Brush.radialGradient(listOf(glow.copy(alpha = 0.07f), Color.Transparent),
                    center = Offset(size.width * 0.1f, 0f), radius = size.width * 1.1f))
            },
            containerColor = Color.Transparent,
            contentColor = Z.Text,   // прозрачный фон не даёт цвета текста по умолчанию — задаём явно
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(if (tab == Tab.TODAY) "Здоровяк" else tab.title, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp, letterSpacing = (-0.3).sp)
                    },
                    actions = { IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Filled.Settings, "Настройки", tint = Z.Muted) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
            bottomBar = { FloatingNav(tab, onTab = { tab = it }, onPlus = { quickOpen = true }) },
        ) { pad ->
            val m = Modifier.padding(pad).fillMaxSize()
            when (tab) {
                Tab.TODAY -> TodayScreen(m, onOpenTab = { tab = it }, onStartWorkout = { workoutOpen = true }, onPhoto = { photoMode = PhotoMode.CAPTURE })
                Tab.FOOD -> NutritionScreen(m, pendingQuick) { pendingQuick = null }
                Tab.BODY -> BodyScreen(m, pendingQuick, onOpenLabs = { labsOpen = true }, onPhotos = { photoMode = it }) { pendingQuick = null }
                Tab.TRAINING -> TrainingScreen(m, pendingQuick, onStartWorkout = { workoutOpen = true }) { pendingQuick = null }
                Tab.COACH -> CoachScreen(m)
            }
        }
        if (quickOpen) {
            ModalBottomSheet(onDismissRequest = { quickOpen = false }, containerColor = Z.Surface) {
                Column(Modifier.fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Быстрый ввод", fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    fun go(q: Quick, t: Tab) { pendingQuick = q; tab = t; quickOpen = false }
                    TextButton(onClick = { go(Quick.FOOD_PHRASE, Tab.FOOD) }) { Text("Еда голосом или фразой") }
                    TextButton(onClick = { go(Quick.FOOD_PHOTO, Tab.FOOD) }) { Text("Еда по фото") }
                    TextButton(onClick = { go(Quick.FOOD_BARCODE, Tab.FOOD) }) { Text("Еда по штрихкоду") }
                    TextButton(onClick = { go(Quick.FOOD_MANUAL, Tab.FOOD) }) { Text("Еда вручную") }
                    TextButton(onClick = { go(Quick.WEIGHT, Tab.BODY) }) { Text("Вес") }
                    TextButton(onClick = { quickOpen = false; photoMode = PhotoMode.CAPTURE }) { Text("Фото прогресса") }
                    TextButton(onClick = { go(Quick.OTHER_ACTIVITY, Tab.TRAINING) }) { Text("Прочая активность (работа по дому)") }
                    TextButton(onClick = { quickOpen = false; tab = Tab.COACH }) { Text("Написать коучу") }
                }
            }
        }
    }
    }
}

/**
 * Плавающая панель навигации (0.5.0): капсула над краем экрана, подпись только у активной вкладки,
 * «+» быстрого ввода — по центру панели. Раньше FAB перекрывал содержимое карточек.
 */
@Composable
private fun FloatingNav(tab: Tab, onTab: (Tab) -> Unit, onPlus: () -> Unit) {
    val left = listOf(Tab.TODAY, Tab.FOOD)
    val right = listOf(Tab.BODY, Tab.TRAINING, Tab.COACH)
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
                .background(Z.Surface2)
                .border(1.dp, Z.Line, androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            left.forEach { NavItem(it, tab == it, Modifier.weight(if (tab == it) 2.2f else 1f)) { onTab(it) } }
            androidx.compose.foundation.layout.Box(
                Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Z.Lime).clickable(onClick = onPlus),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) { Icon(Icons.Filled.Add, "Быстрый ввод", tint = Z.OnAccent) }
            right.forEach { NavItem(it, tab == it, Modifier.weight(if (tab == it) 2.2f else 1f)) { onTab(it) } }
        }
    }
}

@Composable
private fun NavItem(t: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(modifier.height(48.dp).padding(horizontal = 2.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Row(
            Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                .background(if (selected) Z.Lime.copy(alpha = 0.16f) else Color.Transparent)
                .clickable(onClick = onClick)
                .semantics { contentDescription = t.title }
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Icon(t.icon, null, tint = if (selected) Z.Lime else Z.Muted, modifier = Modifier.size(22.dp))
            if (selected) {
                androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                Text(t.short, color = Z.Lime, fontSize = 11.sp, maxLines = 1, softWrap = false, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
