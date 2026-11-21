package com.ugocalc.app.ui.foodhub

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import com.ugocalc.app.data.repository.FavoritesRepository
import com.ugocalc.app.data.repository.RecipeRepository
import com.ugocalc.app.data.repository.SettingsRepository
import com.ugocalc.app.data.repository.TableRepository
import com.ugocalc.app.domain.inference.model.SessionMode
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.ui.AnalysisSessionStore
import com.ugocalc.app.ui.components.AddComboFavoriteDialog
import com.ugocalc.app.ui.components.FoodSearchPicker
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoSectionHeader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FoodHubUiState(
    val totalFoodCount: Int = 0,
    val tableCount: Int = 0,
    val recipeCount: Int = 0,
    val favoritesCount: Int = 0,
    val sessionMode: SessionMode = SessionMode.CASA,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FoodHubViewModel @Inject constructor(
    private val tableRepository: TableRepository,
    private val recipeRepository: RecipeRepository,
    private val favoritesRepository: FavoritesRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionStore: AnalysisSessionStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoodHubUiState())
    val uiState: StateFlow<FoodHubUiState> = _uiState

    private val refreshTrigger = MutableStateFlow(0)

    init {
        combine(
            tableRepository.observeTables(),
            refreshTrigger,
        ) { tables, _ ->
            val totalFoods = tables.sumOf { table ->
                tableRepository.getEntriesForTable(table.id).size
            }
            tables.size to totalFoods
        }
            .flowOn(Dispatchers.IO)
            .onEach { (tableCount, totalFoods) ->
                _uiState.update {
                    it.copy(tableCount = tableCount, totalFoodCount = totalFoods)
                }
            }
            .launchIn(viewModelScope)

        favoritesRepository.observeFavorites()
            .onEach { favorites ->
                _uiState.update { it.copy(favoritesCount = favorites.size) }
            }
            .launchIn(viewModelScope)

        settingsRepository.settings
            .flatMapLatest { settings ->
                _uiState.update { it.copy(sessionMode = settings.activeSessionMode) }
                recipeRepository.getAllRecipes(settings.activeSessionMode)
            }
            .onEach { recipes ->
                _uiState.update { it.copy(recipeCount = recipes.size) }
            }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        refreshTrigger.update { it + 1 }
    }

    fun startManualEntryWithFood(entry: NormalizedFoodEntry) {
        sessionStore.reset()
        sessionStore.setSessionMode(_uiState.value.sessionMode)
        sessionStore.setPendingManualEntry(entry)
    }

    fun addComboFavorite(
        foodName: String,
        grams: Double?,
        choPer100g: Double,
        grassiPer100g: Double,
        proteinePer100g: Double = 0.0,
        indiceGlicemico: Int? = null,
    ) {
        viewModelScope.launch {
            val nextOrder = _uiState.value.favoritesCount
            val label = if (grams != null) "$foodName (${grams.toInt()}g)" else foodName
            favoritesRepository.addComboFavorite(
                label = label,
                grams = grams,
                choPer100g = choPer100g,
                grassiPer100g = grassiPer100g,
                sortOrder = nextOrder,
                proteinePer100g = proteinePer100g,
                indiceGlicemico = indiceGlicemico,
            )
        }
    }
}

@Composable
fun FoodHubScreen(
    onOpenTables: () -> Unit,
    onOpenRecipes: () -> Unit,
    onOpenFavorites: () -> Unit,
    onStartManualEntry: () -> Unit,
    viewModel: FoodHubViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var favoriteCandidate by remember { mutableStateOf<NormalizedFoodEntry?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            FoodHubHeader(
                totalFoodCount = uiState.totalFoodCount,
                tableCount = uiState.tableCount,
            )
        },
    ) { padding ->
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.40f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FoodHubQuickAccessCard(
                        icon = Icons.Rounded.TableChart,
                        accent = MaterialTheme.colorScheme.primary,
                        title = "Tabelle nutrizionali",
                        subtitle = "${uiState.tableCount} ${if (uiState.tableCount == 1) "tabella" else "tabelle"} · ${uiState.totalFoodCount} voci",
                        onClick = onOpenTables,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FoodHubQuickAccessCard(
                        icon = Icons.Rounded.MenuBook,
                        accent = MaterialTheme.colorScheme.secondary,
                        title = "Ricette",
                        subtitle = "${uiState.recipeCount} ${if (uiState.recipeCount == 1) "ricetta" else "ricette"}",
                        onClick = onOpenRecipes,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FoodHubQuickAccessCard(
                        icon = Icons.Rounded.Star,
                        accent = MaterialTheme.colorScheme.tertiary,
                        title = "Preferiti",
                        subtitle = "${uiState.favoritesCount} salvati",
                        onClick = onOpenFavorites,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(0.60f)
                        .fillMaxHeight(),
                ) {
                    UgoSectionHeader(
                        title = "Cerca nel database",
                        subtitle = "Tocca un alimento per calcolare subito i CHO",
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FoodSearchPicker(
                        onSelect = { entry ->
                            viewModel.startManualEntryWithFood(entry)
                            onStartManualEntry()
                        },
                        onFavorite = { entry -> favoriteCandidate = entry },
                        fillAvailableHeight = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // 3 visual quick-access cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FoodHubCompactTile(
                        icon = Icons.Rounded.TableChart,
                        accent = MaterialTheme.colorScheme.primary,
                        title = "Tabelle",
                        countLabel = "${uiState.tableCount}",
                        onClick = onOpenTables,
                        modifier = Modifier.weight(1f),
                    )
                    FoodHubCompactTile(
                        icon = Icons.Rounded.MenuBook,
                        accent = MaterialTheme.colorScheme.secondary,
                        title = "Ricette",
                        countLabel = "${uiState.recipeCount}",
                        onClick = onOpenRecipes,
                        modifier = Modifier.weight(1f),
                    )
                    FoodHubCompactTile(
                        icon = Icons.Rounded.Star,
                        accent = MaterialTheme.colorScheme.tertiary,
                        title = "Preferiti",
                        countLabel = "${uiState.favoritesCount}",
                        onClick = onOpenFavorites,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                UgoSectionHeader(
                    title = "Cerca nel database",
                    subtitle = "Valori per 100g · tocca per avviare un calcolo rapido",
                )

                Spacer(modifier = Modifier.height(10.dp))

                FoodSearchPicker(
                    onSelect = { entry ->
                        viewModel.startManualEntryWithFood(entry)
                        onStartManualEntry()
                    },
                    onFavorite = { entry -> favoriteCandidate = entry },
                    fillAvailableHeight = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }

    favoriteCandidate?.let { entry ->
        AddComboFavoriteDialog(
            initialEntry = entry,
            onDismiss = { favoriteCandidate = null },
            onConfirm = { _, _, _, _ -> },
            onConfirmFull = { foodName, grams, cho, grassi, prot, gi ->
                viewModel.addComboFavorite(foodName, grams, cho, grassi, prot, gi)
                favoriteCandidate = null
                scope.launch {
                    snackbarHostState.showSnackbar("Aggiunto ai preferiti: $foodName")
                }
            },
        )
    }
}

@Composable
private fun FoodHubHeader(
    totalFoodCount: Int,
    tableCount: Int,
) {
    val tablesWord = if (tableCount == 1) "tabella" else "tabelle"
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestaurantMenu,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = "Alimenti",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text(
                    text = "$totalFoodCount alimenti in $tableCount $tablesWord",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun FoodHubCompactTile(
    icon: ImageVector,
    accent: Color,
    title: String,
    countLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UgoCard(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = countLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FoodHubQuickAccessCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UgoCard(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
