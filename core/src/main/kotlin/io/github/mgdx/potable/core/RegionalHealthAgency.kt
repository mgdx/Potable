package io.github.mgdx.potable.core

/**
 * The health authority in charge of the sanitary control of a commune's
 * water: its official [name] (a proper noun, never translated) and its
 * [website], opened in the user's browser.
 */
data class HealthAgency(val name: String, val website: String)

/**
 * Department → regional health agency (ARS), embedded (SPEC §5 F3, §14).
 * The 18 ARS: 13 in mainland France, 5 overseas. Saint-Barthélemy (977) and
 * Saint-Martin (978) depend on the ARS of Guadeloupe; Saint-Pierre-et-Miquelon
 * (975) has no ARS but a territorial health administration (ATS), headed by
 * the prefect, whose pages sit on the prefecture website. The Pacific
 * communities (986, 987, 988) are out of scope. Websites checked on
 * 2026-10-29 (HTTPS, 200).
 */
object RegionalHealthAgencies {
    private val ARA = ars("Auvergne-Rhône-Alpes", "auvergne-rhone-alpes")
    private val BFC = ars("Bourgogne-Franche-Comté", "bourgogne-franche-comte")
    private val BRETAGNE = ars("Bretagne", "bretagne")
    private val CVL = ars("Centre-Val de Loire", "centre-val-de-loire")
    private val CORSE = ars("Corse", "corse")
    private val GRAND_EST = ars("Grand Est", "grand-est")
    private val HDF = ars("Hauts-de-France", "hauts-de-france")
    private val IDF = ars("Île-de-France", "iledefrance")
    private val NORMANDIE = ars("Normandie", "normandie")
    private val NA = ars("Nouvelle-Aquitaine", "nouvelle-aquitaine")
    private val OCCITANIE = ars("Occitanie", "occitanie")
    private val PDL = ars("Pays de la Loire", "pays-de-la-loire")
    private val PACA = ars("Provence-Alpes-Côte d'Azur", "paca")
    private val GUADELOUPE = ars("Guadeloupe, Saint-Martin et Saint-Barthélemy", "guadeloupe")
    private val MARTINIQUE = ars("Martinique", "martinique")
    private val GUYANE = ars("Guyane", "guyane")
    private val REUNION = ars("La Réunion", "lareunion")
    private val MAYOTTE = ars("Mayotte", "mayotte")
    private val SPM = HealthAgency(
        "Administration territoriale de santé de Saint-Pierre-et-Miquelon",
        "https://www.saint-pierre-et-miquelon.gouv.fr/",
    )

    private val byDepartment: Map<String, HealthAgency> = buildMap {
        fun put(agency: HealthAgency, vararg departments: String) =
            departments.forEach { put(it, agency) }
        put(ARA, "01", "03", "07", "15", "26", "38", "42", "43", "63", "69", "73", "74")
        put(BFC, "21", "25", "39", "58", "70", "71", "89", "90")
        put(BRETAGNE, "22", "29", "35", "56")
        put(CVL, "18", "28", "36", "37", "41", "45")
        put(CORSE, "2A", "2B")
        put(GRAND_EST, "08", "10", "51", "52", "54", "55", "57", "67", "68", "88")
        put(HDF, "02", "59", "60", "62", "80")
        put(IDF, "75", "77", "78", "91", "92", "93", "94", "95")
        put(NORMANDIE, "14", "27", "50", "61", "76")
        put(NA, "16", "17", "19", "23", "24", "33", "40", "47", "64", "79", "86", "87")
        put(OCCITANIE, "09", "11", "12", "30", "31", "32", "34", "46", "48", "65", "66", "81", "82")
        put(PDL, "44", "49", "53", "72", "85")
        put(PACA, "04", "05", "06", "13", "83", "84")
        put(GUADELOUPE, "971", "977", "978")
        put(MARTINIQUE, "972")
        put(GUYANE, "973")
        put(REUNION, "974")
        put(SPM, "975")
        put(MAYOTTE, "976")
    }

    private val inseeCode = Regex("[0-9]{5}|2[AB][0-9]{3}")

    /** The agency of the commune with this INSEE code, or null if invalid or out of scope. */
    fun forCommune(communeCode: String): HealthAgency? {
        if (!inseeCode.matches(communeCode)) return null
        return byDepartment[communeCode.take(if (communeCode.startsWith("97")) 3 else 2)]
    }

    private fun ars(region: String, host: String) =
        HealthAgency("ARS $region", "https://www.$host.ars.sante.fr/")
}
