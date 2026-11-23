package com.tikhomirov.zdorovyak.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tikhomirov.zdorovyak.body.BodyPhotos
import com.tikhomirov.zdorovyak.body.Pose
import com.tikhomirov.zdorovyak.data.BodyPhoto
import com.tikhomirov.zdorovyak.ui.theme.Manrope
import com.tikhomirov.zdorovyak.ui.theme.Z
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private fun shortDate(day: Long) = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("d MMM yyyy", ru))

@Composable
private fun rememberBitmap(path: String?, maxSide: Int = 1400): Bitmap? =
    produceState<Bitmap?>(null, path) { value = path?.let { withContext(Dispatchers.IO) { BodyPhotos.load(it, maxSide) } } }.value

// ---------- Карточка на вкладке «Тело» ----------

@Composable
fun BodyPhotosCard(onOpen: (PhotoMode) -> Unit) {
    val app = LocalApp.current
    val photos by app.db.body().photos().collectAsState(initial = emptyList())
    val profile by app.settings.profile.collectAsState()
    val byDay = photos.groupBy { it.day }
    val last = byDay.keys.maxOrNull()
    ZCard(accent = Z.Protein) {
        CardHeader("Фото прогресса", Icons.Filled.CameraAlt, Z.Protein) {
            if (byDay.size >= 2) TextButton(onClick = { onOpen(PhotoMode.COMPARE) }) { Text("Сравнить") }
        }
        if (last == null) {
            Muted("Весы врут из-за воды, а зеркало — из-за привычки. Три ракурса раз в две недели покажут, что меняется на самом деле.")
        } else {
            val shots = byDay[last].orEmpty().sortedBy { Pose.valueOf(it.pose).ordinal }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shots.forEach { p -> Thumb(p, Modifier.weight(1f)) { onOpen(PhotoMode.COMPARE) } }
                repeat(3 - shots.size) { Spacer(Modifier.weight(1f)) }
            }
            val days = LocalDate.now().toEpochDay() - last
            Muted("Последние: ${dayLabel(last)}${if (days > 1) " · $days дн. назад" else ""} · всего сессий ${byDay.size}")
        }
        val due = BodyPhotos.due(last, profile.photoEveryDays)
        Button(onClick = { onOpen(PhotoMode.CAPTURE) }, Modifier.fillMaxWidth(),
            colors = if (due) ButtonDefaults.buttonColors(containerColor = Z.Protein, contentColor = Z.OnAccent)
            else ButtonDefaults.buttonColors(containerColor = Z.Surface3, contentColor = Z.Text)) {
            Icon(Icons.Filled.CameraAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text(if (last == null) "Сделать первые фото" else if (due) "Пора: сделать фото" else "Сделать фото")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, null, tint = Z.Muted, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp))
            Muted("Только на телефоне: никуда не отправляются")
        }
    }
}

@Composable
private fun Thumb(p: BodyPhoto, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bmp = rememberBitmap(p.filePath, 360)
    Box(modifier.aspectRatio(3f / 4f).clip(RoundedCornerShape(14.dp)).background(Z.Surface3).clickable(onClick = onClick)) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Text(Pose.valueOf(p.pose).ru, Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 2.dp), color = Color.White, fontSize = 11.sp)
    }
}

// ---------- Съёмка ----------

/**
 * Свой экран камеры, а не системный: поверх видоискателя — линии «макушка / стопы / центр», контур-силуэт и
 * полупрозрачный «призрак» прошлого снимка того же ракурса. Совпал с призраком — значит, кадр сравним.
 * Таймер с писком: телефон ставится на полку, человек отходит на 2–3 метра.
 */
@Composable
fun PhotoCaptureScreen(onClose: () -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    var poseIdx by rememberSaveable { mutableIntStateOf(0) }
    var front by rememberSaveable { mutableStateOf(false) }
    var timer by rememberSaveable { mutableIntStateOf(10) }
    var count by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var ghostOn by rememberSaveable { mutableStateOf(true) }
    val pose = Pose.entries[poseIdx.coerceIn(0, 2)]
    val today = LocalDate.now().toEpochDay()
    val photos by app.db.body().photos().collectAsState(initial = emptyList())
    // «Призрак» — последний снимок этого ракурса из прошлых дней
    val ghost = photos.filter { it.pose == pose.name && it.day < today }.maxByOrNull { it.day }
    val ghostBmp = rememberBitmap(if (ghostOn) ghost?.filePath else null, 900)
    val doneToday = photos.filter { it.day == today }.map { it.pose }.toSet()

    val capture = remember { ImageCapture.Builder()
        .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build())
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(granted, front) {
        var provider: ProcessCameraProvider? = null
        if (granted) {
            val f = ProcessCameraProvider.getInstance(ctx)
            f.addListener({
                provider = f.get()
                val preview = Preview.Builder()
                    .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build())
                    .build().also { it.surfaceProvider = previewView.surfaceProvider }
                runCatching {
                    provider?.unbindAll()
                    provider?.bindToLifecycle(lifecycle, if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                }.onFailure { snack("Камера недоступна: ${it.message}") }
            }, ContextCompat.getMainExecutor(ctx))
        }
        onDispose { provider?.unbindAll() }
    }

    fun shoot() {
        if (busy) return
        busy = true
        scope.launch {
            val tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }.getOrNull()
            for (s in timer downTo 1) {
                count = s
                tone?.startTone(if (s <= 3) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP, 120)
                delay(1000)
            }
            count = 0
            val tmp = app.photos.tempFile()
            // Фронталка: сохраняем зеркально, как в видоискателе — тогда «призрак» совпадает с превью
            val opts = ImageCapture.OutputFileOptions.Builder(tmp)
                .setMetadata(ImageCapture.Metadata().apply { isReversedHorizontal = front }).build()
            capture.takePicture(opts, ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                    tone?.startTone(ToneGenerator.TONE_PROP_ACK, 250)
                    scope.launch {
                        app.photos.save(today, pose, tmp)
                        busy = false
                        delay(300); tone?.release()
                        if (poseIdx < 2) poseIdx++ else { snack("Готово: три ракурса сохранены"); onClose() }
                    }
                }
                override fun onError(e: ImageCaptureException) {
                    busy = false; tone?.release()
                    snack("Не снялось: ${e.message}")
                }
            })
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Color.White) }
            Text("${pose.ru} · ${poseIdx + 1} из 3", color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { front = !front }, enabled = !busy) { Icon(Icons.Filled.Cameraswitch, "Сменить камеру", tint = Color.White) }
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Box(Modifier.aspectRatio(3f / 4f).clip(RoundedCornerShape(20.dp))) {
                if (granted) AndroidView({ previewView }, Modifier.fillMaxSize())
                else Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text("Нужен доступ к камере", color = Color.White, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { ask.launch(Manifest.permission.CAMERA) }) { Text("Разрешить") }
                }
                ghostBmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().graphicsLayer { alpha = 0.33f }, contentScale = ContentScale.Crop) }
                PoseGuide(pose, Modifier.fillMaxSize())
                if (count > 0) Text("$count", Modifier.align(Alignment.Center), color = Color.White, fontSize = 120.sp,
                    fontFamily = Manrope, fontWeight = FontWeight.ExtraBold)
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(pose.hint, color = Color.White, fontSize = 14.sp)
            Text(if (ghost != null) "Встаньте так, чтобы совпасть с полупрозрачным снимком от ${shortDate(ghost.day)}." else
                "Телефон — на уровне пупка, вертикально, в 2–3 м. Макушка у верхней линии, стопы — у нижней.",
                color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(0, 5, 10, 15).forEach { t ->
                    FilterChip(selected = timer == t, onClick = { timer = t }, label = { Text(if (t == 0) "Без таймера" else "$t с") },
                        colors = FilterChipDefaults.filterChipColors(labelColor = Color.White, selectedContainerColor = Z.Lime, selectedLabelColor = Z.OnAccent))
                }
                if (ghost != null) FilterChip(selected = ghostOn, onClick = { ghostOn = !ghostOn }, label = { Text("Прошлый снимок") },
                    colors = FilterChipDefaults.filterChipColors(labelColor = Color.White, selectedContainerColor = Z.Lime, selectedLabelColor = Z.OnAccent))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pose.entries.forEachIndexed { i, p ->
                        val done = p.name in doneToday
                        Text(p.ru, Modifier.clip(RoundedCornerShape(50)).background(if (i == poseIdx) Color.White else Color.White.copy(alpha = 0.12f))
                            .clickable(enabled = !busy) { poseIdx = i }.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = if (i == poseIdx) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            textDecoration = null)
                        if (done && i != poseIdx) Text("✓", color = Z.Lime, modifier = Modifier.align(Alignment.CenterVertically))
                    }
                }
                Box(Modifier.size(72.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).padding(8.dp).clip(CircleShape)
                    .background(if (busy) Z.Orange else Color.White).clickable(enabled = granted && !busy) { shoot() },
                    contentAlignment = Alignment.Center) {
                    if (busy && count > 0) Text("$count", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                }
            }
        }
    }
}

/** Гид поверх видоискателя: линии макушки и стоп, центральная ось и упрощённый контур фигуры. */
@Composable
private fun PoseGuide(pose: Pose, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))
        val line = Color.White.copy(alpha = 0.55f)
        val top = h * 0.07f; val bottom = h * 0.95f
        drawLine(line, Offset(w * 0.15f, top), Offset(w * 0.85f, top), 2.dp.toPx(), pathEffect = dash)
        drawLine(line, Offset(w * 0.15f, bottom), Offset(w * 0.85f, bottom), 2.dp.toPx(), pathEffect = dash)
        drawLine(line.copy(alpha = 0.25f), Offset(w / 2, top), Offset(w / 2, bottom), 1.dp.toPx(), pathEffect = dash)
        val body = h * (0.95f - 0.07f)
        val cx = w / 2
        val stroke = Stroke(2.dp.toPx(), pathEffect = dash)
        val c = Color.White.copy(alpha = 0.6f)
        val headR = body * 0.065f
        drawCircle(c, headR, Offset(cx, top + headR), style = stroke)
        val sh = top + body * 0.17f; val hip = top + body * 0.50f
        if (pose == Pose.SIDE) {
            val tw = body * 0.11f
            drawRoundRect(c, Offset(cx - tw, sh), Size(tw * 2, hip - sh), CornerRadius(tw), style = stroke)
            drawRoundRect(c, Offset(cx - tw * 0.6f, hip - tw * 0.3f), Size(tw * 1.2f, bottom - hip + tw * 0.3f), CornerRadius(tw * 0.6f), style = stroke)
        } else {
            val tw = body * 0.17f
            drawRoundRect(c, Offset(cx - tw, sh), Size(tw * 2, hip - sh), CornerRadius(tw * 0.5f), style = stroke)
            val lw = tw * 0.45f
            drawRoundRect(c, Offset(cx - tw * 0.95f, hip - lw * 0.3f), Size(lw * 1.9f, bottom - hip), CornerRadius(lw), style = stroke)
            drawRoundRect(c, Offset(cx + tw * 0.95f - lw * 1.9f, hip - lw * 0.3f), Size(lw * 1.9f, bottom - hip), CornerRadius(lw), style = stroke)
            val aw = tw * 0.3f
            drawRoundRect(c, Offset(cx - tw - aw * 1.6f, sh + aw * 0.2f), Size(aw * 1.4f, hip - sh + aw), CornerRadius(aw), style = stroke)
            drawRoundRect(c, Offset(cx + tw + aw * 0.2f, sh + aw * 0.2f), Size(aw * 1.4f, hip - sh + aw), CornerRadius(aw), style = stroke)
        }
    }
}

// ---------- Сравнение ----------

@Composable
fun PhotoCompareScreen(onClose: () -> Unit, onCapture: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val photos by app.db.body().photos().collectAsState(initial = emptyList())
    val weights by app.db.body().weights().collectAsState(initial = emptyList())
    val measurements by app.db.body().measurements().collectAsState(initial = emptyList())
    var pose by rememberSaveable { mutableStateOf(Pose.FRONT) }
    val days = photos.filter { it.pose == pose.name }.map { it.day }.distinct().sorted()
    var before by rememberSaveable { mutableStateOf<Long?>(null) }
    var after by rememberSaveable { mutableStateOf<Long?>(null) }
    val b = before?.takeIf { it in days } ?: days.firstOrNull()
    val a = after?.takeIf { it in days } ?: days.lastOrNull()
    var confirmDelete by remember { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Text("Фото прогресса", fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onCapture) { Icon(Icons.Filled.CameraAlt, "Сделать фото", tint = Z.Lime) }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pose.entries.forEach { p -> FilterChip(selected = pose == p, onClick = { pose = p }, label = { Text(p.ru) }) }
                }
            }
            if (days.isEmpty()) item { Muted("Фото этого ракурса пока нет.") }
            else {
                item {
                    val pb = photos.firstOrNull { it.day == b && it.pose == pose.name }
                    val pa = photos.firstOrNull { it.day == a && it.pose == pose.name }
                    BeforeAfter(pb, pa)
                }
                item {
                    val wb = b?.let { BodyPhotos.weightNear(it, weights) }; val wa = a?.let { BodyPhotos.weightNear(it, weights) }
                    val tb = b?.let { BodyPhotos.waistNear(it, measurements) }; val ta = a?.let { BodyPhotos.waistNear(it, measurements) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatColumn("До", b, wb, tb, Modifier.weight(1f))
                        StatColumn("После", a, wa, ta, Modifier.weight(1f))
                    }
                    if (b != null && a != null && a > b) {
                        val parts = listOfNotNull(
                            if (wb != null && wa != null) "вес ${sign(wa - wb)} кг" else null,
                            if (tb != null && ta != null) "талия ${sign(ta - tb)} см" else null,
                        )
                        val weeks = (a - b) / 7
                        Text("За ${if (weeks >= 1) "$weeks нед." else "${a - b} дн."}" + if (parts.isNotEmpty()) ": " + parts.joinToString(", ") else "",
                            Modifier.padding(top = 8.dp), fontFamily = Manrope, fontWeight = FontWeight.Bold)
                    }
                }
                item { SectionTitle("До") }
                item { DayChips(days, b) { before = it } }
                item { SectionTitle("После") }
                item { DayChips(days, a) { after = it } }
            }
            item { SectionTitle("Все сессии") }
            val byDay = photos.groupBy { it.day }.toSortedMap(compareByDescending { it })
            items(byDay.entries.toList(), key = { it.key }) { (day, list) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.width(84.dp)) {
                        Text(dayLabel(day), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        BodyPhotos.weightNear(day, weights)?.let { Muted("${it.r1()} кг") }
                    }
                    list.sortedBy { Pose.valueOf(it.pose).ordinal }.forEach { p -> Thumb(p, Modifier.weight(1f)) { pose = Pose.valueOf(p.pose); after = day } }
                    repeat(3 - list.size) { Spacer(Modifier.weight(1f)) }
                    IconButton(onClick = { confirmDelete = day }) { Icon(Icons.Filled.Delete, "Удалить", tint = Z.Muted) }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    confirmDelete?.let { day ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Удалить фото за ${dayLabel(day)}?") },
            text = { Text("Файлы удалятся с телефона без возможности восстановить.") },
            confirmButton = { TextButton(onClick = {
                scope.launch { photos.filter { it.day == day }.forEach { app.photos.delete(it) } }
                confirmDelete = null
            }) { Text("Удалить", color = Z.Red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Отмена") } },
        )
    }
}

private fun sign(v: Double) = (if (v > 0) "+" else if (v < 0) "−" else "±") + kotlin.math.abs(v).r1()

@Composable
private fun StatColumn(label: String, day: Long?, w: Double?, waist: Double?, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Z.Surface2).padding(12.dp)) {
        Text(label.uppercase(), color = Z.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(day?.let { shortDate(it) } ?: "—", fontFamily = Manrope, fontWeight = FontWeight.Bold)
        Muted(listOfNotNull(w?.let { "${it.r1()} кг" }, waist?.let { "талия ${it.r1()}" }).joinToString(" · ").ifEmpty { "нет веса рядом с датой" })
    }
}

@Composable
private fun DayChips(days: List<Long>, selected: Long?, onSelect: (Long) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { d -> FilterChip(selected = d == selected, onClick = { onSelect(d) }, label = { Text(shortDate(d)) }) }
    }
}

/** Сравнение слайдером: «после» целиком, «до» — слева от ручки. Тянуть или тапнуть по фото. */
@Composable
private fun BeforeAfter(before: BodyPhoto?, after: BodyPhoto?) {
    val bb = rememberBitmap(before?.filePath)
    val ab = rememberBitmap(after?.filePath)
    var frac by remember { mutableFloatStateOf(0.5f) }
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(24.dp)).background(Z.Surface2)
        .pointerInput(Unit) {
            detectHorizontalDragGestures { change, _ -> frac = (change.position.x / size.width).coerceIn(0f, 1f) }
        }
        .pointerInput(Unit) { detectTapGestures { frac = (it.x / size.width).coerceIn(0f, 1f) } }) {
        ab?.let { Image(it.asImageBitmap(), "После", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        bb?.let {
            Image(it.asImageBitmap(), "До", Modifier.fillMaxSize().drawWithContent {
                clipRect(right = size.width * frac) { this@drawWithContent.drawContent() }
            }, contentScale = ContentScale.Crop)
        }
        val x = maxWidth * frac
        Box(Modifier.fillMaxHeight().width(3.dp).offset(x = x - 1.5.dp).background(Color.White))
        Box(Modifier.size(36.dp).offset(x = x - 18.dp).align(Alignment.CenterStart).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center) { Icon(Icons.Filled.CompareArrows, null, tint = Color.Black, modifier = Modifier.size(20.dp)) }
        Tag("До", Modifier.align(Alignment.TopStart))
        Tag("После", Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun Tag(t: String, modifier: Modifier) =
    Text(t, modifier.padding(10.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 3.dp),
        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
