package com.ugocalc.app.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.ugoTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.inviteResult) {
        when (val result = uiState.inviteResult) {
            is InviteResult.Success ->
                snackbarHostState.showSnackbar("Invito inviato a ${result.invitedEmail}")
            is InviteResult.Error ->
                snackbarHostState.showSnackbar("Errore: ${result.message}")
            null -> {}
        }
        if (uiState.inviteResult != null) viewModel.dismissInviteResult()
    }

    LaunchedEffect(uiState.accountActionResult) {
        when (val result = uiState.accountActionResult) {
            is AccountActionResult.EmailUpdated ->
                snackbarHostState.showSnackbar("Email aggiornata. Controlla la casella per la verifica.")
            is AccountActionResult.PasswordUpdated ->
                snackbarHostState.showSnackbar("Password aggiornata.")
            is AccountActionResult.Error ->
                snackbarHostState.showSnackbar("Errore: ${result.message}")
            else -> {}
        }
        if (uiState.accountActionResult != null) viewModel.dismissAccountActionResult()
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Elimina account") },
            text = {
                Text(
                    "Questa azione è irreversibile. Il tuo account e tutti i dati associati " +
                        "verranno eliminati definitivamente. Sei sicuro?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteUser(onDeleted = onSignedOut)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Elimina definitivamente")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Annulla")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors(),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))

            uiState.currentUser?.let { user ->
                Text("Connesso come", style = MaterialTheme.typography.labelMedium)
                Text(user.email, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "ID: ${user.userId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // — Modifica email —
            Text("Modifica email", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.newEmail,
                onValueChange = viewModel::setNewEmail,
                label = { Text("Nuova email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::updateEmail,
                enabled = !uiState.isLoading && uiState.newEmail.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Aggiorna email")
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // — Modifica password —
            Text("Modifica password", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.oldPassword,
                onValueChange = viewModel::setOldPassword,
                label = { Text("Password attuale") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.newPassword,
                onValueChange = viewModel::setNewPassword,
                label = { Text("Nuova password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.confirmNewPassword,
                onValueChange = viewModel::setConfirmNewPassword,
                label = { Text("Conferma nuova password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::updatePassword,
                enabled = !uiState.isLoading &&
                    uiState.oldPassword.isNotBlank() &&
                    uiState.newPassword.isNotBlank() &&
                    uiState.confirmNewPassword.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Aggiorna password")
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // — Condivisione account —
            Text("Condivisione account", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Invita un'altra persona ad accedere al tuo account. " +
                    "Tutti i pasti, calibrazioni e bias sono condivisi tra chi accede allo stesso account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = uiState.inviteEmail,
                onValueChange = viewModel::setInviteEmail,
                label = { Text("Email da invitare") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::sendInvite,
                enabled = !uiState.isLoading && uiState.inviteEmail.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Invia invito")
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // — Bias globale —
            Text("Bias globale", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Visualizza la media anonimizzata del bias per segmento calcolata su tutti gli account " +
                    "del sistema. I dati sono aggregati: nessun account può risalire ai dati grezzi di un altro.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Mostra bias globale aggregato",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = uiState.globalBiasEnabled,
                    onCheckedChange = viewModel::setGlobalBiasEnabled,
                )
            }

            Spacer(Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // — Azioni account —
            OutlinedButton(
                onClick = {
                    viewModel.signOut()
                    onSignedOut()
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Esci dall'account")
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { showDeleteDialog = true },
                enabled = !uiState.isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Elimina account")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
