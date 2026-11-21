package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.ui.components.StatTile
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun HomeScreen(
    strings: AppStrings,
    profile: UserProfile,
    workouts: List<WorkoutSession>,
    meals: List<MealLog>,
    bodyLogs: List<BodyLog>,
    bests: List<ExerciseBest>,
    selectedDayIdx: Int,
    setStates: Map<String, SetEntryState>,
    customExercises: List<CustomExercise>,
    onStartWorkout: () -> Unit,
    onEditProfile: () -> Unit,
    onActivateDeload: () -> Unit,
    onSyncTdee: (Int) -> Unit,
    onAutoBuildMeals: () -> Unit,
    onQuickAddProtein: (String, Int, Int, Int, Int) -> Unit,
    onAskCoach: () -> Unit,
    onSetWater: (Int) -> Unit
) {
    val isAr = strings.isAr
    val split = FitnessCatalog.splitById(profile.splitId)
    val safeDayIdx = selectedDayIdx.coerceIn(0, split.days.lastIndex)
    val dayObj = split.days[safeDayIdx]

    val todayIso = FitnessCatalog.todayIso()
    val todayMeals = remember(meals, todayIso) { meals.filter { it.dateISO == todayIso } }
    val (effMacros, _) = FitnessCatalog.effectiveMacros(profile, dayObj.heavy)
    val eatenKcal = todayMeals.sumOf { it.cal }
    val eatenProtein = todayMeals.sumOf { it.protein }
    val kcalLeft = (effMacros.kcal - eatenKcal).coerceAtLeast(0)

    val totalVolumeAllTime = workouts.sumOf { it.totalVolume }
    val rankText = when {
        totalVolumeAllTime >= 100_000 -> if (isAr) "⚡ تيتان النخبة" else "⚡ Apex Titan"
        totalVolumeAllTime >= 40_000 -> if (isAr) "🥇 ذهبي IV" else "🥇 Gold IV"
        totalVolumeAllTime >= 15_000 -> if (isAr) "🥈 فضي III" else "🥈 Silver III"
        totalVolumeAllTime >= 4_000 -> if (isAr) "🥉 برونزي II" else "🥉 Bronze II"
        else -> if (isAr) "🔩 حديد I" else "🔩 Iron I"
    }
    val targetRirLabel = if (profile.mesoWeek >= 8) "Deload" else "${(4 - kotlin.math.ceil(profile.mesoWeek / 2.0).toInt()).coerceAtLeast(0)} RIR"

    val streak = remember(workouts) { computeStreak(workouts) }
    val recoveryMap = remember(workouts) { FitnessCatalog.calculateMuscleRecovery(workouts) }
    val readiness = remember(recoveryMap, streak, profile.waterCups) {
        FitnessCatalog.calculateReadinessScore(recoveryMap, streak, profile.waterCups)
    }
    val adaptiveTdee = remember(bodyLogs, meals) { FitnessCatalog.calculateAdaptiveTdee(bodyLogs, meals) }

    val bestsMap = remember(bests) { bests.associateBy { it.exerciseId } }
    val daySlots = remember(split, safeDayIdx, customExercises) {
        split.days[safeDayIdx].exercises + customExercises
            .filter { it.splitId == profile.splitId && it.dayIndex == safeDayIdx }
            .map { SplitExerciseSlot("cx_${it.id}", it.sets, it.reps, it.name, it.id) }
    }
    var doneSets = 0
    var totalSets = 0
    daySlots.forEachIndexed { idx, slot ->
        val resolved = FitnessCatalog.resolveExercise(slot, profile.equip, profile.injury)
        val mod = bestsMap[resolved.info.id]?.volumeMod ?: 0
        var setsN = kotlin.math.max(2, (if (profile.deloadToday) slot.sets - 1 else slot.sets) + mod)
        if (profile.timeCrunch) setsN = minOf(3, setsN)
        totalSets += setsN
        for (s in 0 until setsN) {
            if (setStates["${profile.splitId}-$safeDayIdx-$idx-$s"]?.completed == true) doneSets++
        }
    }
    val todayPct = if (totalSets > 0) ((doneSets * 100f) / totalSets).roundToInt() else 0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. HERO GRADIENT BANNER
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(IndigoPrimary, VioletAccent, CyanAccent)))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RankChip("$rankText · ${(totalVolumeAllTime / 1000.0).roundToInt()}t")
                        RankChip(
                            if (isAr) "📅 أسبوع ${profile.mesoWeek}/8 ($targetRirLabel)"
                            else "📅 Week ${profile.mesoWeek}/8 ($targetRirLabel)"
                        )
                    }
                    Text(
                        text = strings.welcomeBack(profile.name),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = strings.bannerSub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.92f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onStartWorkout,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = IndigoPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("home_start_workout_button")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.startWorkout)
                        }
                        FilledTonalButton(
                            onClick = onEditProfile,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = 0.22f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("home_edit_profile_button")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.editProfile)
                        }
                    }
                }
            }
        }

        // 2. SMART AUTOMATION CARD
        item {
            val overloadCount = bests.count { it.suggestOverload }
            val proLeft = effMacros.protein - eatenProtein
            val hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, IndigoBright.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = IndigoPrimary.copy(alpha = 0.12f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(EmeraldOk))
                        Text(strings.autoTag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }

                    when {
                        readiness < 70 && !profile.deloadToday -> {
                            Text(
                                if (isAr) "🛡️ الجاهزية اليوم $readiness% بسبب ضغط التمارين. هل تفعّل وضع التخفيف التلقائي (Deload)؟"
                                else "🛡️ Readiness is $readiness%. Activate Smart Auto-Deload today?",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Button(onClick = onActivateDeload, colors = ButtonDefaults.buttonColors(containerColor = EmeraldOk)) {
                                Text(if (isAr) "تفعيل Deload اليوم" else "Activate Deload")
                            }
                        }
                        adaptiveTdee != null && kotlin.math.abs(adaptiveTdee - profile.targetKcal) > 250 -> {
                            Text(
                                if (isAr) "🧠 Adaptive TDEE اكتشف أن حرقك الفعلي $adaptiveTdee سعرة. هل نزامن هدفك؟"
                                else "🧠 Adaptive TDEE measured $adaptiveTdee kcal true burn. Sync target?",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Button(onClick = { onSyncTdee(adaptiveTdee) }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldOk)) {
                                Text(if (isAr) "مزامنة الآن" else "Sync Now")
                            }
                        }
                        todayMeals.isEmpty() -> {
                            Text(
                                if (isAr) "🍽️ لم تسجل وجبات اليوم بعد (${effMacros.kcal} سعرة متزامنة مع تمرينك). ولّد جدول وجبات الآن:"
                                else "🍽️ No meals logged today (${effMacros.kcal} kcal synced). Auto-build meals:",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Button(onClick = onAutoBuildMeals) {
                                Text(if (isAr) "⚡ توليد وجبات اليوم" else "⚡ Auto-Build Meals")
                            }
                        }
                        hr >= 16 && proLeft >= 25 -> {
                            Text(
                                if (isAr) "🥩 باقي لك ${proLeft} جم بروتين اليوم. أضف وجبة سريعة:"
                                else "🥩 You still need ${proLeft}g protein today. Quick-fix:",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = {
                                    onQuickAddProtein(if (isAr) "سكوب واي (أتمتة)" else "Whey Shake (Auto)", 120, 24, 3, 1)
                                }) {
                                    Text(if (isAr) "+ سكوب واي (24g)" else "+ Whey (24g P)")
                                }
                                FilledTonalButton(onClick = {
                                    onQuickAddProtein(if (isAr) "صدر دجاج (أتمتة)" else "Chicken 150g (Auto)", 248, 46, 0, 5)
                                }) {
                                    Text(if (isAr) "+ صدر دجاج (46g)" else "+ Chicken (46g P)")
                                }
                            }
                        }
                        else -> {
                            Text(
                                if (overloadCount > 0) {
                                    if (isAr) "🚀 لديك $overloadCount تمارين جاهزة لزيادة الوزن (+2.5 كجم) في تمرين اليوم!"
                                    else "🚀 $overloadCount exercises ready for +2.5kg auto-overload today!"
                                } else {
                                    if (isAr) "✅ جميع الأنظمة (التمرين، المفاصل، والمزامنة الغذائية) تعمل بتناغم."
                                    else "✅ All systems (Training, Joints & Nutrition Sync) optimal."
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            FilledTonalButton(onClick = onAskCoach) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isAr) "اسأل المدرب الذكي" else "Ask AI Coach")
                            }
                        }
                    }
                }
            }
        }

        // 3. 72H MUSCLE RECOVERY & READINESS HEATMAP
        item {
            val orbColor = when {
                readiness >= 80 -> EmeraldOk
                readiness >= 65 -> AmberWarn
                else -> CoralError
            }
            val badgeText = when {
                readiness >= 80 -> if (isAr) "أداء مثالي" else "Peak State"
                readiness >= 65 -> if (isAr) "تعافي متوسط" else "Moderate"
                else -> if (isAr) "يُنصح بالتخفيف" else "Deload Advised"
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(strings.recoveryTitle, style = MaterialTheme.typography.titleMedium)
                        Text(badgeText, style = MaterialTheme.typography.labelLarge, color = orbColor)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(
                            modifier = Modifier
                                .size(78.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(3.dp, orbColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$readiness%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                                Text(strings.readyLbl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        MuscleSilhouetteCanvas(recoveryMap = recoveryMap, mutedColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        MuscleLegendColumn(recoveryMap = recoveryMap, isAr = isAr, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // 4. 24H BIO-RHYTHM STRENGTH CURVE
        item {
            val hr = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) + it.get(Calendar.MINUTE) / 60f }
            val (bioStatus, bioNote) = when {
                hr in 15f..19.5f -> (if (isAr) "🔥 ذروة القوة العصبية" else "🔥 Peak Strength Window") to
                    (if (isAr) "أعلى حرارة للعضلات الآن — مثالي للأوزان الثقيلة." else "Optimal neural drive right now — ideal for heavy PRs.")
                hr in 9f..15f -> (if (isAr) "⚡ طاقة مستقرة" else "⚡ Steady Metabolic Flow") to
                    (if (isAr) "وقت ممتاز للتمارين المتوسطة وامتصاص الكارب." else "Great window for volume and nutrient partitioning.")
                else -> (if (isAr) "🌙 نافذة الاستشفاء" else "🌙 Recovery & Growth Phase") to
                    (if (isAr) "ركّز على التهدئة، وجبة البروتين، والنوم العميق." else "Focus on wind-down, evening protein, and sleep.")
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(strings.bioTitle, style = MaterialTheme.typography.titleMedium)
                        Text(bioStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    BioRhythmCanvas(currentHour = hr)
                    Text(bioNote, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 5. TODAY'S PROGRESS RING + WEEKLY CONSISTENCY
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                        val trackColor = MaterialTheme.colorScheme.surfaceVariant
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawArc(
                                color = trackColor,
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                brush = Brush.linearGradient(listOf(IndigoPrimary, CyanAccent)),
                                startAngle = -90f,
                                sweepAngle = 360f * (todayPct / 100f),
                                useCenter = false,
                                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$todayPct%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                            Text(strings.todayProg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            (if (isAr) dayObj.ar else dayObj.en) + if (profile.deloadToday) " (Deload)" else "",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("$doneSets / $totalSets ${strings.setsLbl}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onStartWorkout, shape = RoundedCornerShape(12.dp)) {
                            Text(strings.openPlan)
                        }
                    }
                }
            }
        }

        // 6. WEEKLY CONSISTENCY
        item {
            val weekDates = remember { currentWeekIsoDates() }
            val labels = if (isAr) listOf("س", "ح", "ن", "ث", "ر", "خ", "ج") else listOf("Sa", "Su", "Mo", "Tu", "We", "Th", "Fr")
            val workoutDates = remember(workouts) { workouts.map { it.dateISO }.toSet() }
            val doneThisWeek = weekDates.count { it in workoutDates }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(strings.weekAct, style = MaterialTheme.typography.titleMedium)
                        Text("$doneThisWeek / ${split.days.size}", style = MaterialTheme.typography.labelLarge, color = EmeraldOk)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        weekDates.forEachIndexed { idx, d ->
                            val isDone = d in workoutDates
                            val isToday = d == todayIso
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(labels[idx], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(if (isDone) EmeraldOk else MaterialTheme.colorScheme.surfaceVariant)
                                        .then(if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isDone) {
                                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7. 4 STAT TILES
        item {
            val lastVol = workouts.lastOrNull()?.totalVolume?.roundToInt() ?: 0
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile(lastVol.toString(), strings.statVol, Modifier.weight(1f))
                    StatTile(streak.toString(), strings.statStreak, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile(kcalLeft.toString(), strings.statKcal, Modifier.weight(1f))
                    StatTile("${profile.waterCups}/10", strings.statWater, Modifier.weight(1f))
                }
            }
        }

        // 8. HYDRATION TRACKER
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.waterTitle, style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (i in 0 until 10) {
                            val filled = i < profile.waterCups
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                                    .background(
                                        if (filled) Brush.verticalGradient(listOf(IndigoBright, CyanAccent))
                                        else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                                    )
                                    .border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
                                    )
                                    .clickable {
                                        val nextCups = if (profile.waterCups == i + 1) i else i + 1
                                        onSetWater(nextCups)
                                    }
                                    .testTag("water_cup_$i")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.22f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MuscleSilhouetteCanvas(recoveryMap: Map<String, Double>, mutedColor: Color) {
    fun colorFor(hrs: Double): Color = when {
        hrs < 24.0 -> CoralError
        hrs < 48.0 -> AmberWarn
        else -> EmeraldOk
    }
    Canvas(modifier = Modifier.size(width = 66.dp, height = 78.dp)) {
        val sx = size.width / 64f
        val sy = size.height / 76f
        drawCircle(color = mutedColor, radius = 6f * sx, center = Offset(32f * sx, 9f * sy))
        val torsoHrs = minOf(recoveryMap["chest"] ?: 72.0, recoveryMap["back"] ?: 72.0)
        drawRoundRect(colorFor(torsoHrs), Offset(20f * sx, 18f * sy), Size(24f * sx, 14f * sy), CornerRadius(4f * sx))
        drawCircle(colorFor(recoveryMap["shoulders"] ?: 72.0), 5f * sx, Offset(15f * sx, 21f * sy))
        drawCircle(colorFor(recoveryMap["shoulders"] ?: 72.0), 5f * sx, Offset(49f * sx, 21f * sy))
        drawRoundRect(colorFor(recoveryMap["arms"] ?: 72.0), Offset(10f * sx, 27f * sy), Size(6f * sx, 17f * sy), CornerRadius(3f * sx))
        drawRoundRect(colorFor(recoveryMap["arms"] ?: 72.0), Offset(48f * sx, 27f * sy), Size(6f * sx, 17f * sy), CornerRadius(3f * sx))
        drawRoundRect(colorFor(recoveryMap["core"] ?: 72.0), Offset(23f * sx, 34f * sy), Size(18f * sx, 10f * sy), CornerRadius(3f * sx))
        drawRoundRect(colorFor(recoveryMap["legs"] ?: 72.0), Offset(21f * sx, 46f * sy), Size(8f * sx, 24f * sy), CornerRadius(4f * sx))
        drawRoundRect(colorFor(recoveryMap["legs"] ?: 72.0), Offset(35f * sx, 46f * sy), Size(8f * sx, 24f * sy), CornerRadius(4f * sx))
    }
}

@Composable
private fun MuscleLegendColumn(recoveryMap: Map<String, Double>, isAr: Boolean, modifier: Modifier = Modifier) {
    val names = if (isAr) {
        listOf("chest" to "الصدر", "back" to "الظهر", "legs" to "الأرجل", "shoulders" to "الأكتاف", "arms" to "الأذرع", "core" to "البطن")
    } else {
        listOf("chest" to "Chest", "back" to "Back", "legs" to "Legs", "shoulders" to "Shoulders", "arms" to "Arms", "core" to "Core")
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        names.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (key, label) ->
                    val hrs = recoveryMap[key] ?: 72.0
                    val dotColor = when {
                        hrs < 24.0 -> CoralError
                        hrs < 48.0 -> AmberWarn
                        else -> EmeraldOk
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                        Spacer(Modifier.width(5.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun BioRhythmCanvas(currentHour: Float) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        val w = size.width
        val h = size.height
        val path = Path()
        val steps = 48
        for (i in 0..steps) {
            val hr = (i.toFloat() / steps) * 24f
            val x = (hr / 24f) * (w - 16f) + 8f
            val yNorm = 0.53f - 0.24f * sin((hr - 6f) * PI / 12.0).toFloat() - 0.12f * sin((hr - 14f) * PI / 4.0).toFloat()
            val y = (yNorm * h).coerceIn(8f, h - 8f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(listOf(IndigoBright, EmeraldOk, CyanAccent)),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
        val curX = (currentHour / 24f) * (w - 16f) + 8f
        val curYNorm = 0.53f - 0.24f * sin((currentHour - 6f) * PI / 12.0).toFloat() - 0.12f * sin((currentHour - 14f) * PI / 4.0).toFloat()
        val curY = (curYNorm * h).coerceIn(8f, h - 8f)
        drawCircle(EmeraldOk.copy(alpha = 0.35f), radius = 9.dp.toPx(), center = Offset(curX, curY))
        drawCircle(Color.White, radius = 5.dp.toPx(), center = Offset(curX, curY))
        drawCircle(EmeraldOk, radius = 3.dp.toPx(), center = Offset(curX, curY))
    }
}

private fun currentWeekIsoDates(): List<String> {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val cal = Calendar.getInstance()
    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // Sun=1..Sat=7
    val daysSinceSaturday = (dayOfWeek - Calendar.SATURDAY + 7) % 7
    cal.add(Calendar.DAY_OF_YEAR, -daysSinceSaturday)
    return List(7) { idx ->
        val d = fmt.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, 1)
        d
    }
}

fun computeStreak(workouts: List<WorkoutSession>): Int {
    val dates = workouts.map { it.dateISO }.toSet()
    if (dates.isEmpty()) return 0
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val cal = Calendar.getInstance()
    if (fmt.format(cal.time) !in dates) {
        cal.add(Calendar.DAY_OF_YEAR, -1)
    }
    var streak = 0
    while (fmt.format(cal.time) in dates) {
        streak++
        cal.add(Calendar.DAY_OF_YEAR, -1)
    }
    return streak
}
