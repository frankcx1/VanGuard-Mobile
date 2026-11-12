package com.vanfieldguide

import org.junit.Assert.*
import org.junit.Test

class RetrievalTest {
    private val catalog = listOf(DiagramReference(13, "Inverter wiring diagram"), DiagramReference(43, "Automatic generator start wiring diagram"))
    @Test fun wiringRequestReturnsSavedDiagramPagesSeparatelyFromText() {
        val passages = listOf(Passage(1, 13, "Wiring Diagram"), Passage(2, 43, "Automatic Generator Start Wiring Diagram"))
        assertEquals(13, Retrieval.diagrams(passages, "find my inverter wiring diagram", catalog).first().page)
        assertEquals(43, Retrieval.diagrams(passages, "generator start schematic", catalog).first().page)
        assertTrue(Retrieval.diagrams(passages, "battery charge settings", catalog).isEmpty())
    }
    @Test fun curatedDiagramsCannotBypassExactIdentifiersOrMissingPages() {
        assertTrue(Retrieval.diagrams(emptyList(), "wiring diagram", catalog).isEmpty())
        assertTrue(Retrieval.diagrams(listOf(Passage(1, 13, "Wiring diagram 12V")), "wiring diagram 24V", catalog).isEmpty())
        assertEquals(listOf(13), Retrieval.diagrams(listOf(Passage(1, 13, "Wiring diagram 12V")), "wiring diagram 12V", catalog).map { it.page })
    }
    @Test fun differentFaultCodeCannotEnterResultsBySimilarity() {
        val candidates = listOf(
            Passage(1, 90, "Warning code 03: battery reference", floatArrayOf(0f, 1f)),
            Passage(2, 91, "Warning code 04: battery reference", floatArrayOf(1f, 0f)),
        )
        assertEquals(listOf(90), Retrieval.search(candidates, "find fault code 03", floatArrayOf(1f, 0f)).map { it.page })
        assertTrue(Retrieval.search(candidates, "fault code 99", floatArrayOf(1f, 0f)).isEmpty())
    }
    @Test fun modelIdentifiersStayWholeAndCaseInsensitive() {
        assertEquals(setOf("riv1230rcl-1ss"), Retrieval.identifiers("Find RIV1230RCL-1SS manual"))
        assertTrue(Retrieval.search(listOf(Passage(1, 0, "PCL1-30111S manual")), "RIV1230RCL-1SS", null).isEmpty())
    }
    @Test fun pageIsReturnedOnceEvenWhenSeveralPassagesMatch() {
        val matches = Retrieval.search(listOf(Passage(1, 13, "Wiring diagram"), Passage(2, 13, "Wiring diagram continued"), Passage(3, 6, "Warranty")), "wiring diagram", null)
        assertEquals(listOf(13), matches.map { it.page })
        assertTrue(matches.single().methods.contains("keyword"))
        assertFalse(matches.single().methods.contains("Gemma"))
    }
    @Test fun identifiersCanAppearElsewhereOnSamePage() {
        val matches = Retrieval.search(listOf(Passage(1, 90, "Fault reference"), Passage(2, 90, "Code 03")), "fault 03", null)
        assertEquals(listOf(90), matches.map { it.page })
    }
    @Test fun chunkingIncludesTheEndOfLongPagesAndBoundsEachInput() {
        val page = (1..900).joinToString(" ") { "section$it" } + " FINAL_MARKER"
        val parts = Retrieval.chunks(page)
        assertTrue(parts.size > 3)
        assertTrue(parts.all { it.length <= 1100 })
        assertTrue(parts.last().endsWith("FINAL_MARKER"))
        (1..900).forEach { number -> assertTrue("Missing section $number", parts.any { "section$number" in it }) }
    }
    @Test fun cosineNormalizesAndRejectsMismatchedSpaces() {
        assertEquals(1.0, Retrieval.cosine(floatArrayOf(2f, 0f), floatArrayOf(1f, 0f)), 0.00001)
        assertEquals(0.0, Retrieval.cosine(floatArrayOf(0f, 0f), floatArrayOf(1f, 0f)), 0.0)
        assertThrows(IllegalArgumentException::class.java) { Retrieval.cosine(floatArrayOf(1f), floatArrayOf(1f, 2f)) }
    }
}
