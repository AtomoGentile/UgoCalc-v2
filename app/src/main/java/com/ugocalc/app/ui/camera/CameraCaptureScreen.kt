package com.ugocalc.app.ui.camera

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import com.ugocalc.app.ui.theme.UgoGrey
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.ugocalc.app.ui.components.LoadingOverlay
import com.ugocalc.app.util.OverlayPermissionHelper
import com.ugocalc.app.util.isCameraPermissionGranted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.File

/** Limite ragionevole di scatti per sessione: più foto migliorano l'analisi ma allungano tempo di invio e costo per chiamata AI. */
private const val MAX_PHOTOS = 5

/**
 * Scatto foto (§2.4 punto 2) delegato alla fotocamera di sistema tramite Intent standard
 * (`ActivityResultContracts.TakePicture`), invece di una preview live custom con CameraX: qualità,
 * gestione delle ottiche multiple, zoom, HDR, stabilizzazione e messa a fuoco sono già gestiti in
 * modo professionale dall'app fotocamera del produttore — molto meglio di quanto sia realistico
 * replicare in-app. Il file va scritto tramite FileProvider (`app/src/main/res/xml/file_paths.xml`,
 * percorso `cache/photos/`) perché la fotocamera di sistema è un'altra app e non può scrivere
 * direttamente nello storage privato di UgoCalc.
 *
 * L'utente può scattare più foto dello stesso piatto in una sessione (angolazioni/distanze
 * diverse), tutte inviate insieme all'AI per un'analisi più efficace: la galleria qui sotto
 * mostra le miniature degli scatti raccolti finora, con la possibilità di aggiungerne altri,
 * ispezionarli a schermo intero (pizzicando per ingrandire) o rimuoverli prima di confermare.
 *
 * Durante ogni scatto (compreso il primo) una livella in tempo reale (accelerometro + giroscopio,
 * [CameraAngleOverlayService]) suggerisce l'inclinazione e la rotazione intorno al piatto per il
 * target di quello scatto (vedi [PHOTO_ANGLE_TARGETS]) disegnata come overlay flottante SOPRA la
 * fotocamera di sistema — non più su una schermata separata prima di aprirla (vecchio
 * `PhotoAngleGuideScreen`, rimosso): l'utente vede la guida mentre inquadra davvero. Per questo
 * serve il permesso overlay ([OverlayPermissionHelper]), oltre a quello fotocamera.
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
        if (uiState.analysisDone) onAnalysisReady()
    }

    // rememberSaveable (non remember): scattare in orizzontale ruota fisicamente il dispositivo
    // mentre la fotocamera di sistema è in primo piano, quindi questa Activity — in background —
    // subisce un cambio di configurazione e viene ricreata quando l'utente torna indietro. Con un
    // semplice `remember`, il riferimento al file in scrittura andava perso alla ricreazione: la
    // callback qui sotto vedeva `file == null` e trattava una foto scattata con successo come un
    // annullamento, perdendo lo scatto. Si salva solo il path (String, sempre pacchettizzabile),
    // non il File stesso.
    var pendingCapturePath by rememberSaveable { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun stopAngleOverlay() {
        context.stopService(Intent(context, CameraAngleOverlayService::class.java))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Se torniamo in foreground e il service è ancora attivo, probabilmente l'utente
                // è tornato senza scattare la foto (es. tasto Home o chiusura forzata).
                // Lo fermiamo per sicurezza (§ bug segnalato).
                stopAngleOverlay()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopAngleOverlay()
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        // Letta PRIMA di fermare il service (che comunque non la cancella, vedi
        // CameraAngleOverlayService.LastReading): è l'angolazione realmente misurata per questo
        // scatto, non solo il target che era stato suggerito.
        val measuredAngle = CameraAngleOverlayService.LastReading.value
        stopAngleOverlay()
        val file = pendingCapturePath?.let(::File)
        pendingCapturePath = null
        if (success && (file != null)) {
            // Nessuna decodifica/ricompressione: i byte del JPEG scritto dalla fotocamera di
            // sistema restano intatti (anche verso le API AI) — la rotazione (se il tag EXIF la
            // richiede) viene applicata solo alle Bitmap mostrate in anteprima, vedi PhotoGallery
            // e PhotoZoomReview. Lettura comunque fuori dal thread UI per file molto grandi.
            coroutineScope.launch(Dispatchers.Default) {
                val bytes = file.readBytes()
                // Letto prima della delete (§16): approssima l'istante dello scatto per
                // interrogare il buffer del giroscopio, dato che la fotocamera di sistema non offre
                // alcun callback nell'istante esatto.
                val capturedAtEpochMillis = file.lastModified()
                file.delete()
                viewModel.onPhotoCaptured(bytes, capturedAtEpochMillis, measuredAngle)
            }
        } else {
            file?.delete()
            // Se l'utente annulla il PRIMO scatto (nessuna foto ancora raccolta) si esce dalla
            // schermata; se ne annulla uno successivo, resta sulla galleria con le foto già prese.
            if (uiState.capturedImages.isEmpty()) onCancelled()
        }
    }

    fun launchSystemCamera() {
        val photosDir = File(context.cacheDir, "photos").apply { mkdirs() }
        val file = File(photosDir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCapturePath = file.absolutePath
        takePictureLauncher.launch(uri)
    }

    // Indice (nella sessione) dello scatto che si sta per fare, per scegliere il target giusto in
    // PHOTO_ANGLE_TARGETS: cerchiamo il primo target non ancora coperto dalle foto già scattate
    // (§ bug riportato: "l'app non si ricorda da che angolazioni ho fatto la foto").
    fun getNextTargetIndex(): Int {
        val images = uiState.capturedImages
        val usedLabels = images.asSequence().mapNotNull { it.measuredAngle?.target?.label }.toSet()
        val firstUnused = PHOTO_ANGLE_TARGETS.indexOfFirst { it.label !in usedLabels }
        
        // Se non abbiamo dati sugli angoli (es. permesso overlay negato) o se abbiamo già coperto
        // tutti i target suggeriti, procediamo semplicemente per conteggio.
        return if (firstUnused == -1 || usedLabels.size < images.size) {
            images.size
        } else {
            firstUnused
        }
    }

    // Va salvato perché sopravvive alla richiesta permesso camera/overlay, che
    // apre un'altra Activity e può far ricreare questa in mezzo.
    var pendingPhotoIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    fun launchSystemCameraForIndex(index: Int) {
        val target = PHOTO_ANGLE_TARGETS[index.coerceIn(0, PHOTO_ANGLE_TARGETS.lastIndex)]
        // L'overlay è solo un aiuto visivo: se il permesso non è (più) concesso si scatta comunque,
        // senza bloccare la funzione principale della schermata.
        if (OverlayPermissionHelper.hasOverlayPermission(context)) {
            context.startService(CameraAngleOverlayService.buildStartIntent(context, target))
        }
        launchSystemCamera()
    }

    // SYSTEM_ALERT_WINDOW è un permesso "speciale": va concesso dalla schermata di sistema aperta
    // con ACTION_MANAGE_OVERLAY_PERMISSION (vedi OverlayPermissionHelper), non c'è un risultato
    // affidabile nell'Intent di ritorno — si rilancia comunque lo scatto, con o senza overlay a
    // seconda di quanto l'utente ha effettivamente concesso.
    val overlayPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val index = pendingPhotoIndex ?: getNextTargetIndex()
        pendingPhotoIndex = null
        launchSystemCameraForIndex(index)
    }

    // Il manifest dichiara android.permission.CAMERA (§2.2 Impostazioni > Permessi): con questo
    // permesso dichiarato, l'intent ACTION_IMAGE_CAPTURE verso la fotocamera di sistema richiede
    // che sia stato concesso a runtime, altrimenti alcune fotocamere di sistema (es. Samsung, stock
    // Android recenti) lo rifiutano con SecurityException e l'app crasha. Se l'utente non è mai
    // passato da Impostazioni > Permessi, va richiesto qui, al primo utilizzo effettivo.
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val index = pendingPhotoIndex ?: getNextTargetIndex()
        if (granted) {
            if (OverlayPermissionHelper.hasOverlayPermission(context)) {
                pendingPhotoIndex = null
                launchSystemCameraForIndex(index)
            } else {
                pendingPhotoIndex = index
                overlayPermissionLauncher.launch(OverlayPermissionHelper.buildRequestOverlayPermissionIntent(context))
            }
        } else {
            pendingPhotoIndex = null
            // Stesso comportamento di un annullamento del primo scatto: si esce dalla schermata.
            if (uiState.capturedImages.isEmpty()) onCancelled()
            // Negato ma foto già raccolte (richiesta partita da "Aggiungi un'altra"): si resta sulla
            // galleria esistente, nessuna foto persa.
        }
    }

    fun ensureCameraPermissionThenLaunch() {
        val index = getNextTargetIndex()
        when {
            !isCameraPermissionGranted(context) -> {
                pendingPhotoIndex = index
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            !OverlayPermissionHelper.hasOverlayPermission(context) -> {
                pendingPhotoIndex = index
                overlayPermissionLauncher.launch(OverlayPermissionHelper.buildRequestOverlayPermissionIntent(context))
            }
            else -> launchSystemCameraForIndex(index)
        }
    }

    // Al primo ingresso in questa schermata (nessuna foto ancora in mano) apre subito la fotocamera
    // di sistema con la livella overlay attiva (previa richiesta dei permessi se servono), senza
    // richiedere un tocco aggiuntivo sull'utente.
    LaunchedEffect(Unit) {
        if (uiState.capturedImages.isEmpty()) ensureCameraPermissionThenLaunch()
    }

    var zoomIndex by remember { mutableStateOf<Int?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        val images = uiState.capturedImages
        if (images.isNotEmpty()) {
            val currentZoomIndex = zoomIndex
            if (currentZoomIndex != null && currentZoomIndex < images.size) {
                PhotoZoomReview(
                    imageBytes = images[currentZoomIndex].bytes,
                    onRemove = {
                        viewModel.removePhoto(currentZoomIndex)
                        zoomIndex = null
                    }
                ) { zoomIndex = null }
            } else {
                PhotoGallery(
                    images = images.map { it.bytes },
                    availableCalibrations = uiState.availableCalibrations,
                    selectedCalibrationIds = uiState.selectedCalibrationIds,
                    onToggleCalibration = { viewModel.toggleCalibrationSelection(it) },
                    onAddAnother = ::ensureCameraPermissionThenLaunch,
                    onSelect = { index -> zoomIndex = index },
                    onConfirm = { viewModel.confirmPhotos(selectedModels) },
                )
            }
        }

        if (uiState.isAnalyzing) {
            LoadingOverlay()
        }

        val qualityWarning = uiState.qualityWarning
        if (qualityWarning != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismissQualityWarning,
                confirmButton = {
                    Button(onClick = viewModel::dismissQualityWarning) { Text("Rifai le foto") }
                },
                text = { Text(qualityWarning) },
            )
        }
    }
}

/**
 * Galleria degli scatti raccolti in questa sessione (§2.4 punto 2 esteso): l'utente vede le
 * miniature delle foto già prese, può aggiungerne altre (fino a [MAX_PHOTOS]), toccarne una per
 * ispezionarla a schermo intero, o confermare per avviare l'analisi con tutte le foto raccolte.
 */
@Composable
private fun PhotoGallery(
    images: List<ByteArray>,
    availableCalibrations: List<ScaleCalibrationEntity> = emptyList(),
    selectedCalibrationIds: Set<Long> = emptySet(),
    onToggleCalibration: (Long) -> Unit = {},
    onAddAnother: () -> Unit,
    onSelect: (Int) -> Unit,
    onConfirm: () -> Unit,
) {
    // statusBarsPadding: lo schermo è a tutto schermo (dietro la status bar), senza questo il
    // titolo "Foto scattate" finiva disegnato sotto l'orologio/le icone di sistema.
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Text("Foto scattate (${images.size})", style = MaterialTheme.typography.titleMedium)
        Text(
            "Scatta il piatto da più angolazioni per aiutare l'AI a stimare meglio gli alimenti e le porzioni.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().height(116.dp),
        ) {
            itemsIndexed(images) { index, bytes ->
                PhotoThumbnail(imageBytes = bytes) { onSelect(index) }
            }
            if (images.size < MAX_PHOTOS) {
                item { AddPhotoTile(onClick = onAddAnother) }
            }
        }

        if (availableCalibrations.isNotEmpty()) {
            Text(
                "Elementi di calibrazione visibili",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            Text(
                "Seleziona manualmente quali dei tuoi oggetti calibrati sono presenti nelle foto per una scala più precisa.",
                style = MaterialTheme.typography.bodySmall,
                color = UgoGrey,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(availableCalibrations) { cal ->
                    val selected = cal.id in selectedCalibrationIds
                    FilterChip(
                        selected = selected,
                        onClick = { onToggleCalibration(cal.id) },
                        label = { Text(cal.name) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            OutlinedButton(onClick = onAddAnother, enabled = images.size < MAX_PHOTOS) {
                Text("Aggiungi foto")
            }
            Button(onClick = onConfirm, enabled = images.isNotEmpty()) {
                Text("Analizza (${images.size})")
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(imageBytes: ByteArray, onClick: () -> Unit) {
    val bitmap = remember(imageBytes) { decodeUprightThumbnail(imageBytes) }
    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Foto scattata, tocca per ingrandire o rimuovere",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun AddPhotoTile(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.AddAPhoto, contentDescription = "Aggiungi un'altra foto")
    }
}

/**
 * Ispezione a schermo intero di una foto già scattata (§2.4 punto 2): l'utente pizzica per
 * ingrandire e valutare davvero la nitidezza, e può rimuoverla dalla sessione o tornare alla
 * galleria.
 *
 * [imageBytes] è il JPEG originale, mai ricompresso (nemmeno qui): se il tag EXIF indica una
 * rotazione, viene applicata solo alla Bitmap tenuta in memoria per la visualizzazione — i byte
 * inviati poi all'AI restano bit-per-bit quelli scattati.
 */
@Composable
private fun PhotoZoomReview(imageBytes: ByteArray, onRemove: () -> Unit, onClose: () -> Unit) {
    val bitmap = remember(imageBytes) { decodeUprightBitmapForDisplay(imageBytes) }
    var scale by remember(imageBytes) { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var offset by remember(imageBytes) { mutableStateOf(Offset.Zero) }
    var containerSize by remember(imageBytes) { mutableStateOf(IntSize.Zero) }

    // API standard di Compose per pinch-zoom + pan. Senza limiti, la foto poteva essere trascinata
    // fuori schermo anche a scala 1x (nessuno zoom): il pan ora è vincolato all'area effettivamente
    // "fuori vista" per via dello zoom corrente, come in un qualunque visualizzatore foto — a 1x non
    // si sposta affatto.
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

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Foto del piatto scattata — pizzica per ingrandire",
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
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Chiudi anteprima", tint = Color.White)
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            OutlinedButton(onClick = onRemove) { Text("Rimuovi foto") }
            Button(onClick = onClose) { Text("Torna alla galleria") }
        }
    }
}

/** Estrae la rotazione (in gradi) indicata dal tag EXIF "Orientation", che `BitmapFactory` ignora di default. */
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

/**
 * Limite di sicurezza per la Bitmap mostrata a schermo intero: fotocamere di sistema moderne
 * scattano anche a 50+ Mp, e decodificare quei byte in una Bitmap ARGB_8888 a piena risoluzione
 * (200+ MB, il doppio durante la rotazione) mandava in crash l'app per OutOfMemoryError o per
 * superamento della dimensione massima di texture supportata da Canvas. 4096px di lato è ben
 * oltre quanto uno schermo di telefono possa mostrare anche con lo zoom a 6x di [PhotoZoomReview],
 * quindi non si perde nitidezza percepita — i byte originali inviati all'AI restano intatti,
 * questa è solo la Bitmap di anteprima.
 */
private const val MAX_ZOOM_VIEW_DIMENSION_PX = 4096

/**
 * `BitmapFactory` ignora il tag EXIF "Orientation": senza questa correzione, una foto scattata in
 * verticale apparirebbe orizzontale nello step di conferma. La rotazione è applicata solo alla
 * Bitmap decodificata qui (mai riserializzata) — [imageBytes], usato per il resto dell'app, non
 * viene mai toccato.
 */
private fun decodeUprightBitmapForDisplay(imageBytes: ByteArray): Bitmap =
    decodeUprightBitmap(imageBytes, MAX_ZOOM_VIEW_DIMENSION_PX)

/**
 * Decodifica una versione ridotta della foto per le miniature della galleria: fino a
 * [MAX_PHOTOS] scatti a piena risoluzione decodificati insieme rischierebbero OOM su molti
 * telefoni, mentre qui basta una Bitmap piccola.
 */
private fun decodeUprightThumbnail(imageBytes: ByteArray, targetSizePx: Int = 200): Bitmap =
    decodeUprightBitmap(imageBytes, targetSizePx * 2)

/** Decodifica [imageBytes] raddrizzata secondo l'EXIF, sottocampionata perché il lato più lungo non superi [maxDimensionPx]. */
private fun decodeUprightBitmap(imageBytes: ByteArray, maxDimensionPx: Int): Bitmap {
    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, boundsOptions)

    var sampleSize = 1
    while ((boundsOptions.outWidth / sampleSize > maxDimensionPx) || (boundsOptions.outHeight / sampleSize > maxDimensionPx)) {
        sampleSize *= 2
    }

    val decoded = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    val rotationDegrees = exifRotationDegrees(imageBytes)
    if (rotationDegrees == 0) return decoded

    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    decoded.recycle()
    return rotated
}
