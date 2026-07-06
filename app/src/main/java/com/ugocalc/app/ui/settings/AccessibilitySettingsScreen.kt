package com.ugocalc.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.data.local.prefs.AppTheme
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.ColorPickerDialog
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.AppFontFamily
import com.ugocalc.app.ui.theme.defaultThemeColors

private enum class ColorSlot { PRIMARIO, SECONDARIO, TERZIARIO, SFONDO, ERRORE, TESTO }

/** Impostazioni > Accessibilità (§2.2): tema chiaro/scuro, dimensione testo, colori personalizzati. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilitySettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var editingSlot by remember { mutableStateOf<ColorSlot?>(null) }

    val useDark = when (state.userSettings.theme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }
    val defaults = defaultThemeColors(useDark)

    Scaffold(topBar = { TopAppBar(title = { Text("Accessibilità") }, navigationIcon = { BackIconButton(onBack) }, colors = ugoTopAppBarColors()) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Tema", style = MaterialTheme.typography.titleMedium)
            AppTheme.entries.forEach { theme ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = state.userSettings.theme == theme, onClick = { viewModel.setTheme(theme) })
                    Text(theme.label())
                }
            }

            Text("Dimensione testo", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
            Slider(
                value = state.userSettings.textScale,
                onValueChange = { viewModel.setTextScale(it) },
                valueRange = 0.8f..1.6f,
                steps = 7,
            )
            Text("Anteprima testo (${"%.0f".format(state.userSettings.textScale * 100)}%)", style = MaterialTheme.typography.bodyLarge)

            Text("Carattere", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
            AppFontFamily.entries.forEach { font ->
                val selected = (state.userSettings.fontFamilyName?.let { runCatching { AppFontFamily.valueOf(it) }.getOrNull() } ?: AppFontFamily.DEFAULT) == font
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(
                        selected = selected,
                        onClick = { viewModel.setFontFamily(if (font == AppFontFamily.DEFAULT) null else font.name) },
                    )
                    Text(font.label, fontFamily = font.fontFamily)
                }
            }
            ColorSlotRow(
                label = "Colore testo",
                color = state.userSettings.fontColorArgb?.let { Color(it) }
                    ?: contentColorFor(state.userSettings.backgroundColorArgb?.let { Color(it) } ?: defaults.background),
                onClick = { editingSlot = ColorSlot.TESTO },
                modifier = Modifier.padding(top = 8.dp),
            )

            val usesRistoranteColors = state.currentSessionMode == SessionMode.RISTORANTE
            val colorPrimary = if (usesRistoranteColors) {
                state.userSettings.primaryColorRistoranteArgb?.let { Color(it) } ?: defaults.primary
            } else {
                state.userSettings.primaryColorArgb?.let { Color(it) } ?: defaults.primary
            }
            val colorSecondary = if (usesRistoranteColors) {
                state.userSettings.secondaryColorRistoranteArgb?.let { Color(it) } ?: defaults.secondary
            } else {
                state.userSettings.secondaryColorArgb?.let { Color(it) } ?: defaults.secondary
            }
            val colorTertiary = if (usesRistoranteColors) {
                state.userSettings.tertiaryColorRistoranteArgb?.let { Color(it) } ?: defaults.tertiary
            } else {
                state.userSettings.tertiaryColorArgb?.let { Color(it) } ?: defaults.tertiary
            }
            val colorBackground = if (usesRistoranteColors) {
                state.userSettings.backgroundColorRistoranteArgb?.let { Color(it) } ?: defaults.background
            } else {
                state.userSettings.backgroundColorArgb?.let { Color(it) } ?: defaults.background
            }
            val colorError = if (usesRistoranteColors) {
                state.userSettings.errorColorRistoranteArgb?.let { Color(it) } ?: defaults.error
            } else {
                state.userSettings.errorColorArgb?.let { Color(it) } ?: defaults.error
            }

            Text(
                "Colori per modalità ${if (state.currentSessionMode == SessionMode.CASA) "casa" else "ristorante"}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp)
            )
            ColorSlotRow(
                label = "Primario",
                color = colorPrimary,
                onClick = { editingSlot = ColorSlot.PRIMARIO },
            )
            ColorSlotRow(
                label = "Secondario",
                color = colorSecondary,
                onClick = { editingSlot = ColorSlot.SECONDARIO },
            )
            ColorSlotRow(
                label = "Terziario",
                color = colorTertiary,
                onClick = { editingSlot = ColorSlot.TERZIARIO },
            )
            ColorSlotRow(
                label = "Sfondo",
                color = colorBackground,
                onClick = { editingSlot = ColorSlot.SFONDO },
            )
            ColorSlotRow(
                label = "Errore",
                color = colorError,
                onClick = { editingSlot = ColorSlot.ERRORE },
            )
        }
    }

    val slot = editingSlot
    if (slot != null) {
        val isRistorante = state.currentSessionMode == SessionMode.RISTORANTE
        val currentColorArgb = when (slot) {
            ColorSlot.PRIMARIO -> if (isRistorante) state.userSettings.primaryColorRistoranteArgb else state.userSettings.primaryColorArgb
            ColorSlot.SECONDARIO -> if (isRistorante) state.userSettings.secondaryColorRistoranteArgb else state.userSettings.secondaryColorArgb
            ColorSlot.TERZIARIO -> if (isRistorante) state.userSettings.tertiaryColorRistoranteArgb else state.userSettings.tertiaryColorArgb
            ColorSlot.SFONDO -> if (isRistorante) state.userSettings.backgroundColorRistoranteArgb else state.userSettings.backgroundColorArgb
            ColorSlot.ERRORE -> if (isRistorante) state.userSettings.errorColorRistoranteArgb else state.userSettings.errorColorArgb
            ColorSlot.TESTO -> state.userSettings.fontColorArgb
        }
        val defaultColor = when (slot) {
            ColorSlot.PRIMARIO -> defaults.primary
            ColorSlot.SECONDARIO -> defaults.secondary
            ColorSlot.TERZIARIO -> defaults.tertiary
            ColorSlot.SFONDO -> defaults.background
            ColorSlot.ERRORE -> defaults.error
            ColorSlot.TESTO -> contentColorFor(state.userSettings.backgroundColorArgb?.let { Color(it) } ?: defaults.background)
        }
        ColorPickerDialog(
            title = "Colore ${slot.name.lowercase()}",
            currentColor = currentColorArgb?.let { Color(it) },
            defaultColor = defaultColor,
            onDismiss = { editingSlot = null },
            onColorSelected = { color ->
                val argb = color?.toArgb()
                when (slot) {
                    ColorSlot.PRIMARIO -> if (isRistorante) viewModel.setPrimaryColorRistorante(argb) else viewModel.setPrimaryColor(argb)
                    ColorSlot.SECONDARIO -> if (isRistorante) viewModel.setSecondaryColorRistorante(argb) else viewModel.setSecondaryColor(argb)
                    ColorSlot.TERZIARIO -> if (isRistorante) viewModel.setTertiaryColorRistorante(argb) else viewModel.setTertiaryColor(argb)
                    ColorSlot.SFONDO -> if (isRistorante) viewModel.setBackgroundColorRistorante(argb) else viewModel.setBackgroundColor(argb)
                    ColorSlot.ERRORE -> if (isRistorante) viewModel.setErrorColorRistorante(argb) else viewModel.setErrorColor(argb)
                    ColorSlot.TESTO -> viewModel.setFontColor(argb)
                }
                editingSlot = null
            },
        )
    }
}

@Composable
private fun ColorSlotRow(label: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
    }
}

private fun AppTheme.label() = when (this) {
    AppTheme.LIGHT -> "Chiaro"
    AppTheme.DARK -> "Scuro"
    AppTheme.SYSTEM -> "Predefinito di sistema"
}
