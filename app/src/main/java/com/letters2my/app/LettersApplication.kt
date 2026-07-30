package com.letters2my.app

import android.app.Application
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.letters2my.app.data.local.BranchEntity
import com.letters2my.app.data.local.LettersDatabase
import com.letters2my.app.data.sync.DriveSyncService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.UUID

class LettersApplication : Application() {
    lateinit var driveSync: DriveSyncService
        private set

    var isDriveSyncing = false
        private set

    override fun onCreate() {
        super.onCreate()
        driveSync = DriveSyncService(this)
        seedDefaultBranches()

        // Attempt Drive sync if user is signed in
        CoroutineScope(Dispatchers.IO).launch {
            syncFromDriveIfSignedIn()
        }
    }

    /**
     * If the user has a Google Sign-In, download the latest
     * database from Google Drive appDataFolder.
     */
    suspend fun syncFromDriveIfSignedIn() {
        val account = GoogleSignIn.getLastSignedInAccount(this) ?: return
        if (isDriveSyncing) return
        isDriveSyncing = true
        try {
            val downloaded = driveSync.downloadDatabase(account)
            if (downloaded) {
                Log.i(TAG, "Downloaded database from Drive — restart recommended")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Drive sync failed", e)
        } finally {
            isDriveSyncing = false
        }
    }

    /**
     * Upload the current database to Google Drive.
     */
    suspend fun syncToDrive() {
        val account = GoogleSignIn.getLastSignedInAccount(this) ?: return
        if (isDriveSyncing) return
        isDriveSyncing = true
        try {
            driveSync.uploadDatabase(account)
            Log.i(TAG, "Uploaded database to Drive")
        } catch (e: Exception) {
            Log.e(TAG, "Drive upload failed", e)
        } finally {
            isDriveSyncing = false
        }
    }

    private val db: LettersDatabase by lazy { LettersDatabase.getInstance(this) }

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