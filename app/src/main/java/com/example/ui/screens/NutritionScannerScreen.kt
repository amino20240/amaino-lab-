package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.ScannedMeal
import com.example.data.UiState
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.IndigoBright
import com.example.ui.theme.IndigoPrimary
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@Composable
fun NutritionScannerScreen(
    strings: AppStrings,
    initialMode: String = "meal",
    mealScanState: UiState<ScannedMeal>?,
    onAnalyzeBitmap: (Bitmap, String) -> Unit,
    onClearScan: () -> Unit,
    onLogMeal: (String, Int, Int, Int, Int, String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isAr = strings.isAr

    var scanMode by remember(initialMode) { mutableStateOf(initialMode) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            decodeScannerBitmap(context, uri)?.let { bmp ->
                capturedBitmap = bmp
                onAnalyzeBitmap(bmp, scanMode)
            }
        }
    }

    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        onDispose {
            onClearScan()
            captureExecutor.shutdown()
        }
    }

    LaunchedEffect(torchEnabled, cameraRef) {
        runCatching {
            if (cameraRef?.cameraInfo?.hasFlashUnit() == true) {
                cameraRef?.cameraControl?.enableTorch(torchEnabled)
            }
        }
    }

    BackHandler {
        if (capturedBitmap != null) {
            capturedBitmap = null
            onClearScan()
        } else {
            onClose()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF050714)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (capturedBitmap != null) {
                            capturedBitmap = null
                            onClearScan()
                        } else {
                            onClose()
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("scanner_close_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Text(
                    text = strings.scannerTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                if (capturedBitmap == null && hasFlash) {
                    IconButton(
                        onClick = { torchEnabled = !torchEnabled },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("scanner_torch_button")
                    ) {
                        Icon(
                            if (torchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            contentDescription = "Toggle Flash",
                            tint = if (torchEnabled) EmeraldOk else Color.White
                        )
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }

            val previewBitmap = capturedBitmap
            if (previewBitmap == null) {
                // Live CameraX Viewfinder Mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF0E132D)),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                                providerFuture.addListener({
                                    val provider = providerFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }
                                    val capture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                        .build()
                                    runCatching {
                                        provider.unbindAll()
                                        val cam = provider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            capture
                                        )
                                        cameraRef = cam
                                        hasFlash = cam.cameraInfo.hasFlashUnit()
                                        imageCaptureRef = capture
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Viewfinder Guide Overlay
                        ScannerGuideOverlay(isLabelMode = scanMode == "label")
                    } else {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Filled.CameraAlt,
                                contentDescription = null,
                                tint = IndigoBright,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = strings.cameraPermNeed,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                modifier = Modifier.testTag("scanner_grant_permission_button")
                            ) {
                                Text(strings.grantCamera)
                            }
                        }
                    }

                    // Bottom Mode Switcher & Hint inside viewfinder
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = if (scanMode == "label") strings.scannerHintLabel else strings.scannerHintMeal,
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Controls Bar below viewfinder
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = scanMode == "meal",
                            onClick = { scanMode = "meal" },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            icon = { Icon(Icons.Filled.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("scanner_mode_meal")
                        ) { Text(strings.modeMealPhoto) }

                        SegmentedButton(
                            selected = scanMode == "label",
                            onClick = { scanMode = "label" },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                            icon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("scanner_mode_label")
                        ) { Text(strings.modeFoodLabel) }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .testTag("scanner_gallery_button")
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = "Pick from Gallery")
                        }

                        // CameraX Shutter Button
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .clip(CircleShape)
                                .border(3.dp, Color.White, CircleShape)
                                .padding(5.dp)
                        ) {
                            Button(
                                onClick = {
                                    val capture = imageCaptureRef ?: return@Button
                                    if (isCapturing) return@Button
                                    isCapturing = true
                                    capture.takePicture(
                                        captureExecutor,
                                        object : ImageCapture.OnImageCapturedCallback() {
                                            override fun onCaptureSuccess(image: ImageProxy) {
                                                val bmp = try {
                                                    imageProxyToRotatedBitmap(image)
                                                } finally {
                                                    image.close()
                                                }
                                                ContextCompat.getMainExecutor(context).execute {
                                                    isCapturing = false
                                                    capturedBitmap = bmp
                                                    onAnalyzeBitmap(bmp, scanMode)
                                                }
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                ContextCompat.getMainExecutor(context).execute {
                                                    isCapturing = false
                                                }
                                            }
                                        }
                                    )
                                },
                                enabled = hasCameraPermission && !isCapturing && imageCaptureRef != null,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (scanMode == "label") EmeraldOk else IndigoBright
                                ),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("scanner_shutter_button")
                            ) {
                                if (isCapturing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.Camera,
                                        contentDescription = "Capture Photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.size(52.dp))
                    }
                }
            } else {
                // Captured Photo Review + AI Nutritional Analysis Sheet
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Captured Food Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                    )

                    when (mealScanState) {
                        is UiState.Loading, null -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator()
                                    Text(
                                        text = if (scanMode == "label") strings.analyzingLabel else strings.analyzingMeal,
                                        style = MaterialTheme.typography.titleMedium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        is UiState.Error -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = mealScanState.message,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                capturedBitmap = null
                                                onClearScan()
                                            },
                                            modifier = Modifier.weight(1f).testTag("scanner_retake_button")
                                        ) { Text(strings.retakePhoto) }
                                        Button(
                                            onClick = { onAnalyzeBitmap(previewBitmap, scanMode) },
                                            modifier = Modifier.weight(1f)
                                        ) { Text(if (isAr) "إعادة المحاولة" else "Retry") }
                                    }
                                }
                            }
                        }

                        is UiState.Success -> {
                            val scanned = mealScanState.data
                            var multiplier by remember(scanned) { mutableFloatStateOf(1f) }
                            var editName by remember(scanned) { mutableStateOf(scanned.name) }
                            var editCal by remember(scanned) { mutableStateOf(scanned.cal.toString()) }
                            var editPro by remember(scanned) { mutableStateOf(scanned.protein.toString()) }
                            var editCarb by remember(scanned) { mutableStateOf(scanned.carbs.toString()) }
                            var editFat by remember(scanned) { mutableStateOf(scanned.fat.toString()) }

                            fun applyMultiplier(mult: Float) {
                                multiplier = mult
                                editCal = (scanned.cal * mult).roundToInt().toString()
                                editPro = (scanned.protein * mult).roundToInt().toString()
                                editCarb = (scanned.carbs * mult).roundToInt().toString()
                                editFat = (scanned.fat * mult).roundToInt().toString()
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, EmeraldOk.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            if (scanned.mode == "label") (if (isAr) "🏷️ نتيجة الملصق الغذائي" else "🏷️ Food Label Analysis")
                                            else (if (isAr) "🍽️ تحليل الوجبة بالذكاء الاصطناعي" else "🍽️ AI Meal Analysis"),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = EmeraldOk
                                        )
                                        if (scanned.servingInfo.isNotBlank()) {
                                            Text(
                                                scanned.servingInfo,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = CyanAccent
                                            )
                                        }
                                    }

                                    // Serving Multiplier Chips
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        listOf(0.5f to "0.5x", 1f to "1x", 1.5f to "1.5x", 2f to "2x").forEach { (m, lbl) ->
                                            FilterChip(
                                                selected = kotlin.math.abs(multiplier - m) < 0.05f,
                                                onClick = { applyMultiplier(m) },
                                                label = { Text(lbl) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }

                                    OutlinedTextField(
                                        value = editName,
                                        onValueChange = { editName = it },
                                        label = { Text(strings.mealNamePh) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedTextField(
                                            value = editCal,
                                            onValueChange = { editCal = it },
                                            label = { Text("Kcal") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = editPro,
                                            onValueChange = { editPro = it },
                                            label = { Text("P(g)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = editCarb,
                                            onValueChange = { editCarb = it },
                                            label = { Text("C(g)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = editFat,
                                            onValueChange = { editFat = it },
                                            label = { Text("F(g)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                capturedBitmap = null
                                                onClearScan()
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(50.dp)
                                                .testTag("scanner_retake_button")
                                        ) {
                                            Text(strings.retakePhoto)
                                        }
                                        Button(
                                            onClick = {
                                                val prefix = if (scanMode == "label") "🏷️ " else "📸 "
                                                val src = if (scanMode == "label") "ai_label" else "ai_cam"
                                                onLogMeal(
                                                    prefix + editName.trim().ifBlank { scanned.name },
                                                    editCal.toIntOrNull() ?: scanned.cal,
                                                    editPro.toIntOrNull() ?: scanned.protein,
                                                    editCarb.toIntOrNull() ?: scanned.carbs,
                                                    editFat.toIntOrNull() ?: scanned.fat,
                                                    src
                                                )
                                                capturedBitmap = null
                                                onClearScan()
                                                onClose()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                            modifier = Modifier
                                                .weight(1.4f)
                                                .height(50.dp)
                                                .testTag("scanner_log_button")
                                        ) {
                                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(strings.logScannedMeal)
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
private fun ScannerGuideOverlay(isLabelMode: Boolean) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val boxW = if (isLabelMode) size.width * 0.68f else size.width * 0.82f
        val boxH = if (isLabelMode) size.height * 0.64f else size.width * 0.65f
        val left = (size.width - boxW) / 2f
        val top = (size.height - boxH) / 2f
        val strokeColor = if (isLabelMode) EmeraldOk else IndigoBright

        drawRoundRect(
            color = strokeColor.copy(alpha = 0.85f),
            topLeft = Offset(left, top),
            size = Size(boxW, boxH),
            cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx())
        )
    }
}

private fun imageProxyToRotatedBitmap(image: ImageProxy): Bitmap {
    val raw = image.toBitmap()
    val deg = image.imageInfo.rotationDegrees.toFloat()
    if (deg == 0f) return raw
    val matrix = Matrix().apply { postRotate(deg) }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
}

private fun decodeScannerBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
            decoder.isMutableRequired = true
        }
    } else {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }
}.getOrNull()
