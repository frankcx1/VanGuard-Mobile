package com.vanfieldguide

import java.text.Normalizer
import java.util.Locale
import kotlin.math.ln
import kotlin.math.sqrt

data class Passage(val id: Long, val page: Int, val text: String, val vector: FloatArray? = null)
data class ReferenceHit(val page: Int, val excerpt: String, val methods: String)
data class DiagramReference(val page: Int, val title: String)

/** Small exact index. Page numbers are zero-based until presentation. */
object Retrieval {
    private val token = Regex("[a-z0-9]+(?:[-][a-z0-9]+)*")
    private val stop = setOf("a", "an", "the", "my", "me", "find", "show", "please", "for", "of", "to", "and", "is", "what", "how", "i", "in", "on")
    fun tokens(text: String): List<String> = token.findAll(Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT)).map { it.value }.toList()
    fun identifiers(query: String): Set<String> = tokens(query).filter { word -> word.any(Char::isDigit) }.toSet()

    /** Curated source labels, kept separate from semantic ranking and image embeddings. */
    fun diagrams(passages: List<Passage>, query: String, catalog: List<DiagramReference>): List<DiagramReference> {
        val words = tokens(query).toSet()
        if (words.none { it in setOf("diagram", "diagrams", "schematic", "schematics", "wiring") }) return emptyList()
        val identifiers = identifiers(query)
        val pages = passages.groupBy { it.page }.mapValues { (_, parts) -> parts.flatMap { tokens(it.text) }.toSet() }
        return catalog.filter { entry -> pages[entry.page]?.containsAll(identifiers) == true }
            .sortedByDescending { entry -> tokens(entry.title).count { it in words } }
    }
    fun chunks(text: String): List<String> {
        val clean = text.trim()
        if (clean.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var start = 0
        while (start < clean.length) {
            var end = minOf(start + 1100, clean.length)
            if (end < clean.length) {
                val space = clean.lastIndexOf(' ', end)
                if (space > start + 700) end = space
            }
            result += clean.substring(start, end).trim()
            if (end == clean.length) break
            start = maxOf(start + 1, end - 150)
        }
        return result
    }

    fun cosine(a: FloatArray, b: FloatArray): Double {
        require(a.size == b.size && a.isNotEmpty())
        var dot = 0.0; var aa = 0.0; var bb = 0.0
        for (i in a.indices) { dot += a[i].toDouble() * b[i]; aa += a[i].toDouble() * a[i]; bb += b[i].toDouble() * b[i] }
        return if (aa == 0.0 || bb == 0.0) 0.0 else dot / sqrt(aa * bb)
    }

    fun search(passages: List<Passage>, query: String, queryVector: FloatArray?): List<ReferenceHit> {
        val terms = tokens(query).filterNot { it in stop }.distinct()
        if (terms.isEmpty()) return emptyList()
        val ids = identifiers(query)
        val tokenized = passages.associate { it.id to tokens(it.text) }
        // Identifiers are matched on the whole physical page, never inferred from similarity.
        val pageTokens = passages.groupBy { it.page }.mapValues { (_, parts) -> parts.flatMap { tokenized.getValue(it.id) }.toSet() }
        val eligible = passages.filter { pageTokens.getValue(it.page).containsAll(ids) }
        val avgLength = tokenized.values.map { it.size }.average().coerceAtLeast(1.0)
        val frequency = terms.associateWith { term -> tokenized.values.count { term in it } }
        val lexical = eligible.map { p ->
            val words = tokenized.getValue(p.id)
            val score = terms.sumOf { term ->
                val tf = words.count { it == term }.toDouble()
                val df = frequency.getValue(term)
                val idf = ln(1.0 + (passages.size - df + 0.5) / (df + 0.5))
                idf * tf * 2.2 / (tf + 1.2 * (0.25 + 0.75 * words.size / avgLength))
            }
            p to score
        }.filter { it.second > 0.0 }.sortedByDescending { it.second }.map { it.first }
        val semantic = if (queryVector == null) emptyList() else eligible.filter { it.vector?.size == queryVector.size }
            .map { it to cosine(queryVector, it.vector!!) }.sortedByDescending { it.second }.map { it.first }
        val scores = mutableMapOf<Long, Double>()
        lexical.forEachIndexed { i, p -> scores[p.id] = (scores[p.id] ?: 0.0) + 1.0 / (60 + i + 1) }
        semantic.forEachIndexed { i, p -> scores[p.id] = (scores[p.id] ?: 0.0) + 1.0 / (60 + i + 1) }
        val lexicalIds = lexical.map { it.id }.toSet()
        val semanticIds = semantic.map { it.id }.toSet()
        return eligible.filter { it.id in scores }.sortedWith(compareByDescending<Passage> { scores[it.id] }.thenBy { it.page })
            .distinctBy { it.page }.take(10).map { p ->
                val evidence = buildList {
                    if (p.id in lexicalIds) add("extracted text / keyword")
                    if (p.id in semanticIds) add("Gemma text similarity")
                    if (ids.isNotEmpty()) add("exact identifiers on page: ${ids.joinToString()}")
                }
                ReferenceHit(p.page, p.text, evidence.joinToString(" · "))
            }
    }
}
