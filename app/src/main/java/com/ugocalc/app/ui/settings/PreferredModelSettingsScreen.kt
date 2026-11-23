package com.ugocalc.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.domain.model.AiEffort
import com.ugocalc.app.domain.model.supportedEfforts
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.modelselect.AiEffortSelectorCard

/**
 * Impostazioni > Modelli preferiti (§2.2): se ne selezioni almeno uno, "Analizza" in Home salta
 * del tutto la schermata di scelta modello e avvia subito l'analisi con questi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferredModelSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val selectedIds = state.userSettings.preferredModelIds
    val supportedEfforts = remember(availableModels, selectedIds) {
        val selected = availableModels.filter { it.uid in selectedIds || it.id in selectedIds }
        val pool = selected.ifEmpty { availableModels }
        val union = pool.flatMap { it.supportedEfforts() }.toSet()
        AiEffort.entries.filter { it in union }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modelli preferiti & Effort") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors(),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            UgoCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
            ) {
                StatusBanner(
                    icon = Icons.Filled.AutoAwesome,
                    title = if (selectedIds.isEmpty()) {
                        "Scelta manuale ad ogni analisi"
                    } else {
                        "${selectedIds.size} modelli preferiti attivi"
                    },
                    subtitle = "Se selezioni uno o più modelli, l'analisi partirà subito con questi senza chiederti ogni volta.",
                    tone = if (selectedIds.isEmpty()) BannerTone.INFO else BannerTone.SUCCESS,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            AiEffortSelectorCard(
                effort = state.userSettings.aiEffort,
                supportedEfforts = supportedEfforts,
                onSelectEffort = viewModel::setAiEffort,
            )

            Spacer(modifier = Modifier.height(12.dp))

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
