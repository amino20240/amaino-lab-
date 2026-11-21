package com.example.ui

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "MainViewModel"

class MainViewModel(
    private val repo: FirestoreRepository,
    private val aiRepo: AiRepository,
    private val photoStore: ProgressPhotoStore,
    private val userId: String,
    private val defaultDisplayName: String
) : ViewModel() {

    private fun <T> Flow<T>.toSafeUiState(initial: UiState<T> = UiState.Loading, label: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                Log.w(TAG, "Error observing $label", root)
                emit(UiState.Error(root.localizedMessage ?: "Failed to load $label"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), initial)

    val profileState: StateFlow<UiState<UserProfile?>> =
        repo.observeUserProfile(userId).toSafeUiState(label = "profile")

    val workoutsState: StateFlow<UiState<List<WorkoutSession>>> =
        repo.observeWorkouts(userId).toSafeUiState(label = "workouts")

    val mealsState: StateFlow<UiState<List<MealLog>>> =
        repo.observeMeals(userId).toSafeUiState(label = "meals")

    val bodyLogsState: StateFlow<UiState<List<BodyLog>>> =
        repo.observeBodyLogs(userId).toSafeUiState(label = "bodyLogs")

    val exerciseBestsState: StateFlow<UiState<List<ExerciseBest>>> =
        repo.observeExerciseBests(userId).toSafeUiState(label = "exerciseBests")

    val coachClientsState: StateFlow<UiState<List<CoachClient>>> =
        repo.observeCoachClients(userId).toSafeUiState(label = "coachClients")

    val customExercisesState: StateFlow<UiState<List<CustomExercise>>> =
        repo.observeCustomExercises(userId).toSafeUiState(label = "customExercises")

    private val _selectedDay = MutableStateFlow(0)
    val selectedDay: StateFlow<Int> = _selectedDay.asStateFlow()

    private val _swappedIds = MutableStateFlow<Map<String, String>>(emptyMap())
    val swappedIds: StateFlow<Map<String, String>> = _swappedIds.asStateFlow()

    private val _setStates = MutableStateFlow<Map<String, SetEntryState>>(emptyMap())
    val setStates: StateFlow<Map<String, SetEntryState>> = _setStates.asStateFlow()

    private val _sessionStartMs = MutableStateFlow<Long?>(null)
    private val _sessionElapsedSec = MutableStateFlow(0)
    val sessionElapsedSec: StateFlow<Int> = _sessionElapsedSec.asStateFlow()

    private val _restRemainingSec = MutableStateFlow<Int?>(null)
    val restRemainingSec: StateFlow<Int?> = _restRemainingSec.asStateFlow()
    private val _restDurationSec = MutableStateFlow(60)
    val restDurationSec: StateFlow<Int> = _restDurationSec.asStateFlow()
    private var restTargetMs: Long = 0L
    private var restJob: Job? = null

    private val _tempoActive = MutableStateFlow(false)
    val tempoActive: StateFlow<Boolean> = _tempoActive.asStateFlow()
    private val _tempoPhaseMs = MutableStateFlow(0L)
    val tempoPhaseMs: StateFlow<Long> = _tempoPhaseMs.asStateFlow()
    private val _tempoRepCount = MutableStateFlow(1)
    val tempoRepCount: StateFlow<Int> = _tempoRepCount.asStateFlow()
    private var tempoJob: Job? = null

    private val _progressPhotos = MutableStateFlow<List<LocalProgressPhoto>>(emptyList())
    val progressPhotos: StateFlow<List<LocalProgressPhoto>> = _progressPhotos.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()
    private val _isCoachStreaming = MutableStateFlow(false)
    val isCoachStreaming: StateFlow<Boolean> = _isCoachStreaming.asStateFlow()
    private val _coachError = MutableStateFlow<String?>(null)
    val coachError: StateFlow<String?> = _coachError.asStateFlow()

    private val _mealScanState = MutableStateFlow<UiState<ScannedMeal>?>(null)
    val mealScanState: StateFlow<UiState<ScannedMeal>?> = _mealScanState.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private val _localDarkTheme = MutableStateFlow(true)
    private val _localLang = MutableStateFlow("ar")

    val currentProfile: UserProfile
        get() = (profileState.value as? UiState.Success)?.data ?: UserProfile(
            userId = userId,
            name = defaultDisplayName,
            waterDate = FitnessCatalog.todayIso(),
            darkTheme = _localDarkTheme.value,
            lang = _localLang.value
        )

    init {
        viewModelScope.launch {
            _progressPhotos.value = photoStore.loadPhotos(userId)
        }
        viewModelScope.launch {
            while (true) {
                delay(1000L)
                val start = _sessionStartMs.value
                if (start != null) {
                    _sessionElapsedSec.value = ((System.currentTimeMillis() - start) / 1000L).toInt()
                }
            }
        }
    }

    fun emitMessage(msg: String) {
        _snackbarEvents.tryEmit(msg)
    }

    private fun hasRemoteProfile(): Boolean =
        (profileState.value as? UiState.Success)?.data != null

    fun ensureDefaultProfileIfMissing() {
        if (profileState.value is UiState.Success && !hasRemoteProfile()) {
            // Wait for onboarding save, or keep local defaults until user submits onboarding
        }
    }

    fun toggleTheme() {
        val cur = currentProfile
        val next = !cur.darkTheme
        _localDarkTheme.value = next
        if (hasRemoteProfile()) {
            viewModelScope.launch {
                repo.updateProfileFields(userId, mapOf("darkTheme" to next))
            }
        }
    }

    fun toggleLang() {
        val cur = currentProfile
        val next = if (cur.lang == "ar") "en" else "ar"
        _localLang.value = next
        if (hasRemoteProfile()) {
            viewModelScope.launch {
                repo.updateProfileFields(userId, mapOf("lang" to next))
            }
        }
    }

    fun saveOnboarding(
        name: String,
        sex: String,
        age: Int,
        weightKg: Double,
        heightCm: Double,
        goal: String,
        actMultiplier: Double
    ) {
        val safeName = name.trim()
        if (safeName.isEmpty()) {
            emitMessage(if (currentProfile.lang == "ar") "اكتب اسمك أولاً" else "Please enter your name")
            return
        }
        val macros = FitnessCatalog.calculateMacros(sex, age, weightKg, heightCm, goal, actMultiplier)
        val autoSplit = when {
            actMultiplier <= 1.4 -> "fb3"
            actMultiplier >= 1.7 -> "ppl6"
            else -> "ul4"
        }
        val existing = hasRemoteProfile()
        val base = currentProfile
        val updated = base.copy(
            userId = userId,
            name = safeName,
            sex = sex,
            age = age.coerceIn(13, 100),
            weightKg = weightKg.coerceIn(25.0, 300.0),
            heightCm = heightCm.coerceIn(100.0, 250.0),
            goal = goal,
            actMultiplier = actMultiplier,
            targetKcal = macros.kcal,
            targetProtein = macros.protein,
            targetCarbs = macros.carbs,
            targetFat = macros.fat,
            splitId = if (existing) base.splitId else autoSplit,
            waterDate = FitnessCatalog.todayIso(),
            onboarded = true
        )
        viewModelScope.launch {
            val res = repo.saveUserProfile(userId, updated, isExisting = existing)
            if (res.isSuccess) {
                val logs = (bodyLogsState.value as? UiState.Success)?.data.orEmpty()
                if (logs.isEmpty()) {
                    repo.addBodyLog(userId, BodyLog(userId = userId, dateISO = FitnessCatalog.todayIso(), weightKg = updated.weightKg))
                }
                _selectedDay.value = 0
                emitMessage(if (updated.lang == "ar") "تم حفظ ملفك وتجهيز خطتك!" else "Profile saved & plan ready!")
            } else {
                emitMessage(res.exceptionOrNull()?.localizedMessage ?: "Failed to save profile")
            }
        }
    }

    fun setWaterCups(cups: Int) {
        val cur = currentProfile
        val clamped = cups.coerceIn(0, 10)
        if (!hasRemoteProfile()) return
        viewModelScope.launch {
            repo.updateProfileFields(
                userId,
                mapOf("waterCups" to clamped, "waterDate" to FitnessCatalog.todayIso())
            )
        }
    }

    fun activateDeloadToday() {
        if (!hasRemoteProfile()) return
        viewModelScope.launch {
            val res = repo.updateProfileFields(userId, mapOf("deloadToday" to true))
            if (res.isSuccess) {
                emitMessage(if (currentProfile.lang == "ar") "تم تفعيل التخفيف الآمن اليوم!" else "Smart Deload activated!")
            }
        }
    }

    fun syncAdaptiveTdee(newKcal: Int) {
        if (!hasRemoteProfile()) return
        viewModelScope.launch {
            val res = repo.updateProfileFields(userId, mapOf("targetKcal" to newKcal.coerceIn(1000, 8000)))
            if (res.isSuccess) {
                emitMessage(if (currentProfile.lang == "ar") "تمت مزامنة Adaptive TDEE!" else "Adaptive TDEE synced!")
            }
        }
    }

    fun selectSplit(splitId: String) {
        _selectedDay.value = 0
        if (!hasRemoteProfile()) return
        viewModelScope.launch { repo.updateProfileFields(userId, mapOf("splitId" to splitId)) }
    }

    fun selectEquip(equip: String) {
        if (!hasRemoteProfile()) return
        viewModelScope.launch { repo.updateProfileFields(userId, mapOf("equip" to equip)) }
    }

    fun selectInjury(injury: String) {
        if (!hasRemoteProfile()) return
        viewModelScope.launch {
            val res = repo.updateProfileFields(userId, mapOf("injury" to injury))
            if (res.isSuccess && injury != "none") {
                emitMessage(
                    if (currentProfile.lang == "ar") "🛡️ تم تفعيل بدائل حماية المفصل تلقائياً!"
                    else "🛡️ Joint-safe exercise alternatives activated!"
                )
            }
        }
    }

    fun toggleTimeCrunch() {
        val next = !currentProfile.timeCrunch
        _restDurationSec.value = if (next) 35 else 60
        if (!hasRemoteProfile()) return
        viewModelScope.launch { repo.updateProfileFields(userId, mapOf("timeCrunch" to next)) }
    }

    fun toggleSyncNutr() {
        val next = !currentProfile.syncNutr
        if (!hasRemoteProfile()) return
        viewModelScope.launch { repo.updateProfileFields(userId, mapOf("syncNutr" to next)) }
    }

    fun selectDay(index: Int) {
        _selectedDay.value = index
    }

    fun setRestDuration(sec: Int) {
        _restDurationSec.value = sec
    }

    fun swapExerciseInSlot(slotKey: String, currentExId: String) {
        val info = FitnessCatalog.findExercise(currentExId)
        val altId = info?.alt?.firstOrNull()
        if (altId != null) {
            _swappedIds.value = _swappedIds.value + (slotKey to altId)
            emitMessage(if (currentProfile.lang == "ar") "🔄 تم التبديل لتمرين مكافئ!" else "🔄 Swapped to equivalent exercise!")
        } else {
            emitMessage(if (currentProfile.lang == "ar") "لا يوجد بديل آخر لهذا التمرين." else "No swap available.")
        }
    }

    fun updateSetWeight(key: String, weight: String) {
        val cur = _setStates.value[key] ?: SetEntryState()
        _setStates.value = _setStates.value + (key to cur.copy(weight = weight))
    }

    fun updateSetReps(key: String, reps: String) {
        val cur = _setStates.value[key] ?: SetEntryState()
        _setStates.value = _setStates.value + (key to cur.copy(reps = reps))
    }

    fun toggleSetDone(key: String, checked: Boolean) {
        if (_sessionStartMs.value == null && checked) {
            _sessionStartMs.value = System.currentTimeMillis()
        }
        val cur = _setStates.value[key] ?: SetEntryState()
        _setStates.value = _setStates.value + (key to cur.copy(completed = checked))
        if (checked) startRestTimer()
    }

    fun startRestTimer() {
        restJob?.cancel()
        restTargetMs = System.currentTimeMillis() + _restDurationSec.value * 1000L
        restJob = viewModelScope.launch {
            while (true) {
                val rem = ((restTargetMs - System.currentTimeMillis()) / 1000L).toInt()
                if (rem <= 0) {
                    _restRemainingSec.value = null
                    break
                }
                _restRemainingSec.value = rem
                delay(300L)
            }
        }
    }

    fun addRestSeconds(extra: Int) {
        restTargetMs += extra * 1000L
    }

    fun stopRestTimer() {
        restJob?.cancel()
        _restRemainingSec.value = null
    }

    fun toggleTempoPacer() {
        val next = !_tempoActive.value
        _tempoActive.value = next
        tempoJob?.cancel()
        if (!next) return
        _tempoPhaseMs.value = 0L
        _tempoRepCount.value = 1
        tempoJob = viewModelScope.launch {
            var ms = 0L
            while (true) {
                _tempoPhaseMs.value = ms % 5000L
                _tempoRepCount.value = (ms / 5000L).toInt() + 1
                delay(100L)
                ms += 100L
            }
        }
    }

    fun getCurrentDaySlots(): List<SplitExerciseSlot> {
        val profile = currentProfile
        val split = FitnessCatalog.splitById(profile.splitId)
        val dayIdx = _selectedDay.value.coerceIn(0, split.days.lastIndex)
        val base = split.days[dayIdx].exercises
        val customs = (customExercisesState.value as? UiState.Success)?.data.orEmpty()
            .filter { it.splitId == profile.splitId && it.dayIndex == dayIdx }
            .map { SplitExerciseSlot(id = "cx_${it.id}", sets = it.sets, reps = it.reps, customName = it.name, customDocId = it.id) }
        return base + customs
    }

    fun effectiveSetsCount(slot: SplitExerciseSlot, resolvedId: String): Int {
        val profile = currentProfile
        val best = (exerciseBestsState.value as? UiState.Success)?.data.orEmpty().find { it.exerciseId == resolvedId }
        val mod = best?.volumeMod ?: 0
        var n = max(2, (if (profile.deloadToday) slot.sets - 1 else slot.sets) + mod)
        if (profile.timeCrunch) n = minOf(3, n)
        return n
    }

    fun autoFillTargetWeights() {
        val profile = currentProfile
        val dayIdx = _selectedDay.value
        val slots = getCurrentDaySlots()
        val bests = (exerciseBestsState.value as? UiState.Success)?.data.orEmpty().associateBy { it.exerciseId }
        val updatedMap = _setStates.value.toMutableMap()
        var filled = 0
        slots.forEachIndexed { idx, slot ->
            val slotKey = "${profile.splitId}-$dayIdx-$idx"
            val effectiveId = _swappedIds.value[slotKey] ?: slot.id
            val resolved = FitnessCatalog.resolveExercise(slot.copy(id = effectiveId), profile.equip, profile.injury)
            val best = bests[resolved.info.id] ?: return@forEachIndexed
            val targetW = if (best.suggestOverload && !profile.deloadToday) best.bestWeight + 2.5 else best.bestWeight
            val setsN = effectiveSetsCount(slot, resolved.info.id)
            for (s in 0 until setsN) {
                val k = "$slotKey-$s"
                val cur = updatedMap[k] ?: SetEntryState()
                if (cur.weight.isBlank()) {
                    updatedMap[k] = cur.copy(
                        weight = if (targetW % 1.0 == 0.0) targetW.toInt().toString() else targetW.toString(),
                        reps = best.bestReps.toString()
                    )
                    filled++
                }
            }
        }
        _setStates.value = updatedMap
        emitMessage(
            if (filled > 0) {
                if (profile.lang == "ar") "⚡ تم ملء الأوزان المستهدفة تلقائياً!" else "⚡ Target weights auto-filled!"
            } else {
                if (profile.lang == "ar") "سجّل أول تمرين ليتم حفظ أوزانك للمرة القادمة." else "Log a workout first to unlock auto-fill."
            }
        )
    }

    fun addCustomExercise(name: String, sets: Int, reps: String) {
        val safeName = name.trim()
        if (safeName.isEmpty()) return
        val profile = currentProfile
        viewModelScope.launch {
            val res = repo.addCustomExercise(
                userId,
                CustomExercise(
                    userId = userId,
                    splitId = profile.splitId,
                    dayIndex = _selectedDay.value,
                    name = safeName,
                    sets = sets.coerceIn(1, 12),
                    reps = reps.trim().ifBlank { "10" }
                )
            )
            if (res.isFailure) emitMessage(res.exceptionOrNull()?.localizedMessage ?: "Failed to add exercise")
        }
    }

    fun deleteCustomExercise(customDocId: String) {
        viewModelScope.launch {
            repo.deleteCustomExercise(userId, customDocId)
        }
    }

    fun finishWorkout(rir: Int, pump: String) {
        val profile = currentProfile
        val split = FitnessCatalog.splitById(profile.splitId)
        val dayIdx = _selectedDay.value.coerceIn(0, split.days.lastIndex)
        val dayObj = split.days[dayIdx]
        val slots = getCurrentDaySlots()
        val bestsMap = (exerciseBestsState.value as? UiState.Success)?.data.orEmpty().associateBy { it.exerciseId }

        var totalVolume = 0.0
        var completedSets = 0
        var gotPR = false
        val muscles = mutableSetOf<String>()
        val bestUpdates = mutableListOf<Pair<ExerciseBest, Boolean>>()

        slots.forEachIndexed { idx, slot ->
            val slotKey = "${profile.splitId}-$dayIdx-$idx"
            val effectiveId = _swappedIds.value[slotKey] ?: slot.id
            val resolved = FitnessCatalog.resolveExercise(slot.copy(id = effectiveId), profile.equip, profile.injury)
            val exId = resolved.info.id
            val setsN = effectiveSetsCount(slot, exId)
            val targetReps = slot.reps.filter { it.isDigit() }.toIntOrNull() ?: 8

            var allHit = true
            var maxW = 0.0
            var bestR = 0
            var didEx = false

            for (s in 0 until setsN) {
                val st = _setStates.value["$slotKey-$s"] ?: SetEntryState()
                val w = st.weight.toDoubleOrNull() ?: 0.0
                val r = st.reps.toIntOrNull() ?: 0
                if (st.completed || (w > 0.0 && r > 0)) {
                    didEx = true
                    completedSets++
                    totalVolume += w * r
                    if (w >= maxW) {
                        maxW = w
                        bestR = r
                    }
                    if (r < targetReps) allHit = false
                } else {
                    allHit = false
                }
            }

            if (didEx) {
                muscles.add(resolved.info.muscle)
                val existingBest = bestsMap[exId]
                val newE1rm = if (maxW > 0.0) ((maxW * (1.0 + bestR / 30.0)) * 10.0).roundToInt() / 10.0 else 0.0
                val isPr = maxW > 0.0 && (existingBest == null || newE1rm > existingBest.e1rm)
                if (isPr && existingBest != null) gotPR = true

                var volMod = existingBest?.volumeMod ?: 0
                if (pump == "low" && rir >= 2) volMod = minOf(2, volMod + 1)
                else if (pump == "pain") volMod = maxOf(-2, volMod - 1)

                bestUpdates.add(
                    ExerciseBest(
                        userId = userId,
                        exerciseId = exId,
                        bestWeight = if (isPr) maxW else (existingBest?.bestWeight ?: maxW),
                        bestReps = if (isPr) bestR else (existingBest?.bestReps ?: bestR),
                        e1rm = if (isPr) newE1rm else (existingBest?.e1rm ?: newE1rm),
                        suggestOverload = allHit && maxW > 0.0,
                        volumeMod = volMod
                    ) to (existingBest != null)
                )
            }
        }

        if (completedSets == 0) {
            emitMessage(if (profile.lang == "ar") "سجّل مجموعة واحدة على الأقل." else "Complete at least one set first.")
            return
        }

        val session = WorkoutSession(
            userId = userId,
            dayName = if (profile.lang == "ar") dayObj.ar else dayObj.en,
            splitId = profile.splitId,
            dateISO = FitnessCatalog.todayIso(),
            completedSets = completedSets,
            totalVolume = totalVolume.roundToInt().toDouble(),
            rir = rir.coerceIn(0, 5),
            pump = pump,
            durationSec = _sessionElapsedSec.value,
            muscles = muscles.toList()
        )

        viewModelScope.launch {
            val res = repo.addWorkout(userId, session)
            if (res.isSuccess) {
                bestUpdates.forEach { (best, exists) -> repo.upsertExerciseBest(userId, best, exists) }
                val workoutsCount = ((workoutsState.value as? UiState.Success)?.data?.size ?: 0) + 1
                if (workoutsCount % split.days.size == 0 && hasRemoteProfile()) {
                    val nextWeek = (profile.mesoWeek % 8) + 1
                    repo.updateProfileFields(userId, mapOf("mesoWeek" to nextWeek))
                }
                _setStates.value = emptyMap()
                _sessionStartMs.value = null
                _sessionElapsedSec.value = 0
                val prText = if (gotPR) (if (profile.lang == "ar") " 🏆 رقم قياسي جديد!" else " 🏆 New PR!") else ""
                val msg = if (profile.lang == "ar") "تم حفظ التمرين! مجموع الحجم: ${session.totalVolume.roundToInt()} كجم$prText"
                else "Workout saved! Volume: ${session.totalVolume.roundToInt()} kg$prText"
                emitMessage(msg)
            } else {
                emitMessage(res.exceptionOrNull()?.localizedMessage ?: "Failed to save workout")
            }
        }
    }

    // Nutrition
    fun addMeal(name: String, cal: Int, protein: Int, carbs: Int, fat: Int, source: String) {
        val safeName = name.trim().ifBlank { if (currentProfile.lang == "ar") "وجبة" else "Meal" }
        viewModelScope.launch {
            val res = repo.addMeal(
                userId,
                MealLog(
                    userId = userId,
                    name = safeName,
                    dateISO = FitnessCatalog.todayIso(),
                    cal = cal,
                    protein = protein,
                    carbs = carbs,
                    fat = fat,
                    source = source
                )
            )
            if (res.isFailure) emitMessage(res.exceptionOrNull()?.localizedMessage ?: "Failed to log meal")
        }
    }

    fun deleteMeal(mealId: String) {
        viewModelScope.launch { repo.deleteMeal(userId, mealId) }
    }

    fun autoBuildMeals() {
        val profile = currentProfile
        val split = FitnessCatalog.splitById(profile.splitId)
        val dayIsHeavy = split.days.getOrNull(_selectedDay.value)?.heavy ?: false
        val (targetMacros, _) = FitnessCatalog.effectiveMacros(profile, dayIsHeavy)

        data class BaseMeal(val ar: String, val en: String, val cal: Int, val p: Int, val c: Int, val f: Int)
        val base = listOf(
            BaseMeal("فطار: شوفان + 3 بيضات + موزة", "Breakfast: Oats + 3 Eggs + Banana", 643, 30, 83, 21),
            BaseMeal("غداء: صدر دجاج 200جم + أرز 250جم", "Lunch: 200g Chicken + 250g Rice", 655, 66, 70, 7),
            BaseMeal("بعد التمرين: سكوب واي + بطاطا حلوة", "Post-Workout: Whey + Sweet Potato", 292, 27, 43, 1),
            BaseMeal("عشاء: تونة + توست + زيت زيتون", "Dinner: Tuna + Toast + Olive Oil", 380, 34, 30, 12)
        )
        val sumCal = base.sumOf { it.cal }.toDouble()
        val ratio = (targetMacros.kcal / sumCal).coerceIn(0.65, 1.6)
        viewModelScope.launch {
            base.forEach { m ->
                repo.addMeal(
                    userId,
                    MealLog(
                        userId = userId,
                        name = if (profile.lang == "ar") m.ar else m.en,
                        dateISO = FitnessCatalog.todayIso(),
                        cal = (m.cal * ratio).roundToInt(),
                        protein = (m.p * ratio).roundToInt(),
                        carbs = (m.c * ratio).roundToInt(),
                        fat = (m.f * ratio).roundToInt(),
                        source = "auto"
                    )
                )
            }
            emitMessage(if (profile.lang == "ar") "⚡ تم توليد وجبات اليوم لتطابق سعراتك!" else "⚡ Daily meals auto-generated!")
        }
    }

    fun applyBudgetPlan(plan: BudgetMealPlan) {
        val isAr = currentProfile.lang == "ar"
        viewModelScope.launch {
            plan.items.forEach { item ->
                repo.addMeal(
                    userId,
                    MealLog(
                        userId = userId,
                        name = "💰 " + if (isAr) item.ar else item.en,
                        dateISO = FitnessCatalog.todayIso(),
                        cal = item.cal,
                        protein = item.protein,
                        carbs = item.carbs,
                        fat = item.fat,
                        source = "budget"
                    )
                )
            }
            emitMessage(if (isAr) "تمت إضافة وجبات الميزانية!" else "Budget meals logged!")
        }
    }

    fun scanMealBitmap(bitmap: Bitmap, mode: String = "meal") {
        viewModelScope.launch {
            _mealScanState.value = UiState.Loading
            val res = aiRepo.analyzeMealPhoto(bitmap, currentProfile.lang, mode)
            _mealScanState.value = res.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.localizedMessage ?: "Nutrition scan failed") }
            )
        }
    }

    fun clearMealScanState() {
        _mealScanState.value = null
    }

    // AI Smart Coach
    fun sendCoachMessage(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || _isCoachStreaming.value) return
        val profile = currentProfile
        val split = FitnessCatalog.splitById(profile.splitId)
        val dayIsHeavy = split.days.getOrNull(_selectedDay.value)?.heavy ?: false
        val (effMacros, _) = FitnessCatalog.effectiveMacros(profile, dayIsHeavy)
        val meals = (mealsState.value as? UiState.Success)?.data.orEmpty()
        val todayMeals = meals.filter { it.dateISO == FitnessCatalog.todayIso() }
        val bodyLogs = (bodyLogsState.value as? UiState.Success)?.data.orEmpty()
        val adapTdee = FitnessCatalog.calculateAdaptiveTdee(bodyLogs, meals)

        val systemCtx = """
            You are Amaino Lab Coach, an elite personal trainer and sports nutritionist.
            User: Name=${profile.name.ifBlank { "Athlete" }}, Age=${profile.age}, Sex=${profile.sex}, Weight=${profile.weightKg}kg, Height=${profile.heightCm}cm, Goal=${profile.goal}.
            Targets: ${effMacros.kcal} kcal, ${effMacros.protein}g protein, ${effMacros.carbs}g carbs, ${effMacros.fat}g fat. Adaptive TDEE=${adapTdee ?: "Pending"}.
            Injury: ${profile.injury}. Equipment: ${profile.equip}. Mesocycle Week: ${profile.mesoWeek}/8.
            Logged today: ${todayMeals.sumOf { it.cal }} kcal, ${todayMeals.sumOf { it.protein }}g protein, ${profile.waterCups}/10 water cups.
            Split: ${if (profile.lang == "ar") split.ar else split.en}.
            Reply concisely and practically in ${if (profile.lang == "ar") "friendly Egyptian Arabic" else "clear English"} using bullet points when helpful.
        """.trimIndent()

        val userMsg = ChatMessage(id = "u_${System.currentTimeMillis()}", isFromUser = true, text = clean)
        val botId = "a_${System.currentTimeMillis() + 1}"
        val prevHistory = _chatMessages.value
        _coachError.value = null
        _chatMessages.value = prevHistory + userMsg + ChatMessage(id = botId, isFromUser = false, text = "…")
        _isCoachStreaming.value = true

        viewModelScope.launch {
            try {
                aiRepo.streamCoachReply(systemCtx, prevHistory, clean).collect { partial ->
                    _chatMessages.value = _chatMessages.value.map {
                        if (it.id == botId) it.copy(text = partial) else it
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Coach timed out", e)
                _coachError.value = if (profile.lang == "ar") "انتهت مهلة الاتصال بالمدرب. حاول مرة أخرى." else "Coach response timed out. Please retry."
                _chatMessages.value = _chatMessages.value.filterNot { it.id == botId }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Coach error", e)
                _coachError.value = e.localizedMessage ?: "Coach request failed"
                _chatMessages.value = _chatMessages.value.filterNot { it.id == botId }
            } finally {
                _isCoachStreaming.value = false
            }
        }
    }

    fun clearCoachChat() {
        _chatMessages.value = emptyList()
        _coachError.value = null
    }

    // Progress & Body Logs
    fun logWeight(weightKg: Double) {
        if (weightKg !in 25.0..300.0) return
        viewModelScope.launch {
            val res = repo.addBodyLog(userId, BodyLog(userId = userId, dateISO = FitnessCatalog.todayIso(), weightKg = weightKg))
            if (res.isSuccess && hasRemoteProfile()) {
                repo.updateProfileFields(userId, mapOf("weightKg" to weightKg))
                emitMessage(if (currentProfile.lang == "ar") "تم تسجيل الوزن!" else "Weight logged!")
            }
        }
    }

    fun saveMeasurements(chest: Double?, waist: Double?, arm: Double?) {
        if (chest == null && waist == null && arm == null) return
        viewModelScope.launch {
            val res = repo.addBodyLog(
                userId,
                BodyLog(
                    userId = userId,
                    dateISO = FitnessCatalog.todayIso(),
                    weightKg = currentProfile.weightKg,
                    chestCm = chest,
                    waistCm = waist,
                    armCm = arm
                )
            )
            if (res.isSuccess) {
                emitMessage(if (currentProfile.lang == "ar") "تم حفظ القياسات!" else "Measurements saved!")
            }
        }
    }

    fun addProgressPhoto(uri: Uri) {
        viewModelScope.launch {
            val saved = photoStore.saveFromUri(userId, uri)
            if (saved != null) {
                _progressPhotos.value = photoStore.loadPhotos(userId)
                emitMessage(if (currentProfile.lang == "ar") "تم حفظ الصورة محلياً!" else "Progress photo saved!")
            }
        }
    }

    fun deleteProgressPhoto(fileName: String) {
        viewModelScope.launch {
            photoStore.deletePhoto(userId, fileName)
            _progressPhotos.value = photoStore.loadPhotos(userId)
        }
    }

    // B2B Coach Portal
    fun addCoachClient(name: String, targetKcal: Int, splitId: String) {
        val safeName = name.trim()
        if (safeName.isEmpty()) return
        viewModelScope.launch {
            val res = repo.addCoachClient(
                userId,
                CoachClient(userId = userId, name = safeName, targetKcal = targetKcal.coerceIn(800, 8000), splitId = splitId)
            )
            if (res.isFailure) emitMessage(res.exceptionOrNull()?.localizedMessage ?: "Failed to add client")
        }
    }

    fun deleteCoachClient(clientId: String) {
        viewModelScope.launch { repo.deleteCoachClient(userId, clientId) }
    }

    fun applyCoachClientPlan(client: CoachClient) {
        if (!hasRemoteProfile()) return
        viewModelScope.launch {
            val res = repo.updateProfileFields(
                userId,
                mapOf("targetKcal" to client.targetKcal.coerceIn(1000, 8000), "splitId" to client.splitId)
            )
            if (res.isSuccess) {
                emitMessage(
                    if (currentProfile.lang == "ar") "تم تطبيق خطة العميل: ${client.name}" else "Loaded client plan: ${client.name}"
                )
            }
        }
    }
}
