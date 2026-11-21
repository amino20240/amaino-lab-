package com.example.data

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AiRepository"

class AiRepository {

    private val chatModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    private val mealSchema = Schema.obj(
        properties = mapOf(
            "name" to Schema.string(description = "Short descriptive name of the meal or packaged food item"),
            "servingInfo" to Schema.string(description = "Serving size or portion description, e.g. '1 serving (40g)' or '1 plate (~350g)'"),
            "cal" to Schema.integer(description = "Calories in kcal per serving or total meal"),
            "protein" to Schema.integer(description = "Protein in grams"),
            "carbs" to Schema.integer(description = "Carbohydrates in grams"),
            "fat" to Schema.integer(description = "Fat in grams")
        )
    )

    private val mealScannerModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = mealSchema
            }
        )
    }

    suspend fun analyzeMealPhoto(bitmap: Bitmap, lang: String, mode: String = "meal"): Result<ScannedMeal> {
        return try {
            val scaled = scaleBitmapForAi(bitmap, maxDim = 1024)
            val targetLanguage = if (lang == "ar") "Arabic" else "English"
            val prompt = if (mode == "label") {
                "Read this packaged food Nutrition Facts / nutritional label carefully. " +
                    "Extract the exact per-serving calories, protein, carbs, and fat in grams. " +
                    "Provide the food/product name and serving size in $targetLanguage."
            } else {
                "Analyze this meal photo and estimate realistic total calories and macronutrients (protein, carbs, fat in grams). " +
                    "Provide a short, clear meal name and estimated portion size in $targetLanguage."
            }
            val response = withTimeout(60_000L) {
                mealScannerModel.generateContent(
                    content {
                        image(scaled)
                        text(prompt)
                    }
                )
            }
            val jsonText = response.text
                ?: throw IllegalStateException("AI returned empty nutritional analysis.")
            val json = JSONObject(jsonText)
            val meal = ScannedMeal(
                name = json.optString("name", if (lang == "ar") "وجبة محللة" else "Scanned Food"),
                cal = json.optInt("cal", 0).coerceIn(0, 10000),
                protein = json.optInt("protein", 0).coerceIn(0, 1000),
                carbs = json.optInt("carbs", 0).coerceIn(0, 2000),
                fat = json.optInt("fat", 0).coerceIn(0, 1000),
                servingInfo = json.optString("servingInfo", ""),
                mode = mode
            )
            Result.success(meal)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Nutrition analysis timed out", e)
            Result.failure(Exception("Nutrition scan timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Nutrition analysis failed", e)
            Result.failure(e)
        }
    }

    fun streamCoachReply(
        systemContext: String,
        history: List<ChatMessage>,
        userMessage: String
    ): Flow<String> = flow {
        val modelWithSystem = Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content { text(systemContext) }
        )
        val chatHistory = history.takeLast(8).map { msg ->
            content(if (msg.isFromUser) "user" else "model") { text(msg.text) }
        }
        val chat = modelWithSystem.startChat(history = chatHistory)
        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            chat.sendMessageStream(userMessage).collect { chunk ->
                val part = chunk.text.orEmpty()
                if (part.isNotEmpty()) {
                    accumulated.append(part)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("Coach returned an empty response.")
        }
    }

    private fun scaleBitmapForAi(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / maxOf(w, h).toFloat()
        return Bitmap.createScaledBitmap(
            bitmap,
            (w * ratio).toInt().coerceAtLeast(1),
            (h * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
