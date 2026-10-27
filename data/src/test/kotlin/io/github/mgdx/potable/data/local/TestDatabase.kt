package io.github.mgdx.potable.data.local

import androidx.room.Room
import io.github.mgdx.potable.core.model.AnalysisResult
import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.core.model.Sample
import org.robolectric.RuntimeEnvironment
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A fresh in-memory database; SQLite comes from Robolectric. */
internal fun inMemoryDatabase(): PotableDatabase = Room
    .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PotableDatabase::class.java)
    .allowMainThreadQueries()
    .build()

/** A clock the test moves by hand. */
internal class MutableClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = now
}

internal fun sample(code: String, date: String, communeCode: String = "45234", results: Int = 2) =
    Sample(
        code = code,
        communeCode = communeCode,
        networkCode = "045000474",
        date = Instant.parse(date),
        conclusion = "Eau conforme.",
        bacteriologicalLimits = Compliance.COMPLIANT,
        physicochemicalLimits = Compliance.COMPLIANT_UNDER_DEROGATION,
        bacteriologicalReferences = Compliance.NOT_ASSESSED,
        physicochemicalReferences = Compliance.UNKNOWN,
        distributor = "DISTRIBUTEUR",
        owner = "MOA",
        results = List(results) {
            AnalysisResult(
                parameterCode = "${1000 + it}",
                parameterLabel = "Paramètre $it",
                parameterTypeCode = "N",
                resultText = "1,$it",
                resultNumber = 1.0 + it / 10.0,
                unit = "mg/L",
                qualityLimit = if (it == 0) "<=50 mg/L" else null,
                qualityReference = null,
                analysisPlace = if (it == 0) "T" else "L",
            )
        },
        upstreamFacility = "USINE",
    )
