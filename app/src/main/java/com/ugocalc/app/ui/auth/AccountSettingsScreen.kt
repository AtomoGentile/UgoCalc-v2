package com.ugocalc.app.ui.auth

import android.content.Intent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.BannerTone
import com.ugocalc.app.ui.components.StatusBanner
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.UgoConfirmDialog
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoSemanticColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    onOpenAuth: () -> Unit = {},
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showApprovalDialog by remember { mutableStateOf(false) }
    var showOldPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }

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

    // Share link generato via token
    LaunchedEffect(uiState.pendingLinkToShare) {
        val link = uiState.pendingLinkToShare ?: return@LaunchedEffect
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Unisciti al mio account UgoCalc! Apri questo link: $link")
        }
        context.startActivity(Intent.createChooser(intent, "Condividi invito"))
        viewModel.clearPendingLinkToShare()
    }

    // Quando il guest ha attivato il token, mostra il dialog di approvazione
    LaunchedEffect(uiState.guestArrived) {
        if (uiState.guestArrived) showApprovalDialog = true
    }

    // Snackbar per risultato approvazione
    LaunchedEffect(uiState.approvalResult) {
        val result = uiState.approvalResult ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(if (result) "Accesso approvato" else "Accesso negato")
        viewModel.dismissApprovalResult()
    }

    // Snackbar per errore generazione link
    LaunchedEffect(uiState.generateLinkResult) {
        val result = uiState.generateLinkResult ?: return@LaunchedEffect
        if (result is GenerateLinkResult.Error) {
            snackbarHostState.showSnackbar("Errore: ${result.message}")
        }
        viewModel.dismissGenerateLinkResult()
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

    // Dialog di approvazione: l'owner conferma con biometria prima di approvare
    if (showApprovalDialog) {
        AlertDialog(
            onDismissRequest = { /* non chiudibile cliccando fuori */ },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = "Richiesta di accesso",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "Qualcuno ha usato il tuo link di invito e sta aspettando la tua autorizzazione.\n\n" +
                        "Vuoi concedere l'accesso al tuo account?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showApprovalDialog = false
                        val activity = context.findFragmentActivity()
                        val canUseBiometric = activity != null && BiometricManager.from(context).canAuthenticate(
                            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        ) == BiometricManager.BIOMETRIC_SUCCESS
                        if (activity != null && canUseBiometric) {
                            val prompt = BiometricPrompt(
                                activity,
                                ContextCompat.getMainExecutor(context),
                                object : BiometricPrompt.AuthenticationCallback() {
                                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                        viewModel.approveRequest(true)
                                    }
                                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                        // annullato: nessuna azione
                                    }
                                }
                            )
                            val info = BiometricPrompt.PromptInfo.Builder()
                                .setTitle("Autorizza accesso")
                                .setSubtitle("Conferma per approvare la richiesta")
                                .setAllowedAuthenticators(
                                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                                )
                                .build()
                            prompt.authenticate(info)
                        } else {
                            viewModel.approveRequest(true)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Approva")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showApprovalDialog = false
                    viewModel.approveRequest(false)
                }) {
                    Text("Nega")
                }
            },
        )
    }

    if (showSignOutDialog) {
        UgoConfirmDialog(
            title = "Esci dall'account",
            message = "Verrai disconnesso dal Cloud Sync e l'app continuerà a funzionare normalmente in modalità locale sul dispositivo.",
            confirmLabel = "Esci",
            isDestructive = true,
            onConfirm = {
                showSignOutDialog = false
                viewModel.signOut()
                onSignedOut()
            },
            onDismiss = { showSignOutDialog = false },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; viewModel.setDeletePassword("") },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Elimina account", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Questa azione è irreversibile. Il tuo account cloud e tutti i dati sincronizzati verranno eliminati definitivamente. Inserisci la password per confermare.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = uiState.deletePassword,
                        onValueChange = viewModel::setDeletePassword,
                        label = { Text("Password") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteUser(onDeleted = onSignedOut)
                    },
                    enabled = uiState.deletePassword.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Elimina definitivamente") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.setDeletePassword("") }) { Text("Annulla") }
            },
        )
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Account e Cloud Sync") },
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val user = uiState.currentUser

            if (user == null) {
                StatusBanner(
                    icon = Icons.Rounded.CloudOff,
                    title = "Modalità locale attiva",
                    subtitle = "Tutti i tuoi dati (tabelle, ricette, preferiti, cronologia e BIAS) sono salvati in locale sul dispositivo.",
                    tone = BannerTone.INFO,
                )

                UgoCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(18.dp),
                ) {
                    Text(
                        text = "Cloud Sync opzionale",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Se vuoi sincronizzare i pasti tra più dispositivi o condividere il profilo con un familiare, puoi accedere o creare un account in qualsiasi momento.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onOpenAuth,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Login,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Accedi o registrati")
                    }
                }
            } else {
                // — Profilo connesso —
                UgoCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "Connesso",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = UgoSemanticColors.success,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                text = user.email,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "ID: ${user.userId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // — Modifica email —
                UgoCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Text(
                        text = "Modifica email",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = uiState.newEmail,
                        onValueChange = viewModel::setNewEmail,
                        label = { Text("Nuova email") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = uiState.emailPassword,
                        onValueChange = viewModel::setEmailPassword,
                        label = { Text("Password attuale") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = viewModel::updateEmail,
                        enabled = !uiState.isLoading && uiState.newEmail.isNotBlank() && uiState.emailPassword.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Aggiorna email")
                        }
                    }
                }

                // — Modifica password —
                UgoCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Text(
                        text = "Modifica password",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = uiState.oldPassword,
                        onValueChange = viewModel::setOldPassword,
                        label = { Text("Password attuale") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = if (showOldPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showOldPassword = !showOldPassword }) {
                                Icon(
                                    imageVector = if (showOldPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (showOldPassword) "Nascondi" else "Mostra",
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.newPassword,
                        onValueChange = viewModel::setNewPassword,
                        label = { Text("Nuova password") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showNewPassword = !showNewPassword }) {
                                Icon(
                                    imageVector = if (showNewPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (showNewPassword) "Nascondi" else "Mostra",
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.confirmNewPassword,
                        onValueChange = viewModel::setConfirmNewPassword,
                        label = { Text("Conferma nuova password") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                                Icon(
                                    imageVector = if (showConfirmPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (showConfirmPassword) "Nascondi" else "Mostra",
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = viewModel::updatePassword,
                        enabled = !uiState.isLoading &&
                            uiState.oldPassword.isNotBlank() &&
                            uiState.newPassword.isNotBlank() &&
                            uiState.confirmNewPassword.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Aggiorna password")
                        }
                    }
                }

                // — Condivisione account —
                UgoCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Text(
                        text = "Condivisione account",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Invita un'altra persona ad accedere al tuo account. Pasti, calibrazioni e bias saranno condivisi tra i dispositivi autorizzati.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = viewModel::generateInviteLink,
                        enabled = !uiState.isLoading,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Link, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Genera link di invito")
                        }
                    }
                    if (uiState.pendingApprovalToken != null && !uiState.guestArrived) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Link attivo — in attesa che il guest lo utilizzi…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = viewModel::cancelOwnerPolling) {
                                    Text("Annulla")
                                }
                            }
                        }
                    }
                }
            }

            // — Bias globale —
            UgoCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
            ) {
                Text(
                    text = "Bias globale",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Visualizza la media anonimizzata del bias per segmento calcolata su tutti gli account del sistema. I dati sono aggregati e anonimi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Mostra bias globale aggregato",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = uiState.globalBiasEnabled,
                        onCheckedChange = viewModel::setGlobalBiasEnabled,
                    )
                }
            }

            if (user != null) {
                // — Azioni account —
                UgoCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Text(
                        text = "Sessione account",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showSignOutDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Esci dall'account")
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { showDeleteDialog = true },
                        enabled = !uiState.isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Elimina account")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
