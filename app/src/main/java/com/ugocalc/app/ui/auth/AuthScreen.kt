package com.ugocalc.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.ui.components.ugoTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val authState by viewModel.authState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var confirmationCode by remember { mutableStateOf("") }
    var pendingEmail by remember { mutableStateOf("") }

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Success -> onAuthenticated()
            is AuthState.NeedsConfirmation -> pendingEmail = state.email
            is AuthState.ConfirmationComplete -> {
                snackbarHostState.showSnackbar("Account confermato! Accedi ora.")
                selectedTab = 0
                confirmationCode = ""
            }
            is AuthState.Error -> snackbarHostState.showSnackbar(state.message)
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UgoCalc") },
                colors = ugoTopAppBarColors(),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val isLoading = authState is AuthState.Loading

            if (pendingEmail.isNotBlank()) {
                // — Confirmation screen —
                Text(
                    text = "Verifica email",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Abbiamo inviato un codice a $pendingEmail. Inseriscilo qui sotto.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = confirmationCode,
                    onValueChange = { confirmationCode = it.trim() },
                    label = { Text("Codice di verifica") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.confirmSignUp(pendingEmail, confirmationCode) },
                    enabled = !isLoading && confirmationCode.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Conferma account")
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { pendingEmail = "" }) {
                    Text("Torna al login")
                }
            } else {
                // — Login / signup screen —
                Text(
                    text = "Accedi al tuo account",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(24.dp))

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Accedi") },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Registrati") },
                    )
                }

                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (selectedTab == 1) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Conferma password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (selectedTab == 0) {
                            viewModel.signIn(email, password)
                        } else {
                            if (password == confirmPassword) {
                                viewModel.signUp(email, password)
                            } else {
                                viewModel.setError("Le password non coincidono")
                            }
                        }
                    },
                    enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (selectedTab == 0) "Accedi" else "Crea account")
                    }
                }

                if (selectedTab == 0) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { selectedTab = 1 }) {
                        Text("Non hai un account? Registrati")
                    }
                }
            }
        }
    }
}
