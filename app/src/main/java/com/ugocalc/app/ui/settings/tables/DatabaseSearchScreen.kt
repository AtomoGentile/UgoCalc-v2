package com.ugocalc.app.ui.settings.tables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.ui.components.AddComboFavoriteDialog
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.FoodSearchPicker
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import kotlinx.coroutines.launch

/**
 * Ricerca libera nel database (§ richiesta utente): per consultare rapidamente CHO/grassi di un
 * alimento senza dover avviare un'analisi, e aggiungere combinazioni ai preferiti tramite stellina.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseSearchScreen(
    onBack: () -> Unit,
    viewModel: DatabaseSearchViewModel = hiltViewModel(),
) {
    var favoriteCandidate by remember { mutableStateOf<NormalizedFoodEntry?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cerca nel database") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors(),
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            StatusBanner(
                icon = Icons.Default.Search,
                title = "Consulta tabelle nutrizionali",
                subtitle = "Cerca per nome o filtra per fascia CHO. Tocca la stella su un alimento per salvarlo nei preferiti.",
                tone = BannerTone.INFO,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            UgoCard(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
            ) {
                FoodSearchPicker(
                    onSelect = {},
                    onFavorite = { entry -> favoriteCandidate = entry },
                    fillAvailableHeight = true,
                    showChoFilter = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    favoriteCandidate?.let { entry ->
        AddComboFavoriteDialog(
            initialEntry = entry,
            onDismiss = { favoriteCandidate = null },
            onConfirm = { foodName, grams, cho, grassi ->
                viewModel.addComboFavorite(foodName, grams, cho, grassi)
                favoriteCandidate = null
                scope.launch {
                    snackbarHostState.showSnackbar("\"$foodName\" aggiunto ai preferiti")
                }
            },
        )
    }
}
