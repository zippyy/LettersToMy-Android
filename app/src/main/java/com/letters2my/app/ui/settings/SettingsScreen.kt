package com.letters2my.app.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.letters2my.app.LettersApplication
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as LettersApplication
    val scope = rememberCoroutineScope()

    var isSignedIn by remember { mutableStateOf(GoogleSignIn.getLastSignedInAccount(context) != null) }
    var isSyncing by remember { mutableStateOf(false) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).result
        isSignedIn = account != null
        if (account != null) {
            scope.launch { app.syncFromDriveIfSignedIn() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Google Drive sync
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Google Drive Sync",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Your letters and attachments are stored privately in your Google Drive app data folder — like iCloud for Apple users. Only this app can access them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            if (isSignedIn) Icons.Default.CloudDone else Icons.Default.Cloud,
                            contentDescription = null,
                            tint = if (isSignedIn)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (isSignedIn) "Signed in to Google Drive" else "Not signed in",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    if (isSignedIn) {
                        if (isSyncing) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Syncing...")
                            }
                        } else {
                            Button(onClick = {
                                scope.launch {
                                    isSyncing = true
                                    app.syncToDrive()
                                    isSyncing = false
                                }
                            }) {
                                Icon(Icons.Default.CloudSync, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Sync Now")
                            }
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = {
                                isSignedIn = false
                            }) {
                                Text("Sign Out")
                            }
                        }
                    } else {
                        Button(onClick = {
                            signInLauncher.launch(app.driveSync.signInClient.signInIntent)
                        }) {
                            Text("Sign In with Google")
                        }
                    }
                }
            }

            // About
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Letters to My",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Version 0.1.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}