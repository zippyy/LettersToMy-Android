package com.letters2my.app.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * `.letterstomy` archive payload models — field-for-field mirror of iOS
 * `BackupPayload` (Sources/LettersToMyCore/Backup.swift). JSON property
 * names, null-omission, and date encoding MUST match the iOS encoder.
 */
data class BackupManifest(
    var formatVersion: Int = 1,
    var archiveID: String,
    var createdAtEpochMs: Long,
    var appVersion: String = "0.1.0",
    var letterCount: Int = 0,
    var attachmentCount: Int = 0,
    var recipientCount: Int = 0,
    var encryptionAlgorithm: String = "AES-256-GCM"
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("formatVersion", formatVersion)
        put("archiveID", SwiftJson.uuid(archiveID))
        put("createdAt", SwiftJson.dateToSwiftNumber(createdAtEpochMs))
        put("appVersion", appVersion)
        put("letterCount", letterCount)
        put("attachmentCount", attachmentCount)
        put("recipientCount", recipientCount)
        put("encryptionAlgorithm", encryptionAlgorithm)
    }

    companion object {
        fun fromJson(o: JsonObject): BackupManifest = BackupManifest(
            formatVersion = SwiftJson.optInt(o, "formatVersion") ?: 1,
            archiveID = SwiftJson.optString(o, "archiveID") ?: "",
            createdAtEpochMs = SwiftJson.dateFromNumber(o["createdAt"]) ?: 0L,
            appVersion = SwiftJson.optString(o, "appVersion") ?: "",
            letterCount = SwiftJson.optInt(o, "letterCount") ?: 0,
            attachmentCount = SwiftJson.optInt(o, "attachmentCount") ?: 0,
            recipientCount = SwiftJson.optInt(o, "recipientCount") ?: 0,
            encryptionAlgorithm = SwiftJson.optString(o, "encryptionAlgorithm") ?: "AES-256-GCM"
        )
    }
}

data class ChildPayload(
    var id: String,
    var name: String,
    var birthDateEpochMs: Long?
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("id", SwiftJson.uuid(id))
        put("name", name)
        birthDateEpochMs?.let { put("birthDate", SwiftJson.dateToSwiftNumber(it)) }
    }

    companion object {
        fun fromJson(o: JsonObject): ChildPayload = ChildPayload(
            id = SwiftJson.optString(o, "id") ?: "",
            name = SwiftJson.optString(o, "name") ?: "",
            birthDateEpochMs = SwiftJson.dateFromNumber(o["birthDate"])
        )
    }
}

data class LetterPayload(
    var id: String,
    var childID: String?,
    var branchID: String?,
    var folderID: String?,
    var authorMemberID: String?,
    var title: String,
    var body: String,
    var authorName: String,
    var createdAtEpochMs: Long,
    var updatedAtEpochMs: Long,
    var sealedAtEpochMs: Long?,
    var isFavorite: Boolean,
    var unlockRuleRawValue: String,
    var unlockDateEpochMs: Long?,
    var unlockAgeYearsValue: Int?,
    var lifeEventName: String,
    var manuallyReleasedAtEpochMs: Long?
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("id", SwiftJson.uuid(id))
        childID?.let { put("childID", SwiftJson.uuid(it)) }
        branchID?.let { put("branchID", SwiftJson.uuid(it)) }
        folderID?.let { put("folderID", SwiftJson.uuid(it)) }
        authorMemberID?.let { put("authorMemberID", SwiftJson.uuid(it)) }
        put("title", title)
        put("body", body)
        put("authorName", authorName)
        put("createdAt", SwiftJson.dateToSwiftNumber(createdAtEpochMs))
        put("updatedAt", SwiftJson.dateToSwiftNumber(updatedAtEpochMs))
        sealedAtEpochMs?.let { put("sealedAt", SwiftJson.dateToSwiftNumber(it)) }
        put("isFavorite", isFavorite)
        put("unlockRuleRawValue", unlockRuleRawValue)
        unlockDateEpochMs?.let { put("unlockDate", SwiftJson.dateToSwiftNumber(it)) }
        unlockAgeYearsValue?.let { put("unlockAgeYearsValue", it) }
        put("lifeEventName", lifeEventName)
        manuallyReleasedAtEpochMs?.let { put("manuallyReleasedAt", SwiftJson.dateToSwiftNumber(it)) }
    }

    companion object {
        fun fromJson(o: JsonObject): LetterPayload = LetterPayload(
            id = SwiftJson.optString(o, "id") ?: "",
            childID = SwiftJson.optString(o, "childID"),
            branchID = SwiftJson.optString(o, "branchID"),
            folderID = SwiftJson.optString(o, "folderID"),
            authorMemberID = SwiftJson.optString(o, "authorMemberID"),
            title = SwiftJson.optString(o, "title") ?: "",
            body = SwiftJson.optString(o, "body") ?: "",
            authorName = SwiftJson.optString(o, "authorName") ?: "",
            createdAtEpochMs = SwiftJson.dateFromNumber(o["createdAt"]) ?: 0L,
            updatedAtEpochMs = SwiftJson.dateFromNumber(o["updatedAt"]) ?: 0L,
            sealedAtEpochMs = SwiftJson.dateFromNumber(o["sealedAt"]),
            isFavorite = SwiftJson.optBool(o, "isFavorite") ?: false,
            unlockRuleRawValue = SwiftJson.optString(o, "unlockRuleRawValue") ?: "specificDate",
            unlockDateEpochMs = SwiftJson.dateFromNumber(o["unlockDate"]),
            unlockAgeYearsValue = SwiftJson.optInt(o, "unlockAgeYearsValue"),
            lifeEventName = SwiftJson.optString(o, "lifeEventName") ?: "",
            manuallyReleasedAtEpochMs = SwiftJson.dateFromNumber(o["manuallyReleasedAt"])
        )
    }
}

data class AttachmentPayload(
    var id: String,
    var letterID: String,
    var fileName: String,
    var contentTypeIdentifier: String,
    var kindRawValue: String,
    var createdAtEpochMs: Long,
    var data: ByteArray
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("id", SwiftJson.uuid(id))
        put("letterID", SwiftJson.uuid(letterID))
        put("fileName", fileName)
        put("contentTypeIdentifier", contentTypeIdentifier)
        put("kindRawValue", kindRawValue)
        put("createdAt", SwiftJson.dateToSwiftNumber(createdAtEpochMs))
        put("data", SwiftJson.data(data))
    }

    companion object {
        fun fromJson(o: JsonObject): AttachmentPayload = AttachmentPayload(
            id = SwiftJson.optString(o, "id") ?: "",
            letterID = SwiftJson.optString(o, "letterID") ?: "",
            fileName = SwiftJson.optString(o, "fileName") ?: "",
            contentTypeIdentifier = SwiftJson.optString(o, "contentTypeIdentifier") ?: "",
            kindRawValue = SwiftJson.optString(o, "kindRawValue") ?: "file",
            createdAtEpochMs = SwiftJson.dateFromNumber(o["createdAt"]) ?: 0L,
            data = SwiftJson.dataFromJson(o["data"]) ?: ByteArray(0)
        )
    }
}

data class BranchPayload(
    var id: String,
    var name: String,
    var kindRawValue: String,
    var parentBranchID: String?
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("id", SwiftJson.uuid(id))
        put("name", name)
        put("kindRawValue", kindRawValue)
        parentBranchID?.let { put("parentBranchID", SwiftJson.uuid(it)) }
    }

    companion object {
        fun fromJson(o: JsonObject): BranchPayload = BranchPayload(
            id = SwiftJson.optString(o, "id") ?: "",
            name = SwiftJson.optString(o, "name") ?: "",
            kindRawValue = SwiftJson.optString(o, "kindRawValue") ?: "custom",
            parentBranchID = SwiftJson.optString(o, "parentBranchID")
        )
    }
}

data class FolderPayload(
    var id: String,
    var branchID: String,
    var parentFolderID: String?,
    var name: String
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("id", SwiftJson.uuid(id))
        put("branchID", SwiftJson.uuid(branchID))
        parentFolderID?.let { put("parentFolderID", SwiftJson.uuid(it)) }
        put("name", name)
    }

    companion object {
        fun fromJson(o: JsonObject): FolderPayload = FolderPayload(
            id = SwiftJson.optString(o, "id") ?: "",
            branchID = SwiftJson.optString(o, "branchID") ?: "",
            parentFolderID = SwiftJson.optString(o, "parentFolderID"),
            name = SwiftJson.optString(o, "name") ?: ""
        )
    }
}

data class MemberPayload(
    var id: String,
    var displayName: String,
    var relationship: String,
    var roleRawValue: String,
    var statusRawValue: String,
    var canInviteOthers: Boolean
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("id", SwiftJson.uuid(id))
        put("displayName", displayName)
        put("relationship", relationship)
        put("roleRawValue", roleRawValue)
        put("statusRawValue", statusRawValue)
        put("canInviteOthers", canInviteOthers)
    }

    companion object {
        fun fromJson(o: JsonObject): MemberPayload = MemberPayload(
            id = SwiftJson.optString(o, "id") ?: "",
            displayName = SwiftJson.optString(o, "displayName") ?: "",
            relationship = SwiftJson.optString(o, "relationship") ?: "",
            roleRawValue = SwiftJson.optString(o, "roleRawValue") ?: "viewer",
            statusRawValue = SwiftJson.optString(o, "statusRawValue") ?: "active",
            canInviteOthers = SwiftJson.optBool(o, "canInviteOthers") ?: false
        )
    }
}

data class InvitationPayload(
    var id: String,
    var inviteeDisplayName: String,
    var inviteeAddress: String,
    var relationship: String,
    var roleRawValue: String,
    var statusRawValue: String
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("id", SwiftJson.uuid(id))
        put("inviteeDisplayName", inviteeDisplayName)
        put("inviteeAddress", inviteeAddress)
        put("relationship", relationship)
        put("roleRawValue", roleRawValue)
        put("statusRawValue", statusRawValue)
    }

    companion object {
        fun fromJson(o: JsonObject): InvitationPayload = InvitationPayload(
            id = SwiftJson.optString(o, "id") ?: "",
            inviteeDisplayName = SwiftJson.optString(o, "inviteeDisplayName") ?: "",
            inviteeAddress = SwiftJson.optString(o, "inviteeAddress") ?: "",
            relationship = SwiftJson.optString(o, "relationship") ?: "",
            roleRawValue = SwiftJson.optString(o, "roleRawValue") ?: "viewer",
            statusRawValue = SwiftJson.optString(o, "statusRawValue") ?: "pending"
        )
    }
}

data class BackupPayload(
    var manifest: BackupManifest,
    var children: List<ChildPayload>,
    var letters: List<LetterPayload>,
    var attachments: List<AttachmentPayload>,
    var branches: List<BranchPayload>,
    var folders: List<FolderPayload>,
    var members: List<MemberPayload>,
    var invitations: List<InvitationPayload>
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("manifest", manifest.toJson())
        put("children", JsonArray(children.map { it.toJson() }))
        put("letters", JsonArray(letters.map { it.toJson() }))
        put("attachments", JsonArray(attachments.map { it.toJson() }))
        put("branches", JsonArray(branches.map { it.toJson() }))
        put("folders", JsonArray(folders.map { it.toJson() }))
        put("members", JsonArray(members.map { it.toJson() }))
        put("invitations", JsonArray(invitations.map { it.toJson() }))
    }

    companion object {
        fun fromJson(root: JsonObject): BackupPayload {
            fun <T> list(key: String, parser: (JsonObject) -> T): List<T> {
                val arr = root[key] as? JsonArray ?: return emptyList()
                return arr.mapNotNull { (it as? JsonObject)?.let(parser) }
            }
            return BackupPayload(
                manifest = BackupManifest.fromJson((root["manifest"] as? JsonObject) ?: JsonObject(emptyMap())),
                children = list("children") { ChildPayload.fromJson(it) },
                letters = list("letters") { LetterPayload.fromJson(it) },
                attachments = list("attachments") { AttachmentPayload.fromJson(it) },
                branches = list("branches") { BranchPayload.fromJson(it) },
                folders = list("folders") { FolderPayload.fromJson(it) },
                members = list("members") { MemberPayload.fromJson(it) },
                invitations = list("invitations") { InvitationPayload.fromJson(it) }
            )
        }
    }
}