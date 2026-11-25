package com.ugocalc.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.ugoTopAppBarColors

/**
 * Impostazioni > Modelli Shadow (testing V2): seleziona quali modelli testare in shadow mode
 * oltre alla V1 durante le analisi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShadowModelSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val enabledAnalysisIds = state.userSettings.enabledAnalysisModelIds
    val analysisModels = remember(availableModels, enabledAnalysisIds) {
        if (enabledAnalysisIds.isNotEmpty()) {
            availableModels.filter { it.uid in enabledAnalysisIds || it.id in enabledAnalysisIds }
        } else {
            availableModels
        }
    }
    val selectedIds = state.userSettings.shadowModelIds

    com.ugocalc.app.ui.components.EditorialDetailScaffold(
        title = "Modelli V2 in shadow",
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            UgoCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
            ) {
                StatusBanner(
                    icon = Icons.Filled.Science,
                    title = if (selectedIds.isEmpty()) {
                        "Nessun modello shadow attivo"
                    } else {
                        "${selectedIds.size} modelli in shadow mode"
                    },
                    subtitle = "La pipeline V1 rimane visibile mentre i modelli selezionati testano il motore V2 in background.",
                    tone = if (selectedIds.isEmpty()) BannerTone.INFO else BannerTone.WARNING,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            BrandFilteredModelGrid(
                models = analysisModels,
                selectedIds = selectedIds,
                onToggleModel = { viewModel.toggleShadowModel(it.uid) },
                emptyTitle = "Nessun modello disponibile",
                emptyMessage = "Abilita almeno un modello per \"Analizza\" in Impostazioni > API.",
                modifier = Modifier.weight(1f),
                gridModifier = Modifier.weight(1f),
            )
        }
    }
}
