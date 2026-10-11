package de.bolzwerk.tagwerk.domain

import de.bolzwerk.tagwerk.domain.ModelCatalog.Purpose
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelCatalogTest {

    private val openAiModels = listOf(
        "gpt-4o-mini", "gpt-4.1", "o4-mini", "whisper-1", "gpt-4o-transcribe", "gpt-4o-mini-transcribe",
        "tts-1", "gpt-4o-mini-tts", "text-embedding-3-small", "dall-e-3", "omni-moderation-latest",
        "gpt-4o-realtime-preview", "gpt-4o-audio-preview", "babbage-002",
    )

    @Test
    fun `chat models exclude audio, image and embedding models`() {
        assertEquals(listOf("gpt-4.1", "gpt-4o-mini", "o4-mini"), ModelCatalog.forPurpose(openAiModels, Purpose.CHAT))
    }

    @Test
    fun `transcription models are recognized`() {
        assertEquals(
            listOf("gpt-4o-mini-transcribe", "gpt-4o-transcribe", "whisper-1"),
            ModelCatalog.forPurpose(openAiModels, Purpose.TRANSCRIPTION),
        )
    }

    @Test
    fun `self hosted names are recognized`() {
        val local = listOf("llama3.1:8b", "Systran/faster-whisper-large-v3", "nvidia/parakeet-tdt-0.6b", "nomic-embed-text")
        assertEquals(listOf("llama3.1:8b"), ModelCatalog.forPurpose(local, Purpose.CHAT))
        assertEquals(
            listOf("nvidia/parakeet-tdt-0.6b", "Systran/faster-whisper-large-v3"),
            ModelCatalog.forPurpose(local, Purpose.TRANSCRIPTION),
        )
    }

    @Test
    fun `include all lists suitable models first`() {
        val result = ModelCatalog.forPurpose(listOf("tts-1", "whisper-1", "gpt-4o-mini"), Purpose.TRANSCRIPTION, includeAll = true)
        assertEquals(listOf("whisper-1", "gpt-4o-mini", "tts-1"), result)
    }
}
