package com.ugocalc.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.domain.model.AiProviderType
import com.ugocalc.app.ui.components.BackIconButton
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
