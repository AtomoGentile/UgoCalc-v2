package com.ugocalc.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.domain.model.AiProviderType
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.ModelLogoBox
import com.ugocalc.app.ui.components.ugoTopAppBarColors

/** Impostazioni > API: sezioni accordion per ciascun provider cloud. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("API") },
            navigationIcon = { BackIconButton(onBack) },
            colors = ugoTopAppBarColors(),
        )
    }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            ApiSection(title = "GitHub Models (OpenAI)") {
                ApiKeyField(
                    label = "GitHub API Key",
                    provider = AiProviderType.OPENAI,
                    viewModel = viewModel,
                )
            }

            Spacer(Modifier.height(8.dp))

            ApiSection(title = "OpenAI (API diretta)") {
                ApiKeyField(
                    label = "OpenAI API Key",
                    provider = AiProviderType.OPENAI_API,
                    viewModel = viewModel,
                )
            }

            Spacer(Modifier.height(8.dp))

            ApiSection(title = "Anthropic (Claude)") {
                ApiKeyField(
                    label = "Anthropic API Key",
                    provider = AiProviderType.ANTHROPIC,
                    viewModel = viewModel,
                )
            }

            Spacer(Modifier.height(8.dp))

            ApiSection(title = "Google Gemini") {
                ApiKeyField(
                    label = "Gemini API Key",
                    provider = AiProviderType.GEMINI,
                    viewModel = viewModel,
                )
            }

            Spacer(Modifier.height(8.dp))

            ApiSection(title = "AWS Bedrock (Mantle)") {
                BedrockCredentialsSection(viewModel)
            }

            Spacer(Modifier.height(8.dp))

            ApiSection(title = "AWS Bedrock (IAM diretto)") {
                BedrockIamCredentialsSection(viewModel)
            }

            val showAllPicker by viewModel.showAllModelsPicker.collectAsState()
            val state by viewModel.state.collectAsState()
            val availableModels by viewModel.availableModels.collectAsState()

            if (availableModels.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.openAllModelsPicker() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Scegli modelli per \"Analizza\"")
                }
            }

            if (showAllPicker) {
                AllModelsPickerDialog(
                    models = availableModels,
                    currentSelection = state.userSettings.enabledAnalysisModelIds,
                    onConfirm = { viewModel.saveAllEnabledModels(it) },
                    onDismiss = { viewModel.dismissAllModelsPicker() },
                )
            }
        }
    }
}

@Composable
private fun ApiSection(
    title: String,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Surface(
        modifier = Modifier.fillMaxWidth().clip(shape),
        shape = shape,
        tonalElevation = 2.dp,
        shadowElevation = if (expanded) 2.dp else 0.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        content()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BedrockCredentialsSection(viewModel: SettingsViewModel) {
    var mantleApiKey by remember { mutableStateOf(viewModel.getBedrockMantleApiKey()) }
    var region by remember { mutableStateOf(viewModel.getBedrockRegion()) }
    val testResult by viewModel.bedrockTestResult.collectAsState()
    val showPicker by viewModel.showBedrockModelPicker.collectAsState()
    val enabledIds by viewModel.enabledBedrockModelIds.collectAsState()

    // Apre automaticamente il picker al primo test riuscito.
    LaunchedEffect(testResult) {
        if (testResult is BedrockTestResult.Success) viewModel.openBedrockModelPicker()
    }

    OutlinedTextField(
        value = mantleApiKey,
        onValueChange = {
            mantleApiKey = it
            viewModel.setBedrockCredentials(mantleApiKey, region)
        },
        label = { Text("Mantle API Key") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )

    val regions = listOf("us-east-1", "us-west-2", "eu-west-1", "eu-central-1", "ap-northeast-1", "ap-southeast-1")
    var regionExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = regionExpanded, onExpandedChange = { regionExpanded = it }) {
        OutlinedTextField(
            value = region,
            onValueChange = {},
            readOnly = true,
            label = { Text("Region") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = regionExpanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .padding(bottom = 8.dp),
        )
        ExposedDropdownMenu(expanded = regionExpanded, onDismissRequest = { regionExpanded = false }) {
            regions.forEach { r ->
                DropdownMenuItem(text = { Text(r) }, onClick = {
                    region = r
                    regionExpanded = false
                    viewModel.setBedrockCredentials(mantleApiKey, region)
                })
            }
        }
    }

    Button(
        onClick = { viewModel.testBedrockConnection(mantleApiKey, region) },
        enabled = testResult !is BedrockTestResult.Loading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (testResult is BedrockTestResult.Loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Text("Test connessione")
        }
    }

    when (val result = testResult) {
        is BedrockTestResult.Success -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp).padding(end = 4.dp),
            )
            Text(result.message, color = MaterialTheme.colorScheme.primary)
        }
        is BedrockTestResult.Error -> Text(
            result.message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 6.dp),
        )
        else -> {}
    }

    if (mantleApiKey.isNotBlank()) {
        OutlinedButton(
            onClick = { viewModel.openBedrockModelPicker() },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text("Scegli modelli Bedrock disponibili")
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BedrockIamCredentialsSection(viewModel: SettingsViewModel) {
    var accessKey by remember { mutableStateOf(viewModel.getBedrockIamAccessKey()) }
    var secretKey by remember { mutableStateOf(viewModel.getBedrockIamSecretKey()) }
    var region by remember { mutableStateOf(viewModel.getBedrockIamRegion()) }
    val testResult by viewModel.bedrockIamTestResult.collectAsState()
    val showPicker by viewModel.showBedrockIamModelPicker.collectAsState()
    val enabledIds by viewModel.enabledBedrockIamModelIds.collectAsState()

    LaunchedEffect(testResult) {
        if (testResult is BedrockTestResult.Success) viewModel.openBedrockIamModelPicker()
    }

    OutlinedTextField(
        value = accessKey,
        onValueChange = {
            accessKey = it
            viewModel.setBedrockIamCredentials(accessKey, secretKey, region)
        },
        label = { Text("Access Key ID") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )

    OutlinedTextField(
        value = secretKey,
        onValueChange = {
            secretKey = it
            viewModel.setBedrockIamCredentials(accessKey, secretKey, region)
        },
        label = { Text("Secret Access Key") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )

    val regions = listOf("us-east-1", "us-west-2", "eu-west-1", "eu-central-1", "ap-northeast-1", "ap-southeast-1")
    var regionExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = regionExpanded, onExpandedChange = { regionExpanded = it }) {
        OutlinedTextField(
            value = region,
            onValueChange = {},
            readOnly = true,
            label = { Text("Region") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = regionExpanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .padding(bottom = 8.dp),
        )
        ExposedDropdownMenu(expanded = regionExpanded, onDismissRequest = { regionExpanded = false }) {
            regions.forEach { r ->
                DropdownMenuItem(text = { Text(r) }, onClick = {
                    region = r
                    regionExpanded = false
                    viewModel.setBedrockIamCredentials(accessKey, secretKey, region)
                })
            }
        }
    }

    Button(
        onClick = { viewModel.testBedrockIamConnection(accessKey, secretKey, region) },
        enabled = testResult !is BedrockTestResult.Loading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (testResult is BedrockTestResult.Loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Text("Test connessione")
        }
    }

    when (val result = testResult) {
        is BedrockTestResult.Success -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp).padding(end = 4.dp),
            )
            Text(result.message, color = MaterialTheme.colorScheme.primary)
        }
        is BedrockTestResult.Error -> Text(
            result.message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 6.dp),
        )
        else -> {}
    }

}

@Composable
private fun ApiKeyField(label: String, provider: AiProviderType, viewModel: SettingsViewModel) {
    var value by remember { mutableStateOf(viewModel.getApiKey(provider).orEmpty()) }
    OutlinedTextField(
        value = value,
        onValueChange = {
            value = it
            viewModel.setApiKey(provider, it)
        },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AllModelsPickerDialog(
    models: List<com.ugocalc.app.domain.model.AiModel>,
    currentSelection: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(currentSelection) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredModels = remember(searchQuery, models) {
        models.filter { modelMatchesQuery(it, searchQuery) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scegli i modelli") },
        text = {
            Column {
                Text(
                    "Seleziona i modelli che vuoi usare in \"Analizza\".",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cerca modello…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(360.dp),
                ) {
                    items(filteredModels, key = { it.uid }) { model ->
                        val isSelected = model.id in selected
                        val onClick = remember(model.uid, isSelected) {
                            { selected = if (isSelected) selected - model.id else selected + model.id }
                        }
                        ApiProviderModelCard(
                            model = model,
                            isSelected = isSelected,
                            onClick = onClick,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selected) },
                enabled = selected.isNotEmpty(),
            ) { Text("Conferma") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        },
    )
}

@Composable
private fun ApiProviderModelCard(
    model: com.ugocalc.app.domain.model.AiModel,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val shape = RoundedCornerShape(12.dp)
    val cardModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1.1f)
        .border(borderWidth, borderColor, shape)
    val elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)

    Card(
        onClick = onClick,
        modifier = cardModifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = elevation,
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
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
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF1C1B1F),
                )
                if (model.subtitle.isNotEmpty()) {
                    Text(
                        text = model.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF49454F),
                    )
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.TopEnd).size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
