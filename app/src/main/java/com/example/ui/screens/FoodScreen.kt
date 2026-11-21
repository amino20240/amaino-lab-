package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.ui.components.MacroProgressBar
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.*

@Composable
fun FoodScreen(
    strings: AppStrings,
    profile: UserProfile,
    meals: List<MealLog>,
    bodyLogs: List<BodyLog>,
    selectedDayIdx: Int,
    mealScanState: UiState<ScannedMeal>?,
    onOpenScanner: (String) -> Unit,
    onScanMealBitmap: (Bitmap) -> Unit,
    onClearMealScan: () -> Unit,
    onToggleSyncNutr: () -> Unit,
    onSyncAdaptiveTdee: (Int) -> Unit,
    onAutoBuildMeals: () -> Unit,
    onApplyBudgetPlan: (BudgetMealPlan) -> Unit,
    onAddMeal: (String, Int, Int, Int, Int, String) -> Unit,
    onDeleteMeal: (String) -> Unit,
    onAdjustTarget: () -> Unit
) {
    val context = LocalContext.current
    val isAr = strings.isAr
    val split = FitnessCatalog.splitById(profile.splitId)
    val safeDayIdx = selectedDayIdx.coerceIn(0, split.days.lastIndex)
    val dayIsHeavy = split.days[safeDayIdx].heavy
    val (effMacros, syncNote) = FitnessCatalog.effectiveMacros(profile, dayIsHeavy)

    val todayIso = FitnessCatalog.todayIso()
    val todayMeals = remember(meals, todayIso) { meals.filter { it.dateISO == todayIso } }
    val sumCal = todayMeals.sumOf { it.cal }
    val sumPro = todayMeals.sumOf { it.protein }
    val sumCarb = todayMeals.sumOf { it.carbs }
    val sumFat = todayMeals.sumOf { it.fat }
    val adaptiveTdee = remember(bodyLogs, meals) { FitnessCatalog.calculateAdaptiveTdee(bodyLogs, meals) }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            decodeBitmapFromUri(context, uri)?.let(onScanMealBitmap)
        }
    }

    // Budget Optimizer state
    var budgetInput by remember { mutableStateOf("120") }
    var currency by remember { mutableStateOf("EGP") }
    var budgetPlan by remember { mutableStateOf<BudgetMealPlan?>(null) }

    // Custom meal inputs
    var mealName by remember { mutableStateOf("") }
    var mealCal by remember { mutableStateOf("") }
    var mealPro by remember { mutableStateOf("") }
    var mealCarb by remember { mutableStateOf("") }
    var mealFat by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(strings.foodTitle, style = MaterialTheme.typography.headlineMedium)
                Text(strings.foodSub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // AI MEAL PHOTO SCANNER CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, IndigoBright.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = IndigoPrimary.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = IndigoBright)
                        Text(strings.scanMeal, style = MaterialTheme.typography.titleMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { onOpenScanner("meal") },
                            modifier = Modifier.weight(1f).testTag("scan_meal_camera_button")
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.modeMealPhoto, maxLines = 1)
                        }
                        Button(
                            onClick = { onOpenScanner("label") },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldOk),
                            modifier = Modifier.weight(1f).testTag("scan_label_camera_button")
                        ) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.modeFoodLabel, maxLines = 1)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.fillMaxWidth().testTag("scan_meal_gallery_button")
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isAr) "اختيار صورة من المعرض" else "Pick Photo from Gallery")
                    }

                    when (mealScanState) {
                        is UiState.Loading -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(if (isAr) "جاري تحليل الطبق عبر Gemini Vision..." else "Analyzing meal with Gemini Vision...")
                            }
                        }
                        is UiState.Error -> {
                            Text(mealScanState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        }
                        is UiState.Success -> {
                            val scanned = mealScanState.data
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(scanned.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${scanned.cal} kcal · P ${scanned.protein}g · C ${scanned.carbs}g · F ${scanned.fat}g",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = EmeraldOk
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                onAddMeal("📸 ${scanned.name}", scanned.cal, scanned.protein, scanned.carbs, scanned.fat, "ai_cam")
                                                onClearMealScan()
                                            },
                                            modifier = Modifier.testTag("confirm_scanned_meal_button")
                                        ) {
                                            Text(if (isAr) "إضافة لوجبات اليوم" else "Log Meal")
                                        }
                                        TextButton(onClick = onClearMealScan) {
                                            Text(if (isAr) "تجاهل" else "Dismiss")
                                        }
                                    }
                                }
                            }
                        }
                        null -> Unit
                    }
                }
            }
        }

        // TRAINING-NUTRITION SYNC + ADAPTIVE TDEE
        item {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (isAr) "🔗 مزامنة التمرين والتغذية" else "🔗 Training–Nutrition Sync", style = MaterialTheme.typography.titleMedium)
                            Text(syncNote, style = MaterialTheme.typography.bodyMedium, color = CyanAccent)
                        }
                        Switch(
                            checked = profile.syncNutr,
                            onCheckedChange = { onToggleSyncNutr() },
                            modifier = Modifier.testTag("sync_nutrition_switch")
                        )
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🧠 Adaptive TDEE", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (adaptiveTdee != null) {
                                    if (isAr) "حرقك الحقيقي المحسوب: $adaptiveTdee سعرة/يوم" else "True measured burn: $adaptiveTdee kcal/day"
                                } else {
                                    if (isAr) "سجّل وزنك وسعراتك لـ 3+ أيام لحساب الحرق الحقيقي." else "Log weight & meals for 3+ days to unlock Adaptive TDEE."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (adaptiveTdee != null) {
                            FilledTonalButton(onClick = { onSyncAdaptiveTdee(adaptiveTdee) }) {
                                Text(if (isAr) "مزامنة" else "Sync")
                            }
                        }
                    }
                }
            }
        }

        // DAILY TARGETS & MACROS + AUTO BUILD
        item {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(strings.dailyTarget, style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = onAdjustTarget) { Text(strings.adjustTarget) }
                            FilledTonalButton(onClick = onAutoBuildMeals, modifier = Modifier.testTag("auto_build_meals_button")) {
                                Text(strings.autoMeals)
                            }
                        }
                    }
                    MacroProgressBar(strings.cal, sumCal, effMacros.kcal, " kcal", IndigoBright)
                    MacroProgressBar(strings.pro, sumPro, effMacros.protein, "g", EmeraldOk)
                    MacroProgressBar(strings.carb, sumCarb, effMacros.carbs, "g", CyanAccent)
                    MacroProgressBar(strings.fat, sumFat, effMacros.fat, "g", AmberWarn)
                }
            }
        }

        // BUDGET MEAL OPTIMIZER
        item {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.budgetTitle, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = budgetInput,
                            onValueChange = { budgetInput = it },
                            label = { Text(if (isAr) "الميزانية" else "Daily Budget") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val b = budgetInput.toDoubleOrNull() ?: 120.0
                                budgetPlan = FitnessCatalog.optimizeBudgetMeals(b, currency, effMacros.kcal)
                            },
                            modifier = Modifier.testTag("run_budget_optimizer_button")
                        ) {
                            Text(strings.runBudget)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("EGP", "SAR", "AED", "USD").forEach { cur ->
                            FilterChip(selected = currency == cur, onClick = { currency = cur }, label = { Text(cur) })
                        }
                    }
                    budgetPlan?.let { plan ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "${plan.totalCostDisplay} → ${plan.totalCal} kcal · ${plan.totalProtein}g Protein",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = EmeraldOk
                                )
                                plan.items.forEach { item ->
                                    Text("• ${if (isAr) item.ar else item.en} (${item.protein}g P)", style = MaterialTheme.typography.bodyMedium)
                                }
                                Button(
                                    onClick = {
                                        onApplyBudgetPlan(plan)
                                        budgetPlan = null
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("apply_budget_plan_button")
                                ) {
                                    Text(if (isAr) "تسجيل هذه الوجبات لليوم" else "Log These Meals")
                                }
                            }
                        }
                    }
                }
            }
        }

        // QUICK ADD PRESETS
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.quickAdd, style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FitnessCatalog.foodPresets.forEachIndexed { idx, p ->
                        AssistChip(
                            onClick = {
                                onAddMeal(if (isAr) p.ar else p.en, p.cal, p.protein, p.carbs, p.fat, "preset")
                            },
                            label = { Text("${if (isAr) p.ar else p.en} (${p.protein}g P)") },
                            modifier = Modifier.testTag("food_preset_chip_$idx")
                        )
                    }
                }
            }
        }

        // CUSTOM MEAL ENTRY
        item {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.customFood, style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = mealName,
                        onValueChange = { mealName = it },
                        label = { Text(strings.mealNamePh) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = mealCal, onValueChange = { mealCal = it }, label = { Text("Kcal") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = mealPro, onValueChange = { mealPro = it }, label = { Text("P(g)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = mealCarb, onValueChange = { mealCarb = it }, label = { Text("C(g)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = mealFat, onValueChange = { mealFat = it }, label = { Text("F(g)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Button(
                        onClick = {
                            if (mealName.isNotBlank()) {
                                onAddMeal(
                                    mealName,
                                    mealCal.toIntOrNull() ?: 0,
                                    mealPro.toIntOrNull() ?: 0,
                                    mealCarb.toIntOrNull() ?: 0,
                                    mealFat.toIntOrNull() ?: 0,
                                    "manual"
                                )
                                mealName = ""; mealCal = ""; mealPro = ""; mealCarb = ""; mealFat = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("add_custom_meal_button")
                    ) {
                        Text(strings.addMealBtn)
                    }
                }
            }
        }

        // TODAY'S LOGGED MEALS
        item {
            Text(strings.todayMeals, style = MaterialTheme.typography.titleMedium)
        }
        if (todayMeals.isEmpty()) {
            item {
                Text(
                    if (isAr) "لم يتم تسجيل وجبات اليوم بعد." else "No meals logged today yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(todayMeals, key = { it.id }) { m ->
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "${m.cal} kcal · P ${m.protein}g · C ${m.carbs}g · F ${m.fat}g",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onDeleteMeal(m.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
            decoder.isMutableRequired = true
        }
    } else {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }
}.getOrNull()
