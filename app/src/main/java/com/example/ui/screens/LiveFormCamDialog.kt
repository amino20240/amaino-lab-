package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.components.StatTile
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.IndigoBright
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

@Composable
fun LiveFormCamDialog(
    strings: AppStrings,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isAr = strings.isAr

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    var repCount by remember { mutableIntStateOf(0) }
    var stabilityPct by remember { mutableIntStateOf(98) }
    var phaseDown by remember { mutableStateOf(false) }
    var normCentroidY by remember { mutableFloatStateOf(0.5f) }
    var feedbackMsg by remember {
        mutableStateOf(
            if (isAr) "قف أمام الكاميرا وابدأ التمرين (سكوات / ضغط / كتف)"
            else "Step in frame & start your reps (Squat / Push-up / Press)"
        )
    }
    var feedbackGood by remember { mutableStateOf(true) }

    val tts = remember {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                instance?.language = if (isAr) Locale.forLanguageTag("ar-EG") else Locale.US
            }
        }
        instance
    }

    DisposableEffect(Unit) {
        if (!hasPermission) {
            permLauncher.launch(Manifest.permission.CAMERA)
        }
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Videocam, contentDescription = null, tint = IndigoBright)
                        Text(
                            if (isAr) "🎥 كوتش الكاميرا المباشر" else "🎥 Live Form & Rep Coach",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasPermission) {
                        val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
                        DisposableEffect(Unit) {
                            onDispose { analysisExecutor.shutdown() }
                        }

                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }

                                    var prevLuma: ByteArray? = null
                                    var smoothY = 0.5f
                                    var phase = "up"
                                    var lastRepTs = System.currentTimeMillis()
                                    var reps = 0

                                    val analysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                    analysis.setAnalyzer(analysisExecutor) { image ->
                                        try {
                                            val plane = image.planes[0]
                                            val buffer = plane.buffer
                                            val rowStride = plane.rowStride
                                            val w = image.width
                                            val h = image.height
                                            val sampleStep = 8
                                            val sampleW = (w / sampleStep).coerceAtLeast(1)
                                            val sampleH = (h / sampleStep).coerceAtLeast(1)
                                            val curLuma = ByteArray(sampleW * sampleH)

                                            var idx = 0
                                            for (y in 0 until sampleH) {
                                                val srcY = y * sampleStep
                                                for (x in 0 until sampleW) {
                                                    val srcX = x * sampleStep
                                                    val pos = srcY * rowStride + srcX
                                                    curLuma[idx++] = if (pos < buffer.limit()) buffer.get(pos) else 0
                                                }
                                            }

                                            val prev = prevLuma
                                            if (prev != null && prev.size == curLuma.size) {
                                                var sumY = 0f
                                                var weight = 0f
                                                for (y in 1 until sampleH - 1) {
                                                    for (x in 1 until sampleW - 1) {
                                                        val i = y * sampleW + x
                                                        val diff = abs((curLuma[i].toInt() and 0xFF) - (prev[i].toInt() and 0xFF))
                                                        if (diff > 28) {
                                                            sumY += (y.toFloat() / sampleH) * diff
                                                            weight += diff
                                                        }
                                                    }
                                                }
                                                if (weight > 2200f) {
                                                    val cy = sumY / weight
                                                    smoothY = smoothY * 0.72f + cy * 0.28f
                                                    if (phase == "up" && smoothY > 0.58f) {
                                                        phase = "down"
                                                    } else if (phase == "down" && smoothY < 0.45f) {
                                                        phase = "up"
                                                        reps++
                                                        val now = System.currentTimeMillis()
                                                        val repMs = now - lastRepTs
                                                        lastRepTs = now
                                                        val stab = if (repMs < 900L) 74 else 97
                                                        ContextCompat.getMainExecutor(ctx).execute {
                                                            repCount = reps
                                                            stabilityPct = stab
                                                            feedbackGood = repMs >= 900L
                                                            feedbackMsg = if (repMs < 900L) {
                                                                if (isAr) "⚠️ أبطئ النزول للتحكم بالعضلة!" else "⚠️ Slow down your eccentric!"
                                                            } else {
                                                                if (isAr) "✅ عدّة ممتازة وتحكم رائع!" else "✅ Clean rep & solid tempo!"
                                                            }
                                                            tts.speak(reps.toString(), TextToSpeech.QUEUE_FLUSH, null, "rep_$reps")
                                                        }
                                                    }
                                                    ContextCompat.getMainExecutor(ctx).execute {
                                                        normCentroidY = smoothY
                                                        phaseDown = phase == "down"
                                                    }
                                                }
                                            }
                                            prevLuma = curLuma
                                        } finally {
                                            image.close()
                                        }
                                    }

                                    runCatching {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_FRONT_CAMERA,
                                            preview,
                                            analysis
                                        )
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawLine(
                                color = IndigoBright.copy(alpha = 0.6f),
                                start = Offset(0f, size.height * 0.5f),
                                end = Offset(size.width, size.height * 0.5f),
                                strokeWidth = 2.dp.toPx()
                            )
                            drawCircle(
                                color = if (phaseDown) AmberWarn else EmeraldOk,
                                radius = 9.dp.toPx(),
                                center = Offset(size.width * 0.5f, size.height * normCentroidY.coerceIn(0.1f, 0.9f))
                            )
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                if (isAr) "اسمح بصلاحية الكاميرا لاستخدام كوتش الحركة المباشر."
                                else "Allow camera permission to use live form tracking.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) {
                                Text(if (isAr) "منح صلاحية الكاميرا" else "Grant Camera Permission")
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile(repCount.toString(), if (isAr) "التكرارات" else "Reps", Modifier.weight(1f))
                    StatTile("$stabilityPct%", if (isAr) "ثبات الأداء" else "Stability", Modifier.weight(1f))
                }

                Text(
                    text = feedbackMsg,
                    color = if (feedbackGood) EmeraldOk else AmberWarn,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
