package com.letters2my.app.data.sync

import android.util.Log
import com.letters2my.app.LettersApplication
import kotlinx.coroutines.*

/**
 * Orchestrates cross-platform sync across multiple providers.
 * On app start, tries each provider — first successful pull wins.
 * On changes, pushes to all configured providers.
 */
class CloudSyncOrchestrator(private val app: LettersApplication) {

    companion object {
        private const val TAG = "CloudSync"
        private const val PLATFORM = "android"
    }

    private var providers = mutableListOf<CloudSyncProvider>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun configure(providers: List<CloudSyncProvider>) {
        this.providers = providers.toMutableList()
    }

    fun pullLatest() {
        scope.launch {
            val dbFile = app.getDatabasePath("letters_to_my.db")
            if (!dbFile.exists()) return@launch

            for (provider in providers) {
                try {
                    val result = provider.pullDatabase() ?: continue
                    if (result.timestamp <= dbFile.lastModified()) {
                        Log.d(TAG, "${provider.name}: local is current")
                        continue
                    }
                    // Replace local DB with synced copy
                    dbFile.outputStream().use { it.write(result.data) }
                    dbFile.setLastModified(result.timestamp)
                    app.getDatabasePath("letters_to_my.db-wal").delete()
                    app.getDatabasePath("letters_to_my.db-shm").delete()
                    Log.i(TAG, "Synced from ${provider.name}: ${result.data.size} bytes")
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "${provider.name} pull failed: ${e.message}")
                }
            }
        }
    }

    fun pushToAll() {
        scope.launch {
            val dbFile = app.getDatabasePath("letters_to_my.db")
            if (!dbFile.exists()) return@launch
            val data = dbFile.readBytes()
            val timestamp = dbFile.lastModified()

            providers.forEach { provider ->
                try {
                    provider.pushDatabase(data, timestamp)
                } catch (e: Exception) {
                    Log.w(TAG, "${provider.name} push failed: ${e.message}")
                }
            }
        }
    }
}