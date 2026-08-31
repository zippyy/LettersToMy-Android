package com.letters2my.app.ui.letters

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letters2my.app.domain.UnlockRuleKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Full Letter editor: title/author/body, recipient child, family side,
 * folder, milestone Quick Start, unlock-rule selector (specific date /
 * birthday age / life event), favorite, draft save vs seal, attachments
 * (gallery picker + files; byte payloads kept in memory until save).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LetterEditorScreen(
    letterId: String?,
    onClose: () -> Unit,
    viewModel: LetterEditorViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(letterId) { viewModel.load(letterId) }

    val title by viewModel.title.collectAsState()
    val body by viewModel.body.collectAsState()
    val authorName by viewModel.authorName.collectAsState()
    val childId by viewModel.childId.collectAsState()
    val branchId by viewModel.branchId.collectAsState()
    val folderId by viewModel.folderId.collectAsState()
    val unlockKind by viewModel.unlockKind.collectAsState()
    val unlockDate by viewModel.unlockDate.collectAsState()
    val unlockAgeYears by viewModel.unlockAgeYears.collectAsState()
    val lifeEventName by viewModel.lifeEventName.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val selectedMilestone by viewModel.selectedMilestone.collectAsState()
    val pendingAttachments by viewModel.pendingAttachments.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveError by viewModel.saveError.collectAsState()
    val children by viewModel.children.collectAsState(initial = emptyList())
    val branches by viewModel.branches.collectAsState(initial = emptyList())
    val folders by viewModel.folders.collectAsState(initial = emptyList())

    // Unlock rule pickers
    var showDatePicker by remember { mutableStateOf(false) }
    var showChildPicker by remember { mutableStateOf(false) }
    var showBranchPicker by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }

    // Attachment launchers (scoped APIs, no broad storage permission)
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { loadUriAttachment(context, it, "photo") { viewModel.addPendingAttachment(it) } }
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { loadUriAttachment(context, it, "file") { viewModel.addPendingAttachment(it) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (letterId == null) "New Letter" else "Edit Letter") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { viewModel.isFavorite.value = !isFavorite }) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (!isSaving) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (letterId == null || viewModel.isDraft()) {
                            OutlinedButton(onClick = { viewModel.save(seal = false, onSaved = onClose) }) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Save Draft")
                            }
                        }
                        Button(onClick = { viewModel.save(seal = true, onSaved = onClose) }) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (letterId == null) "Seal Letter" else "Save Changes")
                        }
                    }
                }
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (saveError != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(saveError!!, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::clearError) { Text("Dismiss") }
                    }
                }
            }

            // Quick Start (new letters only)
            if (letterId == null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Quick Start", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        LetterEditorViewModel.milestoneTemplates.forEach { milestone ->
                            ListItem(
                                headlineContent = { Text(milestone.title) },
                                modifier = Modifier.clickable { viewModel.applyMilestone(milestone) }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { viewModel.title.value = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = authorName,
                onValueChange = { viewModel.authorName.value = it },
                label = { Text("Author") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = body,
                onValueChange = { viewModel.body.value = it },
                label = { Text("Letter") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp)
            )

            // Recipient / child (explicit pick — never auto-selects first)
            OutlinedButton(onClick = { showChildPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Person, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(childId?.let { id -> children.firstOrNull { it.id == id }?.name } ?: "Choose recipient child…")
            }

            // Family side
            OutlinedButton(onClick = { showBranchPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.People, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(branchId?.let { id -> branches.firstOrNull { it.id == id }?.name } ?: "Family side…")
            }
            // Folder (only when a branch is chosen)
            if (branchId != null) {
                OutlinedButton(onClick = { showFolderPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        folderId?.let { id -> folders.firstOrNull { it.id == id }?.name }
                            ?: "Choose folder…"
                    )
                }
            }

            // Unlock rule selector
            Text("Unlock", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UnlockRuleKind.entries.forEach { kind ->
                    FilterChip(
                        selected = unlockKind == kind,
                        onClick = { viewModel.unlockKind.value = kind },
                        label = { Text(kindTitle(kind)) }
                    )
                }
            }
            when (unlockKind) {
                UnlockRuleKind.SPECIFIC_DATE -> {
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(unlockDate?.let { formatDate(it) } ?: "Choose unlock date")
                    }
                }
                UnlockRuleKind.BIRTHDAY_AGE -> {
                    OutlinedTextField(
                        value = (unlockAgeYears ?: 5).toString(),
                        onValueChange = { viewModel.unlockAgeYears.value = it.toIntOrNull() },
                        label = { Text("Age (years)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                UnlockRuleKind.LIFE_EVENT -> {
                    OutlinedTextField(
                        value = lifeEventName,
                        onValueChange = { viewModel.lifeEventName.value = it },
                        label = { Text("Life event") },
                        placeholder = { Text("Graduation, Wedding, …") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Attachments
            Text("Attachments", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Photo")
                }
                OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("File")
                }
            }
            pendingAttachments.forEach { att ->
                ListItem(
                    headlineContent = { Text(att.fileName) },
                    supportingContent = { Text(att.kind) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.removePendingAttachment(att.id) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove")
                        }
                    }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // Date picker
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = unlockDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // Use the ACTUAL selected date (UTC millis at midnight),
                    // not the current time — sealing with today's date must
                    // mean the date the user picked.
                    datePickerState.selectedDateMillis?.let { selected ->
                        viewModel.unlockDate.value = selected
                    } ?: run { viewModel.unlockDate.value = System.currentTimeMillis() }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Child picker
    if (showChildPicker) {
        AlertDialog(
            onDismissRequest = { showChildPicker = false },
            title = { Text("Recipient") },
            text = {
                Column {
                    children.forEach { child ->
                        ListItem(
                            headlineContent = { Text(child.name) },
                            modifier = Modifier.clickable {
                                viewModel.childId.value = child.id
                                showChildPicker = false
                            }
                        )
                    }
                    if (children.isEmpty()) {
                        Text("Add a child in Family first.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChildPicker = false }) { Text("Cancel") } }
        )
    }

    // Branch picker
    if (showBranchPicker) {
        AlertDialog(
            onDismissRequest = { showBranchPicker = false },
            title = { Text("Family Side") },
            text = {
                Column {
                    branches.forEach { branch ->
                        ListItem(
                            headlineContent = { Text(branch.name) },
                            modifier = Modifier.clickable {
                                viewModel.branchId.value = branch.id
                                viewModel.folderId.value = null
                                showBranchPicker = false
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBranchPicker = false }) { Text("Cancel") } }
        )
    }

    // Folder picker
    if (showFolderPicker) {
        AlertDialog(
            onDismissRequest = { showFolderPicker = false },
            title = { Text("Folder") },
            text = {
                Column {
                    folders.filter { it.branchId == branchId }.forEach { folder ->
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            modifier = Modifier.clickable {
                                viewModel.folderId.value = folder.id
                                showFolderPicker = false
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFolderPicker = false }) { Text("Cancel") } }
        )
    }
}

private fun kindTitle(kind: UnlockRuleKind): String = when (kind) {
    UnlockRuleKind.SPECIFIC_DATE -> "Date"
    UnlockRuleKind.BIRTHDAY_AGE -> "Birthday"
    UnlockRuleKind.LIFE_EVENT -> "Life Event"
}

fun loadUriAttachment(
    context: Context,
    uri: Uri,
    kind: String,
    onLoaded: (PendingAttachment) -> Unit
) {
    // Scoped read; loaded in memory (consistent with iOS payload model).
    val resolver = context.contentResolver
    val name = resolver.getType(uri)?.let { type ->
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else "attachment-${UUID.randomUUID()}.bin"
        } ?: "attachment-${UUID.randomUUID()}.bin"
    } ?: "attachment-${UUID.randomUUID()}.bin"

    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
    if (bytes != null) {
        val contentType = resolver.getType(uri) ?: "application/octet-stream"
        onLoaded(PendingAttachment(fileName = name, contentType = contentType, kind = kind, data = bytes))
    }
}