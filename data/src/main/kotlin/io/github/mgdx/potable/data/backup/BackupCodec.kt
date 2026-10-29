package io.github.mgdx.potable.data.backup

import io.github.mgdx.potable.data.preferences.AppPreferences
import io.github.mgdx.potable.data.preferences.ThemeMode
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale

/** A followed unit as kept in a backup: the same fields as a follow, without its id. */
data class BackupFollow(
    val communeCode: String,
    val udiCode: String,
    val communeName: String,
    val udiName: String,
)

/** The settings kept in a backup; null for one absent or out of range. */
data class BackupSettings(
    val themeMode: ThemeMode? = null,
    val historyYears: Int? = null,
    val retentionYears: Int? = null,
)

/**
 * What a backup file holds: the follows, the unit chosen for a commune with
 * several ([udiChoices], by commune code) and the display settings. Never
 * the recent units, the notice's acknowledgement nor the alerts, which need
 * the notification permission and are switched on again by hand.
 */
data class Backup(
    val follows: List<BackupFollow>,
    val udiChoices: Map<String, String> = emptyMap(),
    val settings: BackupSettings = BackupSettings(),
)

/** Why a file cannot be imported. */
enum class BackupError {
    /** Not JSON, or JSON of an unexpected shape. */
    UNREADABLE,

    /** JSON, but not a Potable backup. */
    NOT_A_BACKUP,

    /** A Potable backup of a version this one does not know. */
    UNSUPPORTED_VERSION,

    /** Larger than [BackupCodec.MAX_BYTES]. */
    TOO_LARGE,
}

/** What reading a backup file gave. */
sealed interface BackupRead {
    /** [backup] holds the valid follows; [invalid] were left out (bad code or name). */
    data class Valid(val backup: Backup, val invalid: Int) : BackupRead

    data class Invalid(val error: BackupError) : BackupRead
}

/**
 * The backup file, in JSON:
 * `{"format":"potable-backup","version":1,"follows":[{"communeCode":…,
 * "udiCode":…,"communeName":…,"udiName":…}],"udiChoices":{"<commune>":"<udi>"},
 * "settings":{"theme":"system|light|dark","historyYears":3,"retentionYears":3}}`.
 * Unknown keys are ignored, so that a later version 1 may add some.
 */
object BackupCodec {
    const val FORMAT = "potable-backup"
    const val VERSION = 1

    /** A backup of even a thousand follows is far below this. */
    const val MAX_BYTES = 1 shl 20

    /** As the alerts' notification extras (`AlertIntent`): Hub'Eau's codes. */
    private val CODE = Regex("[0-9A-Za-z]{1,20}")

    /** Longer names than any commune or unit published. */
    private const val MAX_NAME = 200

    private val json = Json { prettyPrint = true }

    fun encode(backup: Backup): String = json.encodeToString(
        JsonElement.serializer(),
        buildJsonObject {
            put(FORMAT_KEY, FORMAT)
            put(VERSION_KEY, VERSION)
            put(
                FOLLOWS_KEY,
                buildJsonArray {
                    backup.follows.forEach { follow ->
                        add(
                            buildJsonObject {
                                put(COMMUNE_CODE, follow.communeCode)
                                put(UDI_CODE, follow.udiCode)
                                put(COMMUNE_NAME, follow.communeName)
                                put(UDI_NAME, follow.udiName)
                            },
                        )
                    }
                },
            )
            put(
                UDI_CHOICES_KEY,
                buildJsonObject {
                    backup.udiChoices.toSortedMap().forEach { (commune, udi) -> put(commune, udi) }
                },
            )
            put(
                SETTINGS_KEY,
                buildJsonObject {
                    backup.settings.themeMode?.let { put(THEME, it.name.lowercase(Locale.ROOT)) }
                    backup.settings.historyYears?.let { put(HISTORY_YEARS, it) }
                    backup.settings.retentionYears?.let { put(RETENTION_YEARS, it) }
                },
            )
        },
    )

    /** Reads at most [MAX_BYTES] of [input], which the caller closes. */
    fun read(input: InputStream): BackupRead {
        val bytes = input.readNBytesCompat(MAX_BYTES + 1)
        if (bytes.size > MAX_BYTES) return BackupRead.Invalid(BackupError.TOO_LARGE)
        return decode(bytes.toString(Charsets.UTF_8))
    }

    fun decode(text: String): BackupRead {
        val element = try {
            Json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            return BackupRead.Invalid(BackupError.UNREADABLE)
        }
        val root = element as? JsonObject ?: return BackupRead.Invalid(BackupError.NOT_A_BACKUP)
        if (root.string(FORMAT_KEY) != FORMAT) return BackupRead.Invalid(BackupError.NOT_A_BACKUP)
        val version = (root[VERSION_KEY] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
        if (version != VERSION) return BackupRead.Invalid(BackupError.UNSUPPORTED_VERSION)

        val entries = when (val follows = root[FOLLOWS_KEY]) {
            null -> emptyList()
            is JsonArray -> follows
            else -> return BackupRead.Invalid(BackupError.UNREADABLE)
        }
        val follows = entries.map { (it as? JsonObject)?.toFollow() }
        val choices = (root[UDI_CHOICES_KEY] as? JsonObject).orEmpty()
            .mapNotNull { (commune, udi) ->
                val code = (udi as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (CODE.matches(commune) && code != null && CODE.matches(code)) {
                    commune to code
                } else {
                    null
                }
            }
            .toMap()
        val settings = (root[SETTINGS_KEY] as? JsonObject)?.toSettings() ?: BackupSettings()
        return BackupRead.Valid(
            Backup(follows.filterNotNull(), choices, settings),
            invalid = follows.count { it == null },
        )
    }

    private fun JsonObject.toFollow(): BackupFollow? {
        val communeCode = string(COMMUNE_CODE)?.takeIf(CODE::matches) ?: return null
        val udiCode = string(UDI_CODE)?.takeIf(CODE::matches) ?: return null
        val communeName = name(COMMUNE_NAME) ?: return null
        val udiName = name(UDI_NAME) ?: return null
        return BackupFollow(communeCode, udiCode, communeName, udiName)
    }

    private fun JsonObject.name(key: String): String? =
        string(key)?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_NAME }

    private fun JsonObject.toSettings(): BackupSettings = BackupSettings(
        themeMode = string(THEME)?.let { theme ->
            ThemeMode.entries.firstOrNull { it.name.equals(theme, ignoreCase = true) }
        },
        historyYears = years(HISTORY_YEARS),
        retentionYears = years(RETENTION_YEARS),
    )

    private fun JsonObject.years(key: String): Int? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
            ?.takeIf { it in AppPreferences.YEAR_CHOICES }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    /** At most [limit] bytes (`InputStream.readNBytes` needs API 33). */
    private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private const val BUFFER = 8 * 1024
    private const val FORMAT_KEY = "format"
    private const val VERSION_KEY = "version"
    private const val FOLLOWS_KEY = "follows"
    private const val UDI_CHOICES_KEY = "udiChoices"
    private const val SETTINGS_KEY = "settings"
    private const val COMMUNE_CODE = "communeCode"
    private const val UDI_CODE = "udiCode"
    private const val COMMUNE_NAME = "communeName"
    private const val UDI_NAME = "udiName"
    private const val THEME = "theme"
    private const val HISTORY_YEARS = "historyYears"
    private const val RETENTION_YEARS = "retentionYears"
}
