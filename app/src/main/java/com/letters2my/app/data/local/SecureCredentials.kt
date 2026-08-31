package com.letters2my.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure credential storage backed by the Android Keystore
 * (AES-256-GCM master key + encrypted SharedPreferences).
 *
 * Secrets — API tokens, provider passwords, OAuth tokens — MUST go through
 * here, never plaintext SharedPreferences.
 *
 * Non-secret configuration (server URL, region, endpoint) lives in
 * [SettingsRepository] (plain DataStore/prefs), which is fine because it is
 * not a credential.
 */
class SecureCredentials(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun get(key: String): String? = prefs.getString(key, null)

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val FILE_NAME = "letters2my_secure_prefs"

        // Keys
        const val KEY_SELFHOSTED_TOKEN = "selfhosted_token"
        const val KEY_S3_SECRET = "s3_secret_key"
        const val KEY_WEBDAV_PASSWORD = "webdav_password"
        const val KEY_DROPBOX_TOKEN = "dropbox_token"
    }
}

/**
 * Non-secret settings (server URL, S3 endpoint/bucket/region) stored
 * plainly — these are configuration, not credentials.
 */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("letters2my_settings", Context.MODE_PRIVATE)

    var selfHostedUrl: String
        get() = prefs.getString("selfhosted_url", "") ?: ""
        set(value) { prefs.edit().putString("selfhosted_url", value).apply() }

    var s3Endpoint: String
        get() = prefs.getString("s3_endpoint", "") ?: ""
        set(value) { prefs.edit().putString("s3_endpoint", value).apply() }

    var s3Bucket: String
        get() = prefs.getString("s3_bucket", "") ?: ""
        set(value) { prefs.edit().putString("s3_bucket", value).apply() }

    var s3Region: String
        get() = prefs.getString("s3_region", "us-east-1") ?: "us-east-1"
        set(value) { prefs.edit().putString("s3_region", value).apply() }

    var webdavUrl: String
        get() = prefs.getString("webdav_url", "") ?: ""
        set(value) { prefs.edit().putString("webdav_url", value).apply() }

    var webdavUser: String
        get() = prefs.getString("webdav_user", "") ?: ""
        set(value) { prefs.edit().putString("webdav_user", value).apply() }

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(value) { prefs.edit().putBoolean("onboarded", value).apply() }
}