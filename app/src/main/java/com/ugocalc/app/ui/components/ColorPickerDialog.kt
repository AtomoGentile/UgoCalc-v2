package com.ugocalc.app.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Selettore di colore libero HSV.
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
    val hexString = remember(previewColor) {
        "#%06X".format(0xFFFFFF and previewColor.toArgb())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(previewColor)
                            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )
                    Column {
                        Text(
                            text = "Anteprima",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = hexString,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                SliderLabel("Tonalità", "${hue.toInt()}°")
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)

                SliderLabel("Saturazione", "${(saturation * 100).toInt()}%")
                Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..1f)

                SliderLabel("Luminosità", "${(brightness * 100).toInt()}%")
                Slider(value = brightness, onValueChange = { brightness = it }, valueRange = 0f..1f)
            }
        },
        confirmButton = {
            Button(
                onClick = { onColorSelected(previewColor) },
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Applica")
            }
        },
        dismissButton = {
            Row(modifier = Modifier.padding(end = 4.dp)) {
                TextButton(onClick = { onColorSelected(null) }) { Text("Predefinito") }
                TextButton(onClick = onDismiss) { Text("Chiudi") }
            }
        },
    )
}

@Composable
private fun SliderLabel(label: String, valueText: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
