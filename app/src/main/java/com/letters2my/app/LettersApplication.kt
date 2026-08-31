package com.letters2my.app

import android.app.Application
import com.letters2my.app.data.local.BranchEntity
import com.letters2my.app.data.local.LettersDatabase
import com.letters2my.app.data.local.SecureCredentials
import com.letters2my.app.data.local.SettingsRepository
import com.letters2my.app.data.sync.CloudSyncOrchestrator
import com.letters2my.app.data.sync.DriveSyncService
import com.letters2my.app.data.sync.SelfHostedApiClient
import com.letters2my.app.data.sync.SelfHostedSyncProvider
import kotlinx.coroutines.runBlocking
import java.util.UUID

/**
 * App container. Offline-first: startup never blocks or couples to remote
 * availability. No automatic raw-DB sync on launch (the unsafe hot-swap path
 * is removed); cloud providers store only portable encrypted archives.
 */
class LettersApplication : Application() {

    lateinit var database: LettersDatabase
        private set

    lateinit var secureCredentials: SecureCredentials
        private set

    lateinit var settings: SettingsRepository
        private set

    val orchestrator = CloudSyncOrchestrator()

    private var driveSync: DriveSyncService? = null

    override fun onCreate() {
        super.onCreate()
        database = LettersDatabase.getInstance(this)
        secureCredentials = SecureCredentials(this)
        settings = SettingsRepository(this)

        seedDefaultBranches()
        configureProviders()
    }

    /**
     * Configure cloud providers from current settings. Self-hosted is the
     * first-class API v1 provider; Drive is available when signed in.
     * Raw device-snapshot sync is NEVER automatic.
     */
    private fun configureProviders() {
        val providers = mutableListOf<SelfHostedSyncProvider>()

        val url = settings.selfHostedUrl
        val token = secureCredentials.get(SecureCredentials.KEY_SELFHOSTED_TOKEN)
        if (url.isNotBlank() && !token.isNullOrBlank()) {
            providers.add(SelfHostedSyncProvider(SelfHostedApiClient.normalizeBaseUrl(url), token))
        }

        driveSync = DriveSyncService(this)
        if (providers.isNotEmpty()) {
            orchestrator.configure(providers)
        }
    }

    /** Rebuild provider list from current settings (after config changes). */
    fun reconfigureProviders() {
        configureProviders()
    }

    /** Self-hosted client for collaboration/settings use. Null unless configured. */
    fun selfHostedProvider(): SelfHostedSyncProvider? =
        orchestrator.selfHostedProvider()

    private val db: LettersDatabase
        get() = database

    private fun seedDefaultBranches() {
        val dao = db.branchDao()
        runBlocking {
            if (dao.count() > 0) return@runBlocking

            val defaults = listOf(
                "Parents" to "parents",
                "Maternal Family" to "maternal",
                "Paternal Family" to "paternal",
                "Chosen Family" to "chosenFamily"
            )

            val now = System.currentTimeMillis()
            defaults.forEach { (name, kind) ->
                dao.insert(
                    BranchEntity(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        kind = kind,
                        isSeeded = true,
                        parentBranchId = null,
                        createdAt = now
                    )
                )
            }
        }
    }

    companion object {
        private const val TAG = "LettersApp"
    }
}