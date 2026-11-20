package com.tikhomirov.zdorovyak.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsTextTest {
    @Test fun cleansMarkdown() {
        assertEquals("Важно: пей воду.\nСпи 8 часов", TtsText.clean("**Важно:** пей воду.\n\n- Спи 8 часов"))
    }

    @Test fun chunksBySentencesWithinLimits() {
        val text = (1..60).joinToString(" ") { "Это предложение номер $it, в нём есть немного слов." }
        val parts = TtsText.chunks(text, first = 350, max = 1500)
        assertTrue(parts[0].length <= 350)
        assertTrue(parts.all { it.length <= 1500 })
        assertEquals(text, parts.joinToString(" "))
    }

    @Test fun longSentenceIsSplit() {
        val text = "слово ".repeat(600).trim()
        val parts = TtsText.chunks(text, first = 350, max = 1000)
        assertTrue(parts.all { it.length <= 1000 })
        assertEquals(text.replace(" ", ""), parts.joinToString("").replace(" ", ""))
    }

    @Test fun cost() { assertEquals(1610.4, TtsText.yandexCostKzt(240_000), 0.01) }
}
