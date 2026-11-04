package io.github.mgdx.potable.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DepartmentsTest {
    @Test
    fun `mainland, Corsica and overseas`() {
        assertEquals(Department("81", "Tarn"), Departments.forCommune("81202"))
        assertEquals(Department("82", "Tarn-et-Garonne"), Departments.forCommune("82137"))
        assertEquals(Department("01", "Ain"), Departments.forCommune("01001"))
        assertEquals(Department("2A", "Corse-du-Sud"), Departments.forCommune("2A004"))
        assertEquals(Department("2B", "Haute-Corse"), Departments.forCommune("2B033"))
        assertEquals(Department("974", "La Réunion"), Departments.forCommune("97411"))
        assertEquals(Department("976", "Mayotte"), Departments.forCommune("97611"))
        assertEquals(Department("977", "Saint-Barthélemy"), Departments.forCommune("97701"))
    }

    @Test
    fun `invalid or out of scope codes have none`() {
        assertNull(Departments.forCommune(""))
        assertNull(Departments.forCommune("8120"))
        assertNull(Departments.forCommune("2C004"))
        assertNull(Departments.forCommune("98714"))
        assertNull(Departments.forCommune("20004"))
    }

    @Test
    fun `every entry of the embedded referential has a department`() {
        Fixtures.communes.forEach { assertNotNull(it.code, Departments.forCommune(it.code)) }
    }
}
