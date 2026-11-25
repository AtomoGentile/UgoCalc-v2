package com.ugocalc.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.data.local.prefs.AppTheme
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.ColorPickerDialog
import com.ugocalc.app.ui.components.EditorialDetailScaffold
import com.ugocalc.app.ui.components.MacroPill
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoSectionHeader
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.AppFontFamily
import com.ugocalc.app.ui.theme.UgoSemanticColors
import com.ugocalc.app.ui.theme.defaultThemeColors

private enum class ColorSlot {
    PRIMARIO, SECONDARIO, TERZIARIO, SFONDO, ERRORE, TESTO,
    PRIMARIO_RISTORANTE, SECONDARIO_RISTORANTE, TERZIARIO_RISTORANTE, SFONDO_RISTORANTE, ERRORE_RISTORANTE,
}

/** Impostazioni > Accessibilità (§2.2): tema chiaro/scuro, dimensione testo, colori personalizzati. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilitySettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var editingSlot by remember { mutableStateOf<ColorSlot?>(null) }
    var sliderScale by remember(state.userSettings.textScale) {
        mutableFloatStateOf(state.userSettings.textScale)
    }

    val useDark = when (state.userSettings.theme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }
    val casaDefaults = defaultThemeColors(useDark, isRistorante = false)
    val ristoranteDefaults = defaultThemeColors(useDark, isRistorante = true)

    val selectedFont = state.userSettings.fontFamilyName
        ?.let { runCatching { AppFontFamily.valueOf(it) }.getOrNull() }
        ?: AppFontFamily.DEFAULT

    val previewBg = state.userSettings.backgroundColorArgb?.let { Color(it) } ?: casaDefaults.background
    val previewPrimary = state.userSettings.primaryColorArgb?.let { Color(it) } ?: casaDefaults.primary
    val previewSecondary = state.userSettings.secondaryColorArgb?.let { Color(it) } ?: casaDefaults.secondary
    val previewTextColor = state.userSettings.fontColorArgb?.let { Color(it) }
        ?: if (previewBg.luminance() > 0.45f) Color(0xFF18181B) else Color(0xFFFAF9F6)

    EditorialDetailScaffold(
        title = "Accessibilità e tema",
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Anteprima dal vivo
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UgoSectionHeader(title = "Anteprima dal vivo")
                ThemeLivePreviewCard(
                    backgroundColor = previewBg,
                    primaryColor = previewPrimary,
                    secondaryColor = previewSecondary,
                    textColor = previewTextColor,
                    textScale = sliderScale,
                    fontFamily = selectedFont.fontFamily,
                    themeLabel = state.userSettings.theme.label(),
                )
            }

            // Tema
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UgoSectionHeader(title = "Tema")
                UgoCard(modifier = Modifier.fillMaxWidth()) {
                    AppTheme.entries.forEachIndexed { index, theme ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setTheme(theme) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            RadioButton(
                                selected = state.userSettings.theme == theme,
                                onClick = { viewModel.setTheme(theme) },
                            )
                            Icon(
                                imageVector = Icons.Filled.Brightness4,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp).padding(end = 2.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = theme.label(),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        if (index < AppTheme.entries.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            )
                        }
                    }
                }
            }

            // Dimensione testo e carattere
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UgoSectionHeader(title = "Testo e carattere")
                UgoCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FormatSize,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Dimensione testo (${"%.0f".format(sliderScale * 100)}%)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Slider(
                        value = sliderScale,
                        onValueChange = { sliderScale = it },
                        onValueChangeFinished = { viewModel.setTextScale(sliderScale) },
                        valueRange = 0.8f..1.6f,
                        steps = 7,
                        modifier = Modifier.padding(top = 6.dp),
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )

                    Text(
                        text = "Carattere",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    AppFontFamily.entries.forEach { font ->
                        val selected = selectedFont == font
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setFontFamily(if (font == AppFontFamily.DEFAULT) null else font.name)
                                }
                                .padding(vertical = 2.dp),
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    viewModel.setFontFamily(if (font == AppFontFamily.DEFAULT) null else font.name)
                                },
                            )
                            Text(
                                text = font.label,
                                fontFamily = font.fontFamily,
                                fontWeight = font.fontWeight ?: FontWeight.Normal,
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )

                    ColorSlotRow(
                        label = "Colore testo",
                        color = previewTextColor,
                        isCustomized = state.userSettings.fontColorArgb != null,
                        onClick = { editingSlot = ColorSlot.TESTO },
                    )
                }
            }

            // Colori Casa
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UgoSectionHeader(title = "Colori modalità Casa")
                UgoCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    ColorSlotRow(
                        label = "Primario",
                        color = state.userSettings.primaryColorArgb?.let { Color(it) } ?: casaDefaults.primary,
                        isCustomized = state.userSettings.primaryColorArgb != null,
                        onClick = { editingSlot = ColorSlot.PRIMARIO },
                    )
                    SlotDivider()
                    ColorSlotRow(
                        label = "Secondario",
                        color = state.userSettings.secondaryColorArgb?.let { Color(it) } ?: casaDefaults.secondary,
                        isCustomized = state.userSettings.secondaryColorArgb != null,
                        onClick = { editingSlot = ColorSlot.SECONDARIO },
                    )
                    SlotDivider()
                    ColorSlotRow(
                        label = "Terziario",
                        color = state.userSettings.tertiaryColorArgb?.let { Color(it) } ?: casaDefaults.tertiary,
                        isCustomized = state.userSettings.tertiaryColorArgb != null,
                        onClick = { editingSlot = ColorSlot.TERZIARIO },
                    )
                    SlotDivider()
                    ColorSlotRow(
                        label = "Sfondo",
                        color = state.userSettings.backgroundColorArgb?.let { Color(it) } ?: casaDefaults.background,
                        isCustomized = state.userSettings.backgroundColorArgb != null,
                        onClick = { editingSlot = ColorSlot.SFONDO },
                    )
                    SlotDivider()
                    ColorSlotRow(
                        label = "Errore",
                        color = state.userSettings.errorColorArgb?.let { Color(it) } ?: casaDefaults.error,
                        isCustomized = state.userSettings.errorColorArgb != null,
                        onClick = { editingSlot = ColorSlot.ERRORE },
                    )
                }
            }

            // Colori Ristorante
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UgoSectionHeader(title = "Colori modalità Ristorante")
                UgoCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    ColorSlotRow(
                        label = "Primario",
                        color = state.userSettings.primaryColorRistoranteArgb?.let { Color(it) } ?: ristoranteDefaults.primary,
                        isCustomized = state.userSettings.primaryColorRistoranteArgb != null,
                        onClick = { editingSlot = ColorSlot.PRIMARIO_RISTORANTE },
                    )
                    SlotDivider()
                    ColorSlotRow(
                        label = "Secondario",
                        color = state.userSettings.secondaryColorRistoranteArgb?.let { Color(it) } ?: ristoranteDefaults.secondary,
                        isCustomized = state.userSettings.secondaryColorRistoranteArgb != null,
                        onClick = { editingSlot = ColorSlot.SECONDARIO_RISTORANTE },
                    )
                    SlotDivider()
                    ColorSlotRow(
                        label = "Terziario",
                        color = state.userSettings.tertiaryColorRistoranteArgb?.let { Color(it) } ?: ristoranteDefaults.tertiary,
                        isCustomized = state.userSettings.tertiaryColorRistoranteArgb != null,
                        onClick = { editingSlot = ColorSlot.TERZIARIO_RISTORANTE },
                    )
                    SlotDivider()
                    ColorSlotRow(
                        label = "Sfondo",
                        color = state.userSettings.backgroundColorRistoranteArgb?.let { Color(it) } ?: ristoranteDefaults.background,
                        isCustomized = state.userSettings.backgroundColorRistoranteArgb != null,
                        onClick = { editingSlot = ColorSlot.SFONDO_RISTORANTE },
                    )
                    SlotDivider()
                    ColorSlotRow(
                        label = "Errore",
                        color = state.userSettings.errorColorRistoranteArgb?.let { Color(it) } ?: ristoranteDefaults.error,
                        isCustomized = state.userSettings.errorColorRistoranteArgb != null,
                        onClick = { editingSlot = ColorSlot.ERRORE_RISTORANTE },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    val slot = editingSlot
    if (slot != null) {
        val currentColorArgb = when (slot) {
            ColorSlot.PRIMARIO -> state.userSettings.primaryColorArgb
            ColorSlot.SECONDARIO -> state.userSettings.secondaryColorArgb
            ColorSlot.TERZIARIO -> state.userSettings.tertiaryColorArgb
            ColorSlot.SFONDO -> state.userSettings.backgroundColorArgb
            ColorSlot.ERRORE -> state.userSettings.errorColorArgb
            ColorSlot.TESTO -> state.userSettings.fontColorArgb
            ColorSlot.PRIMARIO_RISTORANTE -> state.userSettings.primaryColorRistoranteArgb
            ColorSlot.SECONDARIO_RISTORANTE -> state.userSettings.secondaryColorRistoranteArgb
            ColorSlot.TERZIARIO_RISTORANTE -> state.userSettings.tertiaryColorRistoranteArgb
            ColorSlot.SFONDO_RISTORANTE -> state.userSettings.backgroundColorRistoranteArgb
            ColorSlot.ERRORE_RISTORANTE -> state.userSettings.errorColorRistoranteArgb
        }
        val defaultColor = when (slot) {
            ColorSlot.PRIMARIO -> casaDefaults.primary
            ColorSlot.SECONDARIO -> casaDefaults.secondary
            ColorSlot.TERZIARIO -> casaDefaults.tertiary
            ColorSlot.SFONDO -> casaDefaults.background
            ColorSlot.ERRORE -> casaDefaults.error
            ColorSlot.PRIMARIO_RISTORANTE -> ristoranteDefaults.primary
            ColorSlot.SECONDARIO_RISTORANTE -> ristoranteDefaults.secondary
            ColorSlot.TERZIARIO_RISTORANTE -> ristoranteDefaults.tertiary
            ColorSlot.SFONDO_RISTORANTE -> ristoranteDefaults.background
            ColorSlot.ERRORE_RISTORANTE -> ristoranteDefaults.error
            ColorSlot.TESTO -> contentColorFor(state.userSettings.backgroundColorArgb?.let { Color(it) } ?: casaDefaults.background)
        }
        ColorPickerDialog(
            title = "Colore ${slot.name.lowercase().replace("_", " ")}",
            currentColor = currentColorArgb?.let { Color(it) },
            defaultColor = defaultColor,
            onDismiss = { editingSlot = null },
            onColorSelected = { color ->
                val argb = color?.toArgb()
                when (slot) {
                    ColorSlot.PRIMARIO -> viewModel.setPrimaryColor(argb)
                    ColorSlot.SECONDARIO -> viewModel.setSecondaryColor(argb)
                    ColorSlot.TERZIARIO -> viewModel.setTertiaryColor(argb)
                    ColorSlot.SFONDO -> viewModel.setBackgroundColor(argb)
                    ColorSlot.ERRORE -> viewModel.setErrorColor(argb)
                    ColorSlot.TESTO -> viewModel.setFontColor(argb)
                    ColorSlot.PRIMARIO_RISTORANTE -> viewModel.setPrimaryColorRistorante(argb)
                    ColorSlot.SECONDARIO_RISTORANTE -> viewModel.setSecondaryColorRistorante(argb)
                    ColorSlot.TERZIARIO_RISTORANTE -> viewModel.setTertiaryColorRistorante(argb)
                    ColorSlot.SFONDO_RISTORANTE -> viewModel.setBackgroundColorRistorante(argb)
                    ColorSlot.ERRORE_RISTORANTE -> viewModel.setErrorColorRistorante(argb)
                }
                editingSlot = null
            },
        )
    }
}

@Composable
private fun ThemeLivePreviewCard(
    backgroundColor: Color,
    primaryColor: Color,
    secondaryColor: Color,
    textColor: Color,
    textScale: Float,
    fontFamily: FontFamily?,
    themeLabel: String,
) {
    UgoCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = backgroundColor,
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Palette,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Pasta al pomodoro",
                    fontSize = (16f * textScale).sp,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = primaryColor.copy(alpha = 0.16f),
            ) {
                Text(
                    text = themeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = primaryColor,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Anteprima testo al ${(textScale * 100).toInt()}%",
            fontSize = (13f * textScale).sp,
            fontFamily = fontFamily,
            color = textColor.copy(alpha = 0.78f),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MacroPill(
                label = "CHO",
                value = "62.5 g",
                containerColor = UgoSemanticColors.choContainer,
                contentColor = UgoSemanticColors.onChoContainer,
            )
            MacroPill(
                label = "Grassi",
                value = "9.2 g",
                containerColor = UgoSemanticColors.fatContainer,
                contentColor = UgoSemanticColors.onFatContainer,
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {},
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = secondaryColor,
                    contentColor = if (secondaryColor.luminance() > 0.45f) Color(0xFF18181B) else Color.White,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("Analizza", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun SlotDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
}

@Composable
private fun ColorSlotRow(
    label: String,
    color: Color,
    isCustomized: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (isCustomized) "Personalizzato" else "Predefinito",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
    }
}

private fun AppTheme.label() = when (this) {
    AppTheme.LIGHT -> "Chiaro"
    AppTheme.DARK -> "Scuro"
    AppTheme.SYSTEM -> "Sistema"
}
