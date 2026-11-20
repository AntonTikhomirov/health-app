package com.tikhomirov.zdorovyak.voice

/** Подготовка текста к озвучке: чистка разметки и нарезка на куски по предложениям. */
object TtsText {
    fun clean(text: String): String = text
        .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")          // [текст](ссылка) → текст
        .replace(Regex("https?://\\S+"), "")
        .replace(Regex("[*_`#>|]+"), "")
        .replace(Regex("(?m)^\\s*[-•]\\s+"), "")                    // маркеры списков
        .replace(Regex("[\\p{So}\\p{Cn}\\uFE0F]"), "")              // эмодзи
        .replace("—", ", ").replace("–", "-")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{2,}"), "\n")
        .trim()

    /**
     * Куски не длиннее max символов, по границам предложений. Первый кусок короче (first),
     * чтобы облачный голос начал говорить быстрее.
     */
    fun chunks(text: String, first: Int = 350, max: Int = 1500): List<String> {
        val sentences = Regex("(?<=[.!?…:;])\\s+|\\n+").split(text).map { it.trim() }.filter { it.isNotEmpty() }
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        fun limit() = if (out.isEmpty()) first else max
        fun flush() { if (cur.isNotBlank()) out += cur.toString().trim(); cur.clear() }
        for (s0 in sentences) {
            var s = s0
            // Слишком длинное предложение режем по запятым/пробелам
            while (s.length > max) {
                val cut = s.lastIndexOfAny(charArrayOf(',', ' '), max - 1).takeIf { it > max / 2 } ?: (max - 1)
                flush(); out += s.substring(0, cut + 1).trim(); s = s.substring(cut + 1).trim()
            }
            if (cur.isNotEmpty() && cur.length + 1 + s.length > limit()) flush()
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(s)
        }
        flush()
        return out
    }

    /** Стоимость в тенге за месяц: Yandex SpeechKit API v1 — 6 710 ₸ за 1 млн символов с НДС (тарифы на 20.11.2026). */
    fun yandexCostKzt(chars: Int): Double = chars * 6710.0 / 1_000_000
}
