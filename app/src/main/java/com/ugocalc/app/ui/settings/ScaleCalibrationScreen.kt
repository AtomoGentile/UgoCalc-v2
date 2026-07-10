package com.ugocalc.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.domain.inference.model.ScaleAnchorType
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoGrey
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import android.graphics.BitmapFactory
import java.io.File
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow

/**
 * Schermata per visualizzare, aggiungere e rimuovere calibrazioni di scala (§2.2).
 * Supporta misure 3D (X, Y, Z) per una calibrazione precisa.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleCalibrationScreen(onBack: () -> Unit, viewModel: ScaleCalibrationViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val calibrations = viewModel.calibrations

    var newName by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }
    var newWidth by remember { mutableStateOf("") }
    var newHeight by remember { mutableStateOf("") }
    var newCapacity by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ScaleAnchorType.STOVIGLIA_CALIBRATA) }
    
    var tempPhotoPath by remember { mutableStateOf<String?>(null) }
    var savedPhotoPath by remember { mutableStateOf<String?>(null) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calibrazioni scala") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors()
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Lista delle calibrazioni esistenti
            if (calibrations.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Nessun riferimento salvato", color = UgoGrey)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(calibrations) { cal ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (cal.type == ScaleAnchorType.STOVIGLIA_CALIBRATA.name) "Stoviglia" else "Rif. Corporeo",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(text = cal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    
                                    val dims = mutableListOf("X: ${cal.valueCm}")
                                    cal.widthCm?.let { dims.add("Y: $it") }
                                    cal.heightCm?.let { dims.add("Z: $it") }
                                    Text(text = "Dimensioni: ${dims.joinToString(" x ")} cm", style = MaterialTheme.typography.bodyMedium)
                                    
                                    cal.capacityMl?.let { capacity ->
                                        Text(text = "Capacità: $capacity ml", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                
                                cal.imagePath?.let { path ->
                                    val bitmap = remember(path) { 
                                        try { BitmapFactory.decodeFile(path) } catch (e: Exception) { null } 
                                    }
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Foto di riferimento",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .padding(horizontal = 8.dp)
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                        )
                                    }
                                }

                                IconButton(onClick = { viewModel.deleteCalibration(cal.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            // Form di inserimento nuova calibrazione
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp).padding(bottom = 8.dp)) {
                    Text("Aggiungi nuova (X x Y x Z)", style = MaterialTheme.typography.titleSmall)
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = selectedType == ScaleAnchorType.STOVIGLIA_CALIBRATA,
                            onClick = { selectedType = ScaleAnchorType.STOVIGLIA_CALIBRATA },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            label = { Text("Stoviglia", maxLines = 1) }
                        )
                        SegmentedButton(
                            selected = selectedType == ScaleAnchorType.RIFERIMENTO_CORPOREO,
                            onClick = { selectedType = ScaleAnchorType.RIFERIMENTO_CORPOREO },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            label = { Text("Corporeo", maxLines = 1) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nome (es. Piatto fondo)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newValue,
                            onValueChange = { newValue = it },
                            label = { Text("Lungo (X)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = newWidth,
                            onValueChange = { newWidth = it },
                            label = { Text("Largo (Y)") },
                            placeholder = { Text("opz.") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = newHeight,
                            onValueChange = { newHeight = it },
                            label = { Text("Alto (Z)") },
                            placeholder = { Text("opz.") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }
                    
                    OutlinedTextField(
                        value = newCapacity,
                        onValueChange = { newCapacity = it },
                        label = { Text("Capacità in Millilitri (opzionale)") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )

                    // Sezione Foto di Riferimento (§13 punto 4)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (savedPhotoPath == null) {
                            OutlinedButton(
                                onClick = ::launchCamera,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Aggiungi Foto Riferimento")
                            }
                        } else {
                            val bitmap = remember(savedPhotoPath) {
                                try { BitmapFactory.decodeFile(savedPhotoPath) } catch (e: Exception) { null }
                            }
                            if (bitmap != null) {
                                Box(modifier = Modifier.weight(1f)) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Anteprima riferimento",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(80.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    IconButton(
                                        onClick = { savedPhotoPath = null },
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                            .size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Rimuovi", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val x = newValue.replace(",", ".").toDoubleOrNull() ?: return@Button
                            val y = newWidth.replace(",", ".").toDoubleOrNull()
                            val z = newHeight.replace(",", ".").toDoubleOrNull()
                            val cap = newCapacity.replace(",", ".").toDoubleOrNull()
                            viewModel.addCalibration(selectedType, newName, x, y, z, cap, savedPhotoPath)
                            newName = ""
                            newValue = ""
                            newWidth = ""
                            newHeight = ""
                            newCapacity = ""
                            savedPhotoPath = null
                        },
                        enabled = newName.isNotBlank() && newValue.isNotBlank(),
                        modifier = Modifier.padding(top = 16.dp).fillMaxWidth()
                    ) {
                        Text("Salva Calibrazione")
                    }
                }
            }
        }
    }
}
