package de.bolzwerk.tagwerk.domain

/** Feste Farbpalette für Kategorien (ARGB). Gut unterscheidbar in hellem und dunklem Design. */
object CategoryColors {
    val palette: List<Long> = listOf(
        0xFF4E79A7, // Blau
        0xFFF28E2B, // Orange
        0xFF59A14F, // Grün
        0xFFE15759, // Rot
        0xFF76B7B2, // Petrol
        0xFFB07AA1, // Violett
        0xFFEDC948, // Gelb
        0xFFFF9DA7, // Rosa
        0xFF9C755F, // Braun
        0xFF8CD17D, // Hellgrün
    )

    /** Wählt die am wenigsten genutzte Farbe für eine neue Kategorie. */
    fun nextIndex(usedIndices: List<Int>): Int {
        val counts = IntArray(palette.size)
        usedIndices.forEach { if (it in palette.indices) counts[it]++ }
        return counts.indices.minBy { counts[it] }
    }
}
