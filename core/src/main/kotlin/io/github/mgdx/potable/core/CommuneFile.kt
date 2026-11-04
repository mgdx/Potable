package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.model.Commune

object CommuneFile {
    private const val V1_COLUMNS = 6
    private const val V2_COLUMNS = 7

    /**
     * Parses the embedded TSV (format in tools/README.md): lines
     * starting with "#" and empty lines are skipped; every other line has the
     * tab-separated columns `code name postal_codes latitude longitude
     * hubeau_code`, followed by `population` in format v2 (six columns in v1,
     * seven in v2). Postal codes are separated by ","; an empty `hubeau_code`
     * or `population` means null.
     *
     * @throws IllegalArgumentException on a malformed line, with its number
     *   (1-based).
     */
    fun parse(lines: Sequence<String>): List<Commune> {
        val communes = ArrayList<Commune>()
        read(lines) { code, name, postalCodes, latitude, longitude, hubEauCode, population ->
            communes += Commune(
                code = code,
                name = name,
                postalCodes = postalCodes.split(',').filter { it.isNotEmpty() },
                latitude = latitude,
                longitude = longitude,
                hubEauCode = hubEauCode,
                population = population,
            )
        }
        return communes
    }

    /** One entry of the file, as [read] passes it: postal codes as written, "," separated. */
    internal fun interface Row {
        fun accept(
            code: String,
            name: String,
            postalCodes: String,
            latitude: Double,
            longitude: Double,
            hubEauCode: String?,
            population: Int?,
        )
    }

    /**
     * Checks and passes each entry to [row], in file order, without building
     * a [Commune]: what [CommuneIndex.parse] stores in its own arrays.
     */
    internal fun read(lines: Sequence<String>, row: Row) {
        lines.forEachIndexed { index, line ->
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            readLine(line, index + 1, row)
        }
    }

    private fun readLine(line: String, number: Int, row: Row) {
        val columns = line.split('\t')
        require(columns.size == V1_COLUMNS || columns.size == V2_COLUMNS) {
            "line $number: expected $V1_COLUMNS or $V2_COLUMNS columns, found ${columns.size}"
        }
        val (code, name, postalCodes, latitude, longitude) = columns
        require(code.isNotBlank()) { "line $number: empty code" }
        require(name.isNotBlank()) { "line $number: empty name" }
        row.accept(
            code,
            name,
            postalCodes,
            coordinate(latitude, "latitude", number),
            coordinate(longitude, "longitude", number),
            columns[5].ifEmpty { null },
            columns.getOrNull(6)?.let { population(it, number) },
        )
    }

    private fun coordinate(text: String, column: String, number: Int): Double {
        val value = text.toDoubleOrNull()
        require(value != null && value.isFinite()) { "line $number: invalid $column" }
        return value
    }

    private fun population(text: String, number: Int): Int? {
        if (text.isEmpty()) return null
        val value = text.toIntOrNull()
        require(value != null && value >= 0) { "line $number: invalid population" }
        return value
    }
}
