package io.github.mgdx.potable.data.backup

import io.github.mgdx.potable.data.follow.Follow
import io.github.mgdx.potable.data.follow.FollowRepository
import io.github.mgdx.potable.data.preferences.AppPreferences
import kotlinx.coroutines.flow.first
import kotlin.coroutines.cancellation.CancellationException

/**
 * What an import did: [imported] units newly followed, [alreadyFollowed]
 * ones left as they were, [invalid] entries of the file left out. [added]
 * are the new follows, whose alert baseline is still to be set
 * ([FollowsBackup.setBaselines]).
 */
data class ImportSummary(
    val imported: Int,
    val alreadyFollowed: Int,
    val invalid: Int,
    val added: List<Follow> = emptyList(),
)

/**
 * Export and import of the follows (the application keeps no system backup:
 * `allowBackup="false"`). Nothing is asked of the network here; [baseline]
 * is the alert checker's, as when a unit is followed from its sheet.
 * [applyRetention] purges the cache after a retention read from a file.
 */
class FollowsBackup(
    private val follows: FollowRepository,
    private val preferences: AppPreferences,
    private val baseline: suspend (Follow) -> Unit,
    private val applyRetention: suspend () -> Unit = {},
) {
    /**
     * The backup of the follows, of the unit chosen for each followed
     * commune (not for the others, looked at without being followed) and of
     * the display settings.
     */
    suspend fun export(): Backup {
        val followed = follows.follows.first()
        val communes = followed.mapTo(HashSet()) { it.communeCode }
        return Backup(
            follows = followed.map {
                BackupFollow(it.communeCode, it.udiCode, it.communeName, it.udiName)
            },
            udiChoices = preferences.rememberedUdis.first()
                .filterKeys { it in communes }
                .mapValues { it.value.code },
            settings = BackupSettings(
                themeMode = preferences.themeMode.first(),
                historyYears = preferences.historyYears.first(),
                retentionYears = preferences.retentionYears.first(),
            ),
        )
    }

    /**
     * Adds the follows of [backup] to the existing ones, which are never
     * removed nor changed; a unit already followed, or twice in the file, is
     * followed once. The unit chosen for a commune is only taken when none
     * is remembered; the settings of the file replace the current ones.
     */
    suspend fun import(backup: Backup, invalid: Int = 0): ImportSummary {
        val existing = follows.follows.first().mapTo(HashSet()) { it.communeCode to it.udiCode }
        val wanted = backup.follows.distinctBy { it.communeCode to it.udiCode }
        val added = wanted
            .filter { (it.communeCode to it.udiCode) !in existing }
            .map { follows.follow(it.communeCode, it.communeName, it.udiCode, it.udiName) }

        for ((communeCode, udiCode) in backup.udiChoices) {
            if (preferences.rememberedUdi(communeCode) != null) continue
            val name = backup.follows
                .firstOrNull { it.communeCode == communeCode && it.udiCode == udiCode }
                ?.udiName
            if (name != null) {
                preferences.rememberUdi(communeCode, udiCode, name)
            } else {
                preferences.rememberUdi(communeCode, udiCode)
            }
        }

        val settings = backup.settings
        settings.themeMode?.let { preferences.setThemeMode(it) }
        settings.historyYears?.let { preferences.setHistoryYears(it) }
        settings.retentionYears?.let { years ->
            if (years != preferences.retentionYears.first()) {
                preferences.setRetentionYears(years)
                applyRetention()
            }
        }
        return ImportSummary(
            imported = added.size,
            alreadyFollowed = wanted.size - added.size,
            invalid = invalid,
            added = added,
        )
    }

    /**
     * Sets the alert baseline of each of [added], one after the other, as
     * following from a sheet does. Best effort: a failure leaves the unit to
     * its first check.
     */
    suspend fun setBaselines(added: List<Follow>) {
        for (follow in added) {
            try {
                baseline(follow)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Its first check records it.
            }
        }
    }
}
