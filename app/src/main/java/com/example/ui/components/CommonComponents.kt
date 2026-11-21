package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.CoachClient
import com.example.data.FitnessCatalog
import com.example.data.UserProfile
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.IndigoPrimary

@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MacroProgressBar(
    label: String,
    current: Int,
    target: Int,
    unit: String,
    barColor: Color
) {
    val ratio = if (target > 0) (current.toFloat() / target.toFloat()).coerceIn(0f, 1f) else 0f
    val animatedRatio by animateFloatAsState(targetValue = ratio, animationSpec = tween(450), label = "macro")
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text("$current / $target$unit", style = MaterialTheme.typography.labelLarge, color = barColor)
        }
        Spacer(modifier = Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedRatio)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
fun OnboardingDialog(
    strings: AppStrings,
    initialProfile: UserProfile,
    dismissible: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, Double, Double, String, Double) -> Unit
) {
    var name by remember(initialProfile) { mutableStateOf(initialProfile.name) }
    var sex by remember(initialProfile) { mutableStateOf(initialProfile.sex) }
    var age by remember(initialProfile) { mutableStateOf(initialProfile.age.toString()) }
    var weight by remember(initialProfile) { mutableStateOf(initialProfile.weightKg.toString()) }
    var height by remember(initialProfile) { mutableStateOf(initialProfile.heightCm.toString()) }
    var goal by remember(initialProfile) { mutableStateOf(initialProfile.goal) }
    var act by remember(initialProfile) { mutableStateOf(initialProfile.actMultiplier) }

    Dialog(onDismissRequest = { if (dismissible) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.onbTitle, style = MaterialTheme.typography.titleLarge)
                    if (dismissible) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    }
                }
                Text(strings.onbSub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.lFullName) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_name_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = sex == "m",
                        onClick = { sex = "m" },
                        label = { Text(strings.male) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = sex == "f",
                        onClick = { sex = "f" },
                        label = { Text(strings.female) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = age,
                        onValueChange = { age = it },
                        label = { Text(strings.lAge) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text(strings.lWeight) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it },
                        label = { Text(strings.lHeight) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(strings.lGoal, style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("bulk" to strings.gBulk, "cut" to strings.gCut, "maint" to strings.gMaint).forEach { (k, lbl) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { goal = k }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = goal == k, onClick = { goal = k })
                            Text(lbl, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Text(strings.lAct, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(1.375 to strings.aLow, 1.55 to strings.aMed, 1.725 to strings.aHigh).forEach { (m, lbl) ->
                        FilterChip(
                            selected = kotlin.math.abs(act - m) < 0.05,
                            onClick = { act = m },
                            label = { Text(lbl, maxLines = 1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Button(
                    onClick = {
                        onSave(
                            name,
                            sex,
                            age.toIntOrNull() ?: 25,
                            weight.toDoubleOrNull() ?: 75.0,
                            height.toDoubleOrNull() ?: 175.0,
                            goal,
                            act
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_onboarding_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(strings.saveOnb)
                }
            }
        }
    }
}

@Composable
fun B2bCoachPortalDialog(
    strings: AppStrings,
    clients: List<CoachClient>,
    onAddClient: (String, Int, String) -> Unit,
    onDeleteClient: (String) -> Unit,
    onApplyClient: (CoachClient) -> Unit,
    onDismiss: () -> Unit
) {
    var clName by remember { mutableStateOf("") }
    var clKcal by remember { mutableStateOf("2500") }
    var clSplit by remember { mutableStateOf("ul4") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.School, contentDescription = null, tint = EmeraldOk)
                        Text(strings.coachB2bTitle, style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
                Text(strings.coachB2bSub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = clName,
                        onValueChange = { clName = it },
                        label = { Text(if (strings.isAr) "اسم المتدرب" else "Client Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f)
                    )
                    OutlinedTextField(
                        value = clKcal,
                        onValueChange = { clKcal = it },
                        label = { Text("Kcal") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    FitnessCatalog.splits.take(3).forEach { sp ->
                        FilterChip(
                            selected = clSplit == sp.id,
                            onClick = { clSplit = sp.id },
                            label = { Text(sp.id.uppercase()) }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            if (clName.isNotBlank()) {
                                onAddClient(clName, clKcal.toIntOrNull() ?: 2500, clSplit)
                                clName = ""
                            }
                        },
                        modifier = Modifier.testTag("b2b_add_client_button")
                    ) {
                        Text("+")
                    }
                }

                HorizontalDivider()

                if (clients.isEmpty()) {
                    Text(
                        text = if (strings.isAr) "لا يوجد عملاء مضافين بعد." else "No coaching clients added yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(clients, key = { it.id }) { client ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(client.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${client.targetKcal} kcal · ${client.splitId.uppercase()}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        FilledTonalButton(
                                            onClick = {
                                                onApplyClient(client)
                                                onDismiss()
                                            }
                                        ) {
                                            Text(if (strings.isAr) "تطبيق" else "Load")
                                        }
                                        IconButton(onClick = { onDeleteClient(client.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GradientPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Brush.horizontalGradient(listOf(IndigoPrimary.copy(alpha = 0.25f), CyanAccent.copy(alpha = 0.22f))))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
    }
}
