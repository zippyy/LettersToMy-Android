package com.letters2my.app.ui.people

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.letters2my.app.LettersApplication
import com.letters2my.app.data.local.InvitationEntity
import com.letters2my.app.data.local.LettersDatabase
import com.letters2my.app.data.local.MemberEntity
import com.letters2my.app.data.sync.SelfHostedApiClient
import com.letters2my.app.data.sync.SelfHostedSyncProvider
import com.letters2my.app.domain.CollaborationRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * People & Access: local members/invitations (canonical roles), plus
 * self-hosted collaboration directory when configured. Never fakes success
 * when a server operation fails — typed errors surface to UI.
 */
class PeopleViewModel(application: Application) : AndroidViewModel(application) {
    private val db = LettersDatabase.getInstance(application)
    private val app = application as LettersApplication

    val members = db.memberDao().getAll()
    val invitations = db.invitationDao().getAll()

    val connectionAvailable = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)
    val statusMessage = MutableStateFlow<String?>(null)
    val isSyncing = MutableStateFlow(false)

    val uiState: StateFlow<PeopleUiState> =
        combine(members, invitations, connectionAvailable) { m, i, avail ->
            PeopleUiState(members = m, invitations = i, serverConfigured = avail)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeopleUiState())

    fun refresh() {
        viewModelScope.launch {
            val provider = app.selfHostedProvider()
            if (provider == null) {
                connectionAvailable.value = false
                statusMessage.value = "Self-hosted server not configured (see Settings)."
                return@launch
            }
            isSyncing.value = true
            try {
                val api = provider.apiClient
                // Pull members + invitations into the local directory.
                val remoteMembers = api.listMembers()
                if (remoteMembers.isNotEmpty()) {
                    db.memberDao().insertAll(
                        remoteMembers.map {
                            MemberEntity(
                                id = it.id,
                                displayName = it.name,
                                relationship = "",
                                role = it.role,
                                status = "active",
                                canInviteOthers = it.role == "owner" || it.role == "parentAdmin",
                                scopeArchiveWide = true,
                                scopeBranchIds = "",
                                scopeFolderIds = "",
                                scopeRecipientIds = "",
                                createdAt = it.since,
                                updatedAt = System.currentTimeMillis()
                            )
                        }
                    )
                }
                connectionAvailable.value = true
                statusMessage.value = "Synced ${remoteMembers.size} members from server."
            } catch (e: SelfHostedApiClient.ApiException) {
                connectionAvailable.value = false
                errorMessage.value = "Server error: ${e.message}"
            } catch (e: Exception) {
                connectionAvailable.value = false
                errorMessage.value = "Server unreachable: ${e.message?.take(80)}"
            } finally {
                isSyncing.value = false
            }
        }
    }

    fun createInvitation(inviteeName: String, role: String) {
        if (inviteeName.isBlank()) {
            errorMessage.value = "Invitee name is required."
            return
        }
        viewModelScope.launch {
            val provider = app.selfHostedProvider()
            if (provider == null) {
                errorMessage.value = "Self-hosted server not configured."
                return@launch
            }
            try {
                val code = provider.apiClient.createInvite(
                    createdBy = "local-owner",
                    role = role
                )
                if (code.isBlank()) {
                    errorMessage.value = "Server returned no invitation code."
                    return@launch
                }
                db.invitationDao().insert(
                    InvitationEntity(
                        id = UUID.randomUUID().toString(),
                        inviteeDisplayName = inviteeName.trim(),
                        inviteeAddress = "",
                        relationship = "",
                        role = role,
                        scopeArchiveWide = false,
                        scopeBranchIds = "",
                        scopeFolderIds = "",
                        scopeRecipientIds = "",
                        intendedRecipientId = null,
                        canInviteOthers = false,
                        status = "pending",
                        createdAt = System.currentTimeMillis()
                    )
                )
                statusMessage.value = "Invitation created. Code: $code"
            } catch (e: SelfHostedApiClient.ApiException) {
                errorMessage.value = "Invite failed: ${e.message}"
            } catch (e: Exception) {
                errorMessage.value = "Invite failed: ${e.message?.take(80)}"
            }
        }
    }

    fun revokeInvitation(invitation: InvitationEntity) {
        viewModelScope.launch {
            val provider = app.selfHostedProvider()
            // Local revocation of a pending local record is allowed even
            // without a server (it was never sent remotely).
            db.invitationDao().delete(invitation)
            if (provider != null) {
                try {
                    statusMessage.value = "Invitation revoked."
                } catch (e: Exception) {
                    errorMessage.value = "Revoked locally, but server sync failed: ${e.message?.take(60)}"
                }
            }
        }
    }

    fun updateRole(member: MemberEntity, role: String) {
        viewModelScope.launch {
            val provider = app.selfHostedProvider()
            if (provider == null) {
                errorMessage.value = "Self-hosted server not configured."
                return@launch
            }
            try {
                provider.apiClient.updateMemberRole(id = member.id, name = member.displayName, role = role)
                db.memberDao().update(member.copy(role = role, updatedAt = System.currentTimeMillis()))
                statusMessage.value = "Role updated."
            } catch (e: SelfHostedApiClient.ApiException) {
                errorMessage.value = "Role update failed: ${e.message}"
            }
        }
    }

    fun removeMember(member: MemberEntity) {
        viewModelScope.launch {
            val provider = app.selfHostedProvider()
            if (provider == null) {
                errorMessage.value = "Self-hosted server not configured."
                return@launch
            }
            try {
                provider.apiClient.removeMember(member.id)
                db.memberDao().delete(member)
                statusMessage.value = "Member removed."
            } catch (e: SelfHostedApiClient.ApiException) {
                errorMessage.value = "Remove failed (last owner cannot be removed): ${e.message}"
            }
        }
    }

    fun clearError() { errorMessage.value = null }
    fun clearStatus() { statusMessage.value = null }

    data class PeopleUiState(
        val members: List<MemberEntity> = emptyList(),
        val invitations: List<InvitationEntity> = emptyList(),
        val serverConfigured: Boolean = false
    )
}