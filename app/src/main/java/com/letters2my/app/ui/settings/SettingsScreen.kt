package com.letters2my.app.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.letters2my.app.LettersApplication
import com.letters2my.app.data.local.SecureCredentials
import com.letters2my.app.data.sync.SelfHostedApiClient
import kotlinx.coroutines.launch

/**
 * Settings: app info, self-hosted integration (URL + token + Test Connection
 * with typed status states), secure credential storage, backup/restore
 * entry points. Mirrors the iOS BackupSettings/Settings surface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as LettersApplication
    val scope = rememberCoroutineScope()

    val secure = remember { app.secureCredentials }
    val settings = remember { app.settings }

    // Self-hosted config (non-secret URL in settings; token in Keystore).
    var selfhostedURL by remember { mutableStateOf(settings.selfHostedUrl) }
    var selfhostedToken by remember {
        mutableStateOf(secure.get(SecureCredentials.KEY_SELFHOSTED_TOKEN) ?: "")
    }

    // Connection test state — typed status, never silent Boolean/null.
    var connectionState by remember { mutableStateOf<ConnectionState>(ConnectionState.Idle) }

    val scrollState = rememberScrollState()

    fun saveSelfHosted() {
        settings.selfHostedUrl = selfhostedURL.trim()
        if (selfhostedToken.isNotBlank()) {
            secure.put(SecureCredentials.KEY_SELFHOSTED_TOKEN, selfhostedToken.trim())
        } else {
            secure.remove(SecureCredentials.KEY_SELFHOSTED_TOKEN)
        }
        // Reconfigure providers so the new config is live.
        app.reconfigureProviders()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Self-Hosted ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Self-Hosted Server", style = MaterialTheme.typography.titleMedium)
                            Text("LettersToMy-SelfHostedSync API v1 (Docker)", style = MaterialTheme.typography.bodySmall)
                        }
                        if (connectionState is ConnectionState.Connected) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = selfhostedURL,
                        onValueChange = { selfhostedURL = it },
                        label = { Text("Server URL") },
                        placeholder = { Text("https://sync.example.com:8080") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = selfhostedToken,
                        onValueChange = { selfhostedToken = it },
                        label = { Text("API Token") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                saveSelfHosted()
                                scope.launch {
                                    connectionState = ConnectionState.Testing
                                    connectionState = testConnection(
                                        selfhostedURL.trim(),
                                        selfhostedToken.trim()
                                    )
                                }
                            }
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Test Connection")
                        }
                        Spacer(Modifier.width(8.dp))
                        when (connectionState) {
                            is ConnectionState.Testing -> {
                                CircularProgressIndicator(Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Testing…", style = MaterialTheme.typography.bodySmall)
                            }
                            is ConnectionState.Connected -> {
                                Text(
                                    "Connected — ${(connectionState as ConnectionState.Connected).version}",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            is ConnectionState.Failed -> {
                                Text(
                                    (connectionState as ConnectionState.Failed).message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            is ConnectionState.ApiIncompatible -> {
                                Text(
                                    (connectionState as ConnectionState.ApiIncompatible).message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            is ConnectionState.CapabilityMissing -> {
                                Text(
                                    (connectionState as ConnectionState.CapabilityMissing).message,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            ConnectionState.Idle -> {}
                        }
                    }
                    if (selfhostedURL.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = {
                            selfhostedURL = ""
                            selfhostedToken = ""
                            secure.remove(SecureCredentials.KEY_SELFHOSTED_TOKEN)
                            settings.selfHostedUrl = ""
                            app.reconfigureProviders()
                        }) {
                            Text("Clear Configuration", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // ── Backup / Restore ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Backup & Restore", style = MaterialTheme.typography.titleMedium)
                            Text("Portable encrypted .letterstomy archives", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Backups are encrypted with your passphrase and can be restored on iOS or Android.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        OutlinedButton(onClick = {
                            // Triggered from a dedicated flow (see BackupScreen).
                        }) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Create Backup")
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {}) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Restore")
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Full backup/restore flow is on the Backup screen (Letters → overflow).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── About ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Letters to My", style = MaterialTheme.typography.titleMedium)
                    Text("Version 0.1.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Local data: Room  |  Portable recovery: .letterstomy  |  Self-hosted: API v1",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Testing : ConnectionState
    data class Connected(val version: String) : ConnectionState
    data class Failed(val message: String) : ConnectionState
    data class ApiIncompatible(val message: String) : ConnectionState
    data class CapabilityMissing(val message: String) : ConnectionState
}

/**
 * Real Test Connection against the live server: verifies service identity,
 * api_version == 1, and backup capability. Never reports "Connected" merely
 * because TCP/HTTP returned something.
 */
suspend fun testConnection(rawUrl: String, token: String): ConnectionState {
    if (rawUrl.isBlank() || token.isBlank()) {
        return ConnectionState.Failed("Server URL and API token are required.")
    }
    return try {
        val client = SelfHostedApiClient(SelfHostedApiClient.normalizeBaseUrl(rawUrl)) { token }
        val status = client.status()
        when {
            status.service.isEmpty() -> ConnectionState.Failed("Server did not identify itself.")
            status.apiVersion != 1 -> ConnectionState.ApiIncompatible(
                "Server API v${status.apiVersion} — this app requires v1."
            )
            !status.hasBackupCapability -> ConnectionState.CapabilityMissing(
                "Server does not advertise the backups capability."
            )
            else -> ConnectionState.Connected("v${status.apiVersion} · ${status.serverVersion}")
        }
    } catch (e: SelfHostedApiClient.ApiException) {
        when (e.code) {
            "unauthorized", "invalid_token" -> ConnectionState.Failed("Authentication failed (${e.code}).")
            else -> ConnectionState.Failed("Server error: ${e.message}")
        }
    } catch (e: Exception) {
        ConnectionState.Failed("Server unreachable: ${e.message?.take(80)}")
    }
}