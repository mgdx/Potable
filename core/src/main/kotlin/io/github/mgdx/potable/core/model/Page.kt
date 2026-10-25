package io.github.mgdx.potable.core.model

/** One page of a Hub'Eau response: total [count], [next] page URL if any. */
data class Page<T>(val count: Int, val next: String?, val data: List<T>)
