package com.example.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.*
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.*
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
fun PlanScreen(
    strings: AppStrings,
    profile: UserProfile,
    selectedDayIdx: Int,
    daySlots: List<SplitExerciseSlot>,
    swappedIds: Map<String, String>,
    setStates: Map<String, SetEntryState>,
    bests: List<ExerciseBest>,
    sessionElapsedSec: Int,
    restDurationSec: Int,
    tempoActive: Boolean,
    tempoPhaseMs: Long,
    tempoRepCount: Int,
    effectiveSetsProvider: (SplitExerciseSlot, String) -> Int,
    onSelectSplit: (String) -> Unit,
    onSelectEquip: (String) -> Unit,
    onSelectInjury: (String) -> Unit,
    onToggleTimeCrunch: () -> Unit,
    onSelectDay: (Int) -> Unit,
    onSwapExercise: (String, String) -> Unit,
    onUpdateSetWeight: (String, String) -> Unit,
    onUpdateSetReps: (String, String) -> Unit,
    onToggleSetDone: (String, Boolean) -> Unit,
    onAutoFill: () -> Unit,
    onToggleTempo: () -> Unit,
    onSetRestDuration: (Int) -> Unit,
    onAddCustomExercise: (String, Int, String) -> Unit,
    onDeleteCustomExercise: (String) -> Unit,
    onFinishWorkout: (Int, String) -> Unit
) {
    val isAr = strings.isAr
    var subTab by remember { mutableStateOf("routine") }
    var showFormCam by remember { mutableStateOf(false) }
    var showPlateCalc by remember { mutableStateOf(false) }
    var showAddExDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var muscleFilter by remember { mutableStateOf("all") }
    var selectedRir by remember { mutableIntStateOf(2) }
    var selectedPump by remember { mutableStateOf("good") }

    val split = FitnessCatalog.splitById(profile.splitId)
    val safeDayIdx = selectedDayIdx.coerceIn(0, split.days.lastIndex)
    val bestsMap = remember(bests) { bests.associateBy { it.exerciseId } }

    // Tempo audio cue on phase boundary
    val tempoPhaseIdx = when {
        tempoPhaseMs < 3000L -> 0
        tempoPhaseMs < 4000L -> 1
        else -> 2
    }
    var lastBeepPhase by remember { mutableIntStateOf(-1) }
    LaunchedEffect(tempoActive, tempoPhaseIdx, tempoRepCount) {
        if (tempoActive && lastBeepPhase != tempoPhaseIdx) {
            lastBeepPhase = tempoPhaseIdx
            runCatching {
                val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 45)
                tg.startTone(if (tempoPhaseIdx == 2) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP, 90)
            }
        }
    }

    val liveVolume = remember(setStates, daySlots, safeDayIdx, profile.splitId, swappedIds, profile.equip, profile.injury, bestsMap) {
        var total = 0.0
        daySlots.forEachIndexed { idx, slot ->
            val slotKey = "${profile.splitId}-$safeDayIdx-$idx"
            val effId = swappedIds[slotKey] ?: slot.id
            val resolved = FitnessCatalog.resolveExercise(slot.copy(id = effId), profile.equip, profile.injury)
            val setsN = effectiveSetsProvider(slot, resolved.info.id)
            for (s in 0 until setsN) {
                val st = setStates["$slotKey-$s"]
                if (st?.completed == true) {
                    val w = st.weight.toDoubleOrNull() ?: 0.0
                    val r = st.reps.toIntOrNull() ?: 0
                    total += w * r
                }
            }
        }
        total.roundToInt()
    }

    if (showFormCam) {
        LiveFormCamDialog(strings = strings, onDismiss = { showFormCam = false })
    }
    if (showPlateCalc) {
        PlateCalculatorDialog(strings = strings, onDismiss = { showPlateCalc = false })
    }
    if (showAddExDialog) {
        AddExerciseDialog(
            strings = strings,
            onAdd = { name, sets, reps ->
                onAddCustomExercise(name, sets, reps)
                showAddExDialog = false
            },
            onDismiss = { showAddExDialog = false }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(strings.planTitle, style = MaterialTheme.typography.headlineMedium)
                Text(strings.planSub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = subTab == "routine",
                    onClick = { subTab = "routine" },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    modifier = Modifier.testTag("plan_tab_routine")
                ) { Text(strings.tabRoutine) }
                SegmentedButton(
                    selected = subTab == "library",
                    onClick = { subTab = "library" },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    modifier = Modifier.testTag("plan_tab_library")
                ) { Text(strings.tabLibrary) }
            }
        }

        if (subTab == "library") {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(strings.libSearch) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("library_search_input")
                )
            }
            item {
                val muscles = listOf("all", "chest", "back", "legs", "shoulders", "arms", "core")
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    muscles.forEach { m ->
                        FilterChip(
                            selected = muscleFilter == m,
                            onClick = { muscleFilter = m },
                            label = { Text(m.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
            val filtered = FitnessCatalog.exercises.filter { ex ->
                (muscleFilter == "all" || ex.muscle == muscleFilter) &&
                    (searchQuery.isBlank() ||
                        ex.ar.contains(searchQuery, ignoreCase = true) ||
                        ex.en.contains(searchQuery, ignoreCase = true))
            }
            items(filtered, key = { it.id }) { ex ->
                Card(
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (isAr) ex.ar else ex.en, style = MaterialTheme.typography.titleMedium)
                            AssistChip(onClick = {}, label = { Text("${ex.muscle} · ${ex.equip}") })
                        }
                        Text(
                            if (isAr) ex.tipAr else ex.tipEn,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // ROUTINE CONTROLS
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val targetRirLabel = if (profile.mesoWeek >= 8) "Deload" else "${(4 - ceil(profile.mesoWeek / 2.0).toInt()).coerceAtLeast(0)} RIR"
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(strings.chooseSplit, style = MaterialTheme.typography.labelLarge)
                            Text(
                                if (isAr) "دورة ${profile.mesoWeek}/8 ($targetRirLabel)" else "Meso ${profile.mesoWeek}/8 ($targetRirLabel)",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldOk,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FitnessCatalog.splits.forEach { sp ->
                                FilterChip(
                                    selected = profile.splitId == sp.id,
                                    onClick = { onSelectSplit(sp.id) },
                                    label = { Text(if (isAr) sp.ar else sp.en) },
                                    modifier = Modifier.testTag("split_chip_${sp.id}")
                                )
                            }
                        }

                        Text(strings.equipLbl, style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf("all" to strings.eqAll, "db" to strings.eqDb, "bw" to strings.eqBw).forEach { (k, lbl) ->
                                FilterChip(
                                    selected = profile.equip == k,
                                    onClick = { onSelectEquip(k) },
                                    label = { Text(lbl, maxLines = 1) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Text(strings.injuryLbl, style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("none" to strings.injNone, "shoulder" to strings.injShoulder, "back" to strings.injBack, "knee" to strings.injKnee).forEach { (k, lbl) ->
                                FilterChip(
                                    selected = profile.injury == k,
                                    onClick = { onSelectInjury(k) },
                                    label = { Text(lbl) },
                                    modifier = Modifier.testTag("injury_chip_$k")
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = onToggleTimeCrunch,
                            modifier = Modifier.fillMaxWidth().testTag("time_crunch_toggle_button"),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (profile.timeCrunch) OrangeAccent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (profile.timeCrunch) strings.timeCrunchOn else strings.timeCrunchOff)
                        }
                    }
                }
            }

            // TOOL BUTTONS + TEMPO PACER
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(onClick = { showFormCam = true }, modifier = Modifier.testTag("open_form_cam_button")) {
                        Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.formCam)
                    }
                    FilledTonalButton(onClick = onAutoFill, modifier = Modifier.testTag("auto_fill_weights_button")) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.autoFill)
                    }
                    FilledTonalButton(onClick = { showPlateCalc = true }, modifier = Modifier.testTag("open_plates_button")) {
                        Icon(Icons.Filled.FitnessCenter, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.plates)
                    }
                    FilledTonalButton(
                        onClick = onToggleTempo,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (tempoActive) EmeraldOk.copy(alpha = 0.24f) else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.testTag("toggle_tempo_button")
                    ) {
                        Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.tempo)
                    }
                }
            }

            if (tempoActive) {
                item {
                    val phaseLabel = when (tempoPhaseIdx) {
                        0 -> if (isAr) "⬇️ نزول بطيء (3 ثوانٍ)" else "⬇️ Eccentric Lower (3s)"
                        1 -> if (isAr) "⏸️ ثبات في الأسفل (1 ثانية)" else "⏸️ Bottom Pause (1s)"
                        else -> if (isAr) "⬆️ دفع انفجاري (1 ثانية)" else "⬆️ Concentric Drive (1s)"
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().border(1.dp, EmeraldOk, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = EmeraldOk.copy(alpha = 0.14f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(phaseLabel, style = MaterialTheme.typography.titleMedium, color = EmeraldOk)
                            Text(if (isAr) "عدّة #$tempoRepCount" else "Rep #$tempoRepCount", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // DAY TABS
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    split.days.forEachIndexed { idx, d ->
                        FilterChip(
                            selected = idx == safeDayIdx,
                            onClick = { onSelectDay(idx) },
                            label = { Text(if (isAr) d.ar else d.en) },
                            modifier = Modifier.testTag("day_tab_$idx")
                        )
                    }
                }
            }

            // EXERCISES
            itemsIndexed(daySlots, key = { idx, sl -> "${profile.splitId}-$safeDayIdx-$idx-${sl.id}" }) { idx, slot ->
                val slotKey = "${profile.splitId}-$safeDayIdx-$idx"
                val effectiveId = swappedIds[slotKey] ?: slot.id
                val resolved = FitnessCatalog.resolveExercise(slot.copy(id = effectiveId), profile.equip, profile.injury)
                val info = resolved.info
                val setsN = effectiveSetsProvider(slot, info.id)
                val best = bestsMap[info.id]
                val ssLabel = if (profile.timeCrunch) "SS A${(idx / 2) + 1}" else null

                Card(
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (ssLabel != null) {
                                        Box(
                                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(OrangeAccent.copy(alpha = 0.22f)).padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(ssLabel, style = MaterialTheme.typography.labelSmall, color = OrangeAccent)
                                        }
                                    }
                                    Text(
                                        slot.customName ?: (if (isAr) info.ar else info.en),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("$setsN x ${slot.reps}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (resolved.pivoted) {
                                        Text(if (isAr) "🛡️ بديل آمن" else "🛡️ Safe Pivot", style = MaterialTheme.typography.labelSmall, color = EmeraldOk)
                                    }
                                    if (best?.suggestOverload == true && !profile.deloadToday) {
                                        Text("⚡ +2.5kg", style = MaterialTheme.typography.labelSmall, color = CyanAccent)
                                    }
                                }
                                if (best != null) {
                                    Text(
                                        "${strings.prevPerf} ${best.bestWeight} ${strings.kg} x ${best.bestReps}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = IndigoBright
                                    )
                                }
                            }
                            Row {
                                if (info.alt.isNotEmpty() && slot.customDocId == null) {
                                    TextButton(
                                        onClick = { onSwapExercise(slotKey, info.id) },
                                        modifier = Modifier.testTag("swap_ex_button_$idx")
                                    ) {
                                        Icon(Icons.Filled.SwapHoriz, contentDescription = strings.swap, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(2.dp))
                                        Text(strings.swap)
                                    }
                                }
                                if (slot.customDocId != null) {
                                    IconButton(onClick = { onDeleteCustomExercise(slot.customDocId) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }

                        Text(
                            if (isAr) info.tipAr else info.tipEn,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        for (s in 0 until setsN) {
                            val setKey = "$slotKey-$s"
                            val st = setStates[setKey] ?: SetEntryState()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "${strings.setsLbl} ${s + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.width(56.dp)
                                )
                                OutlinedTextField(
                                    value = st.weight,
                                    onValueChange = { onUpdateSetWeight(setKey, it) },
                                    label = { Text(strings.kg) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = st.reps,
                                    onValueChange = { onUpdateSetReps(setKey, it) },
                                    label = { Text(strings.reps) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Checkbox(
                                    checked = st.completed,
                                    onCheckedChange = { onToggleSetDone(setKey, it) },
                                    modifier = Modifier.minimumInteractiveComponentSize().testTag("set_checkbox_${idx}_$s")
                                )
                            }
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { showAddExDialog = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_custom_exercise_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(strings.addExercise)
                }
            }

            // AUTOREGULATION & FINISH
            item {
                val mins = sessionElapsedSec / 60
                val secs = sessionElapsedSec % 60
                Card(
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                if (isAr) "⏱️ المدة: %02d:%02d".format(mins, secs) else "⏱️ Elapsed: %02d:%02d".format(mins, secs),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                if (isAr) "الحجم: $liveVolume كجم" else "Volume: $liveVolume kg",
                                style = MaterialTheme.typography.labelLarge,
                                color = IndigoBright
                            )
                        }

                        Text(if (isAr) "مؤقت الراحة (ثانية)" else "Rest Timer (sec)", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf(35, 45, 60, 90, 120).forEach { sec ->
                                FilterChip(
                                    selected = restDurationSec == sec,
                                    onClick = { onSetRestDuration(sec) },
                                    label = { Text("${sec}s") }
                                )
                            }
                        }

                        Text(strings.rirLbl, style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            (0..3).forEach { r ->
                                FilterChip(
                                    selected = selectedRir == r,
                                    onClick = { selectedRir = r },
                                    label = { Text("$r RIR") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Text(strings.pumpLbl, style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("low" to strings.pumpLow, "good" to strings.pumpGood, "pain" to strings.pumpPain).forEach { (k, lbl) ->
                                FilterChip(
                                    selected = selectedPump == k,
                                    onClick = { selectedPump = k },
                                    label = { Text(lbl) }
                                )
                            }
                        }

                        Button(
                            onClick = { onFinishWorkout(selectedRir, selectedPump) },
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("finish_workout_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(strings.finishWorkout)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlateCalculatorDialog(strings: AppStrings, onDismiss: () -> Unit) {
    var targetWeightText by remember { mutableStateOf("80") }
    val targetWeight = targetWeightText.toDoubleOrNull() ?: 20.0
    val plates = remember(targetWeight) { FitnessCatalog.calculateBarbellPlates(targetWeight) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.plateTitle, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
                OutlinedTextField(
                    value = targetWeightText,
                    onValueChange = { targetWeightText = it },
                    label = { Text(strings.targetWeightKg) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (strings.isAr) "لكل جانب من البار:" else "Per side of 20kg bar:",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth().height(84.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (plates.isEmpty()) {
                        Text(if (strings.isAr) "بار فارغ فقط (20 كجم)" else "Empty 20kg barbell only")
                    } else {
                        Box(modifier = Modifier.width(18.dp).height(10.dp).background(Color.Gray))
                        plates.forEach { p ->
                            Spacer(Modifier.width(3.dp))
                            Box(
                                modifier = Modifier
                                    .width((p.widthDp + 8).dp)
                                    .height(p.heightDp.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(p.colorHex)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (p.weightKg % 1.0 == 0.0) p.weightKg.toInt().toString() else p.weightKg.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddExerciseDialog(
    strings: AppStrings,
    onAdd: (String, Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var sets by remember { mutableStateOf("3") }
    var reps by remember { mutableStateOf("10-12") }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(strings.addExercise, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(if (strings.isAr) "اسم التمرين" else "Exercise Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sets, onValueChange = { sets = it }, label = { Text(strings.setsLbl) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = reps, onValueChange = { reps = it }, label = { Text(strings.reps) }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text(if (strings.isAr) "إلغاء" else "Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onAdd(name, sets.toIntOrNull() ?: 3, reps) }) { Text(if (strings.isAr) "إضافة" else "Add") }
                }
            }
        }
    }
}
