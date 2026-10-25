package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.model.Compliance
import io.github.mgdx.potable.core.model.SampleSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Conclusions quoted as Hub'Eau publishes them (stray spaces included), with
 * the sample they come from; see `docs/hubeau-api.md`, "Conclusions
 * défavorables".
 */
class UnfavourableTest {
    private fun summary(
        conclusion: String?,
        bacteriologicalLimits: Compliance = Compliance.COMPLIANT,
        physicochemicalLimits: Compliance = Compliance.COMPLIANT,
        bacteriologicalReferences: Compliance = Compliance.COMPLIANT,
        physicochemicalReferences: Compliance = Compliance.COMPLIANT,
    ) = SampleSummary(
        code = "P1",
        date = Instant.parse("2026-08-26T12:50:00Z"),
        conclusion = conclusion,
        bacteriologicalLimits = bacteriologicalLimits,
        physicochemicalLimits = physicochemicalLimits,
        bacteriologicalReferences = bacteriologicalReferences,
        physicochemicalReferences = physicochemicalReferences,
    )

    private fun assertUnfavourable(conclusion: String) =
        assertTrue(conclusion, Unfavourable.isUnfavourable(summary(conclusion)))

    private fun assertNotUnfavourable(conclusion: String) =
        assertFalse(conclusion, Unfavourable.isUnfavourable(summary(conclusion)))

    // --- Published compliances ---

    @Test
    fun `any non-compliant code is unfavourable, whatever the conclusion`() {
        // 97400149395, Étang-Salé, 2026-08-24: N, C, N, C.
        val text = "Eau d'alimentation non-conforme aux exigences de qualité."
        assertTrue(
            Unfavourable.isUnfavourable(
                summary(
                    text,
                    bacteriologicalLimits = Compliance.NON_COMPLIANT,
                    bacteriologicalReferences = Compliance.NON_COMPLIANT,
                ),
            ),
        )
        // 97100139684, Le Gosier, 2026-05-05: C, C, C, N under a text that reads compliant.
        val compliantText =
            "Eau d'alimentation faiblement minéralisée conforme aux exigences de qu " +
                "alité en vigueur pour l'ensemble des paramètres mesurés"
        assertTrue(
            Unfavourable.isUnfavourable(
                summary(compliantText, physicochemicalReferences = Compliance.NON_COMPLIANT),
            ),
        )
        for (i in 0 until 4) {
            val codes = MutableList(4) { Compliance.COMPLIANT }
            codes[i] = Compliance.NON_COMPLIANT
            assertTrue(
                Unfavourable.isUnfavourable(
                    summary(null, codes[0], codes[1], codes[2], codes[3]),
                ),
            )
        }
    }

    @Test
    fun `other codes and no conclusion are not unfavourable`() {
        assertFalse(Unfavourable.isUnfavourable(summary(null)))
        assertFalse(Unfavourable.isUnfavourable(summary("")))
        for (code in Compliance.entries - Compliance.NON_COMPLIANT) {
            assertFalse(Unfavourable.isUnfavourable(summary(null, code, code, code, code)))
        }
    }

    @Test
    fun `a compliant conclusion is not unfavourable`() {
        // Orléans, the usual wording.
        assertNotUnfavourable(
            "Eau d'alimentation conforme aux exigences de qualité en vigueur pour l'ensemble " +
                "des paramètres mesurés.",
        )
    }

    // --- Non-compliance stated by the conclusion ---

    @Test
    fun `a non-compliance with the references only is unfavourable`() {
        // 06900172826, Lyon, 2025-10-22, all four codes C.
        assertUnfavourable(
            "Eau d'alimentation conforme aux limites de qualité et non conforme aux références " +
                "de qualité.",
        )
        // 02A00122406, Figari, 2026-08-25, all four codes C.
        assertUnfavourable(
            "Eau d'alimentation conforme aux limites de qualité et ne satisfaisant pas aux " +
                "références de qualité. Teneur en chlore libre élevée.",
        )
        // 03300278758, Pessac, 2026-05-12, all four codes C.
        assertUnfavourable(
            "Eau conforme aux limites réglementaires de qualité des eaux destinées à la " +
                "consommation humaine pour les paramètres mesurés mais ne satisfaisant pas aux " +
                "références de qualité pour les paramètres germes revivifiables à 36 °C . Le " +
                "prélèvement de confirmation réalisé le 20/06/2026 s’est révélé conforme pour " +
                "ces " +
                "paramètres.",
        )
    }

    @Test
    fun `a non-compliance with the limits is unfavourable, even under a derogation`() {
        // 05900342568, Saint-Benin, 2026-06-11, all four codes C.
        assertUnfavourable(
            "Eau d’alimentation non conforme aux limites de qualité en vigueur pour le paramètre " +
                "nitrates. Cette eau est déconseillée aux populations sensibles (femmes " +
                "enceintes, " +
                "nourrissons) pour les usages alimentaires.",
        )
        // 06700272440, Brumath, 2026-07-20: C, D, C, C.
        assertTrue(
            Unfavourable.isUnfavourable(
                summary(
                    "Eau d'alimentation non conforme aux limites de qualité réglementaires en " +
                        "vigueur. La concentration d’un métabolite de pesticide (chloridazone " +
                        "desphényl) est supérieure à la limite de qualité réglementaire en " +
                        "vigueur " +
                        "(0,1 µ g/L). La quantification de cette molécule dans l’eau à une " +
                        "concentration inf érieure à sa valeur dérogatoire, bien que constituant " +
                        "une non-conformi té réglementaire, ne présente pas de risque sanitaire " +
                        "pour le consomma teur. L’eau peut être consommée sans restriction.",
                    physicochemicalLimits = Compliance.COMPLIANT_UNDER_DEROGATION,
                ),
            ),
        )
    }

    // --- Consumption advised against, restricted or forbidden ---

    @Test
    fun `consumption advised against is unfavourable, even for infants only`() {
        // 05900343647, Boussières-en-Cambrésis, 2026-07-31, all four codes C.
        assertUnfavourable(
            "Cette eau d'alimentation respecte les exigences de qualité réglementaires pour les " +
                "paramètres mesurés mais ne respecte pas les recommandations en vigueur " +
                "concernant " +
                "la teneur en ions perchlorates. Sa consommation est donc déconseillée pour les " +
                "nourrissons de moins de 6 mois.",
        )
        // 00200210099, Retheuil, 2026-08-28: a stray space inside the word.
        assertUnfavourable(
            "Eau d’alimentation conforme aux limites de qualité en vigueur. Présenc e de " +
                "perchlorates >4 μg/l et <15μg/l, la consommation de l'eau est déc onseillée aux " +
                "nourrissons de moins de 6 mois. Un contrôle renforcé est mis en place sur ce " +
                "paramètre.",
        )
        // 02A00121106, Cognocoli-Monticchi, 2026-04-13.
        assertUnfavourable(
            "Eau d'alimentation conforme aux exigences de qualité en vigueur pour l'ensemble des " +
                "paramètres mesurés. Toutefois, il est à noter une teneur en nitrates proche de " +
                "la " +
                "limite de qualité. Aussi, à titre préventif il est conseillé d'interdire la " +
                "consommation de cette eau aux nourrissons et aux femmes enceintes.",
        )
    }

    @Test
    fun `a restriction or a ban in force is unfavourable`() {
        // 97100139216, Anse-Bertrand, 2026-01-23.
        assertUnfavourable(
            "Une interdiction de consommer l'eau est en cours. Des mesures de gesti on sont donc " +
                "mises en œuvre pour rétablir la situation.",
        )
        // 97100140413, Le Moule, 2026-06-26: S, C, S, C.
        assertUnfavourable(
            "L’eau est conforme au moment du prélèvement. Toutefois, la détection d e chlorate " +
                "observée sur d’autres points de surveillance de la même uni té de distribution " +
                "justifie la mise en place d’une surveillance renfor cée. Une restriction " +
                "d’usage a " +
                "été demandée, et des mesures de gestion sont actuellement mises en œuvre afin " +
                "de " +
                "rétablir la situation.",
        )
        // 05900343236, Locquignol, 2026-07-24.
        assertUnfavourable(
            "Cette eau est soumise à une restriction de consommation pour tous les usages " +
                "alimentaires (boissons, fabrication de glaçons, lavage, cuisson et préparation " +
                "des " +
                "aliments (pâtes, soupes, thé, café), alimentation des lave-vaisselles. Par " +
                "contre, " +
                "l’utilisation des chasses d’eau des sanitaires peut être maintenue.",
        )
        // 97100140548, Saint-Barthélemy, 2026-06-30: the ban is kept.
        assertUnfavourable(
            "Eau d'alimentation conforme aux exigences de qualité en vigueur pour l'ensemble des " +
                "paramètres mesurés. Ce prélèvement est un recontrôle effectué suite aux " +
                "contrôles " +
                "supplémentaires ajoutés les 22, 23 et 30 juin au contrôle sanitaire. " +
                "L'interdiction de consommation prise par la collectivité le 19 juin 2026 est " +
                "maintenue.",
        )
    }

    @Test
    fun `case and accents do not matter`() {
        assertUnfavourable("EAU NON-CONFORME AUX EXIGENCES DE QUALITÉ.")
        assertUnfavourable("Consommation DECONSEILLEE aux nourrissons.")
        assertUnfavourable("Eau d’alimentation non conforme aux references de qualite")
    }

    // --- Mentions that state nothing unfavourable about this sample ---

    @Test
    fun `a negated restriction is not unfavourable`() {
        // 04500168826, Beaulieu-sur-Loire, 2026-01-19, all four codes C.
        assertNotUnfavourable(
            "Les résultats d'analyses de ce prélèvement ont mis en évidence un dépassement de la " +
                "valeur indicative de 0,9 µg/L sur un métabolite de pesticide non pertinent, ne " +
                "nécessitant pas de restriction de consommation de l’eau d’après l’expertise de " +
                "l’Agence Nationale de Sécurité Sanitaire de l'Alimentation, de l'Environnement " +
                "et " +
                "du Travail (ANSES). Un suivi renforcé est mis en place sur ce paramètre.",
        )
        assertNotUnfavourable(
            "Eau conforme. Aucune restriction des usages de l eau n est préconisée. L'eau peut " +
                "être consommée sans restriction d'usage.",
        )
    }

    @Test
    fun `a lifted ban is not unfavourable`() {
        // 04500167918, Douchy-Montcorbon, 2025-11-05.
        assertNotUnfavourable(
            "Prélèvement réalisé dans le cadre d'une anomalie détectée suite au changement de " +
                "canalisations dans ce secteur. Les résultats d'analyses montrent la conformité " +
                "de " +
                "l'eau sur l'ensemble de ce secteur ce qui a permis de lever l'interdiction de " +
                "consommation de l'eau émise pour ce secteur.",
        )
        // 97100140700, Saint-Barthélemy, 2026-07-17.
        assertNotUnfavourable(
            "Eau d'alimentation conforme aux exigences de qualité en vigueur pour l'ensemble des " +
                "paramètres mesurés. Une demande de levée de l'interdiction de consommation " +
                "prise " +
                "par la collectivité le 19 juin 2026 a été émise le 24 juillet 2026 l'exception " +
                "de " +
                "l'hôpital et de l'EHPAD.",
        )
        assertNotUnfavourable("L'interdiction prise le 05 août 2025 a été levée.")
    }

    @Test
    fun `a non-compliance of an earlier sample is not unfavourable`() {
        // 04500169121, Mareau-aux-Bois, 2026-02-10.
        assertNotUnfavourable(
            "Ce prélèvement fait suite à une non conformité constatée le 30/01/2026. Il montre " +
                "le " +
                "retour à une eau de qualité bactériologique conforme.",
        )
        // 07600308543, Fontenay, 2026-08-13.
        assertNotUnfavourable(
            "Eau Conforme aux exigences de qualité sur le prélèvement réalisé. Il s'agit d'un " +
                "nouveau contrôle effectué après une non conformité bactériologique.",
        )
        // 97100139384, Gourbeyre, 2026-02-24.
        assertNotUnfavourable(
            "Eau d'alimentation conforme aux exigences de qualité en vigueur pour l'ensemble des " +
                "paramètres mesurés. On note cependant un taux excessif de chlore libre. Ce " +
                "prélèvement est un recontrôle effectué suite à une contamination " +
                "bactériologique " +
                "observée sur le réseau et ayant entrainé une demande d'interdiction de " +
                "consommation d'eau destinée à la consommation humaine le 24 février 2026. Au " +
                "regard des présents résultats, une demande de levée de l'interdiction a été " +
                "émise " +
                "le 02 mars 2026.",
        )
    }

    @Test
    fun `a conditional advice is not unfavourable`() {
        // 02200246415, Plélo, 2026-08-19: S, C, S, C.
        assertNotUnfavourable(
            "Les analyses réalisées ont mis en évidence la présence notable de chlorates. La " +
                "valeur la plus faible possible de ce paramètre doit être visée sans pour autant " +
                "compromettre la désinfection. La réglementation fixe une limite de qualité à " +
                "700 µg/l lorsqu'une méthode de désinfection des eaux destinées à la " +
                "consommation " +
                "humaine qui génère des chlorates est utilisée. Au delà de cette valeur, il est " +
                "recommandé de ne pas consommer cette eau, une restriction d'usage est nécessaire.",
        )
    }
}
