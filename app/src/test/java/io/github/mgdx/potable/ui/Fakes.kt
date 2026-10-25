package io.github.mgdx.potable.ui

import io.github.mgdx.potable.core.model.Commune
import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.core.model.Sample
import io.github.mgdx.potable.core.model.Udi
import io.github.mgdx.potable.core.model.UdiChoice
import io.github.mgdx.potable.data.CachedSample
import io.github.mgdx.potable.data.CommuneRepository
import io.github.mgdx.potable.data.WaterRepository
import io.github.mgdx.potable.data.follow.Follow
import io.github.mgdx.potable.data.follow.FollowRepository
import io.github.mgdx.potable.data.preferences.AppPreferences
import io.github.mgdx.potable.data.preferences.RememberedUdi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.Instant

/** Routes `Dispatchers.Main`, hence `viewModelScope`, to a test dispatcher. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) :
    TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

class FakeCommuneRepository(private val communes: List<Commune>) : CommuneRepository {
    val queries = mutableListOf<String>()

    override suspend fun search(query: String, limit: Int): List<Commune> {
        queries += query
        return communes.filter { it.name.contains(query, ignoreCase = true) }.take(limit)
    }

    override suspend fun nearest(
        latitude: Double,
        longitude: Double,
        maxDistanceKm: Double?,
    ): Commune? = null
}

/** Answers each call with the next queued outcome: a value or an exception to throw. */
class FakeWaterRepository : WaterRepository {
    val udiAnswers = ArrayDeque<Result<UdiChoice>>()
    val sampleAnswers = ArrayDeque<Result<Sample?>>()
    val sampleCalls = mutableListOf<Pair<String, String?>>()

    /** The cache of the followed unit, as [cachedLatestSample] emits it. */
    val cache = MutableStateFlow<CachedSample?>(null)

    /** Outcome of each refresh; a success when none is queued. */
    val refreshAnswers = ArrayDeque<Result<Unit>>()

    /** The `force` argument of each refresh. */
    val refreshCalls = mutableListOf<Boolean>()

    /** What a successful refresh puts in [cache], if anything. */
    var refreshedCache: CachedSample? = null

    override fun cachedLatestSample(communeCode: String, udiCode: String): Flow<CachedSample?> =
        cache

    override suspend fun refreshFollowed(
        communeCode: String,
        udiCode: String,
        force: Boolean,
    ): Boolean {
        refreshCalls += force
        (refreshAnswers.removeFirstOrNull() ?: Result.success(Unit)).getOrThrow()
        refreshedCache?.let { cache.value = it }
        return true
    }

    override suspend fun udis(communeCode: String): UdiChoice =
        udiAnswers.removeFirst().getOrThrow()

    override suspend fun latestSample(communeCode: String, networkCode: String?): Sample? {
        sampleCalls += communeCode to networkCode
        return sampleAnswers.removeFirst().getOrThrow()
    }
}

/** Follows kept in memory, with the semantics of the contract. */
class FakeFollowRepository(initial: List<Follow> = emptyList()) : FollowRepository {
    private val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    override val follows: Flow<List<Follow>> = state

    val current: List<Follow> get() = state.value

    override suspend fun follow(
        communeCode: String,
        communeName: String,
        udiCode: String,
        udiName: String,
    ): Follow {
        find(communeCode, udiCode)?.let { return it }
        val follow = Follow(
            id = nextId++,
            communeCode = communeCode,
            communeName = communeName,
            udiCode = udiCode,
            udiName = udiName,
            position = state.value.size,
            isPrimary = state.value.isEmpty(),
        )
        state.value = state.value + follow
        return follow
    }

    override suspend fun unfollow(id: Long) {
        val remaining = state.value.filter { it.id != id }
        val fixed = if (remaining.isNotEmpty() && remaining.none { it.isPrimary }) {
            listOf(remaining.first().copy(isPrimary = true)) + remaining.drop(1)
        } else {
            remaining
        }
        state.value = fixed.renumbered()
    }

    override suspend fun move(id: Long, newPosition: Int) {
        val list = state.value.toMutableList()
        val follow = list.firstOrNull { it.id == id } ?: return
        list.remove(follow)
        list.add(newPosition.coerceIn(0, list.size), follow)
        state.value = list.renumbered()
    }

    override suspend fun setPrimary(id: Long) {
        state.value = state.value.map { it.copy(isPrimary = it.id == id) }
    }

    override suspend fun find(communeCode: String, udiCode: String): Follow? =
        state.value.firstOrNull { it.communeCode == communeCode && it.udiCode == udiCode }

    private fun List<Follow>.renumbered() = mapIndexed { index, it -> it.copy(position = index) }
}

class FakeAppPreferences(
    remembered: Map<String, String> = emptyMap(),
    rememberedNames: Map<String, String> = emptyMap(),
) : AppPreferences {
    val remembered = remembered.toMutableMap()

    /** Names of the units in [remembered], when stored with one. */
    val rememberedNames = rememberedNames.toMutableMap()

    private val writes = MutableStateFlow(0)

    override val rememberedUdis: Flow<Map<String, RememberedUdi>> = writes.map {
        this.remembered.mapValues { (commune, code) ->
            RememberedUdi(code, this.rememberedNames[commune])
        }
    }

    override val disclaimerAccepted: Flow<Boolean> = MutableStateFlow(true)

    override suspend fun acceptDisclaimer() = Unit

    override suspend fun rememberedUdi(communeCode: String): String? = remembered[communeCode]

    override suspend fun rememberUdi(communeCode: String, udiCode: String) {
        remembered[communeCode] = udiCode
        rememberedNames.remove(communeCode)
        writes.value++
    }

    override suspend fun rememberUdi(communeCode: String, udiCode: String, udiName: String) {
        remembered[communeCode] = udiCode
        rememberedNames[communeCode] = udiName
        writes.value++
    }
}

fun follow(
    id: Long,
    communeCode: String = "45234",
    udiCode: String = "045000123",
    position: Int = (id - 1).toInt(),
    isPrimary: Boolean = false,
) = Follow(id, communeCode, "Commune $communeCode", udiCode, "UDI $udiCode", position, isPrimary)

fun commune(code: String, name: String) =
    Commune(code, name, listOf("45000"), latitude = 47.9, longitude = 1.9)

fun udi(code: String, name: String = "UDI $code") = Udi(code, name, district = null, year = 2026)

fun sample(date: Instant = Instant.parse("2026-10-01T08:00:00Z")) = Sample(
    code = "00123",
    communeCode = "45234",
    networkCode = "045000123",
    date = date,
    conclusion = "Eau d'alimentation conforme aux exigences de qualité en vigueur.",
    bacteriologicalLimits = Compliance.COMPLIANT,
    physicochemicalLimits = Compliance.COMPLIANT,
    bacteriologicalReferences = Compliance.NON_COMPLIANT,
    physicochemicalReferences = Compliance.UNKNOWN,
    distributor = null,
    owner = null,
    results = emptyList(),
)
