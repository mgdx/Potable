package io.github.mgdx.potable.data

import mockwebserver3.MockResponse

/** Minimal Hub'Eau payloads, shaped like the real ones (docs/hubeau-api.md). */
object HubEauJson {
    fun page(count: Int, next: String?, rows: List<String>): String {
        val nextJson = next?.let { "\"$it\"" } ?: "null"
        return """{"count":$count,"first":null,"last":null,"prev":null,"next":$nextJson,""" +
            """"api_version":"v1","data":[${rows.joinToString(",")}]}"""
    }

    fun udiRow(network: String, year: Int, commune: String = "45234"): String =
        """{"code_commune":"$commune","nom_commune":"ORLEANS","nom_quartier":"totalité",""" +
            """"code_reseau":"$network","nom_reseau":"RESEAU $network",""" +
            """"debut_alim":"2020-01-01",""" +
            """"annee":"$year"}"""

    fun resultRow(
        sample: String,
        date: String,
        parameter: String?,
        commune: String = "45234",
    ): String {
        val parameterJson = parameter?.let { "\"$it\"" } ?: "null"
        return """{"code_prelevement":"$sample","code_commune":"$commune",""" +
            """"nom_commune":"ORLEANS",""" +
            """"reseaux":[{"code":"045000474","nom":"ORLEANS"}],"date_prelevement":"$date",""" +
            """"conclusion_conformite_prelevement":"Eau conforme.",""" +
            """"conformite_limites_bact_prelevement":"C",""" +
            """"conformite_limites_pc_prelevement":"C",""" +
            """"conformite_references_bact_prelevement":"C",""" +
            """"conformite_references_pc_prelevement":"C",""" +
            """"nom_distributeur":"DISTRIBUTEUR","nom_moa":"MOA",""" +
            """"code_parametre":$parameterJson,""" +
            """"libelle_parametre":"Paramètre $parameter","code_type_parametre":"N",""" +
            """"resultat_alphanumerique":"1,0","resultat_numerique":1.0,"libelle_unite":"mg/L",""" +
            """"limite_qualite_parametre":null,"reference_qualite_parametre":null}"""
    }

    /** 206 when more pages follow, as the API does; 200 otherwise. */
    fun response(body: String, partial: Boolean = false): MockResponse = MockResponse
        .Builder()
        .code(if (partial) 206 else 200)
        .setHeader("Content-Type", "application/json")
        .body(body)
        .build()

    const val NEXT = "https://hubeau.eaufrance.fr/api/v1/qualite_eau_potable/next"
}
