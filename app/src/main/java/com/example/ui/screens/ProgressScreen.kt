package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.*
import com.example.ui.components.MacroProgressBar
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.*
import java.io.File
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(
    strings: AppStrings,
    profile: UserProfile,
    workouts: List<WorkoutSession>,
    bodyLogs: List<BodyLog>,
    photos: List<LocalProgressPhoto>,
    onLogWeight: (Double) -> Unit,
    onSaveMeasurements: (Double?, Double?, Double?) -> Unit,
    onAddPhoto: (Uri) -> Unit,
    onDeletePhoto: (String) -> Unit,
    onOpenB2bPortal: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val isAr = strings.isAr
    var chartMetric by remember { mutableStateOf("weight") }
    var weightInput by remember { mutableStateOf("") }
    var chestInput by remember { mutableStateOf("") }
    var waistInput by remember { mutableStateOf("") }
    var armInput by remember { mutableStateOf("") }
    var compareSplit by remember { mutableFloatStateOf(0.5f) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onAddPhoto(uri)
    }

    val chartPoints = remember(chartMetric, bodyLogs, workouts) {
        when (chartMetric) {
            "vol" -> workouts.takeLast(12).map { it.dateISO.takeLast(5) to it.totalVolume.toFloat() }
            "waist" -> bodyLogs.filter { it.waistCm != null }.takeLast(12).map { it.dateISO.takeLast(5) to (it.waistCm?.toFloat() ?: 0f) }
            else -> bodyLogs.takeLast(12).map { it.dateISO.takeLast(5) to it.weightKg.toFloat() }
        }
    }

    val currentMonthPrefix = remember { FitnessCatalog.todayIso().take(7) }
    val monthlyWorkouts = remember(workouts, currentMonthPrefix) { workouts.filter { it.dateISO.startsWith(currentMonthPrefix) } }
    val monthlyVolume = monthlyWorkouts.sumOf { it.totalVolume }.roundToInt()
    val monthlyCount = monthlyWorkouts.size

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(strings.progTitle, style = MaterialTheme.typography.headlineMedium)
        }

        // CHART + WEIGHT LOGGER
        item {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.chartTitle, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("weight" to strings.mWeight, "vol" to strings.mVol, "waist" to strings.mWaist).forEach { (k, lbl) ->
                            FilterChip(
                                selected = chartMetric == k,
                                onClick = { chartMetric = k },
                                label = { Text(lbl, maxLines = 1) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    PerformanceLineChart(points = chartPoints, isAr = isAr)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = weightInput,
                            onValueChange = { weightInput = it },
                            label = { Text(strings.wPh) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("log_weight_input")
                        )
                        Button(
                            onClick = {
                                weightInput.toDoubleOrNull()?.let {
                                    onLogWeight(it)
                                    weightInput = ""
                                }
                            },
                            modifier = Modifier.height(52.dp).testTag("log_weight_button")
                        ) {
                            Text(strings.logBtn)
                        }
                    }
                }
            }
        }

        // MONTHLY CHALLENGES + SHARE
        item {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.chalTitle, style = MaterialTheme.typography.titleMedium)
                    MacroProgressBar(
                        label = if (isAr) "نادي 20,000 كجم الشهري" else "20,000 kg Monthly Volume Club",
                        current = monthlyVolume,
                        target = 20_000,
                        unit = " kg",
                        barColor = IndigoBright
                    )
                    MacroProgressBar(
                        label = if (isAr) "التزام 12 تمرين / شهر" else "12 Workouts / Month",
                        current = monthlyCount,
                        target = 12,
                        unit = "",
                        barColor = EmeraldOk
                    )
                    FilledTonalButton(
                        onClick = {
                            val totalVol = workouts.sumOf { it.totalVolume }.roundToInt()
                            val summary = if (isAr) {
                                "🏆 ملخص أدائي على Amaino Lab\nالاسم: ${profile.name}\nعدد التمارين: ${workouts.size}\nإجمالي الحجم: $totalVol كجم\nأسبوع الدورة: ${profile.mesoWeek}/8\nالسعرات المستهدفة: ${profile.targetKcal} kcal"
                            } else {
                                "🏆 Amaino Lab Performance Card\nAthlete: ${profile.name}\nSessions: ${workouts.size}\nTotal Volume: $totalVol kg\nMesocycle: Week ${profile.mesoWeek}/8\nTarget: ${profile.targetKcal} kcal"
                            }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, summary)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, strings.shareCard))
                        },
                        modifier = Modifier.fillMaxWidth().testTag("share_summary_button")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.shareCard)
                    }
                }
            }
        }

        // PROGRESS PHOTOS + BEFORE/AFTER SLIDER
        item {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(strings.photosTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        FilledTonalButton(
                            onClick = {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.testTag("add_progress_photo_button")
                        ) {
                            Text(strings.addPhoto)
                        }
                    }

                    if (photos.isEmpty()) {
                        Text(
                            if (isAr) "أضف صور تحولك لمتابعة التطور والمقارنة قبل وبعد." else "Add private device photos to compare Before & After.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(photos, key = { it.fileName }) { photo ->
                                Box(modifier = Modifier.size(110.dp).clip(RoundedCornerShape(14.dp))) {
                                    AsyncImage(
                                        model = File(photo.filePath),
                                        contentDescription = "Progress Photo ${photo.dateISO}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    IconButton(
                                        onClick = { onDeletePhoto(photo.fileName) },
                                        modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "Delete Photo", tint = Color.White)
                                    }
                                }
                            }
                        }

                        if (photos.size >= 2) {
                            val before = photos.last()
                            val after = photos.first()
                            Text(
                                if (isAr) "مقارنة قبل (${before.dateISO}) وبعد (${after.dateISO})" else "Before (${before.dateISO}) vs After (${after.dateISO})",
                                style = MaterialTheme.typography.labelLarge
                            )
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            ) {
                                AsyncImage(
                                    model = File(after.filePath),
                                    contentDescription = "After Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(maxWidth * compareSplit)
                                        .clipToBounds()
                                ) {
                                    AsyncImage(
                                        model = File(before.filePath),
                                        contentDescription = "Before Photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.width(this@BoxWithConstraints.maxWidth).fillMaxHeight()
                                    )
                                }
                            }
                            Slider(value = compareSplit, onValueChange = { compareSplit = it })
                        }
                    }
                }
            }
        }

        // BODY MEASUREMENTS
        item {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.measTitle, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = chestInput, onValueChange = { chestInput = it }, label = { Text(strings.chestPh) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = waistInput, onValueChange = { waistInput = it }, label = { Text(strings.waistPh) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = armInput, onValueChange = { armInput = it }, label = { Text(strings.armPh) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Button(
                        onClick = {
                            onSaveMeasurements(chestInput.toDoubleOrNull(), waistInput.toDoubleOrNull(), armInput.toDoubleOrNull())
                            chestInput = ""; waistInput = ""; armInput = ""
                        },
                        modifier = Modifier.fillMaxWidth().testTag("save_measurements_button")
                    ) {
                        Text(strings.saveMeas)
                    }
                }
            }
        }

        // B2B COACH PORTAL + SIGN OUT
        item {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, EmeraldOk.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = EmeraldOk.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.coachB2bTitle, style = MaterialTheme.typography.titleMedium)
                    Text(strings.coachB2bSub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onOpenB2bPortal,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldOk),
                            modifier = Modifier.weight(1f).testTag("open_b2b_portal_button")
                        ) {
                            Text(strings.openCoachB2b)
                        }
                        OutlinedButton(
                            onClick = onSignOut,
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = strings.signOut, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.signOut)
                        }
                    }
                }
            }
        }

        // WORKOUT HISTORY
        item {
            Text(strings.histTitle, style = MaterialTheme.typography.titleMedium)
        }
        if (workouts.isEmpty()) {
            item {
                Text(
                    if (isAr) "لا توجد تمارين محفوظة بعد." else "No saved workouts yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(workouts.takeLast(12).reversed(), key = { it.id }) { w ->
                Card(
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(w.dayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${w.dateISO} · ${w.completedSets} ${strings.setsLbl} · ${w.rir} RIR", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${w.totalVolume.roundToInt()} ${strings.kg}", style = MaterialTheme.typography.titleMedium, color = IndigoBright)
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformanceLineChart(points: List<Pair<String, Float>>, isAr: Boolean) {
    if (points.size < 2) {
        Box(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (isAr) "سجّل قراءتين على الأقل لعرض الرسم البياني." else "Log at least 2 entries to view trend chart.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    val minV = points.minOf { it.second }
    val maxV = points.maxOf { it.second }.let { if (it == minV) it + 1f else it }
    val gridColor = MaterialTheme.colorScheme.outline

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${points.first().first}: ${points.first().second}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${points.last().first}: ${points.last().second}", style = MaterialTheme.typography.labelSmall, color = EmeraldOk, fontWeight = FontWeight.Bold)
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(130.dp)) {
            val w = size.width
            val h = size.height
            val pad = 12f
            for (g in 0..3) {
                val gy = pad + ((h - 2 * pad) * g / 3f)
                drawLine(gridColor, Offset(0f, gy), Offset(w, gy), strokeWidth = 1.dp.toPx())
            }
            val path = Path()
            val coords = points.mapIndexed { idx, (_, v) ->
                val x = pad + (idx.toFloat() / (points.size - 1)) * (w - 2 * pad)
                val yNorm = (v - minV) / (maxV - minV)
                val y = (h - pad) - yNorm * (h - 2 * pad)
                Offset(x, y)
            }
            coords.forEachIndexed { idx, pt ->
                if (idx == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
            }
            drawPath(
                path = path,
                brush = Brush.horizontalGradient(listOf(IndigoBright, CyanAccent, EmeraldOk)),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
            coords.forEach { pt ->
                drawCircle(EmeraldOk, radius = 4.dp.toPx(), center = pt)
                drawCircle(Color.White, radius = 2.dp.toPx(), center = pt)
            }
        }
    }
}
