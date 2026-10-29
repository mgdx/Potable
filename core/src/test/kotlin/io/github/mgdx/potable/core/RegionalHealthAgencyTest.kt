package io.github.mgdx.potable.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionalHealthAgencyTest {
    private val mainland =
        (1..95).filter { it != 20 }.map { "%02d".format(it) } + listOf("2A", "2B")
    private val overseas = listOf("971", "972", "973", "974", "976")

    @Test
    fun `every mainland department has an ARS`() {
        mainland.forEach { department ->
            val agency = RegionalHealthAgencies.forCommune(department.padEnd(5, '1'))
            assertNotNull(department, agency)
            assertTrue(department, agency!!.name.startsWith("ARS "))
        }
    }

    @Test
    fun `every overseas department has an ARS`() {
        overseas.forEach { department ->
            assertTrue(
                department,
                RegionalHealthAgencies.forCommune("${department}01")!!.name.startsWith("ARS "),
            )
        }
    }

    @Test
    fun `the 18 ARS are all reachable, over HTTPS`() {
        val agencies = (mainland.map { it.padEnd(5, '1') } + overseas.map { "${it}01" })
            .mapNotNull(RegionalHealthAgencies::forCommune)
            .toSet()
        assertEquals(18, agencies.size)
        agencies.forEach { assertTrue(it.website, it.website.startsWith("https://")) }
    }

    @Test
    fun `examples by region`() {
        assertEquals("ARS Grand Est", RegionalHealthAgencies.forCommune("67482")?.name)
        assertEquals("ARS Île-de-France", RegionalHealthAgencies.forCommune("75056")?.name)
        assertEquals("ARS Occitanie", RegionalHealthAgencies.forCommune("09122")?.name)
        assertEquals(
            "ARS Provence-Alpes-Côte d'Azur",
            RegionalHealthAgencies.forCommune("13055")?.name,
        )
    }

    @Test
    fun `Corsica has its own ARS`() {
        assertEquals("ARS Corse", RegionalHealthAgencies.forCommune("2A004")?.name)
        assertEquals("ARS Corse", RegionalHealthAgencies.forCommune("2B033")?.name)
    }

    @Test
    fun `overseas departments are read on three characters`() {
        assertEquals("ARS Martinique", RegionalHealthAgencies.forCommune("97209")?.name)
        assertEquals("ARS La Réunion", RegionalHealthAgencies.forCommune("97411")?.name)
        assertEquals("ARS Mayotte", RegionalHealthAgencies.forCommune("97611")?.name)
        assertEquals("ARS Guyane", RegionalHealthAgencies.forCommune("97302")?.name)
    }

    @Test
    fun `Saint-Barthelemy and Saint-Martin depend on the ARS of Guadeloupe`() {
        val guadeloupe = RegionalHealthAgencies.forCommune("97105")
        assertEquals(guadeloupe, RegionalHealthAgencies.forCommune("97701"))
        assertEquals(guadeloupe, RegionalHealthAgencies.forCommune("97801"))
    }

    @Test
    fun `Saint-Pierre-et-Miquelon has its territorial health administration`() {
        val agency = RegionalHealthAgencies.forCommune("97502")
        assertEquals(
            "Administration territoriale de santé de Saint-Pierre-et-Miquelon",
            agency?.name,
        )
    }

    @Test
    fun `the Pacific communities are out of scope`() {
        listOf("98611", "98735", "98818").forEach {
            assertNull(it, RegionalHealthAgencies.forCommune(it))
        }
    }

    @Test
    fun `an invalid code has no agency`() {
        listOf("", "6748", "674820", "20004", "2C004", "abcde", "96001", "99999", " 6748")
            .forEach { assertNull("'$it'", RegionalHealthAgencies.forCommune(it)) }
    }
}
