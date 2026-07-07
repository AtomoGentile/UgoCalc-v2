package com.ugocalc.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.domain.model.AiProviderType
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.components.BackIconButton

/** Impostazioni > API (§2.2): inserimento chiave API per ciascun provider cloud supportato. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    Scaffold(topBar = { TopAppBar(title = { Text("API") }, navigationIcon = { BackIconButton(onBack) }, colors = ugoTopAppBarColors()) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            ApiKeyField(
                label = "OpenAI – Modelli (GitHub)",
                provider = AiProviderType.OPENAI,
                viewModel = viewModel,
                hint = "Token GitHub per i modelli OpenAI tramite GitHub Models (permesso \"models: read\").",
            )
            ApiKeyField(
                label = "OpenAI – API",
                provider = AiProviderType.OPENAI_API,
                viewModel = viewModel,
                hint = "Chiave API OpenAI per l'uso diretto del servizio (es. gpt‑4o).",
            )
            ApiKeyField("Anthropic Claude", AiProviderType.ANTHROPIC, viewModel)
            ApiKeyField("Google Gemini", AiProviderType.GEMINI, viewModel)
        }
    }
}

@Composable
private fun ApiKeyField(label: String, provider: AiProviderType, viewModel: SettingsViewModel, hint: String? = null) {
    var value by remember { mutableStateOf(viewModel.getApiKey(provider).orEmpty()) }
    OutlinedTextField(
        value = value,
        onValueChange = {
            value = it
            viewModel.setApiKey(provider, it)
        },
        label = { Text("API key $label") },
        supportingText = hint?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    )
}
