package de.bolzwerk.tagwerk.data.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Prompt-Vorlagen und JSON-Schemas für die KI-Aufrufe. */
object Prompts {

    fun categorizationSystem(existingCategories: List<String>): String {
        val existing = if (existingCategories.isEmpty()) "(noch keine)" else existingCategories.joinToString(", ")
        return """
            Du ordnest diktierte Notizen für eine persönliche Notiz-App. Antworte ausschließlich im vorgegebenen JSON-Format.

            1. "title": ein kurzer, prägnanter deutscher Titel (höchstens 6 Wörter), der den Inhalt zusammenfasst. Keine Anführungszeichen, kein Punkt am Ende.
            2. "categories": 1 bis 2 passende Kategorien.
               - Bevorzuge unbedingt vorhandene Kategorien und übernimm deren Schreibweise exakt ("is_new": false).
               - Nur wenn keine vorhandene Kategorie passt, schlage eine neue vor: kurz, allgemein, ein Substantiv (z. B. "Gesundheit", "Finanzen") mit "is_new": true.

            Vorhandene Kategorien: $existing
        """.trimIndent()
    }

    fun categorizationUser(noteText: String): String = "Notiz:\n$noteText"

    val categorizationSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("title") { put("type", "string") }
            putJsonObject("categories") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("name") { put("type", "string") }
                        putJsonObject("is_new") { put("type", "boolean") }
                    }
                    putJsonArray("required") { add("name"); add("is_new") }
                    put("additionalProperties", false)
                }
            }
        }
        putJsonArray("required") { add("title"); add("categories") }
        put("additionalProperties", false)
    }
}
