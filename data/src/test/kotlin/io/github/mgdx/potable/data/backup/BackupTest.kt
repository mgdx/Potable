package io.github.mgdx.potable.data.backup

import io.github.mgdx.potable.data.follow.Follow
import io.github.mgdx.potable.data.follow.FollowRepository
import io.github.mgdx.potable.data.preferences.AppPreferences
import io.github.mgdx.potable.data.preferences.RememberedUdi
import io.github.mgdx.potable.data.preferences.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class BackupTest {
    private class FakeFollows(initial: List<Follow> = emptyList()) : FollowRepository {
        val state = MutableStateFlow(initial)
        private var nextId = 100L

        override val follows: Flow<List<Follow>> = state

        override suspend fun follow(
            communeCode: String,
            communeName: String,
            udiCode: String,
            udiName: String,
        ): Follow = find(communeCode, udiCode)
            ?: Follow(nextId++, communeCode, communeName, udiCode, udiName)
                .also { state.value = state.value + it }

        override suspend fun unfollow(id: Long) {
            state.value = state.value.filter { it.id != id }
        }

        override suspend fun find(communeCode: String, udiCode: String): Follow? =
            state.value.firstOrNull { it.communeCode == communeCode && it.udiCode == udiCode }
    }

    private class FakePreferences : AppPreferences {
        override val disclaimerAccepted: Flow<Boolean> = flowOf(true)

        override suspend fun acceptDisclaimer() = Unit

        override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
        override val historyYears = MutableStateFlow(AppPreferences.DEFAULT_YEARS)
        override val retentionYears = MutableStateFlow(AppPreferences.DEFAULT_YEARS)
        val udis = MutableStateFlow<Map<String, RememberedUdi>>(emptyMap())

        override suspend fun setThemeMode(mode: ThemeMode) {
            themeMode.value = mode
        }

        override suspend fun setHistoryYears(years: Int) {
            historyYears.value = years
        }

        override suspend fun setRetentionYears(years: Int) {
            retentionYears.value = years
        }

        override val rememberedUdis: Flow<Map<String, RememberedUdi>> = udis.map { it }

        override suspend fun rememberedUdi(communeCode: String): String? =
            udis.value[communeCode]?.code

        override suspend fun rememberUdi(communeCode: String, udiCode: String) {
            udis.value = udis.value + (communeCode to RememberedUdi(udiCode, null))
        }

        override suspend fun rememberUdi(communeCode: String, udiCode: String, udiName: String) {
            udis.value = udis.value + (communeCode to RememberedUdi(udiCode, udiName))
        }
    }

    private val orleans = Follow(1, "45234", "Orléans", "045000474", "ORLEANS")
    private val paris = Follow(2, "75056", "Paris", "075000123", "PARIS CENTRE")
    private val baselines = mutableListOf<Follow>()
    private var retentionPurges = 0

    private fun backup(follows: FakeFollows, preferences: FakePreferences) =
        FollowsBackup(follows, preferences, { baselines += it }, { retentionPurges++ })

    private fun valid(text: String) = BackupCodec.decode(text) as BackupRead.Valid

    @Test
    fun `an export reads back as it was, on another phone`() = runBlocking {
        val preferences = FakePreferences().apply {
            themeMode.value = ThemeMode.DARK
            historyYears.value = 5
            retentionYears.value = 10
            rememberUdi("45234", "045000474", "ORLEANS")
            // Looked at without being followed: not exported.
            rememberUdi("13055", "013000999", "MARSEILLE")
        }
        val exported = backup(FakeFollows(listOf(orleans, paris)), preferences).export()
        val text = BackupCodec.encode(exported)
        assertTrue(text.contains("\"format\": \"potable-backup\""))

        val read = valid(text)
        assertEquals(0, read.invalid)
        assertEquals(mapOf("45234" to "045000474"), read.backup.udiChoices)
        assertEquals(BackupSettings(ThemeMode.DARK, 5, 10), read.backup.settings)

        val follows = FakeFollows()
        val target = FakePreferences()
        val summary = backup(follows, target).import(read.backup)
        assertEquals(ImportSummary(2, 0, 0, follows.state.value), summary)
        assertEquals(
            listOf(orleans, paris).map { it.copy(id = 0) },
            follows.state.value.map { it.copy(id = 0) },
        )
        assertEquals(RememberedUdi("045000474", "ORLEANS"), target.udis.value["45234"])
        assertEquals(ThemeMode.DARK, target.themeMode.value)
        assertEquals(5, target.historyYears.value)
        assertEquals(10, target.retentionYears.value)
        assertEquals(1, retentionPurges)
    }

    @Test
    fun `the import merges, existing follows stay and duplicates count once`() = runBlocking {
        val follows = FakeFollows(listOf(orleans))
        val preferences = FakePreferences().apply { rememberUdi("45234", "045000999", "OTHER") }
        val read = valid(
            """
            {"format":"potable-backup","version":1,"follows":[
              {"communeCode":"45234","udiCode":"045000474","communeName":"Orléans","udiName":"ORLEANS"},
              {"communeCode":"75056","udiCode":"075000123","communeName":"Paris","udiName":"PARIS CENTRE"},
              {"communeCode":"75056","udiCode":"075000123","communeName":"Paris","udiName":"PARIS CENTRE"}
            ],"udiChoices":{"45234":"045000474"}}
            """,
        )
        val sut = backup(follows, preferences)
        val summary = sut.import(read.backup, read.invalid)

        assertEquals(1, summary.imported)
        assertEquals(1, summary.alreadyFollowed)
        assertEquals(2, follows.state.value.size)
        assertEquals(orleans, follows.state.value.first())
        // A unit already remembered for the commune is kept.
        assertEquals("045000999", preferences.udis.value["45234"]?.code)
        // Settings absent: unchanged, no purge.
        assertEquals(ThemeMode.SYSTEM, preferences.themeMode.value)
        assertEquals(0, retentionPurges)

        // The baseline is set for the new follows only, as from a sheet.
        sut.setBaselines(summary.added)
        assertEquals(listOf("075000123"), baselines.map { it.udiCode })
    }

    @Test
    fun `invalid codes and names are left out and counted`() {
        val read = valid(
            """
            {"format":"potable-backup","version":1,"follows":[
              {"communeCode":"45234","udiCode":"045000474","communeName":"Orléans","udiName":"ORLEANS"},
              {"communeCode":"../etc","udiCode":"045000474","communeName":"X","udiName":"Y"},
              {"communeCode":"45234","udiCode":"0450004740450004740450","communeName":"X","udiName":"Y"},
              {"communeCode":"45234","udiCode":"045000475","communeName":"  ","udiName":"Y"},
              {"communeCode":45234,"udiCode":"045000476","communeName":"X","udiName":"Y"},
              "not an object"
            ],
            "udiChoices":{"45234":"045000474","bad code":"045000474","75056":"a b"},
            "settings":{"theme":"purple","historyYears":7,"retentionYears":5}}
            """,
        )
        assertEquals(listOf("045000474"), read.backup.follows.map { it.udiCode })
        assertEquals(5, read.invalid)
        assertEquals(mapOf("45234" to "045000474"), read.backup.udiChoices)
        assertEquals(BackupSettings(retentionYears = 5), read.backup.settings)
    }

    @Test
    fun `a file that is not a backup is refused`() {
        listOf("", "not json", "{", "[1,2]").forEach {
            val error = (BackupCodec.decode(it) as BackupRead.Invalid).error
            assertTrue(it, error == BackupError.UNREADABLE || error == BackupError.NOT_A_BACKUP)
        }
        listOf(
            """{"version":1,"follows":[]}""",
            """{"format":"other-app","version":1,"follows":[]}""",
            """"potable-backup"""",
        ).forEach {
            assertEquals(it, BackupRead.Invalid(BackupError.NOT_A_BACKUP), BackupCodec.decode(it))
        }
        assertEquals(
            BackupRead.Invalid(BackupError.UNREADABLE),
            BackupCodec.decode("""{"format":"potable-backup","version":1,"follows":{}}"""),
        )
    }

    @Test
    fun `an unknown version is refused`() {
        listOf("2", "0", "\"1\"", "1.5", "null").forEach {
            assertEquals(
                it,
                BackupRead.Invalid(BackupError.UNSUPPORTED_VERSION),
                BackupCodec.decode("""{"format":"potable-backup","version":$it,"follows":[]}"""),
            )
        }
        assertEquals(
            BackupRead.Invalid(BackupError.UNSUPPORTED_VERSION),
            BackupCodec.decode("""{"format":"potable-backup","follows":[]}"""),
        )
    }

    @Test
    fun `a file larger than the bound is not read`() {
        val small = """{"format":"potable-backup","version":1,"follows":[]}"""
        assertTrue(BackupCodec.read(ByteArrayInputStream(small.toByteArray())) is BackupRead.Valid)
        val padded = small.dropLast(1) + ",\"pad\":\"" + "x".repeat(BackupCodec.MAX_BYTES) + "\"}"
        assertEquals(
            BackupRead.Invalid(BackupError.TOO_LARGE),
            BackupCodec.read(ByteArrayInputStream(padded.toByteArray())),
        )
    }
}
