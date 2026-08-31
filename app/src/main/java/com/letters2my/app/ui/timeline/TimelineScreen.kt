package com.letters2my.app.ui.timeline

import androidx.compose.foundation.clickable
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
import com.letters2my.app.domain.LetterStatus
import com.letters2my.app.ui.letters.formatDate

/**
 * Timeline: sealed/non-draft letters in unlock order. Explicit child filter
 * (All = null); no implicit first-child selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    onOpenLetter: (String) -> Unit = {},
    viewModel: TimelineViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showChildPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Timeline") }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { showChildPicker = true }) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        state.children.firstOrNull { it.first == viewModel.selectedChildId.value }?.second
                            ?: "All Children"
                    )
                }
            }
            if (state.entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null,
                            modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        Text("Nothing scheduled yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Sealed letters appear here in unlock order.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.entries, key = { it.letterId }) { entry ->
                        Card(modifier = Modifier.fillMaxWidth().clickable { onOpenLetter(entry.letterId) }) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (entry.status == LetterStatus.UNLOCKED) Icons.Default.LockOpen
                                    else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (entry.status == LetterStatus.UNLOCKED)
                                        MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "For ${entry.childName}" +
                                            (if (entry.authorName.isNotEmpty()) " · From ${entry.authorName}" else ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        formatDate(entry.unlockAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        if (entry.status == LetterStatus.UNLOCKED) "Unlocked" else "Scheduled",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (entry.status == LetterStatus.UNLOCKED)
                                            MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showChildPicker) {
        AlertDialog(
            onDismissRequest = { showChildPicker = false },
            title = { Text("Filter by Child") },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("All Children") },
                        trailingContent = if (state.selectedChildId == null) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null,
                        modifier = Modifier.clickable {
                            viewModel.setChild(null)
                            showChildPicker = false
                        }
                    )
                    state.children.forEach { (id, name) ->
                        ListItem(
                            headlineContent = { Text(name) },
                            trailingContent = if (state.selectedChildId == id) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null,
                            modifier = Modifier.clickable {
                                viewModel.setChild(id)
                                showChildPicker = false
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChildPicker = false }) { Text("Done") } }
        )
    }
}