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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
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
import com.ugocalc.app.data.local.db.entity.ScaleCalibrationEntity
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.LoadingOverlay
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoSectionHeader
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoSemanticColors
import com.ugocalc.app.util.OverlayPermissionHelper
import com.ugocalc.app.util.isCameraPermissionGranted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Hub di acquisizione foto (§2.4 punto 2):
 * - presenta una guida chiara in 2 passi (90° dall'alto + 45° laterale opzionale) senza aprire
 *   a sorpresa la fotocamera o le impostazioni di sistema;
 * - consente di scattare con la fotocamera di sistema oppure scegliere immagini dalla galleria;
 * - offre un toggle opzionale "Livella overlay" che chiede il permesso overlay solo se attivato.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var pendingPhotoIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var zoomIndex by remember { mutableStateOf<Int?>(null) }
    var cameraPermissionDeniedMessage by remember { mutableStateOf<String?>(null) }
    var hasOverlayPermission by remember { mutableStateOf(OverlayPermissionHelper.hasOverlayPermission(context)) }
    val coroutineScope = rememberCoroutineScope()

    fun stopAngleOverlay() {
        context.stopService(Intent(context, CameraAngleOverlayService::class.java))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                stopAngleOverlay()
                hasOverlayPermission = OverlayPermissionHelper.hasOverlayPermission(context)
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
                        viewModel.onPhotoCaptured(bytes, capturedAtEpochMillis, measuredAngle)
                    }
                }
            }
        } else {
            file?.delete()
        }
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)
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
        val index = pendingPhotoIndex ?: getNextTargetIndex()
        pendingPhotoIndex = null
        if (granted) {
            cameraPermissionDeniedMessage = null
            launchSystemCameraForIndex(index)
        } else {
            cameraPermissionDeniedMessage = "Permesso fotocamera negato: puoi concederlo dalle impostazioni oppure scegliere le foto dalla galleria."
        }
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val granted = OverlayPermissionHelper.hasOverlayPermission(context)
        hasOverlayPermission = granted
        viewModel.setAngleOverlayEnabled(granted)
    }

    fun startCameraCapture() {
        if (uiState.capturedImages.size >= MAX_PHOTOS || uiState.isAnalyzing) return
        val index = getNextTargetIndex()
        if (!isCameraPermissionGranted(context)) {
            pendingPhotoIndex = index
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            cameraPermissionDeniedMessage = null
            launchSystemCameraForIndex(index)
        }
    }

    val images = uiState.capturedImages
    val activeZoomIndex = zoomIndex

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
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Foto del piatto") },
                        navigationIcon = { BackIconButton(onCancelled) },
                        colors = ugoTopAppBarColors(),
                    )
                },
                bottomBar = {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 6.dp,
                        tonalElevation = 2.dp,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            Button(
                                onClick = { viewModel.confirmPhotos(selectedModels) },
                                enabled = images.isNotEmpty() && !uiState.isAnalyzing && !uiState.isLoadingGallery,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .height(52.dp),
                            ) {
                                val ctaLabel = when (images.size) {
                                    0 -> "Aggiungi almeno 1 foto"
                                    1 -> "Analizza (1 foto)"
                                    else -> "Analizza (${images.size} foto)"
                                }
                                Text(
                                    text = ctaLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Guida chiara in 2 passi
                    PhotoGuideCard(photoCount = images.size)

                    // Azioni principali di acquisizione
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = ::startCameraCapture,
                            enabled = images.size < MAX_PHOTOS && !uiState.isAnalyzing,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddAPhoto,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Scatta foto", maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                galleryPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = images.size < MAX_PHOTOS && !uiState.isAnalyzing && !uiState.isLoadingGallery,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Scegli da Galleria", maxLines = 1)
                        }
                    }

                    // Toggle opzionale per la livella overlay
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val overlayActive = uiState.angleOverlayEnabled && hasOverlayPermission
                        FilterChip(
                            selected = overlayActive,
                            onClick = {
                                if (overlayActive) {
                                    viewModel.setAngleOverlayEnabled(false)
                                } else if (OverlayPermissionHelper.hasOverlayPermission(context)) {
                                    hasOverlayPermission = true
                                    viewModel.setAngleOverlayEnabled(true)
                                } else {
                                    overlayPermissionLauncher.launch(
                                        OverlayPermissionHelper.buildRequestOverlayPermissionIntent(context)
                                    )
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (overlayActive) Icons.Filled.Check else Icons.Filled.ScreenRotation,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = { Text("Livella overlay") },
                        )
                        Text(
                            text = if (overlayActive) {
                                "Attiva durante lo scatto"
                            } else {
                                "Guida inclinazione sopra la camera (opzionale)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (cameraPermissionDeniedMessage != null) {
                        StatusBanner(
                            icon = Icons.Filled.PhotoCamera,
                            title = "Fotocamera non autorizzata",
                            subtitle = cameraPermissionDeniedMessage.orEmpty(),
                            tone = BannerTone.WARNING,
                            actionLabel = "Chiudi",
                            onAction = { cameraPermissionDeniedMessage = null },
                        )
                    }

                    if (uiState.galleryError != null) {
                        StatusBanner(
                            icon = Icons.Filled.Warning,
                            title = "Avviso galleria",
                            subtitle = uiState.galleryError.orEmpty(),
                            tone = BannerTone.WARNING,
                            actionLabel = "Chiudi",
                            onAction = viewModel::dismissGalleryError,
                        )
                    }

                    if (uiState.analysisError != null) {
                        StatusBanner(
                            icon = Icons.Filled.ErrorOutline,
                            title = "Errore avvio analisi",
                            subtitle = uiState.analysisError.orEmpty(),
                            tone = BannerTone.ERROR,
                            actionLabel = "Chiudi",
                            onAction = viewModel::dismissAnalysisError,
                        )
                    }

                    if (uiState.isLoadingGallery) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    if (images.isNotEmpty()) {
                        UgoSectionHeader(
                            title = "Foto raccolte (${images.size}/$MAX_PHOTOS)",
                            subtitle = "Tocca una miniatura per ispezionarla o X per rimuoverla",
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 2.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            itemsIndexed(images) { index, photo ->
                                PhotoThumbnail(
                                    imageBytes = photo.bytes,
                                    index = index,
                                    onSelect = { zoomIndex = index },
                                    onRemove = { viewModel.removePhoto(index) },
                                )
                            }
                        }
                    }

                    if (uiState.availableCalibrations.isNotEmpty()) {
                        UgoCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(14.dp),
                        ) {
                            Text(
                                text = "Elementi di calibrazione visibili",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Seleziona eventuali oggetti calibrati visibili nelle foto per migliorare la stima dei pesi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                            )
                            CalibrationSelector(
                                calibrations = uiState.availableCalibrations,
                                selectedIds = uiState.selectedCalibrationIds,
                                onToggle = viewModel::toggleCalibrationSelection,
                            )
                        }
                    }
                }
            }
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

@Composable
private fun PhotoGuideCard(photoCount: Int) {
    val complete = photoCount >= RECOMMENDED_MIN_PHOTOS
    UgoCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (complete) {
            UgoSemanticColors.success.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        },
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Guida rapida allo scatto",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = if (complete) UgoSemanticColors.successContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = if (complete) "Pronto ✓" else "$photoCount/$RECOMMENDED_MIN_PHOTOS consigliate",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (complete) UgoSemanticColors.onSuccessContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        GuideStepRow(
            stepNumber = 1,
            title = "1. Dall'alto (90°)",
            subtitle = "Inquadra il piatto dall'alto per stimare area e diametro.",
            completed = photoCount >= 1,
        )
        Spacer(Modifier.height(8.dp))
        GuideStepRow(
            stepNumber = 2,
            title = "2. Laterale (45°, opzionale)",
            subtitle = "Aiuta l'AI a valutare spessore e volume del cibo.",
            completed = photoCount >= 2,
        )
    }
}

@Composable
private fun GuideStepRow(
    stepNumber: Int,
    title: String,
    subtitle: String,
    completed: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (completed) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Completato",
                tint = UgoSemanticColors.success,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Surface(
                modifier = Modifier.size(24.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = stepNumber.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PhotoThumbnail(
    imageBytes: ByteArray,
    index: Int,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    var decodedState by remember(imageBytes) { mutableStateOf<Pair<Boolean, Bitmap?>>(false to null) }
    LaunchedEffect(imageBytes) {
        val bmp = withContext(Dispatchers.Default) { decodeUprightThumbnail(imageBytes) }
        decodedState = true to bmp
    }
    val (finished, bmp) = decodedState

    Box(
        modifier = Modifier
            .size(108.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onSelect),
    ) {
        when {
            bmp != null -> {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Foto ${index + 1}, tocca per ingrandire",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            finished -> {
                Icon(
                    imageVector = Icons.Filled.BrokenImage,
                    contentDescription = "Anteprima non disponibile",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.Center),
                    strokeWidth = 2.dp,
                )
            }
        }

        // Badge numero foto in basso a sinistra
        Surface(
            color = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp),
        ) {
            Text(
                text = "#${index + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        // Badge X diretta per rimuovere la foto
        Surface(
            onClick = onRemove,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.72f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(26.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Rimuovi foto ${index + 1}",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
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
                    Text("Torna alla galleria")
                }
            }
        }
    }
}

@Composable
private fun CalibrationSelector(
    calibrations: List<ScaleCalibrationEntity>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val count = selectedIds.size
    val label = when (count) {
        0 -> "Seleziona calibrazioni..."
        1 -> "1 calibrazione scelta"
        else -> "$count calibrazioni scelte"
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, color = MaterialTheme.colorScheme.onSurface)
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(12.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            calibrations.forEach { cal ->
                val selected = cal.id in selectedIds
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .padding(end = 8.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Column {
                                Text(cal.name, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                val dims = mutableListOf("${cal.valueCm}cm")
                                cal.widthCm?.let { dims.add("$it cm") }
                                cal.heightCm?.let { dims.add("$it cm") }
                                Text(
                                    "Dim: ${dims.joinToString(" × ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = { onToggle(cal.id) },
                    contentPadding = PaddingValues(8.dp),
                )
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

private fun decodeUprightThumbnail(imageBytes: ByteArray, targetSizePx: Int = 200): Bitmap? =
    decodeUprightBitmap(imageBytes, targetSizePx * 2)

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
