package com.letters2my.app.ui.letters

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.letters2my.app.data.local.LettersDatabase
import com.letters2my.app.data.local.LetterEntity
import kotlinx.coroutines.launch
import java.util.UUID

class LettersViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = LettersDatabase.getInstance(application).letterDao()
    val letters = dao.getAll()

    fun createLetter(title: String, body: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            dao.insert(
                LetterEntity(
                    id = UUID.randomUUID().toString(),
                    childId = null,
                    branchId = null,
                    folderId = null,
                    title = title,
                    body = body,
                    authorName = "",
                    createdAt = now,
                    updatedAt = now,
                    sealedAt = null,
                    isFavorite = false,
                    isDraft = true,
                    unlockRule = "specificDate",
                    unlockDate = null,
                    unlockAgeYears = null,
                    lifeEventName = "",
                    manuallyReleasedAt = null
                )
            )
        }
    }

    fun deleteLetter(letter: LetterEntity) {
        viewModelScope.launch { dao.delete(letter) }
    }
}