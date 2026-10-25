package io.github.mgdx.potable.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Migrations of `potable.db`, checked against the schemas exported to `data/schemas/`. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PotableDatabase::class.java,
    )

    @Test
    fun `v1 to v2 keeps the follows and the latest-sample cache`() = runTest {
        helper.createDatabase(NAME, 1).use { db ->
            db.execSQL(
                "INSERT INTO suivi (id, code_commune, nom_commune, code_reseau, nom_reseau, " +
                    "position, principale) VALUES (1, '45234', 'Orléans', '045000474', 'ORLEANS', 0, 1)",
            )
            db.execSQL(
                "INSERT INTO prelevement (code_commune, code_reseau, code_prelevement, " +
                    "reseau_prelevement, date, conclusion, conformite_limites_bact, " +
                    "conformite_limites_pc, conformite_references_bact, " +
                    "conformite_references_pc, " +
                    "distributeur, maitre_ouvrage) VALUES ('45234', '045000474', 'P1', " +
                    "'045000474', 1787741400000, 'Eau conforme.', 'C', 'D', 'S', NULL, 'D', 'M')",
            )
            db.execSQL(
                "INSERT INTO resultat (code_commune, code_reseau, code_prelevement, ordre, " +
                    "code_parametre, libelle, type_parametre, resultat_texte, " +
                    "resultat_numerique, " +
                    "unite, limite, reference) VALUES ('45234', '045000474', 'P1', 0, '1340', " +
                    "'Nitrates (en NO3)', 'N', '4,40', 4.4, 'mg/L', '<=50 mg/L', NULL)",
            )
            db.execSQL(
                "INSERT INTO synchro (code_commune, code_reseau, dernier_prelevement_vu, " +
                    "dernier_succes) VALUES ('45234', '045000474', 1787741400000, 1787800000000)",
            )
        }

        // Validates the migrated schema against 2.json.
        helper.runMigrationsAndValidate(NAME, 2, true).close()

        val database = Room
            .databaseBuilder(RuntimeEnvironment.getApplication(), PotableDatabase::class.java, NAME)
            .allowMainThreadQueries()
            .build()
        try {
            val follow = database.follows().find("45234", "045000474")!!
            assertEquals("Orléans", follow.communeName)
            assertEquals(true, follow.principale)
            val results = database.cache().results("45234", "045000474", "P1")
            assertEquals(listOf("4,40"), results.map { it.resultText })
            val sync = database.cache().sync("45234", "045000474")!!
            assertEquals(1787800000000, sync.lastSuccess)
            assertEquals(1787741400000, sync.lastSeenSampleDate)
            // The history tables exist and start empty.
            assertNull(database.history().series("45234", "045000474", "1340"))
            assertEquals(0, database.history().pointCount())
            assertEquals(0, database.history().timelineCount())
        } finally {
            database.close()
        }
    }

    @Test
    fun `v2 to v3 keeps the cached samples as latest ones`() = runTest {
        helper.createDatabase(NAME, 2).use { db ->
            db.execSQL(
                "INSERT INTO prelevement (code_commune, code_reseau, code_prelevement, " +
                    "reseau_prelevement, date, conclusion, conformite_limites_bact, " +
                    "conformite_limites_pc, conformite_references_bact, " +
                    "conformite_references_pc, " +
                    "distributeur, maitre_ouvrage) VALUES ('45234', '045000474', 'P1', " +
                    "'045000474', 1787741400000, 'Eau conforme.', 'C', 'C', 'C', 'C', 'D', 'M')",
            )
        }

        // Validates the migrated schema against 3.json.
        helper.runMigrationsAndValidate(NAME, 3, true).close()

        val database = Room
            .databaseBuilder(RuntimeEnvironment.getApplication(), PotableDatabase::class.java, NAME)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(true, database.cache().sample("45234", "045000474", "P1")!!.latest)
            assertEquals(
                listOf("P1"),
                database.cache().observeLatestSamples("45234", "045000474").first().map { it.code },
            )
        } finally {
            database.close()
        }
    }

    private companion object {
        const val NAME = "migration-test.db"
    }
}
