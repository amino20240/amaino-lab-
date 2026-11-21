package com.example.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ExerciseInfo(
    val id: String,
    val muscle: String,
    val equip: String,
    val avoid: List<String>,
    val alt: List<String>,
    val ar: String,
    val en: String,
    val tipAr: String,
    val tipEn: String
)

data class SplitExerciseSlot(
    val id: String,
    val sets: Int,
    val reps: String,
    val customName: String? = null,
    val customDocId: String? = null
)

data class SplitDay(
    val ar: String,
    val en: String,
    val heavy: Boolean,
    val exercises: List<SplitExerciseSlot>
)

data class WorkoutSplit(
    val id: String,
    val ar: String,
    val en: String,
    val days: List<SplitDay>
)

data class FoodPreset(
    val ar: String,
    val en: String,
    val cal: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val costEgp: Int
)

data class ResolvedExercise(
    val info: ExerciseInfo,
    val pivoted: Boolean
)

data class MacroTarget(
    val kcal: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int
)

data class BudgetMealPlan(
    val items: List<FoodPreset>,
    val totalCostDisplay: String,
    val totalCal: Int,
    val totalProtein: Int,
    val totalCarbs: Int,
    val totalFat: Int
)

data class BarbellPlate(
    val weightKg: Double,
    val colorHex: Long,
    val heightDp: Int,
    val widthDp: Int
)

object FitnessCatalog {
    fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    val exercises: List<ExerciseInfo> = listOf(
        ExerciseInfo("bench", "chest", "barbell", listOf("shoulder"), listOf("incdb", "pushup", "cablefly"), "بنش برس بار", "Barbell Bench Press", "ضم لوحي الكتف، انزل البار لمنتصف الصدر بتحكم.", "Retract shoulder blades, lower bar to mid-chest under control."),
        ExerciseInfo("incdb", "chest", "dumbbell", emptyList(), listOf("pushup", "cablefly", "bench"), "انكلاين دامبل", "Incline Dumbbell Press", "زاوية 30 درجة لتركيز أفضل على الصدر العلوي.", "Set bench to 30 degrees for upper chest."),
        ExerciseInfo("cablefly", "chest", "cable", emptyList(), listOf("incdb", "pushup"), "تفتيح كابل", "Cable Chest Fly", "ثنية بسيطة في الكوع واعصر الصدر في النهاية.", "Keep slight bend in elbows and squeeze chest."),
        ExerciseInfo("pushup", "chest", "bodyweight", emptyList(), listOf("incdb", "cablefly"), "ضغط (Push-ups)", "Push-ups", "جسمك خط مستقيم والكوع بزاوية 45 درجة.", "Straight plank line, elbows tucked 45 degrees."),
        ExerciseInfo("lat", "back", "cable", emptyList(), listOf("dbrow", "cablerow"), "سحب علوي واسع", "Lat Pulldown", "اسحب بالكوع لأسفل وتجنب تأرجح الظهر.", "Drive elbows down toward hips; avoid swinging."),
        ExerciseInfo("row", "back", "barbell", listOf("back"), listOf("cablerow", "dbrow"), "تجديف بار", "Barbell Bent-Over Row", "الظهر مفرود واسحب البار لأسفل البطن.", "Neutral spine, pull toward lower abs."),
        ExerciseInfo("cablerow", "back", "cable", emptyList(), listOf("dbrow", "lat"), "سيت كابل رو", "Seated Cable Row", "مد للأمام ثم اسحب بضم اللوحين.", "Stretch forward, pull and squeeze shoulder blades."),
        ExerciseInfo("dbrow", "back", "dumbbell", emptyList(), listOf("cablerow", "lat"), "تجديف دامبل فردي", "One-Arm Dumbbell Row", "اسحب الدامبل تجاه الورك لعزل اللاتس.", "Pull dumbbell toward hip pocket."),
        ExerciseInfo("deadlift", "back", "barbell", listOf("back"), listOf("cablerow", "gbridge"), "ديدلفت تقليدي", "Conventional Deadlift", "البار ملاصق للساقين وشد البطن قبل الرفع.", "Keep bar close to shins, brace core."),
        ExerciseInfo("squat", "legs", "barbell", listOf("knee", "back"), listOf("lpress", "gbridge", "lunge"), "سكوات بار", "Barbell Squat", "انزل حتى يوازي الفخذ الأرض بثبات الكعبين.", "Descend until thighs parallel, heels planted."),
        ExerciseInfo("rdl", "legs", "barbell", listOf("back"), listOf("legcurl", "gbridge"), "رومانيان ديدلفت", "Romanian Deadlift", "ادفع الحوض للخلف بثنية خفيفة في الركبة.", "Push hips back with soft knees."),
        ExerciseInfo("lpress", "legs", "cable", listOf("knee"), listOf("gbridge", "legcurl"), "ليج برس", "Leg Press", "لا تفرد الركبة بالكامل ولا ترفع أسفل الظهر.", "Don't lock knees, keep lower back against pad."),
        ExerciseInfo("legcurl", "legs", "cable", emptyList(), listOf("gbridge", "rdl"), "مرجحة خلفي رجل", "Lying Leg Curl", "انزل ببطء (3 ثوانٍ) للخلفية.", "3-second eccentric lowering for hamstrings."),
        ExerciseInfo("legext", "legs", "cable", listOf("knee"), listOf("gbridge", "lunge"), "مرجحة أمامي رجل", "Leg Extension", "اثبت ثانية في الأعلى لعصر الفخذ.", "Pause 1 second at top for quads."),
        ExerciseInfo("lunge", "legs", "bodyweight", listOf("knee"), listOf("gbridge", "legcurl"), "لانجز (Lunges)", "Walking Lunges", "خطوة متوازنة وانزل بالركبة الخلفية قرب الأرض.", "Controlled steps, lower back knee near floor."),
        ExerciseInfo("gbridge", "legs", "bodyweight", emptyList(), listOf("legcurl", "rdl"), "هيب ثرست / جسر", "Hip Thrust / Glute Bridge", "ارفع الحوض واعصر الألوية في الأعلى.", "Drive through heels and squeeze glutes at top."),
        ExerciseInfo("calf", "legs", "bodyweight", emptyList(), listOf("gbridge"), "سمانة واقف", "Standing Calf Raise", "انزل لأقصى إطالة واثبت ثانية في الأعلى.", "Pause at bottom stretch and top contraction."),
        ExerciseInfo("ohp", "shoulders", "barbell", listOf("shoulder", "back"), listOf("latraise", "facepull"), "أوفر هيد برس", "Overhead Press", "شد البطن والأرداف لمنع تقوس الظهر.", "Brace core and glutes."),
        ExerciseInfo("latraise", "shoulders", "dumbbell", emptyList(), listOf("facepull"), "رفرفة جانبي", "Lateral Raise", "ارفع بالكوعين للجانب بدون هز الجسم.", "Lead with elbows slightly forward."),
        ExerciseInfo("facepull", "shoulders", "cable", emptyList(), listOf("latraise"), "فيس بول (أمان الكتف)", "Face Pull", "اسحب الحبل للجبهة مع تدوير الكتف للخارج.", "Pull rope to forehead with external rotation."),
        ExerciseInfo("bicep", "arms", "dumbbell", emptyList(), listOf("hammer"), "بايسبس دامبل", "Dumbbell Bicep Curl", "ثبت الكوع بجانب الجسم وارفع بتحكم.", "Pin elbows to sides and curl under control."),
        ExerciseInfo("hammer", "arms", "dumbbell", emptyList(), listOf("bicep"), "هامر كيرل", "Hammer Curl", "قبضة محايدة لتقوية البراكياليس والساعد.", "Neutral grip targets brachialis and forearms."),
        ExerciseInfo("tri", "arms", "cable", emptyList(), listOf("pushup"), "ترايسبس كابل", "Triceps Pushdown", "المرفق ثابت وافرد الذراع للأسفل بالكامل.", "Keep elbows locked by ribs."),
        ExerciseInfo("plank", "core", "bodyweight", emptyList(), emptyList(), "بلانك بطن", "Plank", "شد البطن والأرداف وتنفس بانتظام.", "Brace abs & glutes, keep hips level.")
    )

    val splits: List<WorkoutSplit> = listOf(
        WorkoutSplit(
            id = "ul4",
            ar = "علوي / سفلي (4 أيام)",
            en = "Upper / Lower (4 Days)",
            days = listOf(
                SplitDay("علوي A", "Upper A", false, listOf(SplitExerciseSlot("bench", 4, "8"), SplitExerciseSlot("row", 4, "8"), SplitExerciseSlot("ohp", 3, "10"), SplitExerciseSlot("lat", 3, "10"), SplitExerciseSlot("tri", 3, "12"))),
                SplitDay("سفلي A", "Lower A", true, listOf(SplitExerciseSlot("squat", 4, "8"), SplitExerciseSlot("rdl", 3, "10"), SplitExerciseSlot("lpress", 3, "12"), SplitExerciseSlot("calf", 4, "15"))),
                SplitDay("علوي B", "Upper B", false, listOf(SplitExerciseSlot("incdb", 4, "10"), SplitExerciseSlot("cablerow", 4, "10"), SplitExerciseSlot("latraise", 4, "15"), SplitExerciseSlot("bicep", 3, "12"), SplitExerciseSlot("facepull", 3, "15"))),
                SplitDay("سفلي B", "Lower B", true, listOf(SplitExerciseSlot("deadlift", 3, "5"), SplitExerciseSlot("legcurl", 3, "12"), SplitExerciseSlot("legext", 3, "15"), SplitExerciseSlot("plank", 3, "45s")))
            )
        ),
        WorkoutSplit(
            id = "ppl6",
            ar = "دفع / سحب / رجل (6 أيام)",
            en = "Push / Pull / Legs (6 Days)",
            days = listOf(
                SplitDay("دفع (Push)", "Push", false, listOf(SplitExerciseSlot("bench", 4, "8"), SplitExerciseSlot("incdb", 3, "10"), SplitExerciseSlot("ohp", 3, "10"), SplitExerciseSlot("latraise", 4, "15"), SplitExerciseSlot("tri", 3, "12"))),
                SplitDay("سحب (Pull)", "Pull", false, listOf(SplitExerciseSlot("lat", 4, "10"), SplitExerciseSlot("row", 4, "8"), SplitExerciseSlot("facepull", 3, "15"), SplitExerciseSlot("bicep", 3, "12"), SplitExerciseSlot("hammer", 3, "12"))),
                SplitDay("رجل (Legs)", "Legs", true, listOf(SplitExerciseSlot("squat", 4, "8"), SplitExerciseSlot("rdl", 3, "10"), SplitExerciseSlot("lpress", 3, "12"), SplitExerciseSlot("legcurl", 3, "12"), SplitExerciseSlot("calf", 4, "15")))
            )
        ),
        WorkoutSplit(
            id = "fb3",
            ar = "جسم كامل (3 أيام)",
            en = "Full Body (3 Days)",
            days = listOf(
                SplitDay("جسم كامل A", "Full Body A", true, listOf(SplitExerciseSlot("squat", 3, "8"), SplitExerciseSlot("bench", 3, "8"), SplitExerciseSlot("lat", 3, "10"), SplitExerciseSlot("latraise", 3, "12"), SplitExerciseSlot("plank", 3, "45s"))),
                SplitDay("جسم كامل B", "Full Body B", false, listOf(SplitExerciseSlot("rdl", 3, "10"), SplitExerciseSlot("ohp", 3, "10"), SplitExerciseSlot("cablerow", 3, "10"), SplitExerciseSlot("bicep", 3, "12"), SplitExerciseSlot("tri", 3, "12")))
            )
        ),
        WorkoutSplit(
            id = "home3",
            ar = "تمرين منزلي",
            en = "Home Workout",
            days = listOf(
                SplitDay("منزلي علوي", "Home Upper", false, listOf(SplitExerciseSlot("pushup", 4, "15"), SplitExerciseSlot("dbrow", 4, "12"), SplitExerciseSlot("latraise", 3, "15"), SplitExerciseSlot("bicep", 3, "12"))),
                SplitDay("منزلي سفلي", "Home Lower", true, listOf(SplitExerciseSlot("lunge", 4, "12"), SplitExerciseSlot("gbridge", 4, "15"), SplitExerciseSlot("calf", 4, "15"), SplitExerciseSlot("plank", 3, "60s")))
            )
        )
    )

    val foodPresets: List<FoodPreset> = listOf(
        FoodPreset("صدر دجاج (150جم)", "Chicken Breast (150g)", 248, 46, 0, 5, 45),
        FoodPreset("3 بيضات مسلوقة", "3 Boiled Eggs", 234, 19, 2, 16, 22),
        FoodPreset("جبنة قريش (200جم)", "Cottage Cheese (200g)", 196, 26, 6, 7, 24),
        FoodPreset("فول بزيت حار (طبق)", "Fava Beans Bowl", 280, 16, 42, 6, 15),
        FoodPreset("أرز مطبوخ (200جم)", "Cooked Rice (200g)", 260, 5, 56, 1, 10),
        FoodPreset("شوفان (80جم)", "Oats (80g)", 304, 10, 54, 5, 12),
        FoodPreset("عدس مطبوخ (250جم)", "Cooked Lentils (250g)", 290, 22, 48, 1, 16),
        FoodPreset("تونة مصفاة (علبة)", "Canned Tuna", 140, 30, 0, 2, 55),
        FoodPreset("بطاطس مسلوقة (250جم)", "Boiled Potatoes (250g)", 215, 5, 49, 0, 8),
        FoodPreset("سكوب واي بروتين", "1 Scoop Whey", 120, 24, 3, 1, 45)
    )

    fun findExercise(id: String): ExerciseInfo? = exercises.find { it.id == id }

    fun splitById(id: String): WorkoutSplit = splits.find { it.id == id } ?: splits.first()

    fun resolveExercise(slot: SplitExerciseSlot, equip: String, injury: String): ResolvedExercise {
        val base = findExercise(slot.id) ?: ExerciseInfo(
            id = slot.id,
            muscle = "core",
            equip = "all",
            avoid = emptyList(),
            alt = emptyList(),
            ar = slot.customName ?: slot.id,
            en = slot.customName ?: slot.id,
            tipAr = "تحكم في الحركة وركز على العضلة المستهدفة.",
            tipEn = "Control the tempo and focus on target muscle."
        )
        val needsPivot = (injury != "none" && base.avoid.contains(injury)) ||
            (equip == "bw" && base.equip != "bodyweight") ||
            (equip == "db" && base.equip != "dumbbell" && base.equip != "bodyweight")

        if (needsPivot) {
            for (altId in base.alt) {
                val cand = findExercise(altId) ?: continue
                if (injury != "none" && cand.avoid.contains(injury)) continue
                if (equip == "bw" && cand.equip != "bodyweight") continue
                if (equip == "db" && cand.equip != "dumbbell" && cand.equip != "bodyweight") continue
                return ResolvedExercise(cand, pivoted = true)
            }
        }
        return ResolvedExercise(base, pivoted = false)
    }

    fun calculateMacros(
        sex: String,
        age: Int,
        weightKg: Double,
        heightCm: Double,
        goal: String,
        actMultiplier: Double
    ): MacroTarget {
        val bmr = 10 * weightKg + 6.25 * heightCm - 5 * age + if (sex == "m") 5 else -161
        var kcal = (bmr * actMultiplier).roundToInt()
        when (goal) {
            "bulk" -> kcal += 350
            "cut" -> kcal = max(1200, kcal - 450)
        }
        val pro = (weightKg * if (goal == "cut") 2.2 else 2.0).roundToInt()
        val fat = ((kcal * 0.25) / 9.0).roundToInt()
        val carb = max(50, ((kcal - pro * 4 - fat * 9) / 4.0).roundToInt())
        return MacroTarget(kcal, pro, carb, fat)
    }

    fun effectiveMacros(profile: UserProfile, dayIsHeavy: Boolean): Pair<MacroTarget, String> {
        var kcal = profile.targetKcal
        var carbs = profile.targetCarbs
        val note = if (profile.syncNutr) {
            when {
                profile.deloadToday -> {
                    kcal = max(1200, kcal - 200)
                    carbs = max(40, carbs - 50)
                    if (profile.lang == "ar") "Deload (-200 سعرة)" else "Deload (-200 kcal)"
                }
                dayIsHeavy -> {
                    kcal += 250
                    carbs += 60
                    if (profile.lang == "ar") "يوم رجل/ثقيل (+250 سعرة كارب)" else "Heavy Day (+250 kcal Carbs)"
                }
                else -> if (profile.lang == "ar") "متزامن مع تمرين اليوم" else "Synced to Today's Split"
            }
        } else {
            if (profile.lang == "ar") "المزامنة متوقفة" else "Sync Off"
        }
        return MacroTarget(kcal, profile.targetProtein, carbs, profile.targetFat) to note
    }

    fun calculateAdaptiveTdee(bodyLogs: List<BodyLog>, meals: List<MealLog>): Int? {
        if (bodyLogs.size < 2) return null
        val sorted = bodyLogs.sortedBy { it.dateISO }
        val first = sorted.first()
        val last = sorted.last()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val firstDate = runCatching { fmt.parse(first.dateISO) }.getOrNull() ?: return null
        val lastDate = runCatching { fmt.parse(last.dateISO) }.getOrNull() ?: return null
        val days = ((lastDate.time - firstDate.time) / 86_400_000L).toInt()
        if (days < 3) return null

        val dailyTotals = meals.groupBy { it.dateISO }
            .mapValues { entry -> entry.value.sumOf { it.cal } }
            .values
            .filter { it > 500 }
        if (dailyTotals.isEmpty()) return null

        val avgIntake = dailyTotals.average()
        val dailyDelta = ((last.weightKg - first.weightKg) * 7700.0) / days
        return (avgIntake - dailyDelta).roundToInt().coerceIn(1300, 4500)
    }

    fun optimizeBudgetMeals(budget: Double, currency: String, targetKcal: Int): BudgetMealPlan {
        val safeBudget = max(20.0, budget)
        val mult = when (currency) {
            "SAR", "AED" -> 0.08
            "USD" -> 0.022
            else -> 1.0
        }
        val egpBudget = safeBudget / mult
        val ranked = foodPresets.sortedByDescending { it.protein.toDouble() / it.costEgp }
        var spent = 0.0
        var totCal = 0
        var totP = 0
        var totC = 0
        var totF = 0
        val chosen = mutableListOf<FoodPreset>()
        repeat(2) {
            for (item in ranked) {
                if (spent + item.costEgp <= egpBudget && totCal < targetKcal) {
                    chosen.add(item)
                    spent += item.costEgp
                    totCal += item.cal
                    totP += item.protein
                    totC += item.carbs
                    totF += item.fat
                }
            }
        }
        val costVal = spent * mult
        val costStr = if (currency == "USD") String.format(Locale.US, "%.2f", costVal) else costVal.roundToInt().toString()
        return BudgetMealPlan(chosen, "$costStr $currency", totCal, totP, totC, totF)
    }

    fun calculateMuscleRecovery(workouts: List<WorkoutSession>): Map<String, Double> {
        val now = System.currentTimeMillis()
        val status = mutableMapOf(
            "chest" to 72.0,
            "back" to 72.0,
            "legs" to 72.0,
            "shoulders" to 72.0,
            "arms" to 72.0,
            "core" to 72.0
        )
        for (w in workouts) {
            val tsMs = w.createdAt?.toDate()?.time ?: continue
            val hrs = (now - tsMs) / 3_600_000.0
            if (hrs in 0.0..72.0) {
                for (m in w.muscles) {
                    val cur = status[m] ?: 72.0
                    if (hrs < cur) status[m] = hrs
                }
            }
        }
        return status
    }

    fun calculateReadinessScore(recovery: Map<String, Double>, streak: Int, waterCups: Int): Int {
        var score = 95
        for (hrs in recovery.values) {
            if (hrs < 24.0) score -= 7 else if (hrs < 48.0) score -= 3
        }
        if (streak >= 5) score -= 10
        if (waterCups >= 6) score += 3
        return min(99, max(45, score))
    }

    fun calculateBarbellPlates(targetWeightKg: Double): List<BarbellPlate> {
        var perSide = max(0.0, (targetWeightKg - 20.0) / 2.0)
        val specs = listOf(
            BarbellPlate(20.0, 0xFF2563EB, 72, 20),
            BarbellPlate(15.0, 0xFFEAB308, 64, 18),
            BarbellPlate(10.0, 0xFF16A34A, 54, 16),
            BarbellPlate(5.0, 0xFF64748B, 44, 15),
            BarbellPlate(2.5, 0xFFDC2626, 36, 13),
            BarbellPlate(1.25, 0xFF94A3B8, 28, 11)
        )
        val result = mutableListOf<BarbellPlate>()
        for (sp in specs) {
            while (perSide >= sp.weightKg - 0.01) {
                result.add(sp)
                perSide -= sp.weightKg
            }
        }
        return result
    }
}
