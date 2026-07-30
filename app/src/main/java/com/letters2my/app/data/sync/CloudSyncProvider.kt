package com.letters2my.app.data.sync

/**
 * Generic cloud sync provider. Each implementation syncs
 * the Room database to a different cloud backend.
 */
interface CloudSyncProvider {
    val name: String
    suspend fun pushDatabase(data: ByteArray, timestamp: Long)
    suspend fun pullDatabase(): CloudSyncResult?
}

data class CloudSyncResult(
    val data: ByteArray,
    val timestamp: Long,
    val provider: String
)