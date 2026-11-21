package com.tikhomirov.zdorovyak.strength

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleTest {
    // Понедельник 16.11.2026 … воскресенье 22.11.2026
    private val mon = LocalDate.of(2026, 11, 16).toEpochDay()
    private val mwf = setOf(1, 3, 5)

    private fun build(today: Long, workouts: Map<Long, List<String>> = emptyMap(), marks: Map<Long, String> = emptyMap(),
                      replaced: Set<Long> = emptySet(), nextIndex: Int = 0, start: Long = mon - 28, from: Long = mon - 28, to: Long = mon + 13) =
        Schedule.build(from, to, today, mwf, workouts, replaced, marks, listOf("A", "B", "C"), nextIndex, start)

    @Test fun plannedDaysGetRotationFromNextIndex() {
        val days = build(today = mon, nextIndex = 1)
        val week = days.filter { it.day in mon until mon + 7 }
        assertEquals(DayStatus.TODAY, week[0].status); assertEquals("B", week[0].planDay)
        assertEquals(DayStatus.REST, week[1].status)
        assertEquals("C", week[2].planDay)
        assertEquals("A", week[4].planDay)
    }

    @Test fun missedDayDoesNotConsumeProgramDay() {
        // Пн пропущен, сегодня среда: следующая всё ещё A
        val days = build(today = mon + 2, nextIndex = 0)
        assertEquals(DayStatus.MISSED, days.first { it.day == mon }.status)
        val wed = days.first { it.day == mon + 2 }
        assertEquals(DayStatus.TODAY, wed.status); assertEquals("A", wed.planDay)
    }

    @Test fun marksAndWorkouts() {
        val days = build(
            today = mon + 6,
            workouts = mapOf(mon to listOf("A"), mon + 3 to listOf("B")),           // Пн по плану, Чт вместо Ср
            marks = mapOf(mon + 2 to Mark.MOVED, mon + 3 to Mark.EXTRA, mon + 4 to Mark.EXCUSED),
        )
        fun st(d: Long) = days.first { it.day == d }.status
        assertEquals(DayStatus.DONE, st(mon))
        assertEquals(DayStatus.MOVED, st(mon + 2))
        assertEquals(DayStatus.DONE, st(mon + 3))
        assertEquals(DayStatus.EXCUSED, st(mon + 4))
        // Неделя: план Пн + Чт (перенос), Пт уважительно → 2 из 2
        val w = Schedule.weekStat(days, mon, mon + 6)
        assertEquals(2, w.planned); assertEquals(2, w.done); assertTrue(w.complete)
    }

    @Test fun replacedByLabourCountsAsDone() {
        val days = build(today = mon + 1, replaced = setOf(mon))
        assertEquals(DayStatus.REPLACED, days.first { it.day == mon }.status)
        assertTrue(days.first { it.day == mon }.completed)
    }

    @Test fun beforeProgramStartNothingIsMissed() {
        val days = build(today = mon + 2, start = mon + 2)
        assertEquals(DayStatus.REST, days.first { it.day == mon }.status)
        assertEquals(DayStatus.TODAY, days.first { it.day == mon + 2 }.status)
    }

    @Test fun streakAndAdherence() {
        // Три прошлые недели полностью, текущая: Пн сделан, сегодня Ср
        val w = mutableMapOf<Long, List<String>>()
        for (k in 1..3) listOf(0L, 2L, 4L).forEach { w[mon - 7L * k + it] = listOf("A") }
        w[mon] = listOf("A")
        val days = build(today = mon + 2, workouts = w, start = mon - 21)
        val d = Schedule.discipline(days, mon + 2)
        assertEquals(3, d.streakWeeks)
        assertEquals(1, d.thisWeek.planned); assertEquals(1, d.thisWeek.done)   // сегодняшняя ещё не наступила как долг
        assertEquals(1.0, d.adherence!!, 1e-9)
    }

    @Test fun streakBreaksOnPlainSkip() {
        val w = mutableMapOf<Long, List<String>>()
        listOf(0L, 2L, 4L).forEach { w[mon - 7 + it] = listOf("A") }
        listOf(0L, 2L).forEach { w[mon - 14 + it] = listOf("A") }                     // пятница позапрошлой недели пропущена
        val days = build(today = mon, workouts = w, marks = mapOf(mon - 10 to Mark.SKIPPED), start = mon - 14)
        val d = Schedule.discipline(days, mon)
        assertEquals(1, d.streakWeeks)
        assertEquals(5.0 / 6.0, d.adherence!!, 1e-9)
        assertEquals(DayStatus.SKIPPED, days.first { it.day == mon - 10 }.status)
    }

    @Test fun hintsAndDefaults() {
        assertEquals(listOf(1, 3, 5), Schedule.defaultFor(3))
        assertEquals("Пн-Ср-Пт", Schedule.describe(setOf(5, 1, 3)))
        assertEquals(listOf(1 to 2), Schedule.backToBack(setOf(1, 2, 5)))
        assertEquals(listOf(7 to 1), Schedule.backToBack(setOf(3, 7, 1)).filter { it.first == 7 })
        assertTrue(Schedule.backToBack(setOf(1, 3, 5)).isEmpty())
        assertEquals(mon + 2, Schedule.nextPlanned(mon + 1, mwf, emptyMap()))
        assertNull(Schedule.nextPlanned(mon, emptySet(), emptyMap()))
    }
}
