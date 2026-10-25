package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.hubeau.HubEauParser
import io.github.mgdx.potable.core.model.AnalysisResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ParameterFamiliesTest {
    private val latest = Samples.group(
        HubEauParser.parseResultats(Fixtures.hubeau("resultats_dis_45234.json")).data,
    ).first()

    /** Family of each result of the latest Orléans sample, by `code_parametre`. */
    private val orleans = latest.results.associate { it.parameterCode to ParameterFamilies.of(it) }

    /** Labels and units as published by Hub'Eau (probe of 2026-10-25). */
    private fun familyOf(label: String, unit: String?) = ParameterFamilies.of(
        AnalysisResult("0", label, "N", null, null, unit, null, null),
    )

    @Test
    fun `every result of the Orleans sample falls in its family`() {
        val expected = mapOf(
            "1449" to ParameterFamily.MICROBIOLOGY, // Escherichia coli /100ml - MF
            "1447" to ParameterFamily.MICROBIOLOGY, // Bactéries coliformes /100ml-MS
            "6455" to ParameterFamily.MICROBIOLOGY, // Entérocoques /100ml-MS
            "5440" to ParameterFamily.MICROBIOLOGY, // Bact. aér. revivifiables à 22°-68h
            "5441" to ParameterFamily.MICROBIOLOGY,
            "1398" to ParameterFamily.DISINFECTION, // Chlore libre
            "1399" to ParameterFamily.DISINFECTION, // Chlore total
            "1754" to ParameterFamily.DISINFECTION, // Bioxyde de chlore mg/L ClO2
            "1393" to ParameterFamily.METALS, // Fer total, µg/L
            "1394" to ParameterFamily.METALS, // Manganèse total, µg/L
            "1340" to ParameterFamily.PHYSICOCHEMISTRY, // Nitrates (en NO3)
            "1339" to ParameterFamily.PHYSICOCHEMISTRY,
            "6374" to ParameterFamily.PHYSICOCHEMISTRY,
            "7972" to ParameterFamily.PHYSICOCHEMISTRY, // Titre hydrotimétrique
            "1347" to ParameterFamily.PHYSICOCHEMISTRY,
            "1302" to ParameterFamily.PHYSICOCHEMISTRY, // pH
            "1337" to ParameterFamily.PHYSICOCHEMISTRY, // Chlorures: not chlorine
            "1338" to ParameterFamily.PHYSICOCHEMISTRY,
            "1335" to ParameterFamily.PHYSICOCHEMISTRY,
            "1841" to ParameterFamily.PHYSICOCHEMISTRY, // Carbone organique total
            "1309" to ParameterFamily.PHYSICOCHEMISTRY,
            "1301" to ParameterFamily.PHYSICOCHEMISTRY,
            "1295" to ParameterFamily.PHYSICOCHEMISTRY,
            "1303" to ParameterFamily.PHYSICOCHEMISTRY,
            "5900" to ParameterFamily.PHYSICOCHEMISTRY, // Couleur (qualitatif), SANS OBJET
            "5902" to ParameterFamily.PHYSICOCHEMISTRY,
            "6489" to ParameterFamily.PHYSICOCHEMISTRY,
        )
        assertEquals(expected, orleans)
    }

    @Test
    fun `becquerels and sieverts are radioactivity, before metals`() {
        assertEquals(ParameterFamily.RADIOACTIVITY, familyOf("Activité Tritium (3H)", "Bq/L"))
        assertEquals(ParameterFamily.RADIOACTIVITY, familyOf("Activité Plomb 210", "Bq/L"))
        assertEquals(ParameterFamily.RADIOACTIVITY, familyOf("Dose indicative", "mSv/a"))
        assertEquals(ParameterFamily.METALS, familyOf("Plomb", "µg/L"))
        assertEquals(ParameterFamily.METALS, familyOf("Uranium en µg/l", "µg/L"))
    }

    @Test
    fun `organisms are microbiology, by unit or by label`() {
        assertEquals(ParameterFamily.MICROBIOLOGY, familyOf("Legionella sp", "UFC/L"))
        assertEquals(
            ParameterFamily.MICROBIOLOGY,
            familyOf("Kystes totaux giardia sp/10L", "n/(10L)"),
        )
        assertEquals(
            ParameterFamily.MICROBIOLOGY,
            familyOf("Pseudomonas aéruginosa par 100ml", null),
        )
    }

    @Test
    fun `disinfection by-products are disinfection, not organic compounds`() {
        assertEquals(
            ParameterFamily.DISINFECTION,
            familyOf("Trihalométhanes (4 substances)", "µg/L"),
        )
        assertEquals(ParameterFamily.DISINFECTION, familyOf("Chloroforme", "µg/L"))
        assertEquals(ParameterFamily.DISINFECTION, familyOf("Acides haloacétiques", "µg/L"))
        assertEquals(ParameterFamily.DISINFECTION, familyOf("Bromates", "µg/L"))
        assertEquals(ParameterFamily.DISINFECTION, familyOf("Chlorite en  mg/L", "mg/L"))
    }

    @Test
    fun `solvents, PAH and PFAS are organic compounds`() {
        assertEquals(ParameterFamily.ORGANIC_COMPOUNDS, familyOf("Trichloroéthylène", "µg/L"))
        assertEquals(ParameterFamily.ORGANIC_COMPOUNDS, familyOf("Benzo(a)pyrène *", "µg/L"))
        assertEquals(
            ParameterFamily.ORGANIC_COMPOUNDS,
            familyOf("Acide sulfonique de perfluorooctane (PFOS)", "µg/L"),
        )
        assertEquals(
            ParameterFamily.ORGANIC_COMPOUNDS,
            familyOf("Chlorure de vinyl monomère", "µg/L"),
        )
        assertEquals(ParameterFamily.ORGANIC_COMPOUNDS, familyOf("17b-estradiol", "ng/L"))
    }

    @Test
    fun `the other substances measured in micrograms are pesticides`() {
        assertEquals(ParameterFamily.PESTICIDES, familyOf("Glyphosate", "µg/L"))
        assertEquals(ParameterFamily.PESTICIDES, familyOf("ESA metolachlore", "µg/L"))
        assertEquals(ParameterFamily.PESTICIDES, familyOf("Total des pesticides analysés", "µg/L"))
        // A herbicide whose name starts like a polycyclic aromatic hydrocarbon.
        assertEquals(ParameterFamily.PESTICIDES, familyOf("Benzobicyclon", "µg/L"))
        // Ethylene thiourea, a metabolite, is not a solvent ending in "-éthylène".
        assertEquals(ParameterFamily.PESTICIDES, familyOf("Ethylenethiouree", "µg/L"))
    }

    @Test
    fun `inorganic traces are physico-chemistry, non-substances are other`() {
        assertEquals(ParameterFamily.PHYSICOCHEMISTRY, familyOf("Perchlorate", "µg/L"))
        assertEquals(ParameterFamily.OTHER, familyOf("Prélèvement sous acréditation", "SANS OBJET"))
        assertEquals(
            ParameterFamily.OTHER,
            familyOf("Total des microcystines analysées - test ELISA", "µg/L"),
        )
    }

    @Test
    fun `a result without unit nor known label is other, never lost`() {
        assertEquals(ParameterFamily.OTHER, familyOf("Paramètre inconnu", null))
        assertEquals(ParameterFamily.OTHER, familyOf("Paramètre inconnu", "  "))
    }
}
