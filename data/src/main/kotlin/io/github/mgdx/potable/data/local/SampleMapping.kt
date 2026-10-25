package io.github.mgdx.potable.data.local

import io.github.mgdx.potable.core.model.AnalysisResult
import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.core.model.Sample
import java.time.Instant

/*
 * Conversions between the cache rows and the `:core` model. The model holds
 * compliances as [Compliance]; the cache stores the matching Hub'Eau code, and
 * [Compliance.fromCode] reads it back, so the round trip is exact.
 */

/** Inverse of [Compliance.fromCode]; [Compliance.UNKNOWN] is stored as null. */
internal fun Compliance.toCode(): String? = when (this) {
    Compliance.COMPLIANT -> "C"
    Compliance.COMPLIANT_UNDER_DEROGATION -> "D"
    Compliance.NON_COMPLIANT -> "N"
    Compliance.NOT_ASSESSED -> "S"
    Compliance.UNKNOWN -> null
}

internal fun Sample.toEntity(communeCode: String, udiCode: String, latest: Boolean = true) =
    SampleEntity(
        communeCode = communeCode,
        udiCode = udiCode,
        code = code,
        sampleNetworkCode = networkCode,
        date = date.toEpochMilli(),
        conclusion = conclusion,
        bacteriologicalLimits = bacteriologicalLimits.toCode(),
        physicochemicalLimits = physicochemicalLimits.toCode(),
        bacteriologicalReferences = bacteriologicalReferences.toCode(),
        physicochemicalReferences = physicochemicalReferences.toCode(),
        distributeur = distributor,
        owner = owner,
        latest = latest,
    )

internal fun Sample.resultEntities(communeCode: String, udiCode: String) =
    results.mapIndexed { index, result ->
        ResultEntity(
            communeCode = communeCode,
            udiCode = udiCode,
            sampleCode = code,
            ordre = index,
            parameterCode = result.parameterCode,
            libelle = result.parameterLabel,
            parameterTypeCode = result.parameterTypeCode,
            resultText = result.resultText,
            resultNumber = result.resultNumber,
            unite = result.unit,
            limite = result.qualityLimit,
            reference = result.qualityReference,
        )
    }

internal fun SampleEntity.toSample(results: List<ResultEntity>) = Sample(
    code = code,
    communeCode = communeCode,
    networkCode = sampleNetworkCode,
    date = Instant.ofEpochMilli(date),
    conclusion = conclusion,
    bacteriologicalLimits = Compliance.fromCode(bacteriologicalLimits),
    physicochemicalLimits = Compliance.fromCode(physicochemicalLimits),
    bacteriologicalReferences = Compliance.fromCode(bacteriologicalReferences),
    physicochemicalReferences = Compliance.fromCode(physicochemicalReferences),
    distributor = distributeur,
    owner = owner,
    results = results.map {
        AnalysisResult(
            parameterCode = it.parameterCode,
            parameterLabel = it.libelle,
            parameterTypeCode = it.parameterTypeCode,
            resultText = it.resultText,
            resultNumber = it.resultNumber,
            unit = it.unite,
            qualityLimit = it.limite,
            qualityReference = it.reference,
        )
    },
)
