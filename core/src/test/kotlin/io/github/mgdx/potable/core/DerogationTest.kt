package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.core.model.Sample
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Conclusions quoted as Hub'Eau publishes them, with the sample they come
 * from; see `docs/hubeau-api.md`, "Dérogations".
 */
class DerogationTest {
    private fun sample(
        conclusion: String?,
        physicochemicalLimits: Compliance = Compliance.COMPLIANT,
        bacteriologicalReferences: Compliance = Compliance.COMPLIANT,
    ) = Sample(
        code = "P1",
        communeCode = "77095",
        networkCode = null,
        date = Instant.parse("2026-06-15T09:00:00Z"),
        conclusion = conclusion,
        bacteriologicalLimits = Compliance.COMPLIANT,
        physicochemicalLimits = physicochemicalLimits,
        bacteriologicalReferences = bacteriologicalReferences,
        physicochemicalReferences = Compliance.COMPLIANT,
        distributor = null,
        owner = null,
        results = emptyList(),
    )

    @Test
    fun `a D code concerns a derogation, whatever the conclusion`() {
        // 05100146939, Haussimont (51), 2026-08-31: C, D, C, C; the
        // conclusion does not say "dérogation".
        val text = "L'eau distribuée présente des concentrations en pesticides supérieures aux " +
            "exigences réglementaires fixées à 0,1 µg/l, mais inférieures aux valeurs définies " +
            "pour en limiter les usages sanitaires. Un contrôle renforcé est en place et des " +
            "actions sont engagées par l’exploitant pour restaurer la qualité de l’eau."
        assertFalse(Derogation.mentionedIn(text))
        assertTrue(Derogation.concerns(sample(text, Compliance.COMPLIANT_UNDER_DEROGATION)))
        assertTrue(
            Derogation.concerns(
                sample(null, bacteriologicalReferences = Compliance.COMPLIANT_UNDER_DEROGATION),
            ),
        )
    }

    @Test
    fun `the conclusion of a sample under derogation mentions it`() {
        // 07700262150, Charny (77), 2026-06-15: C, D, C, C.
        val text = "Eau d’alimentation conforme aux valeurs dérogatoires définies dans l’arrêté " +
            "préfectoral n°26 ARS 12 SE du 17 mars 2026 autorisant la PRPDE à déroger " +
            "temporairement aux limites de qualité pour les paramètres chlorothalonil R417888, " +
            "chloridazone desphényl, chloridazone méthyl desphényl et la somme totale des " +
            "pesticides. La PRPDE s’est engagée à mettre en place un plan d’actions sur la durée " +
            "de la dérogation afin de retrouver une eau conforme aux limites de qualité."
        assertTrue(Derogation.mentionedIn(text))
    }

    @Test
    fun `a conclusion may mention a derogation under N codes only`() {
        // 07700261861, Chamigny (77), 2026-06-25: C, N, C, C.
        val text = "Eau d'alimentation non conforme aux limites de qualité mais conforme aux " +
            "valeurs dérogatoires pour les paramètres chloridazone desphényl et chloridazone " +
            "méthyl desphényl sans dépassement des seuils sanitaires."
        assertTrue(Derogation.concerns(sample(text, Compliance.NON_COMPLIANT)))
    }

    @Test
    fun `case, accents and stray spaces do not matter`() {
        assertTrue(Derogation.mentionedIn("EAU CONFORME AUX VALEURS DÉROGATOIRES."))
        assertTrue(Derogation.mentionedIn("dans le cadre d'une derogation."))
        assertTrue(Derogation.mentionedIn("sur la durée de la déroga tion afin de"))
        assertTrue(Derogation.mentionedIn("Plusieurs Dérogations sont en cours."))
    }

    @Test
    fun `other words are not a derogation`() {
        assertFalse(Derogation.mentionedIn(null))
        assertFalse(Derogation.mentionedIn("   "))
        assertFalse(Derogation.mentionedIn("Eau d'alimentation conforme aux limites de qualité."))
        // A place name after "de" is not "déroger".
        assertFalse(Derogation.mentionedIn("Prélèvement au réservoir de Rogerville."))
        assertFalse(
            Derogation.concerns(sample("Eau d'alimentation conforme.", Compliance.NON_COMPLIANT)),
        )
    }
}
