package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val sex: String = "m",
    val age: Int = 25,
    val weightKg: Double = 75.0,
    val heightCm: Double = 175.0,
    val goal: String = "bulk",
    val actMultiplier: Double = 1.55,
    val targetKcal: Int = 2800,
    val targetProtein: Int = 165,
    val targetCarbs: Int = 340,
    val targetFat: Int = 80,
    val splitId: String = "ul4",
    val equip: String = "all",
    val injury: String = "none",
    val mesoWeek: Int = 1,
    val syncNutr: Boolean = true,
    val timeCrunch: Boolean = false,
    val deloadToday: Boolean = false,
    val lang: String = "ar",
    val darkTheme: Boolean = true,
    val waterCups: Int = 0,
    val waterDate: String = "",
    val onboarded: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class WorkoutSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dayName: String = "",
    val splitId: String = "ul4",
    val dateISO: String = "",
    val completedSets: Int = 0,
    val totalVolume: Double = 0.0,
    val rir: Int = 2,
    val pump: String = "good",
    val durationSec: Int = 0,
    val muscles: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MealLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val dateISO: String = "",
    val cal: Int = 0,
    val protein: Int = 0,
    val carbs: Int = 0,
    val fat: Int = 0,
    val source: String = "manual",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BodyLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateISO: String = "",
    val weightKg: Double = 75.0,
    val chestCm: Double? = null,
    val waistCm: Double? = null,
    val armCm: Double? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ExerciseBest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val exerciseId: String = "",
    val bestWeight: Double = 0.0,
    val bestReps: Int = 0,
    val e1rm: Double = 0.0,
    val suggestOverload: Boolean = false,
    val volumeMod: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CoachClient(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val targetKcal: Int = 2500,
    val splitId: String = "ul4",
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CustomExercise(
    @DocumentId val id: String = "",
    val userId: String = "",
    val splitId: String = "ul4",
    val dayIndex: Int = 0,
    val name: String = "",
    val sets: Int = 3,
    val reps: String = "10-12",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class SetEntryState(
    val weight: String = "",
    val reps: String = "",
    val completed: Boolean = false
)

data class ChatMessage(
    val id: String,
    val isFromUser: Boolean,
    val text: String
)

data class ScannedMeal(
    val name: String,
    val cal: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val servingInfo: String = "",
    val mode: String = "meal"
)

data class LocalProgressPhoto(
    val fileName: String,
    val filePath: String,
    val dateISO: String,
    val timestampMs: Long
)
