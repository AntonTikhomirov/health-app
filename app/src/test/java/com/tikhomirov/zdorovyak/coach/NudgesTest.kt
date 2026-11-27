package com.tikhomirov.zdorovyak.coach

import com.tikhomirov.zdorovyak.strength.CalDay
import com.tikhomirov.zdorovyak.strength.DayStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NudgesTest {
    private val t = 20_000L
    private fun day(d: Long, kcal: Double, protein: Double = 140.0, entries: Int = 4) = FoodDay(d, kcal, protein, entries, 2000.0, 140.0)
    private fun normalWeek() = (1..14).map { day(t - it, 1950.0) }
    private fun inp(food: List<FoodDay> = normalWeek(), cal: List<CalDay> = emptyList(), history: List<NudgeRecord> = emptyList(),
                    lastWeigh: Long? = t - 2, muted: Set<NudgeGroup> = emptySet()) =
        NudgeInput(t, food, cal, lastWeigh, null, true, history, muted)

    @Test fun quietWhenAllNormal() {
        // Норма каждый день — повод только похвалить
        val c = Nudges.pick(inp())
        assertTrue(c == null || c.kind == NudgeKind.PRAISE)
    }

    @Test fun overeatTwoOfThree() {
        val f = normalWeek().map { if (it.day >= t - 2) it.copy(kcal = 2500.0) else it }
        assertEquals(NudgeKind.OVEREAT, Nudges.pick(inp(f))?.kind)
    }

    @Test fun oneSlightlyHighDayIsNotANudge() {
        val f = normalWeek().map { if (it.day == t - 1) it.copy(kcal = 2300.0) else it }
        assertTrue(Nudges.candidates(inp(f)).none { it.kind == NudgeKind.OVEREAT })
    }

    @Test fun todayIsIgnoredForFood() {
        // Сегодня ещё не закончилось: даже 4000 ккал сегодня не повод
        val f = normalWeek() + day(t, 4000.0)
        assertTrue(Nudges.candidates(inp(f)).none { it.kind == NudgeKind.OVEREAT })
    }

    @Test fun lowProtein() {
        val f = normalWeek().map { if (it.day >= t - 3) it.copy(protein = 80.0) else it }
        assertEquals(NudgeKind.LOW_PROTEIN, Nudges.pick(inp(f))?.kind)
    }

    @Test fun underEat() {
        val f = normalWeek().map { if (it.day >= t - 2) it.copy(kcal = 1100.0) else it }
        assertEquals(NudgeKind.UNDEREAT, Nudges.pick(inp(f))?.kind)
    }

    @Test fun missedTraining() {
        val cal = listOf(CalDay(t - 3, true, DayStatus.MISSED, "A"), CalDay(t + 1, true, DayStatus.PLANNED, "A"))
        assertEquals(NudgeKind.MISSED_TRAINING, Nudges.pick(inp(cal = cal))?.kind)
    }

    @Test fun excusedIsNotMissed() {
        val cal = listOf(CalDay(t - 3, true, DayStatus.EXCUSED, "A", "болезнь"))
        assertTrue(Nudges.candidates(inp(cal = cal)).none { it.kind == NudgeKind.MISSED_TRAINING })
    }

    @Test fun onePerDayAndCooldown() {
        val f = normalWeek().map { if (it.day >= t - 2) it.copy(kcal = 2500.0) else it }
        assertNull(Nudges.pick(inp(f, history = listOf(NudgeRecord(t, "PRAISE", "x", "y")))))
        // OVEREAT был позавчера (пауза 3 дня) — выбираем что-то другое или ничего, но не OVEREAT
        val c = Nudges.pick(inp(f, history = listOf(NudgeRecord(t - 2, "OVEREAT", "x", "y"))))
        assertTrue(c?.kind != NudgeKind.OVEREAT)
    }

    @Test fun weeklyLimit() {
        val f = normalWeek().map { if (it.day >= t - 2) it.copy(kcal = 2500.0) else it }
        val h = (1..4).map { NudgeRecord(t - it, "NO_WEIGH", "x", "y") }
        assertNull(Nudges.pick(inp(f, history = h)))
    }

    @Test fun mutedGroup() {
        val f = normalWeek().map { if (it.day >= t - 2) it.copy(kcal = 2500.0, protein = 60.0) else it }
        val c = Nudges.pick(inp(f, muted = setOf(NudgeGroup.FOOD)))
        assertTrue(c == null || c.kind.group != NudgeGroup.FOOD)
    }

    @Test fun praiseAfterTwoCritical() {
        val f = normalWeek()
        val h = listOf(NudgeRecord(t - 3, "LOW_PROTEIN", "x", "y"), NudgeRecord(t - 2, "OVEREAT", "x", "y"))
        val c = Nudges.pick(inp(f, history = h, lastWeigh = t - 10))
        assertEquals(NudgeKind.PRAISE, c?.kind)
    }

    @Test fun noLogging() {
        val f = normalWeek().filter { it.day < t - 2 }
        assertEquals(NudgeKind.NO_LOGGING, Nudges.pick(inp(f))?.kind)
    }

    @Test fun fallbackTextsAreGentle() {
        val bad = listOf("должен", "срыв", "провал", "лень", "!")
        val f = normalWeek().map { if (it.day >= t - 2) it.copy(kcal = 2500.0, protein = 60.0) else it } + emptyList()
        val cal = listOf(CalDay(t - 3, true, DayStatus.MISSED, "A"))
        Nudges.candidates(inp(f, cal = cal, lastWeigh = t - 10)).forEach { c ->
            bad.forEach { w -> assertTrue("${c.kind}: «$w» в «${c.fallbackText}»", !c.fallbackText.contains(w, ignoreCase = true)) }
        }
    }
}
