package de.bolzwerk.tagwerk.domain

import de.bolzwerk.tagwerk.data.ai.SuggestedCategory
import de.bolzwerk.tagwerk.domain.CategoryMatcher.Match
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryMatcherTest {

    private val existing = listOf(
        CategoryMatcher.Existing(1, "Arbeit"),
        CategoryMatcher.Existing(2, "Haushalt"),
        CategoryMatcher.Existing(3, "Auto & Verkehr"),
    )

    @Test
    fun `matches existing category regardless of case and punctuation`() {
        val result = CategoryMatcher.resolve(
            listOf(SuggestedCategory("arbeit", isNew = true), SuggestedCategory("Auto und Verkehr", isNew = false)),
            existing,
        )
        assertEquals(Match.ExistingCategory(1, "Arbeit"), result[0])
        // "Auto und Verkehr" ist nicht identisch mit "Auto & Verkehr" → neue Kategorie
        assertEquals(Match.NewCategory("Auto und Verkehr"), result[1])
    }

    @Test
    fun `existing match wins even if ai claims it is new`() {
        val result = CategoryMatcher.resolve(listOf(SuggestedCategory(" HAUSHALT. ", isNew = true)), existing)
        assertEquals(listOf(Match.ExistingCategory(2, "Haushalt")), result)
    }

    @Test
    fun `new categories are cleaned and capitalized`() {
        val result = CategoryMatcher.resolve(listOf(SuggestedCategory("  „gesundheit“ ", isNew = true)), existing)
        assertEquals(listOf(Match.NewCategory("Gesundheit")), result)
    }

    @Test
    fun `duplicates and blanks are dropped and result is capped`() {
        val result = CategoryMatcher.resolve(
            listOf(
                SuggestedCategory("", isNew = true),
                SuggestedCategory("Arbeit", isNew = false),
                SuggestedCategory("ARBEIT", isNew = false),
                SuggestedCategory("Finanzen", isNew = true),
                SuggestedCategory("Ideen", isNew = true),
            ),
            existing,
        )
        assertEquals(listOf(Match.ExistingCategory(1, "Arbeit"), Match.NewCategory("Finanzen")), result)
    }

    @Test
    fun `next color prefers least used palette entry`() {
        assertEquals(2, CategoryColors.nextIndex(listOf(0, 1, 3, 4, 5, 6, 7, 8, 9)))
        assertEquals(0, CategoryColors.nextIndex(emptyList()))
    }
}
