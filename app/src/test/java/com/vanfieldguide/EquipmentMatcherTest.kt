package com.vanfieldguide

import org.junit.Assert.*
import org.junit.Test

class EquipmentMatcherTest {
    @Test fun exactLabelAndOcrWhitespace() {
        for (text in listOf("Renogy\nModel: RIV1230RCL-1SS-G1-US\n12V 3000W", "RIV1230RCL – 1SS – G1 – US", "RIV1230RCL-1SS-\nG1-US"))
            assertEquals(text, MatchKind.EXACT, EquipmentMatcher.match(text, false).kind)
    }
    @Test fun incompleteModelOnlyMatchesFamily() {
        assertEquals(MatchKind.FAMILY, EquipmentMatcher.match("RIV1230RCL-1SS", false).kind)
    }
    @Test fun differentModelsAndVariantsOverrideVisualSimilarity() {
        for (text in listOf("RIV2430RCL-1SS-G1-US", "RIV1230RCL-1SS-G2-EU", "RIV1230RCL-1SS-G1-US-X", "RIV1230RCL-1SS-G1-US\nRIV2430RCL-1SS-G1-US")) {
            val result = EquipmentMatcher.match(text, true)
            assertEquals(text, MatchKind.CONFLICT, result.kind)
            assertFalse(result.hasCandidate)
        }
    }
    @Test fun noFuzzyDigitsAndNoSubstringConfirmation() {
        for (text in listOf("RIVI230RCL-1SS-G1-US", "XRIV1230RCL-1SS-G1-US", "RIV1230RCL-1SS-G1-USA"))
            assertNotEquals(text, MatchKind.EXACT, EquipmentMatcher.match(text, true).kind)
    }
    @Test fun visualAndBrandOnlyRemainUnconfirmed() {
        assertEquals(MatchKind.POSSIBLE, EquipmentMatcher.match("", true).kind)
        assertEquals(MatchKind.POSSIBLE, EquipmentMatcher.match("RENOGY REGO", false).kind)
        assertEquals(MatchKind.NONE, EquipmentMatcher.match("A hiking shoe", false).kind)
        assertEquals(MatchKind.NONE, EquipmentMatcher.match("", false).kind)
    }
}
