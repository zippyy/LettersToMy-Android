package com.letters2my.app.ui.letters

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letters2my.app.domain.FilterId
import com.letters2my.app.domain.LetterFiltering
import com.letters2my.app.domain.LetterStatus
import com.letters2my.app.domain.UnlockRule
import com.letters2my.app.domain.LetterStatusCalculator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Letters home: filter chips (All/Draft/Scheduled/Unlocked), All Children
 * selector (null = all — never auto-selects the first child), search, and
 * the letter list. Row shows title, status, schedule summary, author,
 * favorite; never leaks sealed body previews.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LettersScreen(
    onOpenEditor: (String?) -> Unit,
    onOpenDetail: (String) -> Unit,
    viewModel: LettersViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showChildPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Letters") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenEditor(null) }) {
                Icon(Icons.Default.Add, contentDescription = "New Letter")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Filter chips
            FilterChipRow(
                selected = state.filterId,
                onSelect = viewModel::setFilter
            )
            // All Children selector + search
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { showChildPicker = true }) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(state.selectedChildId?.let { id -> state.children.firstOrNull { it.id == id }?.name ?: "Child" } ?: "All Children")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            if (state.letters.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        Text("No letters yet", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(8.dp))
                        Text("Tap + to write your first letter.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.letters, key = { it.id }) { letter ->
                        LetterRow(
                            letter = letter,
                            childName = state.children.firstOrNull { c -> c.id == letter.childId }?.name,
                            now = System.currentTimeMillis(),
                            onClick = { onOpenDetail(letter.id) },
                            onDelete = { /* handled in detail; list swipe added below */ }
                        )
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
                    // All Children is an explicit choice, never implicit.
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
                    state.children.forEach { child ->
                        ListItem(
                            headlineContent = { Text(child.name) },
                            supportingContent = child.birthDateEpochMs?.let {
                                { Text(formatDate(it)) }
                            },
                            trailingContent = if (state.selectedChildId == child.id) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null,
                            modifier = Modifier.clickable {
                                viewModel.setChild(child.id)
                                showChildPicker = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChildPicker = false }) { Text("Done") }
            }
        )
    }

    // Delete confirmation (state-aware copy)
    val pending = viewModel.pendingDelete.collectAsState().value
    if (pending != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text(pending.second.substringBefore('?') + "?") },
            text = { Text(pending.second.removePrefix(pending.second.substringBefore('?') + "?")) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun FilterChipRow(selected: FilterId, onSelect: (FilterId) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == FilterId.ALL,
            onClick = { onSelect(FilterId.ALL) },
            label = { Text("All") }
        )
        FilterChip(
            selected = selected == FilterId.DRAFT,
            onClick = { onSelect(FilterId.DRAFT) },
            label = { Text("Draft") }
        )
        FilterChip(
            selected = selected == FilterId.SCHEDULED,
            onClick = { onSelect(FilterId.SCHEDULED) },
            label = { Text("Scheduled") }
        )
        FilterChip(
            selected = selected == FilterId.UNLOCKED,
            onClick = { onSelect(FilterId.UNLOCKED) },
            label = { Text("Unlocked") }
        )
    }
}

@Composable
private fun LetterRow(
    letter: LetterFiltering.LetterLike,
    childName: String?,
    now: Long,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val status = LetterStatusCalculator.status(
        sealedAtEpochMs = letter.sealedAtEpochMs,
        unlockRuleRaw = letter.unlockRuleRaw,
        unlockDateEpochMs = letter.unlockDateEpochMs,
        unlockAgeYears = letter.unlockAgeYears,
        lifeEventName = letter.lifeEventName,
        manuallyReleasedAtEpochMs = letter.manuallyReleasedAtEpochMs,
        childBirthDateEpochMs = letter.childBirthDateEpochMs,
        nowEpochMs = now
    )
    val summary = UnlockRule.summary(
            unlockRuleRaw = letter.unlockRuleRaw,
            unlockDateEpochMs = letter.unlockDateEpochMs,
            unlockAgeYears = letter.unlockAgeYears,
            lifeEventName = letter.lifeEventName,
            manuallyReleasedAtEpochMs = letter.manuallyReleasedAtEpochMs,
            childBirthDateEpochMs = letter.childBirthDateEpochMs
        )

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (status) {
                        LetterStatus.DRAFT -> Icons.Default.Edit
                        LetterStatus.SCHEDULED -> Icons.Default.Lock
                        LetterStatus.UNLOCKED -> Icons.Default.LockOpen
                    },
                    contentDescription = status.title,
                    tint = when (status) {
                        LetterStatus.DRAFT -> MaterialTheme.colorScheme.onSurfaceVariant
                        LetterStatus.SCHEDULED -> MaterialTheme.colorScheme.primary
                        LetterStatus.UNLOCKED -> MaterialTheme.colorScheme.tertiary
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    letter.title.ifEmpty { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (letter.sealedAtEpochMs != null) {
                    // RECIPIENT PRIVACY: sealed letters show NO body preview.
                    Text(
                        "Sealed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (letter.isFavorite) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.Favorite, contentDescription = "Favorite",
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                when (status) {
                    LetterStatus.DRAFT -> "Draft"
                    LetterStatus.SCHEDULED -> "Scheduled · $summary"
                    LetterStatus.UNLOCKED -> "Unlocked"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (childName != null) {
                Spacer(Modifier.height(2.dp))
                Text("For $childName", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
            }
            if (letter.authorName.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text("From ${letter.authorName}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

internal fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}