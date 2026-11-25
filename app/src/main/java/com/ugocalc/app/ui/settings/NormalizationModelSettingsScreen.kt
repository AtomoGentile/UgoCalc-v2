package com.ugocalc.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.ugoTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NormalizationModelSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val selectedIds = state.userSettings.enabledNormalizationModelIds

    com.ugocalc.app.ui.components.EditorialDetailScaffold(
        title = "Modelli per normalizzazione",
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
                    icon = Icons.Filled.Tune,
                    title = if (selectedIds.isEmpty()) {
                        "Tutti i modelli attivi abilitati"
                    } else {
                        "${selectedIds.size} modelli selezionati"
                    },
                    subtitle = "Scegli quali modelli utilizzare per importare e normalizzare le tabelle nutrizionali.",
                    tone = BannerTone.INFO,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            BrandFilteredModelGrid(
                models = availableModels,
                selectedIds = selectedIds,
                onToggleModel = { viewModel.toggleNormalizationModel(it.uid) },
                modifier = Modifier.weight(1f),
                gridModifier = Modifier.weight(1f),
            )
        }
    }
}
