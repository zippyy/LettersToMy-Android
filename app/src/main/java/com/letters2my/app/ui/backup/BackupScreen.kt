package com.letters2my.app.ui.backup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letters2my.app.ui.letters.formatDate
import kotlinx.coroutines.launch

/**
 * Backup & Restore: create encrypted `.letterstomy` backup, upload to the
 * self-hosted server, list remote backups, download/preview/restore, delete
 * remote backups. letter_count comes from the archive manifest.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onClose: () -> Unit,
    viewModel: BackupViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()
    val isBusy by viewModel.isBusy.collectAsState()
    val status by viewModel.statusMessage.collectAsState()
    val error by viewModel.errorMessage.collectAsState()
    val remoteBackups by viewModel.remoteBackups.collectAsState()
    val localHistory by viewModel.localHistory.collectAsState()
    val showPassphrase by viewModel.passphrasePrompt.collectAsState()
    val preview by viewModel.restorePreview.collectAsState()

    var passphrase by remember { mutableStateOf("") }
    var restoringPassphrase by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.refresh()
        viewModel.passphrasePrompt.value = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (error != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(error!!, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::clearError) { Text("Dismiss") }
                    }
                }
            }
            if (status != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(status!!, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::clearStatus) { Text("Dismiss") }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Portable encrypted archive (.letterstomy)",
                                style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Encrypted with AES-256-GCM. Compatible with iOS: an archive " +
                                    "created here can be restored on an iPhone and vice versa.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = passphrase,
                                onValueChange = { passphrase = it },
                                label = { Text("Backup passphrase") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                enabled = !isBusy
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.createAndPush(passphrase) },
                                enabled = passphrase.isNotBlank() && !isBusy
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Working…")
                                } else {
                                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Create & Upload Backup")
                                }
                            }
                        }
                    }
                }

                item {
                    Text("Remote Backups (${remoteBackups.size})", style = MaterialTheme.typography.titleMedium)
                }
                if (remoteBackups.isEmpty()) {
                    item {
                        Text("No backups on the server yet.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(remoteBackups, key = { it.id }) { backup ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Description, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("${backup.letterCount} letters", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${formatDate(backup.timestamp)} · ${backup.size} bytes",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    restoringPassphrase = passphrase
                                    viewModel.previewRemote(backup.id, restoringPassphrase)
                                }, enabled = restoringPassphrase.isNotBlank() && !isBusy) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Restore")
                                }
                                TextButton(onClick = { viewModel.deleteRemote(backup.id) }) {
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(4.dp)) }
                item { Text("Local History (${localHistory.size})", style = MaterialTheme.typography.titleMedium) }
                items(localHistory, key = { it.id }) { record ->
                    ListItem(
                        headlineContent = { Text("${record.letterCount} letters · ${record.status}") },
                        supportingContent = {
                            Text("${formatDate(record.createdAt)} · ${record.sizeBytes} bytes")
                        }
                    )
                }
            }
        }
    }

    // Restore preview
    preview?.let { p ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRestorePreview,
            title = { Text("Restore Archive") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Created ${formatDate(p.createdAt)}", style = MaterialTheme.typography.bodySmall)
                    Text("Letters: ${p.letterCount}")
                    Text("Attachments: ${p.attachmentCount}")
                    Text("Children: ${p.childCount}")
                    Text("Family sides: ${p.branchCount}")
                    Text("Folders: ${p.folderCount}")
                    Text("Members: ${p.memberCount}")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Restored content is added alongside existing data. " +
                            "Duplicate prevention skips records that already exist. " +
                            "Restoring an OLD backup may bring back letters deleted since — " +
                            "that is the point of a historical backup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::applyRestore) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRestorePreview) { Text("Cancel") }
            }
        )
    }
}