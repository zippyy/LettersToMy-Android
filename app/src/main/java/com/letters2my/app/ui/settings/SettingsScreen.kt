package com.letters2my.app.ui.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.letters2my.app.LettersApplication
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as LettersApplication
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("cloud_sync_config", Context.MODE_PRIVATE) }

    val scrollState = rememberScrollState()

    // Google Drive sign-in state
    var isSignedIn by remember { mutableStateOf(GoogleSignIn.getLastSignedInAccount(context) != null) }
    var isSyncing by remember { mutableStateOf(false) }

    // S3 config state
    var s3Endpoint by remember { mutableStateOf(prefs.getString("s3_endpoint", "") ?: "") }
    var s3Bucket by remember { mutableStateOf(prefs.getString("s3_bucket", "") ?: "") }
    var s3AccessKey by remember { mutableStateOf(prefs.getString("s3_access_key", "") ?: "") }
    var s3SecretKey by remember { mutableStateOf(prefs.getString("s3_secret_key", "") ?: "") }
    var s3Region by remember { mutableStateOf(prefs.getString("s3_region", "us-east-1") ?: "us-east-1") }

    // WebDAV config state
    var webdavURL by remember { mutableStateOf(prefs.getString("webdav_url", "") ?: "") }
    var webdavUser by remember { mutableStateOf(prefs.getString("webdav_user", "") ?: "") }
    var webdavPassword by remember { mutableStateOf(prefs.getString("webdav_password", "") ?: "") }

    // Dropbox config state
    var dropboxToken by remember { mutableStateOf(prefs.getString("dropbox_token", "") ?: "") }

    // Self-hosted config state
    var selfhostedURL by remember { mutableStateOf(prefs.getString("selfhosted_url", "") ?: "") }
    var selfhostedToken by remember { mutableStateOf(prefs.getString("selfhosted_token", "") ?: "") }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).result
        isSignedIn = account != null
        if (account != null) {
            scope.launch { app.syncFromDriveIfSignedIn() }
        }
    }

    fun savePrefs() {
        prefs.edit().apply {
            putString("s3_endpoint", s3Endpoint)
            putString("s3_bucket", s3Bucket)
            putString("s3_access_key", s3AccessKey)
            putString("s3_secret_key", s3SecretKey)
            putString("s3_region", s3Region)
            putString("webdav_url", webdavURL)
            putString("webdav_user", webdavUser)
            putString("webdav_password", webdavPassword)
            putString("dropbox_token", dropboxToken)
            putString("selfhosted_url", selfhostedURL)
            putString("selfhosted_token", selfhostedToken)
        }.apply()
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
            // ── Google Drive ──
            ProviderCard(
                title = "Google Drive",
                icon = Icons.Default.Cloud,
                iconColor = MaterialTheme.colorScheme.primary,
                isConfigured = isSignedIn
            ) {
                if (isSignedIn) {
                    if (isSyncing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Syncing...")
                        }
                    } else {
                        Button(onClick = {
                            scope.launch { isSyncing = true; app.syncToDrive(); isSyncing = false }
                        }) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Sync Now")
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

            // ── S3 Compatible ──
            var s3Expanded by remember { mutableStateOf(false) }
            ProviderCard(
                title = "S3 Compatible",
                subtitle = "AWS S3, Backblaze B2, Cloudflare R2, MinIO",
                icon = Icons.Default.Storage,
                iconColor = MaterialTheme.colorScheme.tertiary,
                isConfigured = s3Endpoint.isNotEmpty() && s3Bucket.isNotEmpty(),
                expanded = s3Expanded,
                onToggle = { s3Expanded = it }
            ) {
                OutlinedTextField(s3Endpoint, { s3Endpoint = it; savePrefs() }, label = { Text("Endpoint URL") }, placeholder = { Text("https://s3.us-east-1.amazonaws.com") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(s3Bucket, { s3Bucket = it; savePrefs() }, label = { Text("Bucket") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(s3AccessKey, { s3AccessKey = it; savePrefs() }, label = { Text("Access Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(s3SecretKey, { s3SecretKey = it; savePrefs() }, label = { Text("Secret Key") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(s3Region, { s3Region = it; savePrefs() }, label = { Text("Region") }, placeholder = { Text("us-east-1") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }

            // ── WebDAV / Nextcloud ──
            var webdavExpanded by remember { mutableStateOf(false) }
            ProviderCard(
                title = "WebDAV / Nextcloud",
                subtitle = "Nextcloud, ownCloud, Apache mod_dav",
                icon = Icons.Default.Dns,
                iconColor = MaterialTheme.colorScheme.secondary,
                isConfigured = webdavURL.isNotEmpty(),
                expanded = webdavExpanded,
                onToggle = { webdavExpanded = it }
            ) {
                OutlinedTextField(webdavURL, { webdavURL = it; savePrefs() }, label = { Text("Base URL") }, placeholder = { Text("https://nextcloud.example.com/remote.php/dav/files/USER") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(webdavUser, { webdavUser = it; savePrefs() }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(webdavPassword, { webdavPassword = it; savePrefs() }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            }

            // ── Dropbox ──
            var dropboxExpanded by remember { mutableStateOf(false) }
            ProviderCard(
                title = "Dropbox",
                subtitle = "OAuth access token from Dropbox App Console",
                icon = Icons.Default.Folder,
                iconColor = MaterialTheme.colorScheme.error,
                isConfigured = dropboxToken.isNotEmpty(),
                expanded = dropboxExpanded,
                onToggle = { dropboxExpanded = it }
            ) {
                OutlinedTextField(dropboxToken, { dropboxToken = it; savePrefs() }, label = { Text("Access Token") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Text("Get a token from the Dropbox App Console → Generate access token.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ── Self-Hosted ──
            var selfhostedExpanded by remember { mutableStateOf(false) }
            ProviderCard(
                title = "Self-Hosted",
                subtitle = "Your own LettersToMy sync server (Docker)",
                icon = Icons.Default.Home,
                iconColor = MaterialTheme.colorScheme.primaryContainer,
                isConfigured = selfhostedURL.isNotEmpty(),
                expanded = selfhostedExpanded,
                onToggle = { selfhostedExpanded = it }
            ) {
                OutlinedTextField(selfhostedURL, { selfhostedURL = it; savePrefs() }, label = { Text("Server URL") }, placeholder = { Text("https://sync.example.com:8080") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(selfhostedToken, { selfhostedToken = it; savePrefs() }, label = { Text("API Token") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Text("Run docker compose up -d from LettersToMy-SelfHostedSync.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ── About ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Letters to My", style = MaterialTheme.typography.titleMedium)
                    Text("Version 0.1.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProviderCard(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: androidx.compose.ui.graphics.Color,
    isConfigured: Boolean,
    expanded: Boolean = true,
    onToggle: ((Boolean) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (onToggle != null) Modifier.fillMaxWidth() else Modifier
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(
                    if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
            }
            if (onToggle != null) {
                TextButton(onClick = { onToggle(!expanded) }) {
                    Text(if (expanded) "Hide" else "Configure")
                }
            }
        }
    }
}