package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ProgressPhotoStore(private val context: Context) {

    private fun userDir(uid: String): File {
        val dir = File(context.filesDir, "progress_photos/$uid")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun loadPhotos(uid: String): List<LocalProgressPhoto> = withContext(Dispatchers.IO) {
        val dir = userDir(uid)
        val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".jpg") } ?: emptyList()
        files.mapNotNull { file ->
            // Expected format: <timestampMs>_<yyyy-MM-dd>.jpg
            val stem = file.nameWithoutExtension
            val parts = stem.split("_", limit = 2)
            val ts = parts.getOrNull(0)?.toLongOrNull() ?: file.lastModified()
            val dateIso = parts.getOrNull(1) ?: FitnessCatalog.todayIso()
            LocalProgressPhoto(
                fileName = file.name,
                filePath = file.absolutePath,
                dateISO = dateIso,
                timestampMs = ts
            )
        }.sortedBy { it.timestampMs }
    }

    suspend fun saveFromUri(uid: String, uri: Uri): LocalProgressPhoto? = withContext(Dispatchers.IO) {
        runCatching {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val decoded = input.use { BitmapFactory.decodeStream(it) } ?: return@withContext null
            val maxDim = 900
            val scale = minOf(1f, maxDim.toFloat() / maxOf(decoded.width, decoded.height))
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else decoded

            val ts = System.currentTimeMillis()
            val dateIso = FitnessCatalog.todayIso()
            val outFile = File(userDir(uid), "${ts}_${dateIso}.jpg")
            FileOutputStream(outFile).use { fos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 82, fos)
            }
            if (scaled !== decoded) decoded.recycle()
            decoded.recycle()

            LocalProgressPhoto(
                fileName = outFile.name,
                filePath = outFile.absolutePath,
                dateISO = dateIso,
                timestampMs = ts
            )
        }.getOrNull()
    }

    suspend fun deletePhoto(uid: String, fileName: String) = withContext(Dispatchers.IO) {
        val target = File(userDir(uid), fileName)
        if (target.exists()) target.delete()
    }
}
