package com.letters2my.app.ui.letters

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letters2my.app.domain.LetterStatus

/**
 * Letter detail: metadata, schedule, status, favorite, edit, attachments,
 * and state-aware deletion.
 *
 * RECIPIENT PRIVACY: sealed body content is rendered ONLY when the viewer
 * may read sealed content (canViewSealed). The gate runs before rendering,
 * so a recipient/limited viewer never receives sealed content merely because
 * the screen renders the entity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LetterDetailScreen(
    letterId: String,
    onEdit: () -> Unit,
    onClose: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: LetterDetailViewModel = viewModel()
) {
    val letter by viewModel.letter.collectAsState()
    val attachments by viewModel.attachments.collectAsState()
    val childName by viewModel.childName.collectAsState()
    val canViewSealed by viewModel.canViewSealed.collectAsState()
    val deleteError by viewModel.deleteError.collectAsState()
    val showDeleteConfirm by viewModel.showDeleteConfirm.collectAsState()

    LaunchedEffect(letterId) { viewModel.load(letterId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(letter?.title?.ifEmpty { "Untitled" } ?: "Letter") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (letter != null) {
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                if (letter!!.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (letter!!.isFavorite) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = viewModel::requestDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        val entity = letter
        if (entity == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val status = viewModel.status()
        val summary = viewModel.unlockSummary()
        val isSealed = entity.sealedAt != null && status != LetterStatus.UNLOCKED

        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status + schedule
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (status) {
                                LetterStatus.DRAFT -> Icons.Default.Edit
                                LetterStatus.SCHEDULED -> Icons.Default.Lock
                                LetterStatus.UNLOCKED -> Icons.Default.LockOpen
                            },
                            contentDescription = null,
                            tint = when (status) {
                                LetterStatus.DRAFT -> MaterialTheme.colorScheme.onSurfaceVariant
                                LetterStatus.SCHEDULED -> MaterialTheme.colorScheme.primary
                                LetterStatus.UNLOCKED -> MaterialTheme.colorScheme.tertiary
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (status) {
                                LetterStatus.DRAFT -> "Draft"
                                LetterStatus.SCHEDULED -> "Scheduled"
                                LetterStatus.UNLOCKED -> "Unlocked"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    if (childName != null) {
                        Spacer(Modifier.height(4.dp))
                        Text("For $childName", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (entity.authorName.isNotEmpty()) {
                        Text("From ${entity.authorName}", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (status != LetterStatus.DRAFT) {
                        Spacer(Modifier.height(4.dp))
                        Text("Unlocks: $summary", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(formatDate(entity.updatedAt), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                }
            }

            // BODY — privacy gate: never render sealed content for viewers
            // without viewSealedContent.
            if (isSealed && !canViewSealed) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "This letter is sealed and will unlock on its schedule.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        entity.body.ifEmpty { "Empty letter" },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // Attachments
            if (attachments.isNotEmpty()) {
                Text("Attachments (${attachments.size})", style = MaterialTheme.typography.titleSmall)
                attachments.forEach { att ->
                    ListItem(
                        headlineContent = { Text(att.fileName) },
                        supportingContent = { Text("${att.kind} · ${att.data.size} bytes") },
                        leadingContent = {
                            Icon(
                                when (att.kind) {
                                    "photo" -> Icons.Default.Photo
                                    "video" -> Icons.Default.Videocam
                                    "audio" -> Icons.Default.Mic
                                    else -> Icons.Default.AttachFile
                                },
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        }
    }

    if (deleteError != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Delete failed") },
            text = { Text(deleteError!!) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("OK") } }
        )
    }

    if (showDeleteConfirm) {
        val entity = letter
        if (entity != null) {
            val isDraft = entity.sealedAt == null
            AlertDialog(
                onDismissRequest = viewModel::cancelDelete,
                title = { Text(if (isDraft) "Delete Draft?" else "Delete Sealed Letter?") },
                text = {
                    Text(
                        if (isDraft) "This draft will be permanently removed."
                        else "This permanently removes this letter and its attachments."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmDelete(onDeleted) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") }
                }
            )
        }
    }
}