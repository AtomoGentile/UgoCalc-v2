package com.ugocalc.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ugocalc.app.ui.components.BackIconButton
import com.ugocalc.app.ui.components.UgoCard
import com.ugocalc.app.ui.components.ugoTopAppBarColors
import com.ugocalc.app.ui.theme.UgoSemanticColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteRequestScreen(
    token: String,
    onApproved: () -> Unit,
    onBack: () -> Unit,
    viewModel: InviteRequestViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(token) {
        viewModel.handleInviteToken(token)
    }

    LaunchedEffect(state) {
        if (state is InviteRequestState.Approved) {
            onApproved()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Invito account") },
                navigationIcon = { BackIconButton(onBack) },
                colors = ugoTopAppBarColors(),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            UgoCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(24.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (val current = state) {
                        is InviteRequestState.Idle,
                        is InviteRequestState.Requesting -> {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = "Connessione in corso…",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                        }

                        is InviteRequestState.WaitingApproval -> {
                            StatusIconBadge(
                                icon = Icons.Rounded.HourglassTop,
                                tint = MaterialTheme.colorScheme.primary,
                                container = MaterialTheme.colorScheme.primaryContainer,
                            )
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = "In attesa di autorizzazione",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Il proprietario dell'account deve confermare la tua richiesta.\nQuesta schermata si aggiornerà automaticamente.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(22.dp))
                            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                        }

                        is InviteRequestState.Approved -> {
                            StatusIconBadge(
                                icon = Icons.Rounded.CheckCircle,
                                tint = UgoSemanticColors.success,
                                container = UgoSemanticColors.successContainer,
                            )
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = "Accesso approvato!",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                        }

                        is InviteRequestState.Denied -> {
                            StatusIconBadge(
                                icon = Icons.Rounded.ErrorOutline,
                                tint = MaterialTheme.colorScheme.error,
                                container = MaterialTheme.colorScheme.errorContainer,
                            )
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = "Richiesta negata",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Il proprietario ha rifiutato la richiesta di accesso.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(22.dp))
                            Button(
                                onClick = onBack,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Torna indietro")
                            }
                        }

                        is InviteRequestState.Error -> {
                            StatusIconBadge(
                                icon = Icons.Rounded.ErrorOutline,
                                tint = MaterialTheme.colorScheme.error,
                                container = MaterialTheme.colorScheme.errorContainer,
                            )
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = "Errore",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = current.message,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(22.dp))
                            Button(
                                onClick = {
                                    viewModel.reset()
                                    onBack()
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Torna indietro")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusIconBadge(
    icon: ImageVector,
    tint: Color,
    container: Color,
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(34.dp),
        )
    }
}
