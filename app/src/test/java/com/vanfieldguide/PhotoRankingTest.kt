package com.vanfieldguide

import org.junit.Assert.*
import org.junit.Test

class PhotoRankingTest {
    private fun vector(x: Float, y: Float = 0f) = FloatArray(768).also { it[0] = x; it[1] = y }
    @Test fun ranksByImageSimilarityWithBoundedResultsNotFilename() {
        val ranking = PhotoRanking(vector(1f), 2)
        ranking.add("van-photo.jpg", vector(-1f))
        ranking.add("IMG_1234.jpg", vector(8f))
        ranking.add("IMG_9999.jpg", vector(1f, 1f))
        ranking.add("other.jpg", vector(0f, 1f))
        assertEquals(listOf("IMG_1234.jpg", "IMG_9999.jpg"), ranking.results())
    }
    @Test fun malformedEmbeddingsCannotPollutePhotoResults() {
        val ranking = PhotoRanking(vector(1f), 24)
        ranking.add("wrong-model", floatArrayOf(1f))
        ranking.add("zero", vector(0f))
        ranking.add("nan", vector(Float.NaN))
        ranking.add("infinity", vector(Float.POSITIVE_INFINITY))
        ranking.add("valid", vector(1f))
        assertEquals(listOf("valid"), ranking.results())
        assertThrows(IllegalArgumentException::class.java) { PhotoRanking(vector(Float.NaN), 24) }
    }
    @Test fun tiesAreStableRegardlessOfScanOrder() {
        val a = PhotoRanking(vector(1f), 2)
        val b = PhotoRanking(vector(1f), 2)
        listOf("c", "a", "b").forEach { a.add(it, vector(1f)) }
        listOf("b", "a", "c").forEach { b.add(it, vector(1f)) }
        assertEquals(listOf("a", "b"), a.results())
        assertEquals(a.results(), b.results())
    }
}
