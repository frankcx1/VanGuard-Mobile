package com.vanfieldguide

import java.util.Locale

enum class MatchKind { EXACT, FAMILY, POSSIBLE, CONFLICT, NONE }
data class EquipmentMatch(val kind: MatchKind, val title: String, val detail: String) {
    val hasCandidate get() = kind in setOf(MatchKind.EXACT, MatchKind.FAMILY, MatchKind.POSSIBLE)
}

/** Identity comes from a reviewed identifier; visual similarity never confirms a model. */
object EquipmentMatcher {
    private const val FULL = "RIV1230RCL1SSG1US"
    private const val FAMILY = "RIV1230RCL1SS"
    fun match(text: String, visuallySimilar: Boolean): EquipmentMatch {
        var normalized = text.uppercase(Locale.ROOT).replace(Regex("[‐‑–—−]"), "-")
        // Permit OCR spaces/line breaks and punctuation inside a model, but never fuzzy digits.
        for (known in listOf(FULL, FAMILY)) {
            normalized = normalized.replace(Regex("(?<![A-Z0-9])" + known.map { Regex.escape(it.toString()) }.joinToString("[\\s-]*"))) { known }
        }
        normalized = normalized.replace(Regex("\\s*-\\s*"), "-")
        val codes = Regex("(?<![A-Z0-9])RIV[0-9][A-Z0-9]*(?:-[A-Z0-9]+)*").findAll(normalized)
            .map { it.value.replace("-", "") }.toList()
        val exact = FULL in codes
        val family = FAMILY in codes
        val other = codes.any { it != FULL && it != FAMILY }
        if (other) return EquipmentMatch(MatchKind.CONFLICT, "Different or ambiguous model number", "The label includes a model that does not match your saved REGO manual. Review the text or take a closer photo of the label.")
        if (exact) return EquipmentMatch(MatchKind.EXACT, "Model number matches", "Read RIV1230RCL-1SS-G1-US. Compare the recognized text with the equipment label before using the reference.")
        if (family) return EquipmentMatch(MatchKind.FAMILY, "Model family matches", "Read RIV1230RCL-1SS. Confirm the G1-US variant on the equipment label; the complete model number was not matched.")
        val named = Regex("\\bRENOGY\\b").containsMatchIn(normalized) && Regex("\\bREGO\\b").containsMatchIn(normalized)
        if (named || visuallySimilar) return EquipmentMatch(MatchKind.POSSIBLE, "Possible saved manual", if (named) "Renogy REGO appears in the label text. Photograph the model label to confirm the exact model." else "Gemma found visual similarity to inverter equipment. Photograph the label to confirm the model.")
        return EquipmentMatch(MatchKind.NONE, "No equipment match confirmed", "Try a close, well-lit photo of the brand and model label. This pack currently contains one REGO inverter manual.")
    }
}
