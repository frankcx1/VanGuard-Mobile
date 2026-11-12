package com.vanfieldguide

import java.util.PriorityQueue

/** Bounded top-k ranking: never retain the whole library's embeddings in memory. */
class PhotoRanking(private val query: FloatArray, private val limit: Int) {
    private data class Match(val uri: String, val score: Double)
    private val order = compareBy<Match> { it.score }.thenByDescending { it.uri }
    private val best = PriorityQueue(order)
    init { require(valid(query)); require(limit > 0) }
    fun add(uri: String, vector: FloatArray) {
        if (!valid(vector)) return
        best.add(Match(uri, Retrieval.cosine(query, vector)))
        if (best.size > limit) best.poll()
    }
    fun results(): List<String> = best.toList().sortedWith(order.reversed()).map { it.uri }
    companion object {
        fun valid(vector: FloatArray) = vector.size == 768 && vector.all { it.isFinite() } && vector.any { it != 0f }
    }
}
