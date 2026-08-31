package com.letters2my.app.ui.people

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letters2my.app.domain.CollaborationRole

/**
 * People & Access: member list with role display, invitations, role
 * changes, member removal — backed by the SelfHostedSync collaboration
 * directory when configured. Typed status; failures are never hidden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(viewModel: PeopleViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val error by viewModel.errorMessage.collectAsState()
    val status by viewModel.statusMessage.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    var showInviteDialog by remember { mutableStateOf(false) }
    var inviteName by remember { mutableStateOf("") }
    var inviteRole by remember { mutableStateOf(CollaborationRole.CONTRIBUTOR.raw) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("People") }) },
        floatingActionButton = {
            if (state.serverConfigured) {
                FloatingActionButton(onClick = { showInviteDialog = true }) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Invite")
                }
            }
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!state.serverConfigured) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Self-hosted not configured",
                                    style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Configure a server URL and API token in Settings to use the collaboration directory.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isSyncing) {
                                    Spacer(Modifier.height(8.dp))
                                    CircularProgressIndicator(Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Text("Members (${state.members.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    if (state.members.isEmpty()) {
                        item {
                            Text("No members found on the server.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    items(state.members, key = { it.id }) { member ->
                        var roleMenu by remember { mutableStateOf(false) }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Person, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(member.displayName, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            CollaborationRole.from(member.role).title,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Box {
                                        IconButton(onClick = { roleMenu = true }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "Member actions")
                                        }
                                        DropdownMenu(expanded = roleMenu, onDismissRequest = { roleMenu = false }) {
                                            CollaborationRole.entries.forEach { role ->
                                                DropdownMenuItem(
                                                    text = { Text(role.title) },
                                                    onClick = {
                                                        roleMenu = false
                                                        viewModel.updateRole(member, role.raw)
                                                    }
                                                )
                                            }
                                            HorizontalDivider()
                                            DropdownMenuItem(
                                                text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    roleMenu = false
                                                    viewModel.removeMember(member)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                    item { Text("Invitations (${state.invitations.size})", style = MaterialTheme.typography.titleMedium) }
                    items(state.invitations, key = { it.id }) { invitation ->
                        ListItem(
                            headlineContent = { Text(invitation.inviteeDisplayName) },
                            supportingContent = {
                                Text("${CollaborationRole.from(invitation.role).title} · ${invitation.status}")
                            },
                            trailingContent = {
                                IconButton(onClick = { viewModel.revokeInvitation(invitation) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Revoke",
                                        tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showInviteDialog) {
        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            title = { Text("Invite Collaborator") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = inviteName,
                        onValueChange = { inviteName = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                    CollaborationRole.entries
                        .filter { it != CollaborationRole.OWNER && it != CollaborationRole.RECIPIENT }
                        .forEach { role ->
                            FilterChip(
                                selected = inviteRole == role.raw,
                                onClick = { inviteRole = role.raw },
                                label = { Text(role.title) }
                            )
                        }
                    Text(
                        "An invitation code is created on your server and shared with the invitee.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createInvitation(inviteName, inviteRole)
                    inviteName = ""
                    showInviteDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showInviteDialog = false }) { Text("Cancel") }
            }
        )
    }
}