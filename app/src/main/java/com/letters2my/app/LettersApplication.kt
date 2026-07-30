package com.letters2my.app

import android.app.Application
import com.letters2my.app.data.local.BranchEntity
import com.letters2my.app.data.local.LettersDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

class LettersApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        seedDefaultBranches()
    }

    private fun seedDefaultBranches() {
        val db = LettersDatabase.getInstance(this)
        val dao = db.branchDao()

        CoroutineScope(Dispatchers.IO).launch {
            val existing = dao.getAll()
            kotlinx.coroutines.flow.firstOrNull(existing)?.let { return@launch }

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
}