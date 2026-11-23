package com.ugocalc.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ugocalc.app.R
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.AiModelTier
import com.ugocalc.app.domain.model.recommendedTier
import com.ugocalc.app.ui.components.BrandFilterRow
import com.ugocalc.app.ui.components.EmptyStateMessage
import com.ugocalc.app.ui.modelselect.ModelGuidanceRow

fun modelMatchesQuery(model: AiModel, query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim().lowercase()
    val haystack = "${model.displayName} ${model.subtitle} ${model.id}".lowercase()
    if (haystack.contains(q)) return true
    val tokens = q.split("\\s+".toRegex()).filter { it.isNotEmpty() }
    if (tokens.all { token -> haystack.contains(token) }) return true
    val haystackWords = haystack.split("[\\s\\-_.:]+".toRegex()).filter { it.isNotEmpty() }
    return tokens.all { token ->
        haystackWords.any { word -> fuzzyTokenMatch(token, word) }
    }
}

private fun fuzzyTokenMatch(a: String, b: String): Boolean {
    if (a.length <= 2) return b.startsWith(a)
    val maxDist = if (a.length <= 4) 1 else 2
    return levenshtein(a, b) <= maxDist
}

private fun levenshtein(a: String, b: String): Int {
    val dp = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) dp[i][0] = i
    for (j in 0..b.length) dp[0][j] = j
    for (i in 1..a.length) {
        for (j in 1..b.length) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
            else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
        }
    }
    return dp[a.length][b.length]
}

internal fun modelBrand(model: AiModel): String = when (model.logoRes) {
    R.drawable.gpt -> "GPT"
    R.drawable.logoclaude -> "Claude"
    R.drawable.logogemini -> "Gemini"
    R.drawable.kimi_ai_logo_hd -> "Kimi"
    R.drawable.deepseek_logo -> "DeepSeek"
    R.drawable.nova -> "Amazon Nova"
    R.drawable.facebook_llama_logo_3dcc -> "Llama"
    R.drawable.mistral -> "Mistral"
    R.drawable.qwen_logo_png_seeklogo_611723 -> "Qwen"
    R.drawable.gemma -> "Gemma"
    R.drawable.cohere_logo_png_seeklogo_513871 -> "Cohere"
    R.drawable.ai21_labs_logo -> "AI21"
    R.drawable.nvidialogo -> "Nvidia"
    R.drawable.minimax -> "MiniMax"
    R.drawable.writer -> "Writer"
    R.drawable.zai -> "Zhipu"
    R.drawable.twelvelabs -> "TwelveLabs"
    else -> "Altro"
}

internal fun brandOrder(brand: String): Int = when (brand) {
    "GPT" -> 0
    "Claude" -> 1
    "Gemini" -> 2
    "Kimi" -> 3
    "DeepSeek" -> 4
    "Amazon Nova" -> 5
    "Llama" -> 6
    "Mistral" -> 7
    "Qwen" -> 8
    "Gemma" -> 9
    "Cohere" -> 10
    "AI21" -> 11
    "Nvidia" -> 12
    "MiniMax" -> 13
    "Writer" -> 14
    "Zhipu" -> 15
    "TwelveLabs" -> 16
    else -> 999
}

@Composable
internal fun BrandFilteredModelGrid(
    models: List<AiModel>,
    selectedIds: Set<String>,
    onToggleModel: (AiModel) -> Unit,
    modifier: Modifier = Modifier,
    emptyTitle: String = "Nessun modello disponibile",
    emptyMessage: String = "Configura almeno una chiave API in Impostazioni > API.",
    gridModifier: Modifier = Modifier,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedBrand by rememberSaveable { mutableStateOf<String?>(null) }

    val groupedByBrand = remember(models) {
        models.groupBy { modelBrand(it) }
    }
    val activeBrands = remember(groupedByBrand) {
        groupedByBrand.keys.sortedBy { brandOrder(it) }
    }
    val brandCounts = remember(groupedByBrand) {
        groupedByBrand.mapValues { it.value.size }
    }

    val effectiveBrand = if (selectedBrand != null && selectedBrand in activeBrands) selectedBrand else null
    val filteredModels = remember(models, searchQuery, effectiveBrand) {
        val base = if (effectiveBrand == null) models else groupedByBrand[effectiveBrand].orEmpty()
        base.filter { modelMatchesQuery(it, searchQuery) }
            .sortedBy {
                when (it.recommendedTier()) {
                    AiModelTier.RECOMMENDED -> 0
                    AiModelTier.FAST -> 1
                    AiModelTier.ACCURATE -> 2
                    AiModelTier.STANDARD -> 3
                }
            }
    }

    Column(modifier = modifier) {
        if (models.isEmpty()) {
            EmptyStateMessage(
                icon = Icons.Default.Psychology,
                title = emptyTitle,
                message = emptyMessage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
            )
            return
        }

        if (activeBrands.size > 1) {
            BrandFilterRow(
                brands = activeBrands,
                selectedBrand = effectiveBrand,
                counts = brandCounts,
                onSelect = { selectedBrand = it },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (models.size > 5) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cerca modello…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancella")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(10.dp))
        }

        if (filteredModels.isEmpty()) {
            EmptyStateMessage(
                icon = Icons.Default.Search,
                title = "Nessun modello trovato",
                message = "Prova a modificare la ricerca o il filtro per brand.",
                actionLabel = "Mostra tutti",
                onAction = {
                    searchQuery = ""
                    selectedBrand = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                modifier = gridModifier.fillMaxWidth(),
            ) {
                items(filteredModels, key = { it.uid }, contentType = { "model_row" }) { model ->
                    val isSelected = model.uid in selectedIds || model.id in selectedIds
                    ModelGuidanceRow(
                        model = model,
                        isSelected = isSelected,
                        onClick = { onToggleModel(model) },
                    )
                }
            }
        }
    }
}

internal fun toggleModelSelection(current: Set<String>, model: AiModel): Set<String> {
    val isSelected = model.uid in current || model.id in current
    return if (isSelected) current - model.uid - model.id else current + model.uid
}
