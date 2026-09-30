package com.letters2my.app

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking

/**
 * Shared instrumentation state helpers.
 *
 * Instrumentation runs every test in ONE process with a single
 * [LettersApplication] singleton, so the Room database and preference stores
 * are created once in onCreate and never rebuilt. Deleting the DB file mid-run
 * does nothing (the connection is already open) — therefore resets go through
 * the app's own DAOs/prefs, exactly as the product does.
 *
 * Seeded family branches are intentionally preserved: they are created once by
 * LettersApplication.onCreate and would not be re-seeded.
 */
object AppState {

    val app: LettersApplication
        get() = ApplicationProvider.getApplicationContext<LettersApplication>()

    fun prefs() = app.getSharedPreferences("letters2my_settings", Context.MODE_PRIVATE)

    /** Clear user data + preferences, leaving seeded branches in place. */
    fun resetAll() {
        prefs().edit().clear().commit()
        app.secureCredentials.clear()
        // The orchestrator caches the provider list built at Application.onCreate
        // (and on save). Clearing prefs alone would leave a stale provider, so
        // People would still render as configured. The product's own "Clear
        // Configuration" path calls this too.
        app.reconfigureProviders()
        runBlocking {
            val db = app.database
            db.attachmentDao().getAllOnce().forEach { db.attachmentDao().delete(it) }
            db.letterDao().getAllOnce().forEach { db.letterDao().delete(it) }
            db.childDao().getAllOnce().forEach { db.childDao().delete(it) }
            db.folderDao().getAllOnce().forEach { db.folderDao().delete(it) }
        }
    }

    fun markOnboarded() {
        prefs().edit().putBoolean("onboarded", true).commit()
    }

    fun isOnboarded(): Boolean = prefs().getBoolean("onboarded", false)

    fun launch(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java)

    /** Clean slate that skips onboarding (what a returning user sees). */
    fun resetToOnboarded() {
        resetAll()
        markOnboarded()
    }
}
