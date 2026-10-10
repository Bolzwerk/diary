package de.bolzwerk.tagwerk.domain

import de.bolzwerk.tagwerk.data.ai.SuggestedCategory

/** Ordnet KI-Vorschläge vorhandenen Kategorien zu, um Dubletten und Wildwuchs zu vermeiden. */
object CategoryMatcher {

    data class Existing(val id: Long, val name: String)

    sealed interface Match {
        val name: String

        data class ExistingCategory(val id: Long, override val name: String) : Match
        data class NewCategory(override val name: String) : Match
    }

    const val MAX_CATEGORIES = 2
    private const val MAX_NAME_LENGTH = 30

    fun resolve(suggestions: List<SuggestedCategory>, existing: List<Existing>): List<Match> {
        val byKey = existing.associateBy { normalize(it.name) }
        val result = LinkedHashMap<String, Match>()
        for (suggestion in suggestions) {
            val cleaned = clean(suggestion.name) ?: continue
            val key = normalize(cleaned)
            if (key in result) continue
            val match = byKey[key]
            result[key] = if (match != null) {
                Match.ExistingCategory(match.id, match.name)
            } else {
                Match.NewCategory(cleaned)
            }
            if (result.size == MAX_CATEGORIES) break
        }
        return result.values.toList()
    }

    /** Vergleichsschlüssel: Groß-/Kleinschreibung, Leer- und Satzzeichen spielen keine Rolle. */
    fun normalize(name: String): String =
        name.lowercase().filter { it.isLetterOrDigit() }

    private fun clean(raw: String): String? {
        val trimmed = raw.trim().trim('"', '„', '“', '#', '.', ',').replace(Regex("\\s+"), " ")
        if (trimmed.isEmpty() || normalize(trimmed).isEmpty()) return null
        val capped = trimmed.take(MAX_NAME_LENGTH).trim()
        return capped.replaceFirstChar { it.uppercase() }
    }
}
