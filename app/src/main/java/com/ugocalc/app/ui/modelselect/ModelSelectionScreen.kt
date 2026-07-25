package com.ugocalc.app.ui.modelselect

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.ui.components.ModelLogoBox
import com.ugocalc.app.ui.components.ugoTopAppBarColors

/** Schermata di selezione modello (§2.4 punto 1): griglia di card con logo e selezione visiva. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionScreen(
    onNext: (modelIds: List<String>) -> Unit,
    onBack: () -> Unit,
    viewModel: ModelSelectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Scegli i modelli") }, colors = ugoTopAppBarColors()) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (uiState.isOffline) {
                Text(
                    "Sei offline: nessun modello è selezionabile senza connessione a internet.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            if (uiState.availableModels.isEmpty() && !uiState.isOffline) {
                Text(
                    "Nessun modello disponibile. Configura una API key nelle Impostazioni > API.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.availableModels, key = { it.uid }) { model ->
                    val onClick = remember { { viewModel.toggleModel(model.uid) } }
                    ModelCard(
                        model = model,
                        isSelected = model.uid in uiState.selectedModelUids,
                        onClick = onClick,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Button(onClick = onBack) { Text("Indietro") }
                Button(
                    enabled = uiState.selectedModelUids.isNotEmpty(),
                    onClick = { onNext(viewModel.confirmSelectionAndProceed()) },
                ) { Text("Avanti") }
            }
        }
    }
}

@Composable
private fun ModelCard(model: AiModel, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val shape = RoundedCornerShape(12.dp)
    val cardModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(0.9f)
        .border(borderWidth, borderColor, shape)
    val elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)

    Card(
        onClick = onClick,
        modifier = cardModifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = elevation,
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ModelLogoBox(
                    logoRes = model.logoRes,
                    displayName = model.displayName,
                    subtitle = model.subtitle,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF1C1B1F),
                )
                if (model.subtitle.isNotEmpty()) {
                    Text(
                        text = model.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF49454F),
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.TopEnd).size(22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
