package com.ugocalc.app.ui.settings.tables

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.ui.components.AddComboFavoriteDialog
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.FoodSearchPicker
import com.ugocalc.app.ui.components.ugoTopAppBarColors

/**
 * Ricerca libera nel database (§ richiesta utente): stessa ricerca già usata per comporre un
 * pasto a mano o correggere un abbinamento AI, ma qui fine a se stessa — per consultare
 * rapidamente CHO/grassi di un alimento senza dover avviare un'analisi. La stellina su un
 * risultato lo aggiunge ai preferiti come combinazione standard alimento+peso.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseSearchScreen(
    onBack: () -> Unit,
    viewModel: DatabaseSearchViewModel = hiltViewModel(),
) {
    var favoriteCandidate by remember { mutableStateOf<NormalizedFoodEntry?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Cerca nel database") }, navigationIcon = { BackIconButton(onBack) }, colors = ugoTopAppBarColors()) }) { padding ->
        FoodSearchPicker(
            onSelect = {},
            onFavorite = { entry -> favoriteCandidate = entry },
            fillAvailableHeight = true,
            showChoFilter = true,
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        )
    }

    favoriteCandidate?.let { entry ->
        AddComboFavoriteDialog(
            initialEntry = entry,
            onDismiss = { favoriteCandidate = null },
            onConfirm = { foodName, grams, cho, grassi ->
                viewModel.addComboFavorite(foodName, grams, cho, grassi)
                favoriteCandidate = null
            },
        )
    }
}
