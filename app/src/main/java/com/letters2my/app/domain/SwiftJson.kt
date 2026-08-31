package com.letters2my.app.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Swift-compatible JSON encoder semantics, derived from real iOS output
 * (generated with LettersToMyCore 0.1.0, see /tmp/ltm-fixture).
 *
 * Swift JSONEncoder:
 *  - Date encodes as Double seconds since 2001-01-01T00:00:00Z
 *    (timeIntervalSinceReferenceDate). Whole-second values emit without a
 *    decimal point (e.g. 790171200, not 790171200.0).
 *  - nil optionals are OMITTED from output.
 *  - non-optional fields are ALWAYS present, even when equal to their
 *    default (e.g. isFavorite:false is encoded).
 *  - Data encodes as base64 string.
 *  - UUID encodes as lowercase canonical string.
 */
object SwiftJson {

    /** Seconds between 1970-01-01 and 2001-01-01 (Swift reference date). */
    const val REFERENCE_DATE_OFFSET_SECONDS = 978_307_200.0

    fun dateToSwiftNumber(epochMs: Long): JsonPrimitive {
        val seconds = epochMs / 1000.0 - REFERENCE_DATE_OFFSET_SECONDS
        return swiftDouble(seconds)
    }

    /** Emit Double like Swift's JSONSerialization: whole values without ".0". */
    fun swiftDouble(value: Double): JsonPrimitive {
        val asLong = value.toLong()
        return if (value == asLong.toDouble() && asLong in Long.MIN_VALUE..Long.MAX_VALUE) {
            JsonPrimitive(asLong)
        } else {
            JsonPrimitive(value)
        }
    }

    fun uuid(value: String): JsonPrimitive = JsonPrimitive(value.lowercase())

    /** Encode Data as base64 like Swift. */
    fun data(value: ByteArray): JsonPrimitive =
        JsonPrimitive(java.util.Base64.getEncoder().encodeToString(value))

    fun dataFromJson(element: JsonElement?): ByteArray? {
        val s = element?.jsonPrimitive?.contentOrNull ?: return null
        return try {
            java.util.Base64.getDecoder().decode(s)
        } catch (_: Exception) {
            null
        }
    }

    fun dateFromNumber(element: JsonElement?): Long? {
        val content = element?.jsonPrimitive?.contentOrNull ?: return null
        val seconds = content.toDoubleOrNull() ?: return null
        return ((seconds + REFERENCE_DATE_OFFSET_SECONDS) * 1000.0).toLong()
    }

    fun optString(obj: JsonObject, key: String): String? =
        obj[key]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }

    fun optLong(obj: JsonObject, key: String): Long? =
        obj[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

    fun optInt(obj: JsonObject, key: String): Int? =
        obj[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

    fun optBool(obj: JsonObject, key: String): Boolean? =
        obj[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()

    fun optStringArray(obj: JsonObject, key: String): List<String>? {
        val el = obj[key] as? JsonArray ?: return null
        return el.mapNotNull { it.jsonPrimitive.contentOrNull }
    }

    fun stringArray(values: List<String>): JsonArray =
        JsonArray(values.map { JsonPrimitive(it) })
}