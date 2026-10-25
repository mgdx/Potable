package io.github.mgdx.potable.core.model

/** A distribution unit (UDI) serving a commune, as of a given [year]. */
data class Udi(val code: String, val name: String, val district: String?, val year: Int)

sealed interface UdiChoice {
    data object None : UdiChoice

    data class Single(val udi: Udi) : UdiChoice

    data class Multiple(val udis: List<Udi>) : UdiChoice
}
