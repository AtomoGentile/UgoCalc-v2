package com.ugocalc.app.ui.camera

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.ui.components.LoadingOverlay
import com.ugocalc.app.ui.theme.UgoSemanticColors
import com.ugocalc.app.util.OpticalGeometryCalculator
import com.ugocalc.app.util.OverlayPermissionHelper
import com.ugocalc.app.util.isCameraPermissionGranted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Hub di acquisizione foto (§2.4 punto 2, CameraX Pro Studio 4.1.0):
 * - apre direttamente [CameraXStudioView] (preview integrata con livella 60fps, telemetria Camera2
 *   in tempo reale, tap-to-focus, pinch-to-zoom, flash/torcia e filmstrip);
 * - mantiene la scelta da galleria e un fallback discreto `"App Camera"` verso la fotocamera di
 *   sistema (con eventuale [CameraAngleOverlayService] se abilitato);
 * - consente l'ispezione a schermo intero con pinch-to-zoom di qualsiasi foto scattata.
 */
@Composable
fun CameraCaptureScreen(
    selectedModels: List<AiModel>,
    onAnalysisReady: () -> Unit,
    onCancelled: () -> Unit,
    viewModel: CameraCaptureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.analysisDone) {
        if (uiState.analysisDone) {
            onAnalysisReady()
            viewModel.onAnalysisDoneConsumed()
        }
    }

    // rememberSaveable: la rotazione durante la fotocamera esterna può ricreare l'Activity.
    var pendingCapturePath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSystemCameraFallback by rememberSaveable { mutableStateOf(false) }
    var zoomIndex by remember { mutableStateOf<Int?>(null) }
    var hasCameraPermission by remember { mutableStateOf(isCameraPermissionGranted(context)) }
    var cameraPermissionDeniedMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun stopAngleOverlay() {
        context.stopService(Intent(context, CameraAngleOverlayService::class.java))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                stopAngleOverlay()
                hasCameraPermission = isCameraPermissionGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopAngleOverlay()
        }
    }

    LaunchedEffect(uiState.capturedImages.size) {
        val currentZoom = zoomIndex
        if (currentZoom != null && currentZoom >= uiState.capturedImages.size) {
            zoomIndex = null
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val measuredAngle = CameraAngleOverlayService.LastReading.value
        stopAngleOverlay()
        val file = pendingCapturePath?.let(::File)
        pendingCapturePath = null
        if (success && file != null) {
            coroutineScope.launch(Dispatchers.IO) {
                val readResult = runCatching {
                    val bytes = file.readBytes()
                    val capturedAt = file.lastModified()
                    bytes to capturedAt
                }
                file.delete()
                readResult.onSuccess { (bytes, capturedAtEpochMillis) ->
                    if (bytes.isNotEmpty()) {
                        viewModel.onPhotoCaptured(
                            imageBytes = bytes,
                            capturedAtEpochMillis = capturedAtEpochMillis,
                            measuredAngle = measuredAngle,
                            liveTelemetry = null,
                            captureSource = OpticalGeometryCalculator.CAPTURE_SOURCE_SYSTEM_CAMERA,
                        )
                    }
                }
            }
        } else {
            file?.delete()
        }
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onGalleryUrisSelected(uris)
        }
    }

    fun getNextTargetIndex(): Int {
        val images = uiState.capturedImages
        val usedLabels = images.asSequence().mapNotNull { it.measuredAngle?.target?.label }.toSet()
        val firstUnused = PHOTO_ANGLE_TARGETS.indexOfFirst { it.label !in usedLabels }
        return if (firstUnused == -1 || usedLabels.size < images.size) {
            images.size
        } else {
            firstUnused
        }
    }

    fun launchSystemCameraForIndex(index: Int) {
        if (uiState.capturedImages.size >= MAX_PHOTOS || uiState.isAnalyzing) return
        val target = PHOTO_ANGLE_TARGETS[index.coerceIn(0, PHOTO_ANGLE_TARGETS.lastIndex)]
        if (uiState.angleOverlayEnabled && OverlayPermissionHelper.hasOverlayPermission(context)) {
            context.startService(CameraAngleOverlayService.buildStartIntent(context, target))
        }
        val photosDir = File(context.cacheDir, "photos").apply { mkdirs() }
        val file = File(photosDir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCapturePath = file.absolutePath
        takePictureLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        val launchFallback = pendingSystemCameraFallback
        pendingSystemCameraFallback = false
        if (granted) {
            cameraPermissionDeniedMessage = null
            if (launchFallback) {
                launchSystemCameraForIndex(getNextTargetIndex())
            }
        } else {
            cameraPermissionDeniedMessage = "Permesso fotocamera negato: puoi concederlo dalle impostazioni oppure scegliere le foto dalla galleria."
        }
    }

    fun startSystemCameraFallback() {
        if (uiState.capturedImages.size >= MAX_PHOTOS || uiState.isAnalyzing) return
        if (!isCameraPermissionGranted(context)) {
            pendingSystemCameraFallback = true
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            cameraPermissionDeniedMessage = null
            launchSystemCameraForIndex(getNextTargetIndex())
        }
    }

    val images = uiState.capturedImages
    val activeZoomIndex = zoomIndex
    val nextTargetIndex = getNextTargetIndex().coerceIn(0, PHOTO_ANGLE_TARGETS.lastIndex)
    val nextTarget = PHOTO_ANGLE_TARGETS[nextTargetIndex]

    val activeBannerError = uiState.analysisError ?: uiState.galleryError ?: cameraPermissionDeniedMessage

    Box(modifier = Modifier.fillMaxSize()) {
        if (activeZoomIndex != null && activeZoomIndex in images.indices) {
            PhotoZoomReview(
                imageBytes = images[activeZoomIndex].bytes,
                onRemove = {
                    viewModel.removePhoto(activeZoomIndex)
                    zoomIndex = null
                },
                onClose = { zoomIndex = null },
            )
        } else {
            CameraXStudioView(
                hasCameraPermission = hasCameraPermission,
                capturedImages = images,
                nextTarget = nextTarget,
                isAnalyzing = uiState.isAnalyzing,
                isLoadingGallery = uiState.isLoadingGallery,
                availableCalibrations = uiState.availableCalibrations,
                selectedCalibrationIds = uiState.selectedCalibrationIds,
                preMealGlucoseMgDl = uiState.preMealGlucoseMgDl,
                onSetPreMealGlucose = viewModel::setPreMealGlucoseMgDl,
                bannerErrorMessage = activeBannerError,
                onDismissBannerError = {
                    cameraPermissionDeniedMessage = null
                    viewModel.dismissGalleryError()
                    viewModel.dismissAnalysisError()
                },
                onRequestCameraPermission = {
                    pendingSystemCameraFallback = false
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onPhotoCaptured = { bytes, capturedAtEpochMillis, measuredAngle, liveTelemetry ->
                    viewModel.onPhotoCaptured(
                        imageBytes = bytes,
                        capturedAtEpochMillis = capturedAtEpochMillis,
                        measuredAngle = measuredAngle,
                        liveTelemetry = liveTelemetry,
                        captureSource = OpticalGeometryCalculator.CAPTURE_SOURCE_CAMERAX_PRO,
                    )
                },
                onRemovePhoto = viewModel::removePhoto,
                onInspectPhoto = { index -> zoomIndex = index },
                onOpenGallery = {
                    galleryPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onOpenSystemCameraFallback = ::startSystemCameraFallback,
                onToggleCalibration = viewModel::toggleCalibrationSelection,
                onConfirmAnalysis = { viewModel.confirmPhotos(selectedModels) },
                onBack = onCancelled,
            )
        }

        if (uiState.isAnalyzing) {
            LoadingOverlay(statusText = "Analisi fotografica AI in corso...")
        }

        val qualityWarning = uiState.qualityWarning
        if (qualityWarning != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissQualityWarning,
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = UgoSemanticColors.warning,
                    )
                },
                title = {
                    Text(
                        text = "Qualità foto da verificare",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Il controllo qualità ha rilevato alcuni problemi che possono ridurre l'accuratezza della stima:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        qualityWarning.reasons.forEach { reason ->
                            Text(
                                text = "• $reason",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.confirmPhotosIgnoringQuality(selectedModels) },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Analizza comunque")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = viewModel::dismissQualityWarning,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Rifai le foto")
                    }
                },
            )
        }
    }
}

/**
 * Ispezione a schermo intero con sfondo nero ad alto contrasto e controlli ben visibili.
 */
@Composable
private fun PhotoZoomReview(
    imageBytes: ByteArray,
    onRemove: () -> Unit,
    onClose: () -> Unit,
) {
    var bitmap by remember(imageBytes) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(imageBytes) {
        bitmap = withContext(Dispatchers.Default) { decodeUprightBitmapForDisplay(imageBytes) }
    }
    var scale by remember(imageBytes) { mutableFloatStateOf(1f) }
    var offset by remember(imageBytes) { mutableStateOf(Offset.Zero) }
    var containerSize by remember(imageBytes) { mutableStateOf(IntSize.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 6f)
        val maxOffsetX = (containerSize.width * (newScale - 1) / 2f).coerceAtLeast(0f)
        val maxOffsetY = (containerSize.height * (newScale - 1) / 2f).coerceAtLeast(0f)
        offset = Offset(
            (offset.x + panChange.x * newScale).coerceIn(-maxOffsetX, maxOffsetX),
            (offset.y + panChange.y * newScale).coerceIn(-maxOffsetY, maxOffsetY),
        )
        scale = newScale
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Foto del piatto — pizzica per ingrandire",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { containerSize = it }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    )
                    .transformable(state = transformState),
            )
        } else {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.65f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Chiudi anteprima",
                    tint = Color.White,
                )
            }
        }

        Surface(
            color = Color.Black.copy(alpha = 0.72f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onRemove,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Rimuovi foto")
                }
                Button(
                    onClick = onClose,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Torna allo studio")
                }
            }
        }
    }
}

/** Estrae la rotazione (in gradi) indicata dal tag EXIF "Orientation". */
private fun exifRotationDegrees(imageBytes: ByteArray): Int = try {
    when (ExifInterface(ByteArrayInputStream(imageBytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
} catch (_: Exception) {
    0
}

private const val MAX_ZOOM_VIEW_DIMENSION_PX = 4096

private fun decodeUprightBitmapForDisplay(imageBytes: ByteArray): Bitmap? =
    decodeUprightBitmap(imageBytes, MAX_ZOOM_VIEW_DIMENSION_PX)

/** Decodifica [imageBytes] raddrizzata secondo l'EXIF in modo sicuro, restituendo `null` per dati non validi. */
private fun decodeUprightBitmap(imageBytes: ByteArray, maxDimensionPx: Int): Bitmap? {
    if (imageBytes.isEmpty()) return null
    return try {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, boundsOptions)
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

        var sampleSize = 1
        while ((boundsOptions.outWidth / sampleSize > maxDimensionPx) || (boundsOptions.outHeight / sampleSize > maxDimensionPx)) {
            sampleSize *= 2
        }

        val decoded = BitmapFactory.decodeByteArray(
            imageBytes,
            0,
            imageBytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: return null

        val rotationDegrees = exifRotationDegrees(imageBytes)
        if (rotationDegrees == 0) return decoded

        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated !== decoded) decoded.recycle()
        rotated
    } catch (_: OutOfMemoryError) {
        null
    } catch (_: Exception) {
        null
    }
}
