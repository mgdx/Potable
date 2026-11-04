package io.github.mgdx.potable.core

import org.junit.Test

/**
 * Micro-benchmark of the referential as the application loads it: reading
 * the file into a [CommuneIndex], memory retained by the index, search and
 * "near me". It prints figures and asserts nothing: a JVM on a PC says little
 * about a phone, it only compares two versions of the code.
 */
class CommuneIndexBenchmark {
    private fun load(): CommuneIndex =
        Fixtures.communesFile.bufferedReader().useLines { CommuneIndex.parse(it) }

    private fun millis(block: () -> Unit): Double {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1e6
    }

    private fun usedBytes(): Long {
        val runtime = Runtime.getRuntime()
        repeat(3) {
            System.gc()
            Thread.sleep(50)
        }
        return runtime.totalMemory() - runtime.freeMemory()
    }

    @Test
    fun `load, memory, search and nearest`() {
        val first = millis { load() }
        val next = List(5) { millis { load() } }.sorted()[2]

        val before = usedBytes()
        val index = load()
        val retained = usedBytes() - before

        val queries = listOf("orleans", "saint", "a", "l isle", "st jean de braye", "45000", "7")
        repeat(10) { queries.forEach { index.search(it) } }
        val rounds = 20
        val search = millis { repeat(rounds) { queries.forEach { index.search(it) } } } /
            (rounds * queries.size)
        repeat(10) { index.nearest(47.9, 1.9) }
        val nearest = millis { repeat(50) { index.nearest(47.9, 1.9) } } / 50

        println(
            "CommuneIndex: first load %.0f ms, next loads %.0f ms (median), retained %.1f MB, "
                .format(first, next, retained / 1e6) +
                "search %.2f ms, nearest %.2f ms".format(search, nearest),
        )
        check(index.search("orleans").isNotEmpty())
    }
}
