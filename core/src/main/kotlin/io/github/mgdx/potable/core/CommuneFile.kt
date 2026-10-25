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
        lines.forEachIndexed { index, line ->
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            communes += parseLine(line, index + 1)
        }
        return communes
    }

    private fun parseLine(line: String, number: Int): Commune {
        val columns = line.split('\t')
        require(columns.size == V1_COLUMNS || columns.size == V2_COLUMNS) {
            "line $number: expected $V1_COLUMNS or $V2_COLUMNS columns, found ${columns.size}"
        }
        val (code, name, postalCodes, latitude, longitude) = columns
        require(code.isNotBlank()) { "line $number: empty code" }
        require(name.isNotBlank()) { "line $number: empty name" }
        return Commune(
            code = code,
            name = name,
            postalCodes = postalCodes.split(',').filter { it.isNotEmpty() },
            latitude = coordinate(latitude, "latitude", number),
            longitude = coordinate(longitude, "longitude", number),
            hubEauCode = columns[5].ifEmpty { null },
            population = columns.getOrNull(6)?.let { population(it, number) },
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
