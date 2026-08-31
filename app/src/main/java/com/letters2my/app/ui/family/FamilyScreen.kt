package com.letters2my.app.ui.family

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letters2my.app.data.local.BranchEntity
import com.letters2my.app.ui.letters.formatDate
import java.util.Calendar

/**
 * Family: children (list/create/edit/delete with birth date), family sides
 * (seeded defaults protected), folders per side.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyScreen(viewModel: FamilyViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showAddChild by remember { mutableStateOf(false) }
    var showAddBranch by remember { mutableStateOf(false) }
    var editingChild by remember { mutableStateOf<com.letters2my.app.data.local.ChildEntity?>(null) }
    var newChildName by remember { mutableStateOf("") }
    var newChildBirth by remember { mutableStateOf<Long?>(null) }
    var newBranchName by remember { mutableStateOf("") }
    var newFolderFor by remember { mutableStateOf<String?>(null) }
    var newFolderName by remember { mutableStateOf("") }

    val error by viewModel.errorMessage.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Family") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddChild = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Child")
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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("Children", style = MaterialTheme.typography.titleMedium)
                }
                if (state.children.isEmpty()) {
                    item {
                        Text("No children yet. Tap + to add your first child.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(state.children, key = { it.id }) { child ->
                    ListItem(
                        headlineContent = { Text(child.name) },
                        supportingContent = {
                            Text(child.birthDate?.let { formatDate(it) } ?: "No birth date")
                        },
                        leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                        trailingContent = {
                            IconButton(onClick = { editingChild = child }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                        },
                        modifier = Modifier.clickable { editingChild = child }
                    )
                }

                item { Spacer(Modifier.height(8.dp)) }
                item { Text("Family Sides", style = MaterialTheme.typography.titleMedium) }
                items(state.branches, key = { it.id }) { branch ->
                    ListItem(
                        headlineContent = { Text(branch.name) },
                        supportingContent = {
                            Text(if (branch.isSeeded) "Seeded" else branch.kind)
                        },
                        leadingContent = { Icon(Icons.Default.People, contentDescription = null) },
                        trailingContent = {
                            if (!branch.isSeeded) {
                                IconButton(onClick = { viewModel.deleteBranch(branch) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    )
                }
                item {
                    OutlinedButton(onClick = { showAddBranch = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Family Side")
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }
                item { Text("Folders", style = MaterialTheme.typography.titleMedium) }
                items(state.folders, key = { it.id }) { folder ->
                    val branchName = state.branches.firstOrNull { it.id == folder.branchId }?.name
                    ListItem(
                        headlineContent = { Text(folder.name) },
                        supportingContent = { Text(branchName ?: "Unknown side") },
                        leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.deleteFolder(folder) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                }
                item {
                    if (state.branches.isNotEmpty()) {
                        OutlinedButton(onClick = { newFolderFor = state.branches.first().id }) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Folder")
                        }
                    }
                }
            }
        }
    }

    // Add child dialog
    if (showAddChild) {
        AddChildDialog(
            title = "Add Child",
            name = newChildName,
            birth = newChildBirth,
            onNameChange = { newChildName = it },
            onBirthChange = { newChildBirth = it },
            onConfirm = {
                viewModel.addChild(newChildName, newChildBirth)
                newChildName = ""; newChildBirth = null
                showAddChild = false
            },
            onDismiss = { showAddChild = false }
        )
    }

    // Edit child dialog
    editingChild?.let { child ->
        var editName by remember(child.id) { mutableStateOf(child.name) }
        var editBirth by remember(child.id) { mutableStateOf(child.birthDate) }
        AddChildDialog(
            title = "Edit Child",
            name = editName,
            birth = editBirth,
            onNameChange = { editName = it },
            onBirthChange = { editBirth = it },
            onConfirm = {
                viewModel.updateChild(child, editName, editBirth)
                editingChild = null
            },
            onDismiss = { editingChild = null }
        )
    }

    // Add branch dialog
    if (showAddBranch) {
        AlertDialog(
            onDismissRequest = { showAddBranch = false },
            title = { Text("Add Family Side") },
            text = {
                OutlinedTextField(
                    value = newBranchName,
                    onValueChange = { newBranchName = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addBranch(newBranchName, "custom")
                    newBranchName = ""
                    showAddBranch = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddBranch = false }) { Text("Cancel") }
            }
        )
    }

    // Add folder dialog
    newFolderFor?.let { branchId ->
        AlertDialog(
            onDismissRequest = { newFolderFor = null },
            title = { Text("Add Folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.branches.forEach { branch ->
                        FilterChip(
                            selected = branchId == branch.id,
                            onClick = { newFolderFor = branch.id },
                            label = { Text(branch.name) }
                        )
                    }
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("Folder name") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addFolder(branchId, newFolderName)
                    newFolderName = ""
                    newFolderFor = null
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { newFolderFor = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AddChildDialog(
    title: String,
    name: String,
    birth: Long?,
    onNameChange: (String) -> Unit,
    onBirthChange: (Long?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    singleLine = true
                )
                OutlinedButton(onClick = {
                    val cal = Calendar.getInstance()
                    birth?.let { cal.timeInMillis = it }
                    DatePickerDialog(
                        context,
                        { _, y, m, d ->
                            val picked = Calendar.getInstance().apply {
                                clear()
                                set(y, m, d)
                            }
                            onBirthChange(picked.timeInMillis)
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }) {
                    Text(birth?.let { formatDate(it) } ?: "Set birth date")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}