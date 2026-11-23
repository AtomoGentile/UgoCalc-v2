package com.ugocalc.app.ui.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.camera.core.ZoomState
import com.ugocalc.app.data.local.db.entity.ScaleCalibrationEntity
import com.ugocalc.app.util.Camera2LiveMetadata
import com.ugocalc.app.util.OpticalGeometryCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

private const val STUDIO_TAG = "CameraXStudio"

private val EmeraldLocked = Color(0xFF10B981)
private val AmberCaution = Color(0xFFF59E0B)
private val StudioScrimDark = Color(0xCC090B0E)
private val StudioPillBg = Color(0xB3101419)

private enum class StudioFlashMode {
    OFF,
    AUTO,
    TORCH,
}

/**
 * CameraX Pro Studio a schermo intero:
 * - preview CameraX (`ProcessCameraProvider` + `PreviewView`) con tap-to-focus, pinch-to-zoom,
 *   chip `1×`/`2×`, controllo flash/torcia e telemetria Camera2 + Y-plane in tempo reale;
 * - livella ottica 60fps in Canvas (dall'alto 90° e laterale 45°) con aggancio verde smeraldo e
 *   feedback aptico al lock;
 * - HUD telemetrico collassabile, badge passo guidato (`RECOMMENDED_MIN_PHOTOS = 2`), filmstrip con
 *   anteprima/rimozione, pulsante galleria e fallback discreto `"App Camera"`.
 */
@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun CameraXStudioView(
    hasCameraPermission: Boolean,
    capturedImages: List<CapturedPhoto>,
    nextTarget: PhotoAngleTarget,
    isAnalyzing: Boolean,
    isLoadingGallery: Boolean,
    availableCalibrations: List<ScaleCalibrationEntity>,
    selectedCalibrationIds: Set<Long>,
    preMealGlucoseMgDl: Int? = null,
    onSetPreMealGlucose: ((Int?) -> Unit)? = null,
    bannerErrorMessage: String?,
    onDismissBannerError: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onPhotoCaptured: (
        bytes: ByteArray,
        capturedAtEpochMillis: Long,
        measuredAngle: MeasuredAngle?,
        liveTelemetry: Camera2LiveMetadata,
    ) -> Unit,
    onRemovePhoto: (Int) -> Unit,
    onInspectPhoto: (Int) -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSystemCameraFallback: () -> Unit,
    onToggleCalibration: (Long) -> Unit,
    onConfirmAnalysis: () -> Unit,
    onBack: () -> Unit,
) {
    if (!hasCameraPermission) {
        CameraPermissionRationaleOverlay(
            onRequestPermission = onRequestCameraPermission,
            onOpenSystemCameraFallback = onOpenSystemCameraFallback,
            onOpenGallery = onOpenGallery,
            onBack = onBack,
        )
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current

    var telemetry by remember { mutableStateOf(LiveCameraTelemetryState()) }
    var hudExpanded by remember { mutableStateOf(false) }
    var flashMode by remember { mutableStateOf(StudioFlashMode.OFF) }
    var isCapturing by remember { mutableStateOf(false) }
    var cameraBindError by remember { mutableStateOf<String?>(null) }
    var captureError by remember { mutableStateOf<String?>(null) }
    var focusTapOffset by remember { mutableStateOf<Offset?>(null) }
    var showCalibrationMenu by remember { mutableStateOf(false) }
    var showPreMealGlucoseDialog by remember { mutableStateOf(false) }

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var boundImageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var boundCameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    val attitudeTracker = remember(context) {
        LiveAttitudeTracker(context) { reading ->
            telemetry = telemetry.copy(
                pitchDeg = reading.pitchDeg,
                rollDeg = reading.rollDeg,
                turnDeg = reading.turnDeg,
                hasAttitudeReading = true,
                motionRadPerSec = reading.motionRadPerSec ?: telemetry.motionRadPerSec,
            )
        }
    }

    // Azzera il riferimento di rotazione se tutte le foto vengono eliminate
    LaunchedEffect(capturedImages.size) {
        if (capturedImages.isEmpty()) {
            attitudeTracker.clearReferenceHeading()
        }
    }

    val captureCallback = remember(context) {
        Camera2TelemetryCaptureCallback(throttleMs = 100L) { packet ->
            mainExecutor.execute {
                val cam = boundCamera
                val (resolvedId, physicalSize) = if (cam != null) {
                    extractCamera2SensorInfo(cam.cameraInfo, context, packet.activePhysicalCameraId)
                } else {
                    packet.activePhysicalCameraId to null
                }
                val previewW = previewViewRef?.width?.takeIf { it > 0 } ?: 3000
                val previewH = previewViewRef?.height?.takeIf { it > 0 } ?: 4000
                telemetry = telemetry.copy(
                    activeCameraId = resolvedId ?: telemetry.activeCameraId,
                    sensorWidthMm = physicalSize?.width?.toDouble() ?: telemetry.sensorWidthMm,
                    sensorHeightMm = physicalSize?.height?.toDouble() ?: telemetry.sensorHeightMm,
                    focusDistanceDiopters = packet.focusDistanceDiopters ?: telemetry.focusDistanceDiopters,
                    afState = packet.afState ?: telemetry.afState,
                    oisEnabled = packet.oisEnabled ?: telemetry.oisEnabled,
                    isoValue = packet.isoValue ?: telemetry.isoValue,
                    exposureTimeSeconds = packet.exposureTimeSeconds ?: telemetry.exposureTimeSeconds,
                    focalLengthMm = packet.focalLengthMm ?: telemetry.focalLengthMm,
                    aperture = packet.aperture ?: telemetry.aperture,
                ).withComputedOptics(previewWidthPx = previewW, previewHeightPx = previewH)
            }
        }
    }

    val qualityAnalyzer = remember(mainExecutor) {
        LiveQualityImageAnalyzer(minIntervalMs = 250L) { brightness, sharpness ->
            mainExecutor.execute {
                telemetry = telemetry.copy(
                    liveBrightness = brightness,
                    liveSharpness = sharpness,
                )
            }
        }
    }

    // Avvio/arresto sensori inerziali a 60fps e cleanup completo di CameraX e thread pool
    DisposableEffect(lifecycleOwner) {
        attitudeTracker.start()
        onDispose {
            attitudeTracker.stop()
            runCatching { boundCamera?.cameraControl?.enableTorch(false) }
            runCatching { boundCameraProvider?.unbindAll() }
            boundCamera = null
            boundImageCapture = null
            boundCameraProvider = null
            cameraExecutor.shutdown()
        }
    }

    // Binding CameraX quando PreviewView è pronto
    LaunchedEffect(previewViewRef, lifecycleOwner) {
        val previewView = previewViewRef ?: return@LaunchedEffect
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                runCatching {
                    val provider = providerFuture.get()
                    boundCameraProvider = provider

                    val previewBuilder = Preview.Builder()
                    Camera2Interop.Extender(previewBuilder)
                        .setSessionCaptureCallback(captureCallback)
                    val preview = previewBuilder.build().apply {
                        surfaceProvider = previewView.surfaceProvider
                    }

                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                        .build()

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(cameraExecutor, qualityAnalyzer) }

                    provider.unbindAll()
                    val camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture,
                        imageAnalysis,
                    )

                    boundCamera = camera
                    boundImageCapture = imageCapture
                    cameraBindError = null

                    val (cameraId, sensorSize) = extractCamera2SensorInfo(camera.cameraInfo, context)
                    val initialZoom = camera.cameraInfo.zoomState.value
                    telemetry = telemetry.copy(
                        activeCameraId = cameraId,
                        sensorWidthMm = sensorSize?.width?.toDouble(),
                        sensorHeightMm = sensorSize?.height?.toDouble(),
                        zoomRatio = initialZoom?.zoomRatio ?: 1f,
                        minZoomRatio = initialZoom?.minZoomRatio ?: 1f,
                        maxZoomRatio = initialZoom?.maxZoomRatio ?: 1f,
                    )
                }.onFailure { err ->
                    Log.e(STUDIO_TAG, "Unable to bind CameraX use cases", err)
                    cameraBindError = "Impossibile avviare la fotocamera integrata (${err.localizedMessage ?: "errore hardware"})."
                }
            },
            mainExecutor,
        )
    }

    // Osserva ZoomState con rimozione garantita su dispose
    DisposableEffect(boundCamera, lifecycleOwner) {
        val cam = boundCamera ?: return@DisposableEffect onDispose {}
        val observer = Observer<ZoomState> { zoomState ->
            val pw = previewViewRef?.width?.takeIf { it > 0 } ?: 3000
            val ph = previewViewRef?.height?.takeIf { it > 0 } ?: 4000
            telemetry = telemetry.copy(
                zoomRatio = zoomState.zoomRatio,
                minZoomRatio = zoomState.minZoomRatio,
                maxZoomRatio = zoomState.maxZoomRatio,
            ).withComputedOptics(previewWidthPx = pw, previewHeightPx = ph)
        }
        cam.cameraInfo.zoomState.observe(lifecycleOwner, observer)
        onDispose {
            cam.cameraInfo.zoomState.removeObserver(observer)
        }
    }

    // Aggiorna flash/torcia quando l'utente cambia modalità
    LaunchedEffect(flashMode, boundCamera, boundImageCapture) {
        val cam = boundCamera ?: return@LaunchedEffect
        val cap = boundImageCapture ?: return@LaunchedEffect
        when (flashMode) {
            StudioFlashMode.OFF -> {
                cam.cameraControl.enableTorch(false)
                cap.flashMode = ImageCapture.FLASH_MODE_OFF
            }
            StudioFlashMode.AUTO -> {
                cam.cameraControl.enableTorch(false)
                cap.flashMode = ImageCapture.FLASH_MODE_AUTO
            }
            StudioFlashMode.TORCH -> {
                cam.cameraControl.enableTorch(true)
                cap.flashMode = ImageCapture.FLASH_MODE_OFF
            }
        }
    }

    // Dissolvenza dell'anello tap-to-focus
    LaunchedEffect(focusTapOffset) {
        if (focusTapOffset != null) {
            delay(1100L)
            focusTapOffset = null
        }
    }

    val aligned = isAngleAligned(telemetry, nextTarget)
    val (coachingText, coachingSeverity) = computeLiveCoachingHint(telemetry, nextTarget, aligned)

    // Feedback aptico una tantum sul passaggio da non allineato -> agganciato (lock)
    var wasAligned by remember { mutableStateOf(false) }
    LaunchedEffect(aligned) {
        if (aligned && !wasAligned) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        wasAligned = aligned
    }

    fun triggerShutterCapture() {
        val imageCapture = boundImageCapture ?: return
        if (isCapturing || isAnalyzing || capturedImages.size >= MAX_PHOTOS || cameraExecutor.isShutdown) return
        isCapturing = true
        captureError = null
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

        // Istante esatto dello scatto per GyroscopeMotionTracker.peakMotionNear + istantanea ottica
        val shutterTimestampMs = System.currentTimeMillis()
        val latestPacket = captureCallback.latestPacket
        val cam = boundCamera
        val (activeId, activeSensor) = if (cam != null) {
            extractCamera2SensorInfo(cam.cameraInfo, context, latestPacket?.activePhysicalCameraId)
        } else {
            telemetry.activeCameraId to null
        }
        val snapshotTelemetry = telemetry.copy(
            activeCameraId = activeId ?: telemetry.activeCameraId,
            sensorWidthMm = activeSensor?.width?.toDouble() ?: telemetry.sensorWidthMm,
            sensorHeightMm = activeSensor?.height?.toDouble() ?: telemetry.sensorHeightMm,
            focusDistanceDiopters = latestPacket?.focusDistanceDiopters ?: telemetry.focusDistanceDiopters,
            afState = latestPacket?.afState ?: telemetry.afState,
            oisEnabled = latestPacket?.oisEnabled ?: telemetry.oisEnabled,
            isoValue = latestPacket?.isoValue ?: telemetry.isoValue,
            exposureTimeSeconds = latestPacket?.exposureTimeSeconds ?: telemetry.exposureTimeSeconds,
            focalLengthMm = latestPacket?.focalLengthMm ?: telemetry.focalLengthMm,
            aperture = latestPacket?.aperture ?: telemetry.aperture,
        )
        val measuredAngle = if (snapshotTelemetry.hasAttitudeReading) {
            MeasuredAngle(
                pitchDeg = snapshotTelemetry.pitchDeg,
                turnDeg = snapshotTelemetry.turnDeg,
                target = nextTarget,
                motionRadPerSec = snapshotTelemetry.motionRadPerSec ?: 0.0,
            )
        } else {
            null
        }
        val liveMetadata = snapshotTelemetry.toCamera2LiveMetadata()

        // Fissa l'orientamento di riferimento al primo scatto (e al primo scatto laterale)
        if (capturedImages.isEmpty() || nextTarget.pitchDeg < 80f && nextTarget.turnDeg == 0f) {
            attitudeTracker.captureReferenceHeading()
        }

        val outputStream = ByteArrayOutputStream()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputStream).build()

        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val bytes = outputStream.toByteArray()
                    mainExecutor.execute {
                        isCapturing = false
                        if (bytes.isNotEmpty()) {
                            onPhotoCaptured(bytes, shutterTimestampMs, measuredAngle, liveMetadata)
                        } else {
                            captureError = "Scatto vuoto: riprova."
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(STUDIO_TAG, "ImageCapture failed", exception)
                    mainExecutor.execute {
                        isCapturing = false
                        captureError = "Errore scatto: ${exception.localizedMessage ?: "riprova o usa App Camera"}"
                    }
                }
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (cameraBindError != null) {
            CameraUnavailableFallback(
                message = cameraBindError.orEmpty(),
                onOpenSystemCameraFallback = onOpenSystemCameraFallback,
                onOpenGallery = onOpenGallery,
                onBack = onBack,
            )
            return@Box
        }

        // 1. Preview CameraX full-screen con pinch-to-zoom + tap-to-focus
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewViewRef = this
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        val cam = boundCamera ?: return@detectTransformGestures
                        if (abs(zoomChange - 1f) > 0.005f) {
                            val current = telemetry.zoomRatio
                            val next = (current * zoomChange).coerceIn(telemetry.minZoomRatio, telemetry.maxZoomRatio)
                            cam.cameraControl.setZoomRatio(next)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val previewView = previewViewRef ?: return@detectTapGestures
                        val cam = boundCamera ?: return@detectTapGestures
                        focusTapOffset = offset
                        val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                            .setAutoCancelDuration(3, TimeUnit.SECONDS)
                            .build()
                        cam.cameraControl.startFocusAndMetering(action)
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                },
        )

        // 2. Livella 60fps in Canvas al centro dell'inquadratura
        SpiritLevelReticle(
            telemetry = telemetry,
            target = nextTarget,
            aligned = aligned,
            modifier = Modifier.fillMaxSize(),
        )

        // 3. Anello visivo Tap-to-Focus
        focusTapOffset?.let { tapPos ->
            val density = LocalDensity.current
            val ringSizePx = with(density) { 74.dp.toPx() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (tapPos.x - ringSizePx / 2f).roundToInt(),
                            (tapPos.y - ringSizePx / 2f).roundToInt(),
                        )
                    }
                    .size(74.dp)
                    .border(
                        width = 1.8.dp,
                        color = if (telemetry.isAfLocked) EmeraldLocked else Color(0xFFFBBF24),
                        shape = RoundedCornerShape(14.dp),
                    ),
            )
        }

        // 4. Barra superiore: Indietro, Passo guidato, App Camera (discreto) e Flash/Torcia
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent),
                    ),
                )
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    shape = CircleShape,
                    color = StudioPillBg,
                    modifier = Modifier.size(40.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Indietro",
                            tint = Color.White,
                        )
                    }
                }

                // Badge passo guidato (RECOMMENDED_MIN_PHOTOS = 2)
                StepGuidanceBadge(
                    photoCount = capturedImages.size,
                    target = nextTarget,
                    aligned = aligned,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Fallback discreto alla fotocamera di sistema
                    Surface(
                        onClick = onOpenSystemCameraFallback,
                        shape = RoundedCornerShape(50),
                        color = StudioPillBg,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                    ) {
                        Text(
                            text = "App Camera",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.88f),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                    }

                    // Toggle Flash / Auto / Torcia
                    Surface(
                        shape = CircleShape,
                        color = if (flashMode == StudioFlashMode.TORCH) AmberCaution.copy(alpha = 0.30f) else StudioPillBg,
                        border = if (flashMode != StudioFlashMode.OFF) {
                            BorderStroke(1.dp, AmberCaution.copy(alpha = 0.65f))
                        } else null,
                        modifier = Modifier.size(40.dp),
                    ) {
                        IconButton(
                            onClick = {
                                flashMode = when (flashMode) {
                                    StudioFlashMode.OFF -> StudioFlashMode.AUTO
                                    StudioFlashMode.AUTO -> StudioFlashMode.TORCH
                                    StudioFlashMode.TORCH -> StudioFlashMode.OFF
                                }
                            },
                        ) {
                            val icon = when (flashMode) {
                                StudioFlashMode.OFF -> Icons.Filled.FlashOff
                                StudioFlashMode.AUTO -> Icons.Filled.FlashAuto
                                StudioFlashMode.TORCH -> Icons.Filled.FlashlightOn
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = "Flash o torcia",
                                tint = if (flashMode == StudioFlashMode.OFF) Color.White else AmberCaution,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            // Pillola Telemetria Live collassabile
            CollapsibleTelemetryHud(
                telemetry = telemetry,
                expanded = hudExpanded,
                onToggleExpanded = { hudExpanded = !hudExpanded },
            )

            // Suggerimento live conciso
            CoachingHintPill(
                text = coachingText,
                severity = coachingSeverity,
            )

            // Eventuale errore discreto (galleria / scatto / analisi)
            val activeError = captureError ?: bannerErrorMessage
            if (activeError != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xDC991B1B),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = activeError,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                captureError = null
                                onDismissBannerError()
                            },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Chiudi avviso",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }

        // 5. Dock inferiore: Zoom chips, Calibrazioni opzionali, Filmstrip e Barra Scatto
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, StudioScrimDark, Color.Black.copy(alpha = 0.92f)),
                    ),
                )
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Riga Zoom chips (0.6× / 1× / 2×) + selettore calibrazioni opzionale
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                ZoomQuickChips(
                    zoomRatio = telemetry.zoomRatio,
                    minZoomRatio = telemetry.minZoomRatio,
                    maxZoomRatio = telemetry.maxZoomRatio,
                    onSelectZoom = { targetZoom ->
                        boundCamera?.cameraControl?.setZoomRatio(
                            targetZoom.coerceIn(telemetry.minZoomRatio, telemetry.maxZoomRatio),
                        )
                    },
                )

                if (availableCalibrations.isNotEmpty()) {
                    Spacer(Modifier.width(10.dp))
                    Box {
                        val selCount = selectedCalibrationIds.size
                        Surface(
                            onClick = { showCalibrationMenu = true },
                            shape = RoundedCornerShape(50),
                            color = if (selCount > 0) EmeraldLocked.copy(alpha = 0.25f) else StudioPillBg,
                            border = BorderStroke(
                                1.dp,
                                if (selCount > 0) EmeraldLocked else Color.White.copy(alpha = 0.25f),
                            ),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Straighten,
                                    contentDescription = null,
                                    tint = if (selCount > 0) EmeraldLocked else Color.White,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = if (selCount > 0) "Calib. ($selCount)" else "Calibrazioni",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showCalibrationMenu,
                            onDismissRequest = { showCalibrationMenu = false },
                        ) {
                            availableCalibrations.forEach { cal ->
                                val checked = cal.id in selectedCalibrationIds
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (checked) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .padding(end = 6.dp),
                                                )
                                            }
                                            Column {
                                                Text(cal.name, fontWeight = FontWeight.Medium)
                                                Text(
                                                    "${cal.valueCm} cm",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    },
                                    onClick = { onToggleCalibration(cal.id) },
                                )
                            }
                        }
                    }
                }

                if (onSetPreMealGlucose != null) {
                    Spacer(Modifier.width(10.dp))
                    val hasGlucose = preMealGlucoseMgDl != null
                    Surface(
                        onClick = { showPreMealGlucoseDialog = true },
                        shape = RoundedCornerShape(50),
                        color = if (hasGlucose) EmeraldLocked.copy(alpha = 0.25f) else StudioPillBg,
                        border = BorderStroke(
                            1.dp,
                            if (hasGlucose) EmeraldLocked else Color.White.copy(alpha = 0.25f),
                        ),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ShowChart,
                                contentDescription = null,
                                tint = if (hasGlucose) EmeraldLocked else Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = if (hasGlucose) "Pre-pasto: ${preMealGlucoseMgDl}" else "Pre-pasto",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            // Filmstrip delle foto catturate (con tocco per zoom e X per rimozione)
            if (capturedImages.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                ) {
                    itemsIndexed(capturedImages) { index, photo ->
                        StudioFilmstripThumbnail(
                            imageBytes = photo.bytes,
                            index = index,
                            onSelect = { onInspectPhoto(index) },
                            onRemove = { onRemovePhoto(index) },
                        )
                    }
                }
            }

            // Barra controlli principale: Galleria | Otturatore Tattile | Analizza
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Pulsante Galleria
                Surface(
                    onClick = onOpenGallery,
                    enabled = capturedImages.size < MAX_PHOTOS && !isAnalyzing && !isLoadingGallery,
                    shape = CircleShape,
                    color = StudioPillBg,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isLoadingGallery) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.PhotoLibrary,
                                contentDescription = "Scegli da Galleria",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }

                // Pulsante Scatto Tattile
                val shutterEnabled = capturedImages.size < MAX_PHOTOS && !isCapturing && !isAnalyzing
                val shutterScale by animateFloatAsState(
                    targetValue = if (isCapturing) 0.88f else 1f,
                    animationSpec = tween(110),
                    label = "shutterScale",
                )
                val innerColor by animateColorAsState(
                    targetValue = when {
                        !shutterEnabled -> Color.White.copy(alpha = 0.35f)
                        aligned -> EmeraldLocked
                        else -> Color.White
                    },
                    animationSpec = tween(180),
                    label = "shutterColor",
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(76.dp)
                        .scale(shutterScale)
                        .clip(CircleShape)
                        .border(
                            width = 3.5.dp,
                            color = if (aligned) EmeraldLocked else Color.White,
                            shape = CircleShape,
                        )
                        .clickable(enabled = shutterEnabled, onClick = ::triggerShutterCapture)
                        .padding(6.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(innerColor),
                    ) {
                        if (isCapturing) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }

                // Pulsante Analizza (o contatore passi quando ancora 0 foto)
                if (capturedImages.isNotEmpty()) {
                    Button(
                        onClick = onConfirmAnalysis,
                        enabled = !isAnalyzing && !isLoadingGallery && !isCapturing,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldLocked,
                            contentColor = Color(0xFF022C22),
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier.height(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Analizza (${capturedImages.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = StudioPillBg,
                        modifier = Modifier.height(48.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        ) {
                            Text(
                                text = "0/$RECOMMENDED_MIN_PHOTOS",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.72f),
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPreMealGlucoseDialog && onSetPreMealGlucose != null) {
        var inputText by remember(preMealGlucoseMgDl) {
            mutableStateOf(preMealGlucoseMgDl?.toString().orEmpty())
        }
        val parsed = inputText.trim().toIntOrNull()
        val isValid = inputText.isBlank() || (parsed != null && parsed in 40..400)
        AlertDialog(
            onDismissRequest = { showPreMealGlucoseDialog = false },
            shape = RoundedCornerShape(20.dp),
            title = { Text("Glicemia pre-pasto", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Inserisci la glicemia attuale prima di questo pasto (40–400 mg/dL).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { next -> inputText = next.filter { it.isDigit() }.take(3) },
                        label = { Text("mg/dL (opzionale)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = !isValid,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSetPreMealGlucose(if (inputText.isBlank()) null else parsed?.coerceIn(40, 400))
                        showPreMealGlucoseDialog = false
                    },
                    enabled = isValid,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Salva")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPreMealGlucoseDialog = false }) {
                    Text("Annulla")
                }
            },
        )
    }
}

/**
 * Livella ottica 60fps disegnata in Canvas:
 * - per lo scatto dall'alto (target ~90°): mirino circolare con bolla mobile (pitch/roll) che si
 *   illumina di verde smeraldo al raggiungimento del lock;
 * - per gli scatti laterali (target ~45°): orizzonte artificiale + barra di inclinazione verticale
 *   con tacca target e indicatore di rotazione (se richiesta sugli scatti 3–5).
 */
@Composable
private fun SpiritLevelReticle(
    telemetry: LiveCameraTelemetryState,
    target: PhotoAngleTarget,
    aligned: Boolean,
    modifier: Modifier = Modifier,
) {
    val activeColor by animateColorAsState(
        targetValue = if (aligned) EmeraldLocked else Color.White.copy(alpha = 0.85f),
        animationSpec = tween(160),
        label = "reticleColor",
    )

    val isTopDown = target.pitchDeg >= 80f

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height * 0.46f)
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))

        if (isTopDown) {
            val outerRadius = 72.dp.toPx()
            val lockRadius = 18.dp.toPx()

            // Cerchio guida esterno (inquadratura piatto)
            drawCircle(
                color = activeColor.copy(alpha = if (aligned) 0.55f else 0.30f),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = dashEffect),
            )

            // Crociere di riferimento centrali
            val crossHalf = 28.dp.toPx()
            drawLine(
                color = activeColor.copy(alpha = 0.45f),
                start = Offset(center.x - crossHalf, center.y),
                end = Offset(center.x + crossHalf, center.y),
                strokeWidth = 1.dp.toPx(),
            )
            drawLine(
                color = activeColor.copy(alpha = 0.45f),
                start = Offset(center.x, center.y - crossHalf),
                end = Offset(center.x, center.y + crossHalf),
                strokeWidth = 1.dp.toPx(),
            )

            // Anello centrale di lock
            drawCircle(
                color = activeColor,
                radius = lockRadius,
                center = center,
                style = Stroke(width = if (aligned) 2.5.dp.toPx() else 1.5.dp.toPx()),
            )

            // Bolla live (pitch deviation su Y, roll su X)
            if (telemetry.hasAttitudeReading) {
                val pitchErr = (target.pitchDeg - telemetry.pitchDeg).coerceIn(-35f, 35f)
                val rollErr = telemetry.rollDeg.coerceIn(-35f, 35f)
                val maxTravel = outerRadius - 12.dp.toPx()
                val bubbleCenter = Offset(
                    x = center.x + (rollErr / 35f) * maxTravel,
                    y = center.y + (pitchErr / 35f) * maxTravel,
                )
                if (aligned) {
                    drawCircle(
                        color = EmeraldLocked.copy(alpha = 0.28f),
                        radius = lockRadius * 1.35f,
                        center = center,
                    )
                }
                drawCircle(
                    color = activeColor,
                    radius = 8.dp.toPx(),
                    center = bubbleCenter,
                )
            }
        } else {
            // Modalità laterale 45°: linea orizzonte roll + indicatore verticale di pitch
            val horizonHalf = 82.dp.toPx()
            val pitchDelta = (target.pitchDeg - telemetry.pitchDeg).coerceIn(-35f, 35f)
            val verticalOffset = if (telemetry.hasAttitudeReading) (pitchDelta / 35f) * 64.dp.toPx() else 0f

            // Linea target orizzontale fissa al centro
            drawLine(
                color = Color.White.copy(alpha = 0.32f),
                start = Offset(center.x - horizonHalf, center.y),
                end = Offset(center.x + horizonHalf, center.y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = dashEffect,
            )

            // Tacche laterali di tolleranza
            val tickX = horizonHalf + 10.dp.toPx()
            drawLine(
                color = activeColor,
                start = Offset(center.x - tickX, center.y),
                end = Offset(center.x - horizonHalf - 2.dp.toPx(), center.y),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = activeColor,
                start = Offset(center.x + horizonHalf + 2.dp.toPx(), center.y),
                end = Offset(center.x + tickX, center.y),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
            )

            // Barra orizzonte live ruotata per -rollDeg e traslata per pitchDelta
            val liveCenter = Offset(center.x, center.y + verticalOffset)
            val rollAngle = if (telemetry.hasAttitudeReading) (-telemetry.rollDeg).coerceIn(-45f, 45f) else 0f
            rotate(degrees = rollAngle, pivot = liveCenter) {
                drawLine(
                    color = activeColor,
                    start = Offset(liveCenter.x - horizonHalf * 0.72f, liveCenter.y),
                    end = Offset(liveCenter.x - 14.dp.toPx(), liveCenter.y),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = activeColor,
                    start = Offset(liveCenter.x + 14.dp.toPx(), liveCenter.y),
                    end = Offset(liveCenter.x + horizonHalf * 0.72f, liveCenter.y),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            drawCircle(
                color = activeColor,
                radius = 5.dp.toPx(),
                center = liveCenter,
            )
        }
    }
}

@Composable
private fun StepGuidanceBadge(
    photoCount: Int,
    target: PhotoAngleTarget,
    aligned: Boolean,
) {
    val reachedRecommended = photoCount >= RECOMMENDED_MIN_PHOTOS
    val stepLabel = when (photoCount) {
        0 -> "Passo 1/$RECOMMENDED_MIN_PHOTOS · 90° alto"
        1 -> "Passo 2/$RECOMMENDED_MIN_PHOTOS · 45° lato"
        else -> "Pronto ($photoCount/$MAX_PHOTOS) · ${target.pitchDeg.toInt()}°"
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = when {
            aligned -> EmeraldLocked.copy(alpha = 0.25f)
            reachedRecommended -> Color(0xCC064E3B)
            else -> StudioPillBg
        },
        border = BorderStroke(
            width = 1.dp,
            color = when {
                aligned -> EmeraldLocked
                reachedRecommended -> EmeraldLocked.copy(alpha = 0.65f)
                else -> Color.White.copy(alpha = 0.22f)
            },
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            if (reachedRecommended || aligned) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = EmeraldLocked,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = stepLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CollapsibleTelemetryHud(
    telemetry: LiveCameraTelemetryState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    val compactText = remember(telemetry) {
        buildList {
            telemetry.estimatedDistanceCm?.let { add("~${it.roundToInt()} cm") }
            if (telemetry.fovWidthCm != null && telemetry.fovHeightCm != null) {
                add("FoV ${telemetry.fovWidthCm.roundToInt()}×${telemetry.fovHeightCm.roundToInt()} cm")
            }
            if (telemetry.hasAttitudeReading) {
                add("${telemetry.pitchDeg.roundToInt()}°")
            }
            telemetry.focalLength35mmEq?.let { add("${it.roundToInt()}mm") }
            if (telemetry.isAfLocked) add("AF ✓")
        }.ifEmpty { listOf("Telemetria CameraX attiva") }.joinToString(" · ")
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onToggleExpanded,
            shape = RoundedCornerShape(50),
            color = StudioPillBg,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    text = compactText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    ),
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Comprimi telemetria" else "Espandi telemetria",
                    tint = Color.White.copy(alpha = 0.78f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xD60C1014),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val distStr = telemetry.estimatedDistanceCm?.let { String.format(Locale.US, "%.1f cm", it) } ?: "—"
                    val fovStr = if (telemetry.fovWidthCm != null && telemetry.fovHeightCm != null) {
                        String.format(Locale.US, "%.1f × %.1f cm", telemetry.fovWidthCm, telemetry.fovHeightCm)
                    } else "—"
                    val angleStr = if (telemetry.hasAttitudeReading) {
                        String.format(Locale.US, "Pitch %.0f° · Roll %+.0f°", telemetry.pitchDeg, telemetry.rollDeg)
                    } else "—"
                    val focalStr = buildString {
                        telemetry.focalLength35mmEq?.let { append("${it.roundToInt()}mm eq") } ?: append("—")
                        telemetry.focalLengthMm?.let { append(String.format(Locale.US, " (%.1fmm)", it)) }
                        append(String.format(Locale.US, " · %.1f×", telemetry.zoomRatio))
                    }
                    val expStr = buildList {
                        telemetry.isoValue?.let { add("ISO $it") }
                        OpticalGeometryCalculator.formatShutterSpeed(telemetry.exposureTimeSeconds)?.let { add(it) }
                        telemetry.aperture?.let { add(String.format(Locale.US, "f/%.1f", it)) }
                    }.ifEmpty { listOf("—") }.joinToString(" · ")
                    val afOisStr = buildList {
                        OpticalGeometryCalculator.afStateLabel(telemetry.afState)?.let { add(it) }
                        telemetry.oisEnabled?.let { add(if (it) "OIS ON" else "OIS OFF") }
                    }.ifEmpty { listOf("—") }.joinToString(" · ")
                    val sensorQualityStr = buildList {
                        telemetry.motionRadPerSec?.let { add(String.format(Locale.US, "Gyro %.2f rad/s", it)) }
                        telemetry.liveSharpness?.let { add("Nit. ${it.roundToInt()}") }
                        telemetry.liveBrightness?.let { add("Lum. ${it.roundToInt()}") }
                    }.ifEmpty { listOf("—") }.joinToString(" · ")

                    TelemetryHudRow("Distanza / FoV", "$distStr · $fovStr")
                    TelemetryHudRow("Assetto 60Hz", angleStr)
                    TelemetryHudRow("Ottica / Zoom", focalStr)
                    TelemetryHudRow("Esposizione", expStr)
                    TelemetryHudRow("AF / OIS", afOisStr)
                    TelemetryHudRow("Sensori live", sensorQualityStr)
                }
            }
        }
    }

}

@Composable
private fun TelemetryHudRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.62f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = Color.White.copy(alpha = 0.94f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CoachingHintPill(
    text: String,
    severity: CoachingSeverity,
) {
    val bgColor = when (severity) {
        CoachingSeverity.OK -> EmeraldLocked.copy(alpha = 0.88f)
        CoachingSeverity.WARNING -> AmberCaution.copy(alpha = 0.88f)
        CoachingSeverity.INFO -> StudioPillBg
    }
    val textColor = when (severity) {
        CoachingSeverity.OK -> Color(0xFF022C22)
        CoachingSeverity.WARNING -> Color(0xFF271502)
        CoachingSeverity.INFO -> Color.White.copy(alpha = 0.92f)
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = bgColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun ZoomQuickChips(
    zoomRatio: Float,
    minZoomRatio: Float,
    maxZoomRatio: Float,
    onSelectZoom: (Float) -> Unit,
) {
    val options = remember(minZoomRatio, maxZoomRatio) {
        buildList {
            if (minZoomRatio <= 0.72f) add(0.6f to "0.6×")
            add(1.0f to "1×")
            if (maxZoomRatio >= 1.9f) add(2.0f to "2×")
        }
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = StudioPillBg,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { (value, label) ->
                val active = abs(zoomRatio - value) < 0.25f
                Surface(
                    onClick = { onSelectZoom(value) },
                    shape = CircleShape,
                    color = if (active) Color.White else Color.Transparent,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (active) Color.Black else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }

}

@Composable
private fun StudioFilmstripThumbnail(
    imageBytes: ByteArray,
    index: Int,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    var decoded by remember(imageBytes) { mutableStateOf<Pair<Boolean, Bitmap?>>(false to null) }
    LaunchedEffect(imageBytes) {
        val bmp = withContext(Dispatchers.Default) { decodeStudioThumbnail(imageBytes) }
        decoded = true to bmp
    }
    val (finished, bmp) = decoded

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1A2026))
            .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect),
    ) {
        when {
            bmp != null -> Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Foto ${index + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            finished -> Icon(
                imageVector = Icons.Filled.BrokenImage,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.Center),
            )
            else -> CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .size(18.dp)
                    .align(Alignment.Center),
            )
        }

        Surface(
            color = Color.Black.copy(alpha = 0.70f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(3.dp),
        ) {
            Text(
                text = "#${index + 1}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = Color.White,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }

        Surface(
            onClick = onRemove,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.78f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(20.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Rimuovi foto ${index + 1}",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }

}

@Composable
private fun CameraPermissionRationaleOverlay(
    onRequestPermission: () -> Unit,
    onOpenSystemCameraFallback: () -> Unit,
    onOpenGallery: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E11))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = StudioPillBg,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(42.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Indietro",
                    tint = Color.White,
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = EmeraldLocked.copy(alpha = 0.16f),
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = null,
                        tint = EmeraldLocked,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }

            Text(
                text = "CameraX Pro Studio",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            Text(
                text = "Concedi l'accesso alla fotocamera per usare la livella a 60fps e la telemetria ottica 3D (distanza, FoV e messa a fuoco) direttamente in app.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldLocked,
                    contentColor = Color(0xFF022C22),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text("Consenti fotocamera", fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenGallery,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Galleria", maxLines = 1)
                }

                OutlinedButton(
                    onClick = onOpenSystemCameraFallback,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                ) {
                    Text("App Camera", maxLines = 1)
                }
            }
        }
    }

}

@Composable
private fun CameraUnavailableFallback(
    message: String,
    onOpenSystemCameraFallback: () -> Unit,
    onOpenGallery: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E11))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = StudioPillBg,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(42.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Indietro",
                    tint = Color.White,
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Anteprima CameraX non disponibile",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onOpenSystemCameraFallback,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text("Usa App Camera di sistema", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onOpenGallery,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text("Scegli foto da Galleria")
            }
        }
    }

}

private fun decodeStudioThumbnail(imageBytes: ByteArray, targetSizePx: Int = 180): Bitmap? {
    if (imageBytes.isEmpty()) return null
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > targetSizePx * 2 || bounds.outHeight / sampleSize > targetSizePx * 2) {
            sampleSize *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            imageBytes,
            0,
            imageBytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: return null

        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(imageBytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotation == 0f) return decoded
        val matrix = Matrix().apply { postRotate(rotation) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated !== decoded) decoded.recycle()
        rotated
    } catch (_: Throwable) {
        null
    }
}
