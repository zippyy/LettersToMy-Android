package com.letters2my.app.ui.family

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.letters2my.app.data.local.BranchEntity
import com.letters2my.app.data.local.ChildEntity
import com.letters2my.app.data.local.FolderEntity
import com.letters2my.app.data.local.LettersDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Family management: children CRUD (name, birth date), family sides
 * (branches), folders. Seeded default branches match iOS.
 */
class FamilyViewModel(application: Application) : AndroidViewModel(application) {
    private val db = LettersDatabase.getInstance(application)

    val children = db.childDao().getAll()
    val branches = db.branchDao().getAll()
    val folders = db.folderDao().getAll()

    val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<FamilyUiState> =
        combine(children, branches, folders) { c, b, f ->
            FamilyUiState(children = c, branches = b, folders = f)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FamilyUiState())

    fun addChild(name: String, birthDate: Long?) {
        if (name.isBlank()) {
            errorMessage.value = "Name is required."
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            runCatching {
                db.childDao().insert(
                    ChildEntity(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        birthDate = birthDate,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }.onFailure { errorMessage.value = "Could not save child: ${it.message}" }
        }
    }

    fun updateChild(child: ChildEntity, name: String, birthDate: Long?) {
        if (name.isBlank()) {
            errorMessage.value = "Name is required."
            return
        }
        viewModelScope.launch {
            runCatching {
                db.childDao().update(
                    child.copy(name = name.trim(), birthDate = birthDate, updatedAt = System.currentTimeMillis())
                )
            }.onFailure { errorMessage.value = "Could not update child: ${it.message}" }
        }
    }

    fun deleteChild(child: ChildEntity) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    // Letters reference children by UUID; removal of the child
                    // profile does NOT delete letters (preserves the archive).
                    db.childDao().delete(child)
                }.isSuccess
            }
            if (!ok) errorMessage.value = "Could not delete child."
        }
    }

    fun addBranch(name: String, kind: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching {
                db.branchDao().insert(
                    BranchEntity(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        kind = kind,
                        isSeeded = false,
                        parentBranchId = null,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }.onFailure { errorMessage.value = "Could not add family side." }
        }
    }

    fun deleteBranch(branch: BranchEntity) {
        if (branch.isSeeded) {
            errorMessage.value = "Seeded family sides can't be removed."
            return
        }
        viewModelScope.launch {
            runCatching { db.branchDao().delete(branch) }
                .onFailure { errorMessage.value = "Could not delete family side." }
        }
    }

    fun addFolder(branchId: String, name: String) {
        if (name.isBlank() || branchId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                db.folderDao().insert(
                    FolderEntity(
                        id = UUID.randomUUID().toString(),
                        branchId = branchId,
                        parentFolderId = null,
                        name = name.trim(),
                        createdAt = System.currentTimeMillis()
                    )
                )
            }.onFailure { errorMessage.value = "Could not add folder." }
        }
    }

    fun deleteFolder(folder: FolderEntity) {
        viewModelScope.launch {
            runCatching { db.folderDao().delete(folder) }
                .onFailure { errorMessage.value = "Could not delete folder." }
        }
    }

    fun clearError() { errorMessage.value = null }

    data class FamilyUiState(
        val children: List<ChildEntity> = emptyList(),
        val branches: List<BranchEntity> = emptyList(),
        val folders: List<FolderEntity> = emptyList()
    )
}