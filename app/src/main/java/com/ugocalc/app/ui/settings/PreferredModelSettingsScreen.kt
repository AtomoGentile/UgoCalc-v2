package com.ugocalc.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.domain.model.AiEffort
import com.ugocalc.app.domain.model.supportedEfforts
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.EditorialDetailScaffold
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.modelselect.AiEffortSelectorCard

/**
 * Impostazioni > Modelli preferiti (§2.2): se ne selezioni almeno uno, "Analizza" in Home salta
 * del tutto la schermata di scelta modello e avvia subito l'analisi con questi.
 */
@Composable
fun PreferredModelSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val selectedIds = state.userSettings.preferredModelIds
    val selectedModels = remember(availableModels, selectedIds) {
        availableModels.filter { it.uid in selectedIds || it.id in selectedIds }
    }
    val supportedEfforts = remember(availableModels, selectedModels) {
        val pool = selectedModels.ifEmpty { availableModels }
        val union = pool.flatMap { it.supportedEfforts() }.toSet()
        AiEffort.entries.filter { it in union }
    }

    EditorialDetailScaffold(
        title = "Modelli preferiti & Effort",
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            StatusBanner(
                icon = Icons.Filled.AutoAwesome,
                title = if (selectedIds.isEmpty()) {
                    "Scelta manuale o predefinita"
                } else {
                    "${selectedIds.size} ${if (selectedIds.size == 1) "modello preferito attivo" else "modelli preferiti attivi"}"
                },
                subtitle = "Puoi cambiare modello ed effort anche direttamente dalla card in Home.",
                tone = if (selectedIds.isEmpty()) BannerTone.INFO else BannerTone.SUCCESS,
            )

            Spacer(modifier = Modifier.height(14.dp))

            AiEffortSelectorCard(
                effort = state.userSettings.aiEffort,
                supportedEfforts = supportedEfforts,
                onSelectEffort = viewModel::setAiEffort,
                referenceModel = selectedModels.firstOrNull() ?: availableModels.firstOrNull(),
            )

            Spacer(modifier = Modifier.height(14.dp))

            BrandFilteredModelGrid(
                models = availableModels,
                selectedIds = selectedIds,
                onToggleModel = { viewModel.togglePreferredModel(it.uid) },
                modifier = Modifier.weight(1f),
                gridModifier = Modifier.weight(1f),
            )
        }
    }
}
