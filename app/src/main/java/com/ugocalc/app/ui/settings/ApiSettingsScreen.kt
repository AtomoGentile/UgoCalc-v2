package com.ugocalc.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.domain.model.AiModel
import com.ugocalc.app.domain.model.AiProviderType
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoConfirmDialog
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.components.EditorialDetailScaffold
import com.ugocalc.app.ui.theme.UgoSemanticColors

/** Impostazioni > API: configurazione esplicita e test connessione per ogni provider AI. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiSettingsScreen(
    onBack: () -> Unit,
    onNavigateToModelsPicker: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val testStates by viewModel.providerTestStates.collectAsState()
    val configuredMap by viewModel.configuredProviders.collectAsState()
    val showAllPicker by viewModel.showAllModelsPicker.collectAsState()
    val state by viewModel.state.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()

    EditorialDetailScaffold(
        title = "API e Provider AI",
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ApiSection(
                title = "Google Gemini (AI Studio)",
                isConfigured = configuredMap[AiProviderType.GEMINI] == true,
                initiallyExpanded = configuredMap[AiProviderType.GEMINI] != true && configuredMap.values.none { it },
            ) {
                SimpleApiKeySection(
                    label = "Gemini API Key",
                    tip = "Consigliato per iniziare: piano gratuito disponibile su Google AI Studio (aistudio.google.com).",
                    initialValue = viewModel.getApiKey(AiProviderType.GEMINI).orEmpty(),
                    isConfigured = configuredMap[AiProviderType.GEMINI] == true,
                    testState = testStates[AiProviderType.GEMINI] ?: ProviderTestState.Idle,
                    onSaveAndTest = { viewModel.testAndSaveGeminiKey(it) },
                    onClear = { viewModel.clearApiKey(AiProviderType.GEMINI) },
                )
            }

            ApiSection(
                title = "OpenAI (API diretta)",
                isConfigured = configuredMap[AiProviderType.OPENAI_API] == true,
            ) {
                SimpleApiKeySection(
                    label = "OpenAI API Key",
                    tip = "Accesso diretto a GPT-4o, GPT-4.1, o3 e o4 tramite platform.openai.com.",
                    initialValue = viewModel.getApiKey(AiProviderType.OPENAI_API).orEmpty(),
                    isConfigured = configuredMap[AiProviderType.OPENAI_API] == true,
                    testState = testStates[AiProviderType.OPENAI_API] ?: ProviderTestState.Idle,
                    onSaveAndTest = { viewModel.testAndSaveOpenAiKey(it) },
                    onClear = { viewModel.clearApiKey(AiProviderType.OPENAI_API) },
                )
            }

            ApiSection(
                title = "Anthropic (Claude)",
                isConfigured = configuredMap[AiProviderType.ANTHROPIC] == true,
            ) {
                SimpleApiKeySection(
                    label = "Anthropic API Key",
                    tip = "Modelli Claude Sonnet, Haiku e Opus tramite console.anthropic.com.",
                    initialValue = viewModel.getApiKey(AiProviderType.ANTHROPIC).orEmpty(),
                    isConfigured = configuredMap[AiProviderType.ANTHROPIC] == true,
                    testState = testStates[AiProviderType.ANTHROPIC] ?: ProviderTestState.Idle,
                    onSaveAndTest = { viewModel.testAndSaveAnthropicKey(it) },
                    onClear = { viewModel.clearApiKey(AiProviderType.ANTHROPIC) },
                )
            }

            ApiSection(
                title = "AWS Bedrock (Mantle)",
                isConfigured = configuredMap[AiProviderType.BEDROCK_CLAUDE] == true,
            ) {
                BedrockCredentialsSection(
                    viewModel = viewModel,
                    isConfigured = configuredMap[AiProviderType.BEDROCK_CLAUDE] == true,
                    testState = testStates[AiProviderType.BEDROCK_CLAUDE] ?: ProviderTestState.Idle,
                )
            }

            ApiSection(
                title = "AWS Bedrock (IAM diretto)",
                isConfigured = configuredMap[AiProviderType.BEDROCK_IAM_CLAUDE] == true,
            ) {
                BedrockIamCredentialsSection(
                    viewModel = viewModel,
                    isConfigured = configuredMap[AiProviderType.BEDROCK_IAM_CLAUDE] == true,
                    testState = testStates[AiProviderType.BEDROCK_IAM_CLAUDE] ?: ProviderTestState.Idle,
                )
            }

            ApiSection(
                title = "Google Vertex AI",
                isConfigured = configuredMap[AiProviderType.VERTEX_AI] == true,
            ) {
                VertexAiCredentialsSection(
                    viewModel = viewModel,
                    isConfigured = configuredMap[AiProviderType.VERTEX_AI] == true,
                    testState = testStates[AiProviderType.VERTEX_AI] ?: ProviderTestState.Idle,
                )
            }

            if (availableModels.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { viewModel.openAllModelsPicker() },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Scegli modelli per \"Analizza\" (${availableModels.size} disponibili)")
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }

    if (showAllPicker) {
        AllModelsPickerDialog(
            models = availableModels,
            currentSelection = state.userSettings.enabledAnalysisModelIds,
            onConfirm = { viewModel.saveAllEnabledModels(it) },
            onDismiss = { viewModel.dismissAllModelsPicker() },
            onOpenFullscreen = {
                viewModel.dismissAllModelsPicker()
                onNavigateToModelsPicker()
            },
        )
    }
}

@Composable
private fun ApiSection(
    title: String,
    isConfigured: Boolean,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }

    UgoCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            ProviderConfigPill(isConfigured = isConfigured)
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Comprimi" else "Espandi",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun ProviderConfigPill(isConfigured: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isConfigured) {
            UgoSemanticColors.successContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Text(
            text = if (isConfigured) "Configurato ✓" else "Non configurato",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isConfigured) {
                UgoSemanticColors.onSuccessContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SecretTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var visible by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Nascondi" else "Mostra",
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun ProviderTestFeedback(testState: ProviderTestState) {
    when (testState) {
        is ProviderTestState.Idle -> Unit
        is ProviderTestState.Testing -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 10.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Verifica connessione e modelli in corso…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is ProviderTestState.Success -> {
            Spacer(modifier = Modifier.height(10.dp))
            StatusBanner(
                icon = Icons.Filled.CheckCircle,
                title = "Connessione riuscita",
                subtitle = testState.message,
                tone = BannerTone.SUCCESS,
            )
        }
        is ProviderTestState.Error -> {
            Spacer(modifier = Modifier.height(10.dp))
            StatusBanner(
                icon = Icons.Filled.ErrorOutline,
                title = "Errore verifica",
                subtitle = testState.message,
                tone = BannerTone.ERROR,
            )
        }
    }
}

@Composable
private fun ProviderActionRow(
    canSaveAndTest: Boolean,
    isTesting: Boolean,
    isConfigured: Boolean,
    onSaveAndTest: () -> Unit,
    onRequestRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onSaveAndTest,
            enabled = canSaveAndTest && !isTesting,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Verifica…")
            } else {
                Text("Salva e testa")
            }
        }
        if (isConfigured) {
            TextButton(
                onClick = onRequestRemove,
                enabled = !isTesting,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Rimuovi")
            }
        }
    }
}

@Composable
private fun SimpleApiKeySection(
    label: String,
    tip: String,
    initialValue: String,
    isConfigured: Boolean,
    testState: ProviderTestState,
    onSaveAndTest: (String) -> Unit,
    onClear: () -> Unit,
) {
    var inputValue by rememberSaveable { mutableStateOf(initialValue) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    Text(
        text = tip,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 10.dp),
    )

    SecretTextField(
        value = inputValue,
        onValueChange = { inputValue = it },
        label = label,
    )

    ProviderActionRow(
        canSaveAndTest = inputValue.isNotBlank(),
        isTesting = testState is ProviderTestState.Testing,
        isConfigured = isConfigured,
        onSaveAndTest = { onSaveAndTest(inputValue) },
        onRequestRemove = { showRemoveConfirm = true },
    )

    ProviderTestFeedback(testState)

    if (showRemoveConfirm) {
        UgoConfirmDialog(
            title = "Rimuovi chiave API",
            message = "Vuoi rimuovere la chiave salvata per $label?",
            confirmLabel = "Rimuovi",
            isDestructive = true,
            onConfirm = {
                showRemoveConfirm = false
                inputValue = ""
                onClear()
            },
            onDismiss = { showRemoveConfirm = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BedrockCredentialsSection(
    viewModel: SettingsViewModel,
    isConfigured: Boolean,
    testState: ProviderTestState,
) {
    var mantleApiKey by rememberSaveable { mutableStateOf(viewModel.getBedrockMantleApiKey()) }
    var region by rememberSaveable { mutableStateOf(viewModel.getBedrockRegion()) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    Text(
        text = "Usa una chiave API Bedrock Mantle con endpoint regionale AWS.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 10.dp),
    )

    SecretTextField(
        value = mantleApiKey,
        onValueChange = { mantleApiKey = it },
        label = "Mantle API Key",
        modifier = Modifier.padding(bottom = 8.dp),
    )

    RegionDropdownField(
        label = "Region",
        selectedRegion = region,
        regions = listOf("us-east-1", "us-west-2", "eu-west-1", "eu-central-1", "ap-northeast-1", "ap-southeast-1"),
        onRegionSelected = { region = it },
    )

    ProviderActionRow(
        canSaveAndTest = mantleApiKey.isNotBlank(),
        isTesting = testState is ProviderTestState.Testing,
        isConfigured = isConfigured,
        onSaveAndTest = { viewModel.testBedrockConnection(mantleApiKey, region) },
        onRequestRemove = { showRemoveConfirm = true },
    )

    ProviderTestFeedback(testState)

    if (showRemoveConfirm) {
        UgoConfirmDialog(
            title = "Rimuovi Bedrock Mantle",
            message = "Vuoi rimuovere le credenziali AWS Bedrock Mantle salvate?",
            confirmLabel = "Rimuovi",
            isDestructive = true,
            onConfirm = {
                showRemoveConfirm = false
                mantleApiKey = ""
                viewModel.clearBedrockCredentials()
            },
            onDismiss = { showRemoveConfirm = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BedrockIamCredentialsSection(
    viewModel: SettingsViewModel,
    isConfigured: Boolean,
    testState: ProviderTestState,
) {
    var accessKey by rememberSaveable { mutableStateOf(viewModel.getBedrockIamAccessKey()) }
    var secretKey by rememberSaveable { mutableStateOf(viewModel.getBedrockIamSecretKey()) }
    var accountId by rememberSaveable { mutableStateOf(viewModel.getBedrockIamAccountId()) }
    var region by rememberSaveable { mutableStateOf(viewModel.getBedrockIamRegion()) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    Text(
        text = "Accesso diretto AWS Bedrock Runtime autenticato con firma IAM SigV4.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 10.dp),
    )

    OutlinedTextField(
        value = accessKey,
        onValueChange = { accessKey = it },
        label = { Text("Access Key ID") },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )

    SecretTextField(
        value = secretKey,
        onValueChange = { secretKey = it },
        label = "Secret Access Key",
        modifier = Modifier.padding(bottom = 8.dp),
    )

    OutlinedTextField(
        value = accountId,
        onValueChange = { accountId = it },
        label = { Text("AWS Account ID") },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )

    RegionDropdownField(
        label = "Region",
        selectedRegion = region,
        regions = listOf("us-east-1", "us-west-2", "eu-west-1", "eu-central-1", "ap-northeast-1", "ap-southeast-1"),
        onRegionSelected = { region = it },
    )

    ProviderActionRow(
        canSaveAndTest = accessKey.isNotBlank() && secretKey.isNotBlank() && accountId.isNotBlank(),
        isTesting = testState is ProviderTestState.Testing,
        isConfigured = isConfigured,
        onSaveAndTest = { viewModel.testBedrockIamConnection(accessKey, secretKey, accountId, region) },
        onRequestRemove = { showRemoveConfirm = true },
    )

    ProviderTestFeedback(testState)

    if (showRemoveConfirm) {
        UgoConfirmDialog(
            title = "Rimuovi Bedrock IAM",
            message = "Vuoi rimuovere le credenziali AWS Bedrock IAM salvate?",
            confirmLabel = "Rimuovi",
            isDestructive = true,
            onConfirm = {
                showRemoveConfirm = false
                accessKey = ""
                secretKey = ""
                accountId = ""
                viewModel.clearBedrockIamCredentials()
            },
            onDismiss = { showRemoveConfirm = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VertexAiCredentialsSection(
    viewModel: SettingsViewModel,
    isConfigured: Boolean,
    testState: ProviderTestState,
) {
    var projectId by rememberSaveable { mutableStateOf(viewModel.getVertexAiProjectId()) }
    var region by rememberSaveable { mutableStateOf(viewModel.getVertexAiRegion()) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    val serviceAccountConfigured = viewModel.isVertexAiServiceAccountConfigured()

    val jsonPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importVertexAiServiceAccount(uri, region) { detected ->
                if (detected.isNotBlank()) {
                    projectId = detected
                }
            }
        }
    }

    Text(
        text = "Richiede un progetto Google Cloud con API Vertex AI abilitata e il file JSON Service Account.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 10.dp),
    )

    OutlinedTextField(
        value = projectId,
        onValueChange = { projectId = it },
        label = { Text("Project ID") },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )

    RegionDropdownField(
        label = "Region",
        selectedRegion = region,
        regions = listOf("us-central1", "europe-west1", "asia-northeast1", "us-west1", "us-east1", "europe-west4"),
        onRegionSelected = { region = it },
    )

    OutlinedButton(
        onClick = { jsonPickerLauncher.launch(arrayOf("application/json", "*/*")) },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            if (serviceAccountConfigured) {
                "Service Account JSON caricato (aggiorna)"
            } else {
                "Carica file JSON Service Account"
            },
        )
    }

    ProviderActionRow(
        canSaveAndTest = projectId.isNotBlank(),
        isTesting = testState is ProviderTestState.Testing,
        isConfigured = isConfigured || projectId.isNotBlank() || serviceAccountConfigured,
        onSaveAndTest = { viewModel.testAndSaveVertexAi(projectId, region) },
        onRequestRemove = { showRemoveConfirm = true },
    )

    ProviderTestFeedback(testState)

    if (showRemoveConfirm) {
        UgoConfirmDialog(
            title = "Rimuovi Google Vertex AI",
            message = "Vuoi rimuovere Project ID e Service Account Vertex AI salvati?",
            confirmLabel = "Rimuovi",
            isDestructive = true,
            onConfirm = {
                showRemoveConfirm = false
                projectId = ""
                viewModel.clearVertexAiCredentials()
            },
            onDismiss = { showRemoveConfirm = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegionDropdownField(
    label: String,
    selectedRegion: String,
    regions: List<String>,
    onRegionSelected: (String) -> Unit,
) {
    var regionExpanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = regionExpanded,
        onExpandedChange = { regionExpanded = it },
    ) {
        OutlinedTextField(
            value = selectedRegion,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            shape = RoundedCornerShape(12.dp),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = regionExpanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .padding(bottom = 8.dp),
        )
        ExposedDropdownMenu(
            expanded = regionExpanded,
            onDismissRequest = { regionExpanded = false },
        ) {
            regions.forEach { r ->
                DropdownMenuItem(
                    text = { Text(r) },
                    onClick = {
                        onRegionSelected(r)
                        regionExpanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun AllModelsPickerDialog(
    models: List<AiModel>,
    currentSelection: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    onOpenFullscreen: () -> Unit,
) {
    var selected by remember { mutableStateOf(currentSelection) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Modelli per \"Analizza\"",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenFullscreen) {
                    Icon(
                        imageVector = Icons.Filled.OpenInFull,
                        contentDescription = "Schermo intero",
                    )
                }
            }
        },
        text = {
            BrandFilteredModelGrid(
                models = models,
                selectedIds = selected,
                onToggleModel = { model ->
                    selected = toggleModelSelection(selected, model)
                },
                gridModifier = Modifier.height(300.dp),
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selected) },
                enabled = selected.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Conferma (${selected.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllModelsPickerScreen(
    onBack: () -> Unit,
    models: List<AiModel> = emptyList(),
    currentSelection: Set<String> = emptySet(),
    onConfirm: (Set<String>) -> Unit = {},
) {
    var selected by remember(currentSelection) { mutableStateOf(currentSelection) }

    EditorialDetailScaffold(
        title = "Seleziona modelli",
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            BrandFilteredModelGrid(
                models = models,
                selectedIds = selected,
                onToggleModel = { model ->
                    selected = toggleModelSelection(selected, model)
                },
                modifier = Modifier.weight(1f),
                gridModifier = Modifier.weight(1f),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Annulla")
                }
                Button(
                    onClick = {
                        onConfirm(selected)
                        onBack()
                    },
                    enabled = selected.isNotEmpty(),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Conferma (${selected.size})")
                }
            }
        }
    }
}
