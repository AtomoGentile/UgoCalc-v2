package com.ugocalc.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ugocalc.app.data.local.db.entity.ScaleCalibrationEntity
import com.ugocalc.app.domain.inference.model.ScaleAnchorType
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.EmptyStateMessage
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoConfirmDialog
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.util.parseLocaleDoubleOrNull
import com.ugocalc.app.util.parsePositiveLocaleDoubleOrNull
import java.io.File

/**
 * Schermata per visualizzare, aggiungere e rimuovere calibrazioni di scala (§2.2).
 * Supporta misure 3D (X, Y, Z) per una calibrazione precisa.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleCalibrationScreen(
    onBack: () -> Unit,
    viewModel: ScaleCalibrationViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val calibrations = viewModel.calibrations

    var newName by rememberSaveable { mutableStateOf("") }
    var newValue by rememberSaveable { mutableStateOf("") }
    var newWidth by rememberSaveable { mutableStateOf("") }
    var newHeight by rememberSaveable { mutableStateOf("") }
    var newCapacity by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf(ScaleAnchorType.STOVIGLIA_CALIBRATA) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<ScaleCalibrationEntity?>(null) }

    var tempPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var savedPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            savedPhotoPath = tempPhotoPath
        }
        tempPhotoPath = null
    }

    fun launchCamera() {
        val calibrationsDir = File(context.filesDir, "calibrations").apply { mkdirs() }
        val file = File(calibrationsDir, "cal_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        tempPhotoPath = file.absolutePath
        takePictureLauncher.launch(uri)
    }

    fun resetForm() {
        editingId = null
        newName = ""
        newValue = ""
        newWidth = ""
        newHeight = ""
        newCapacity = ""
        savedPhotoPath = null
        validationError = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calibrazioni scala") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors(),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (calibrations.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyStateMessage(
                        icon = Icons.Default.Straighten,
                        title = "Nessun riferimento salvato",
                        message = "Aggiungi una stoviglia o un riferimento corporeo per migliorare la stima delle porzioni.",
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(calibrations, key = { it.id }) { cal ->
                        UgoCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(14.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                    ) {
                                        Text(
                                            text = if (cal.type == ScaleAnchorType.STOVIGLIA_CALIBRATA.name) "Stoviglia" else "Rif. Corporeo",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = cal.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    val dims = buildList {
                                        add("X: ${cal.valueCm}")
                                        cal.widthCm?.let { add("Y: $it") }
                                        cal.heightCm?.let { add("Z: $it") }
                                    }
                                    Text(
                                        text = "Dimensioni: ${dims.joinToString(" × ")} cm",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    cal.capacityMl?.let { capacity ->
                                        Text(
                                            text = "Capacità: $capacity ml",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                cal.imagePath?.let { path ->
                                    AsyncImage(
                                        model = path,
                                        contentDescription = "Foto di riferimento",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp)
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        editingId = cal.id
                                        newName = cal.name
                                        newValue = cal.valueCm.toString()
                                        newWidth = cal.widthCm?.toString().orEmpty()
                                        newHeight = cal.heightCm?.toString().orEmpty()
                                        newCapacity = cal.capacityMl?.toString().orEmpty()
                                        selectedType = if (cal.type == ScaleAnchorType.RIFERIMENTO_CORPOREO.name) {
                                            ScaleAnchorType.RIFERIMENTO_CORPOREO
                                        } else {
                                            ScaleAnchorType.STOVIGLIA_CALIBRATA
                                        }
                                        savedPhotoPath = cal.imagePath
                                        validationError = null
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Modifica",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                IconButton(onClick = { pendingDelete = cal }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Elimina",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Form inserimento o modifica
            UgoCard(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                contentPadding = PaddingValues(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (editingId == null) "Aggiungi riferimento (X × Y × Z)" else "Modifica calibrazione",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (editingId != null) {
                        TextButton(onClick = ::resetForm) {
                            Text("Annulla")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedType == ScaleAnchorType.STOVIGLIA_CALIBRATA,
                        onClick = { selectedType = ScaleAnchorType.STOVIGLIA_CALIBRATA },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        label = { Text("Stoviglia", maxLines = 1) },
                    )
                    SegmentedButton(
                        selected = selectedType == ScaleAnchorType.RIFERIMENTO_CORPOREO,
                        onClick = { selectedType = ScaleAnchorType.RIFERIMENTO_CORPOREO },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        label = { Text("Corporeo", maxLines = 1) },
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it; validationError = null },
                    label = { Text("Nome (es. Piatto fondo)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newValue,
                        onValueChange = { newValue = it; validationError = null },
                        label = { Text("Lungo (X)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    OutlinedTextField(
                        value = newWidth,
                        onValueChange = { newWidth = it; validationError = null },
                        label = { Text("Largo (Y)") },
                        placeholder = { Text("opz.") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    OutlinedTextField(
                        value = newHeight,
                        onValueChange = { newHeight = it; validationError = null },
                        label = { Text("Alto (Z)") },
                        placeholder = { Text("opz.") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                }

                OutlinedTextField(
                    value = newCapacity,
                    onValueChange = { newCapacity = it; validationError = null },
                    label = { Text("Capacità in millilitri (opzionale)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (savedPhotoPath == null) {
                        OutlinedButton(
                            onClick = ::launchCamera,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Aggiungi foto riferimento")
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f)) {
                            AsyncImage(
                                model = savedPhotoPath,
                                contentDescription = "Anteprima riferimento",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                            IconButton(
                                onClick = { savedPhotoPath = null },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                                    .size(26.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Rimuovi",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                validationError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                Button(
                    onClick = {
                        val x = newValue.parsePositiveLocaleDoubleOrNull()
                        if (x == null) {
                            validationError = "Inserisci una lunghezza X valida maggiore di zero."
                            return@Button
                        }
                        val y = if (newWidth.isBlank()) null else {
                            newWidth.parseLocaleDoubleOrNull()?.takeIf { it > 0.0 }
                                ?: run {
                                    validationError = "Valore Y non valido."
                                    return@Button
                                }
                        }
                        val z = if (newHeight.isBlank()) null else {
                            newHeight.parseLocaleDoubleOrNull()?.takeIf { it > 0.0 }
                                ?: run {
                                    validationError = "Valore Z non valido."
                                    return@Button
                                }
                        }
                        val cap = if (newCapacity.isBlank()) null else {
                            newCapacity.parseLocaleDoubleOrNull()?.takeIf { it > 0.0 }
                                ?: run {
                                    validationError = "Capacità in ml non valida."
                                    return@Button
                                }
                        }

                        val eid = editingId
                        if (eid == null) {
                            viewModel.addCalibration(selectedType, newName.trim(), x, y, z, cap, savedPhotoPath)
                        } else {
                            viewModel.updateCalibration(eid, selectedType, newName.trim(), x, y, z, cap, savedPhotoPath)
                        }
                        resetForm()
                    },
                    enabled = newName.isNotBlank() && newValue.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                ) {
                    Text(if (editingId == null) "Salva calibrazione" else "Aggiorna calibrazione")
                }
            }
        }
    }

    pendingDelete?.let { cal ->
        UgoConfirmDialog(
            title = "Elimina calibrazione",
            message = "Vuoi eliminare \"${cal.name}\" dalle calibrazioni salvate?",
            confirmLabel = "Elimina",
            isDestructive = true,
            onConfirm = {
                pendingDelete = null
                if (editingId == cal.id) resetForm()
                viewModel.deleteCalibration(cal.id)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}
