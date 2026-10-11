package de.bolzwerk.tagwerk.domain

/**
 * Ordnet die Modell-IDs eines OpenAI-kompatiblen Servers den Aufgaben der App zu.
 * Die `/models`-Schnittstelle liefert keine verlässlichen Fähigkeiten, daher anhand typischer Namensmuster.
 */
object ModelCatalog {

    enum class Purpose { CHAT, TRANSCRIPTION }

    private val transcriptionPattern = Regex("whisper|transcrib|speech-to-text|\\bstt\\b|\\basr\\b|parakeet|canary", RegexOption.IGNORE_CASE)

    /** Modelle, die sicher keine Chat-Modelle sind. */
    private val notChatPattern = Regex(
        "embed|whisper|transcrib|tts|text-to-speech|speech|audio|realtime|dall-e|image|moderation|rerank|" +
            "davinci|babbage|\\bstt\\b|\\basr\\b|parakeet|canary|clip|sora|video",
        RegexOption.IGNORE_CASE,
    )

    fun matches(modelId: String, purpose: Purpose): Boolean = when (purpose) {
        Purpose.TRANSCRIPTION -> transcriptionPattern.containsMatchIn(modelId)
        Purpose.CHAT -> !notChatPattern.containsMatchIn(modelId)
    }

    /** Passende Modelle zuerst (alphabetisch); mit [includeAll] folgen die übrigen. */
    fun forPurpose(modelIds: List<String>, purpose: Purpose, includeAll: Boolean = false): List<String> {
        val sorted = modelIds.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        val (suitable, rest) = sorted.partition { matches(it, purpose) }
        return if (includeAll) suitable + rest else suitable
    }
}
