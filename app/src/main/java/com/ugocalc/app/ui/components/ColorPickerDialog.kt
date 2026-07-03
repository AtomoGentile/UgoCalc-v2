package com.ugocalc.app.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/**
 * Selettore di colore libero (§ richiesta utente: "l'utente dovrebbe poter scegliere il colore che
 * preferisce, non una tavolozza"): tonalità/saturazione/luminosità coprono l'intero spettro, non
 * solo un set di colori predefiniti. [defaultColor] è il colore che il tema userebbe senza
 * personalizzazione (per seminare i cursori quando non c'è ancora un colore custom, e per il
 * pulsante "Predefinito").
 */
@Composable
fun ColorPickerDialog(
    title: String,
    currentColor: Color?,
    defaultColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color?) -> Unit,
) {
    val seedHsv = remember(currentColor, defaultColor) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV((currentColor ?: defaultColor).toArgb(), hsv)
        hsv
    }
    var hue by remember { mutableFloatStateOf(seedHsv[0]) }
    var saturation by remember { mutableFloatStateOf(seedHsv[1]) }
    var brightness by remember { mutableFloatStateOf(seedHsv[2]) }

    val previewColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(previewColor)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .align(Alignment.CenterHorizontally),
                )
                Text("Tonalità", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 16.dp))
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text("Saturazione", style = MaterialTheme.typography.bodySmall)
                Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..1f)
                Text("Luminosità", style = MaterialTheme.typography.bodySmall)
                Slider(value = brightness, onValueChange = { brightness = it }, valueRange = 0f..1f)
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(previewColor) }) { Text("Applica") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onColorSelected(null) }) { Text("Predefinito") }
                TextButton(onClick = onDismiss) { Text("Chiudi") }
            }
        },
    )
}
