package io.github.mgdx.potable.core

/**
 * A department, or an overseas collectivity with a 97x code: its [code]
 * ("81", "2A", "974") and its official name ("Tarn"), a proper noun shown
 * as is in every language.
 */
data class Department(val code: String, val name: String)

/**
 * Department of an INSEE commune code, embedded: the referential gives the
 * code only. To tell homonymous communes apart in the search results
 * ("Parisot", Tarn (81) or Tarn-et-Garonne (82)). Names of the Code officiel
 * géographique (INSEE); the 97x codes of the referential included
 * (tools/generate_communes.py), the Pacific ones (98x) out of scope.
 */
object Departments {
    private val names: Map<String, String> = mapOf(
        "01" to "Ain",
        "02" to "Aisne",
        "03" to "Allier",
        "04" to "Alpes-de-Haute-Provence",
        "05" to "Hautes-Alpes",
        "06" to "Alpes-Maritimes",
        "07" to "Ardèche",
        "08" to "Ardennes",
        "09" to "Ariège",
        "10" to "Aube",
        "11" to "Aude",
        "12" to "Aveyron",
        "13" to "Bouches-du-Rhône",
        "14" to "Calvados",
        "15" to "Cantal",
        "16" to "Charente",
        "17" to "Charente-Maritime",
        "18" to "Cher",
        "19" to "Corrèze",
        "2A" to "Corse-du-Sud",
        "2B" to "Haute-Corse",
        "21" to "Côte-d'Or",
        "22" to "Côtes-d'Armor",
        "23" to "Creuse",
        "24" to "Dordogne",
        "25" to "Doubs",
        "26" to "Drôme",
        "27" to "Eure",
        "28" to "Eure-et-Loir",
        "29" to "Finistère",
        "30" to "Gard",
        "31" to "Haute-Garonne",
        "32" to "Gers",
        "33" to "Gironde",
        "34" to "Hérault",
        "35" to "Ille-et-Vilaine",
        "36" to "Indre",
        "37" to "Indre-et-Loire",
        "38" to "Isère",
        "39" to "Jura",
        "40" to "Landes",
        "41" to "Loir-et-Cher",
        "42" to "Loire",
        "43" to "Haute-Loire",
        "44" to "Loire-Atlantique",
        "45" to "Loiret",
        "46" to "Lot",
        "47" to "Lot-et-Garonne",
        "48" to "Lozère",
        "49" to "Maine-et-Loire",
        "50" to "Manche",
        "51" to "Marne",
        "52" to "Haute-Marne",
        "53" to "Mayenne",
        "54" to "Meurthe-et-Moselle",
        "55" to "Meuse",
        "56" to "Morbihan",
        "57" to "Moselle",
        "58" to "Nièvre",
        "59" to "Nord",
        "60" to "Oise",
        "61" to "Orne",
        "62" to "Pas-de-Calais",
        "63" to "Puy-de-Dôme",
        "64" to "Pyrénées-Atlantiques",
        "65" to "Hautes-Pyrénées",
        "66" to "Pyrénées-Orientales",
        "67" to "Bas-Rhin",
        "68" to "Haut-Rhin",
        "69" to "Rhône",
        "70" to "Haute-Saône",
        "71" to "Saône-et-Loire",
        "72" to "Sarthe",
        "73" to "Savoie",
        "74" to "Haute-Savoie",
        "75" to "Paris",
        "76" to "Seine-Maritime",
        "77" to "Seine-et-Marne",
        "78" to "Yvelines",
        "79" to "Deux-Sèvres",
        "80" to "Somme",
        "81" to "Tarn",
        "82" to "Tarn-et-Garonne",
        "83" to "Var",
        "84" to "Vaucluse",
        "85" to "Vendée",
        "86" to "Vienne",
        "87" to "Haute-Vienne",
        "88" to "Vosges",
        "89" to "Yonne",
        "90" to "Territoire de Belfort",
        "91" to "Essonne",
        "92" to "Hauts-de-Seine",
        "93" to "Seine-Saint-Denis",
        "94" to "Val-de-Marne",
        "95" to "Val-d'Oise",
        "971" to "Guadeloupe",
        "972" to "Martinique",
        "973" to "Guyane",
        "974" to "La Réunion",
        "975" to "Saint-Pierre-et-Miquelon",
        "976" to "Mayotte",
        "977" to "Saint-Barthélemy",
        "978" to "Saint-Martin",
    )

    private val inseeCode = Regex("[0-9]{5}|2[AB][0-9]{3}")

    /**
     * The department of the commune with this INSEE code: its first three
     * characters overseas ("97…"), else two. Null if the code is invalid or
     * out of scope.
     */
    fun forCommune(communeCode: String): Department? {
        if (!inseeCode.matches(communeCode)) return null
        val code = communeCode.take(if (communeCode.startsWith("97")) 3 else 2)
        return names[code]?.let { Department(code, it) }
    }
}
