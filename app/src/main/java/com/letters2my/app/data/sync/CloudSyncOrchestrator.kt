package com.letters2my.app.data.sync

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Corrected sync architecture:
 *
 *  - Portable cross-device recovery is `.letterstomy` ENCRYPTED ARCHIVES,
 *    never raw SQLite bytes. Both iOS and Android can restore these.
 *  - Device snapshots (self-hosted /sync, Drive, Dropbox, WebDAV, S3) are
 *    PLATFORM-SPECIFIC artifacts: android snapshot -> android recovery only.
 *    Never pull an iOS snapshot into an Android Room database.
 *  - The Room database is NEVER hot-replaced underneath an open Room
 *    instance. Raw snapshot recovery, when explicitly requested, is a
 *    controlled stop/validate/replace/restart workflow.
 */
class CloudSyncOrchestrator {

    companion object {
        private const val TAG = "CloudSync"
        const val PLATFORM = "android"
    }

    private var providers = mutableListOf<CloudSyncProvider>()
    private val _recoveryEvents = MutableStateFlow<String?>(null)

    /** User-visible recovery status (explicit recovery only). */
    val recoveryEvents: StateFlow<String?> = _recoveryEvents

    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    fun configure(providers: List<CloudSyncProvider>) {
        this.providers = providers.toMutableList()
    }

    /**
     * ✗ REMOVED: automatic pullLatest() that swapped raw SQLite files over
     * the open Room database. That is unsafe and was never cross-platform
     * logical sync.
     */

    /**
     * Portable encrypted backup push: each provider stores the encrypted
     * `.letterstomy` archive bytes under a stable name. This is the ONLY
     * automatic cross-device backup path.
     */
    fun pushBackupArchive(archive: ByteArray, name: String, letterCount: Int = 0) {
        scope.launch {
            providers.filter { it.supportsPortableArchives }.forEach { provider ->
                try {
                    provider.pushArchive(archive, name, letterCount)
                    Log.i(TAG, "${provider.name}: pushed portable archive $name (${archive.size} bytes)")
                } catch (e: Exception) {
                    Log.w(TAG, "${provider.name}: archive push failed: ${e.message}")
                }
            }
        }
    }

    suspend fun pushArchiveSuspend(archive: ByteArray, name: String, letterCount: Int = 0): List<String> =
        withContext(Dispatchers.IO) {
            val succeeded = mutableListOf<String>()
            providers.filter { it.supportsPortableArchives }.forEach { provider ->
                try {
                    provider.pushArchive(archive, name, letterCount)
                    succeeded.add(provider.name)
                } catch (e: Exception) {
                    Log.w(TAG, "${provider.name}: archive push failed: ${e.message}")
                }
            }
            succeeded
        }

    /**
     * Explicit Android device-snapshot recovery. This is NOT automatic and
     * NOT cross-platform: only an `android` platform snapshot is accepted.
     *
     * Returns the raw snapshot bytes; the caller must perform the CONTROLLED
     * recovery workflow (close Room, validate the snapshot, replace the DB
     * file, restart) — never hot-swap under an open Room instance.
     */
    suspend fun recoverAndroidSnapshot(provider: CloudSyncProvider): ByteArray? =
        withContext(Dispatchers.IO) {
            val snapshot = provider.pullSnapshot(PLATFORM) ?: run {
                _recoveryEvents.value = "No Android snapshot available from ${provider.name}"
                return@withContext null
            }
            _recoveryEvents.value =
                "Android snapshot downloaded from ${provider.name} (${snapshot.size} bytes). " +
                    "Explicit recovery required: close Room, validate, replace, restart."
            snapshot
        }

    /** Expose the configured self-hosted provider (for collaboration/settings). */
    fun selfHostedProvider(): SelfHostedSyncProvider? =
        providers.filterIsInstance<SelfHostedSyncProvider>().firstOrNull()
}

/**
 * Corrected provider contract: portable archives vs platform snapshots are
 * distinct capabilities. Nothing here touches the Room DB implicitly.
 */
interface CloudSyncProvider {
    val name: String

    /** Whether this backend stores portable encrypted `.letterstomy` archives. */
    val supportsPortableArchives: Boolean

    /** Push an encrypted portable archive. letterCount is a metadata hint (0 = unknown). */
    suspend fun pushArchive(archive: ByteArray, name: String, letterCount: Int = 0)

    /** List portable archive names (for restore pickers). */
    suspend fun listArchives(): List<String>

    /** Pull a portable archive by name. */
    suspend fun pullArchive(name: String): ByteArray?

    /** Delete a portable archive by name. */
    suspend fun deleteArchive(name: String)

    /** Platform snapshot support (android only for recovery). */
    suspend fun pushSnapshot(platform: String, data: ByteArray)
    suspend fun pullSnapshot(platform: String): ByteArray?
}