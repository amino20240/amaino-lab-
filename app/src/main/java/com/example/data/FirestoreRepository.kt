package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUid: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        if (expectedUid != null) {
            check(uid == expectedUid) { "Signed-in user changed; refusing cross-user write." }
        }
        return uid
    }

    private fun handleStreamError(error: Throwable, op: OperationType, path: String, uid: String): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> =
        db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }
            .catch { error -> handleStreamError(error, OperationType.GET, "users/$userId", userId) }

    fun observeWorkouts(userId: String): Flow<List<WorkoutSession>> =
        db.collection("users").document(userId).collection("workouts")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snap -> snap.toObjects(WorkoutSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error -> handleStreamError(error, OperationType.LIST, "users/$userId/workouts", userId) }

    fun observeMeals(userId: String): Flow<List<MealLog>> =
        db.collection("users").document(userId).collection("meals")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snap -> snap.toObjects(MealLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error -> handleStreamError(error, OperationType.LIST, "users/$userId/meals", userId) }

    fun observeBodyLogs(userId: String): Flow<List<BodyLog>> =
        db.collection("users").document(userId).collection("bodyLogs")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snap -> snap.toObjects(BodyLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error -> handleStreamError(error, OperationType.LIST, "users/$userId/bodyLogs", userId) }

    fun observeExerciseBests(userId: String): Flow<List<ExerciseBest>> =
        db.collection("users").document(userId).collection("exerciseBests")
            .snapshots()
            .map { snap -> snap.toObjects(ExerciseBest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error -> handleStreamError(error, OperationType.LIST, "users/$userId/exerciseBests", userId) }

    fun observeCoachClients(userId: String): Flow<List<CoachClient>> =
        db.collection("users").document(userId).collection("coachClients")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snap -> snap.toObjects(CoachClient::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error -> handleStreamError(error, OperationType.LIST, "users/$userId/coachClients", userId) }

    fun observeCustomExercises(userId: String): Flow<List<CustomExercise>> =
        db.collection("users").document(userId).collection("customExercises")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snap -> snap.toObjects(CustomExercise::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error -> handleStreamError(error, OperationType.LIST, "users/$userId/customExercises", userId) }

    suspend fun saveUserProfile(userId: String, profile: UserProfile, isExisting: Boolean): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val safeName = profile.name.trim().ifBlank { "Athlete" }.take(80)
            val safeWaterDate = profile.waterDate.ifBlank { FitnessCatalog.todayIso() }.take(10)
            val docRef = db.collection("users").document(uid)
            if (isExisting) {
                val updates = mapOf(
                    "name" to safeName,
                    "sex" to profile.sex,
                    "age" to profile.age.coerceIn(13, 100),
                    "weightKg" to profile.weightKg.coerceIn(25.0, 300.0),
                    "heightCm" to profile.heightCm.coerceIn(100.0, 250.0),
                    "goal" to profile.goal,
                    "actMultiplier" to profile.actMultiplier.coerceIn(1.2, 2.2),
                    "targetKcal" to profile.targetKcal.coerceIn(1000, 8000),
                    "targetProtein" to profile.targetProtein.coerceIn(30, 500),
                    "targetCarbs" to profile.targetCarbs.coerceIn(20, 1000),
                    "targetFat" to profile.targetFat.coerceIn(15, 300),
                    "splitId" to profile.splitId,
                    "equip" to profile.equip,
                    "injury" to profile.injury,
                    "mesoWeek" to profile.mesoWeek.coerceIn(1, 8),
                    "syncNutr" to profile.syncNutr,
                    "timeCrunch" to profile.timeCrunch,
                    "deloadToday" to profile.deloadToday,
                    "lang" to profile.lang,
                    "darkTheme" to profile.darkTheme,
                    "waterCups" to profile.waterCups.coerceIn(0, 20),
                    "waterDate" to safeWaterDate,
                    "onboarded" to profile.onboarded,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "name" to safeName,
                    "sex" to profile.sex,
                    "age" to profile.age.coerceIn(13, 100),
                    "weightKg" to profile.weightKg.coerceIn(25.0, 300.0),
                    "heightCm" to profile.heightCm.coerceIn(100.0, 250.0),
                    "goal" to profile.goal,
                    "actMultiplier" to profile.actMultiplier.coerceIn(1.2, 2.2),
                    "targetKcal" to profile.targetKcal.coerceIn(1000, 8000),
                    "targetProtein" to profile.targetProtein.coerceIn(30, 500),
                    "targetCarbs" to profile.targetCarbs.coerceIn(20, 1000),
                    "targetFat" to profile.targetFat.coerceIn(15, 300),
                    "splitId" to profile.splitId,
                    "equip" to profile.equip,
                    "injury" to profile.injury,
                    "mesoWeek" to profile.mesoWeek.coerceIn(1, 8),
                    "syncNutr" to profile.syncNutr,
                    "timeCrunch" to profile.timeCrunch,
                    "deloadToday" to profile.deloadToday,
                    "lang" to profile.lang,
                    "darkTheme" to profile.darkTheme,
                    "waterCups" to profile.waterCups.coerceIn(0, 20),
                    "waterDate" to safeWaterDate,
                    "onboarded" to profile.onboarded,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isExisting) OperationType.UPDATE else OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateProfileFields(userId: String, updates: Map<String, Any>): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val payload = updates.toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("users").document(uid).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addWorkout(userId: String, session: WorkoutSession): Result<Unit> {
        var docPath = "users/$userId/workouts"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid).collection("workouts").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "dayName" to session.dayName.trim().ifBlank { "Workout" }.take(100),
                "splitId" to session.splitId.take(30),
                "dateISO" to session.dateISO.take(10),
                "completedSets" to session.completedSets.coerceIn(1, 200),
                "totalVolume" to session.totalVolume.coerceIn(0.0, 500000.0),
                "rir" to session.rir.coerceIn(0, 5),
                "pump" to session.pump,
                "durationSec" to session.durationSec.coerceIn(0, 36000),
                "muscles" to session.muscles.take(10),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addMeal(userId: String, meal: MealLog): Result<Unit> {
        var docPath = "users/$userId/meals"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid).collection("meals").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "name" to meal.name.trim().ifBlank { "Meal" }.take(120),
                "dateISO" to meal.dateISO.take(10),
                "cal" to meal.cal.coerceIn(0, 10000),
                "protein" to meal.protein.coerceIn(0, 1000),
                "carbs" to meal.carbs.coerceIn(0, 2000),
                "fat" to meal.fat.coerceIn(0, 1000),
                "source" to meal.source,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteMeal(userId: String, mealId: String): Result<Unit> {
        val docPath = "users/$userId/meals/$mealId"
        return try {
            val uid = requireUserId(userId)
            require(mealId.isNotBlank())
            db.collection("users").document(uid).collection("meals").document(mealId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addBodyLog(userId: String, log: BodyLog): Result<Unit> {
        var docPath = "users/$userId/bodyLogs"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid).collection("bodyLogs").document()
            docPath = docRef.path
            val rawPayload = mutableMapOf<String, Any?>(
                "userId" to uid,
                "dateISO" to log.dateISO.take(10),
                "weightKg" to log.weightKg.coerceIn(25.0, 300.0),
                "chestCm" to log.chestCm?.coerceIn(0.0, 300.0),
                "waistCm" to log.waistCm?.coerceIn(0.0, 300.0),
                "armCm" to log.armCm?.coerceIn(0.0, 150.0),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            @Suppress("UNCHECKED_CAST")
            val payload = rawPayload.filterValues { it != null } as Map<String, Any>
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertExerciseBest(userId: String, best: ExerciseBest, exists: Boolean): Result<Unit> {
        val safeExId = best.exerciseId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(60)
        val docPath = "users/$userId/exerciseBests/$safeExId"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid).collection("exerciseBests").document(safeExId)
            if (exists) {
                docRef.update(
                    mapOf(
                        "exerciseId" to safeExId,
                        "bestWeight" to best.bestWeight.coerceIn(0.0, 1000.0),
                        "bestReps" to best.bestReps.coerceIn(0, 200),
                        "e1rm" to best.e1rm.coerceIn(0.0, 2000.0),
                        "suggestOverload" to best.suggestOverload,
                        "volumeMod" to best.volumeMod.coerceIn(-2, 2),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "exerciseId" to safeExId,
                        "bestWeight" to best.bestWeight.coerceIn(0.0, 1000.0),
                        "bestReps" to best.bestReps.coerceIn(0, 200),
                        "e1rm" to best.e1rm.coerceIn(0.0, 2000.0),
                        "suggestOverload" to best.suggestOverload,
                        "volumeMod" to best.volumeMod.coerceIn(-2, 2),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (exists) OperationType.UPDATE else OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addCoachClient(userId: String, client: CoachClient): Result<Unit> {
        var docPath = "users/$userId/coachClients"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid).collection("coachClients").document()
            docPath = docRef.path
            val raw = mutableMapOf<String, Any?>(
                "userId" to uid,
                "name" to client.name.trim().ifBlank { "Client" }.take(80),
                "targetKcal" to client.targetKcal.coerceIn(800, 8000),
                "splitId" to client.splitId,
                "notes" to client.notes?.take(300)?.ifBlank { null },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            @Suppress("UNCHECKED_CAST")
            val payload = raw.filterValues { it != null } as Map<String, Any>
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCoachClient(userId: String, clientId: String): Result<Unit> {
        val docPath = "users/$userId/coachClients/$clientId"
        return try {
            val uid = requireUserId(userId)
            require(clientId.isNotBlank())
            db.collection("users").document(uid).collection("coachClients").document(clientId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addCustomExercise(userId: String, exercise: CustomExercise): Result<Unit> {
        var docPath = "users/$userId/customExercises"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid).collection("customExercises").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "splitId" to exercise.splitId.take(30),
                "dayIndex" to exercise.dayIndex.coerceIn(0, 10),
                "name" to exercise.name.trim().ifBlank { "Exercise" }.take(100),
                "sets" to exercise.sets.coerceIn(1, 12),
                "reps" to exercise.reps.trim().ifBlank { "10" }.take(30),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCustomExercise(userId: String, customId: String): Result<Unit> {
        val docPath = "users/$userId/customExercises/$customId"
        return try {
            val uid = requireUserId(userId)
            require(customId.isNotBlank())
            db.collection("users").document(uid).collection("customExercises").document(customId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
