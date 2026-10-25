package io.github.mgdx.potable.core

import io.github.mgdx.potable.core.model.Commune
import java.io.File

/** Test fixtures: Hub'Eau responses in resources/hubeau, the real communes file. */
object Fixtures {
    fun hubeau(name: String): String {
        val url = checkNotNull(Fixtures::class.java.getResource("/hubeau/$name")) {
            "missing fixture $name"
        }
        return url.readText()
    }

    val communesFile: File by lazy {
        val path = checkNotNull(System.getProperty("potable.communesFile")) {
            "system property potable.communesFile not set (see core/build.gradle.kts)"
        }
        File(path)
    }

    val communes: List<Commune> by lazy {
        communesFile.bufferedReader().useLines { CommuneFile.parse(it) }
    }
}
