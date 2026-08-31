package com.letters2my.app.data.sync

import android.util.Log

/**
 * Self-hosted API v1 provider (typed client). Portable encrypted archives
 * go to /backup endpoints (push, list, pull, delete); platform snapshots
 * to /sync endpoints remain device-snapshot storage only and are NEVER
 * cross-restored.
 */
class SelfHostedSyncProvider(
    baseUrl: String,
    apiToken: String
) : CloudSyncProvider {

    override val name = "Self-Hosted"
    override val supportsPortableArchives: Boolean = true

    private val api = SelfHostedApiClient(baseUrl) { apiToken }

    override suspend fun pushArchive(archive: ByteArray, name: String, letterCount: Int) {
        val result = api.pushBackup(id = name, letterCount = letterCount, archive = archive)
        Log.d("SelfHostedSync", "backup ${result.id} pushed: ${result.size} bytes sha256=${result.sha256.take(12)}…")
    }

    override suspend fun listArchives(): List<String> =
        api.listBackups().map { it.id }

    override suspend fun pullArchive(name: String): ByteArray? = try {
        api.pullBackup(name)
    } catch (e: SelfHostedApiClient.ApiException) {
        if (e.code == "not_found") null else throw e
    }

    override suspend fun deleteArchive(name: String) {
        api.deleteBackup(name)
    }

    override suspend fun pushSnapshot(platform: String, data: ByteArray) {
        api.pushSnapshot(platform, data)
    }

    override suspend fun pullSnapshot(platform: String): ByteArray? =
        api.pullSnapshot(platform)
}