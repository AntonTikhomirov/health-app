package com.tikhomirov.zdorovyak.strength

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private val inv = Inventory(dumbbells = true, dbMin = 2.0, dbMax = 24.0, dbStep = 2.0, bench = true, pullupBar = true, bands = true)
    private val db = inv.weightsFor(Load.DUMBBELL)
    private val goblet = Catalog["goblet_squat"]!!
    private val curl = Catalog["db_curl"]!!
    private val pushup = Catalog["pushup"]!!
    private fun st(w: Double, target: Int = 8, sets: Int = 3) = ExState("x", w, sets, 8, 12, target, calibrated = true)

    @Test fun hardHoldsThenDrops() {
        val o1 = Engine.progress(goblet, st(16.0), ExResult(listOf(8, 8, 8), 16.0, Feel.HARD), db)
        assertEquals(16.0, o1.state.weightKg, 0.01); assertEquals(1, o1.state.hardStreak)
        val o2 = Engine.progress(goblet, o1.state, ExResult(listOf(8, 8, 8), 16.0, Feel.HARD), db)
        assertEquals(14.0, o2.state.weightKg, 0.01); assertEquals(0, o2.state.hardStreak)
    }

    @Test fun normalAddsRep() {
        val o = Engine.progress(goblet, st(16.0, 9), ExResult(listOf(9, 9, 9), 16.0, Feel.NORMAL), db)
        assertEquals(10, o.state.targetReps); assertEquals(16.0, o.state.weightKg, 0.01)
    }

    @Test fun normalAtTopCountsAsEasy() {
        // 2 кг шаг при 20 кг = 10% > 5% для крупных → вес не растёт, растут подходы
        val o = Engine.progress(goblet, st(20.0, 12), ExResult(listOf(12, 12, 12), 20.0, Feel.NORMAL), db)
        assertEquals(20.0, o.state.weightKg, 0.01); assertEquals(4, o.state.sets)
    }

    @Test fun easyLargeStepWithinLimit() {
        val bb = Inventory(barbell = true, barbellStep = 2.5).weightsFor(Load.BARBELL)
        val sq = Catalog["back_squat_bb"]!!
        val o = Engine.progress(sq, st(60.0, 10), ExResult(listOf(10, 10, 10), 60.0, Feel.EASY), bb)
        assertEquals(62.5, o.state.weightKg, 0.01)
    }

    @Test fun easySmallMuscleBigStepGrowsReps() {
        val o = Engine.progress(curl, st(10.0, 8), ExResult(listOf(8, 8, 8), 10.0, Feel.EASY), db)
        assertEquals(10.0, o.state.weightKg, 0.01); assertEquals(10, o.state.targetReps)
    }

    @Test fun belowMinIsHard() {
        val o = Engine.progress(goblet, st(16.0), ExResult(listOf(8, 7, 6), 16.0, Feel.EASY), db)
        assertEquals(1, o.state.hardStreak)
    }

    @Test fun painRemovesExercise() {
        val o = Engine.progress(goblet, st(16.0), ExResult(listOf(8, 8, 8), 16.0, Feel.NORMAL, pain = 6), db)
        assertEquals("box_squat_bw", o.state.variantId)
    }

    @Test fun hardAfterLabourNotPenalized() {
        val o = Engine.progress(goblet, st(16.0), ExResult(listOf(8, 8, 8), 16.0, Feel.HARD, afterHeavyLabour = true), db)
        assertEquals(0, o.state.hardStreak)
    }

    @Test fun calibrationNormalKeepsWeight() {
        // Баг 0.5.0: 12 кг × 10 «нормально» → 10 кг. «Нормально» = запас 2–3, вес должен остаться.
        val s = ExState("goblet_squat", 12.0, 3, 8, 12, 8)
        val o = Engine.calibrate(goblet, s, ExResult(listOf(10), 12.0, Feel.NORMAL), db)
        assertEquals(12.0, o.state.weightKg, 0.01); assertEquals(10, o.state.targetReps); assertEquals(1, o.state.calibrationSessions)
        val o2 = Engine.calibrate(goblet, o.state, ExResult(listOf(10, 10, 10), 12.0, Feel.NORMAL), db)
        assertEquals(12.0, o2.state.weightKg, 0.01); assertTrue(o2.state.calibrated)
    }

    @Test fun calibrationNormalNeverDrops() {
        // Любой вес и любые повторы в диапазоне при «нормально» — вес не снижается
        for (w in listOf(6.0, 10.0, 14.0, 20.0, 24.0)) for (reps in 8..12) {
            val o = Engine.calibrate(goblet, ExState("goblet_squat", w, 3, 8, 12, 8), ExResult(listOf(reps), w, Feel.NORMAL), db)
            assertTrue("w=$w reps=$reps → ${o.state.weightKg}", o.state.weightKg >= w)
        }
    }

    @Test fun calibrationEasyGoesUp() {
        val s = ExState("goblet_squat", 12.0, 3, 8, 12, 8)
        val o = Engine.calibrate(goblet, s, ExResult(listOf(10), 12.0, Feel.EASY), db)
        assertEquals(14.0, o.state.weightKg, 0.01)
        // «нормально», но все подходы на верху диапазона — тоже вверх
        val o2 = Engine.calibrate(goblet, s.copy(calibrationSessions = 1), ExResult(listOf(12, 12, 12), 12.0, Feel.NORMAL), db)
        assertEquals(14.0, o2.state.weightKg, 0.01); assertTrue(o2.state.calibrated)
    }

    @Test fun calibrationHardGoesDown() {
        val s = ExState("goblet_squat", 16.0, 3, 8, 12, 8)
        val o = Engine.calibrate(goblet, s, ExResult(listOf(6), 16.0, Feel.HARD), db)
        assertTrue(o.state.weightKg < 16.0); assertEquals(8, o.state.targetReps)
        // тяжело на 10 повторах ≈ нормально на 8: вес держим, цель — низ диапазона
        val o2 = Engine.calibrate(goblet, s, ExResult(listOf(10), 16.0, Feel.HARD), db)
        assertEquals(16.0, o2.state.weightKg, 0.01); assertEquals(8, o2.state.targetReps)
    }

    @Test fun calibration15PlusJumps() {
        val o = Engine.calibrate(goblet, ExState("goblet_squat", 10.0, 3, 8, 12, 8), ExResult(listOf(16), 10.0, Feel.EASY), db)
        assertEquals(10.0 * 1.15, o.state.weightKg, 1.01)
        assertTrue(o.state.weightKg > 10.0)
    }

    @Test fun bodyweightTopGoesHarder() {
        val s = ExState("pushup", 0.0, 4, 8, 15, 15, calibrated = true)
        val o = Engine.progress(pushup, s, ExResult(listOf(15, 15, 15, 15), 0.0, Feel.EASY), listOf(0.0))
        assertEquals("pushup_feet_elevated", o.state.variantId)
    }

    @Test fun freezeWhenMostHard() {
        assertTrue(Engine.shouldFreeze(listOf(Feel.HARD, Feel.HARD, Feel.NORMAL)))
    }

    @Test fun fallbackPlanIsValid() {
        for (d in 2..4) {
            val p = PlanValidator.fallbackPlan(inv, d, emptySet())
            val errs = PlanValidator.validate(p, inv, d, 60, emptySet())
            assertTrue("days=$d: $errs", errs.isEmpty())
        }
    }

    @Test fun fallbackWithKneeLimitation() {
        val banned = Catalog.jointsFromText("болит колено")
        val p = PlanValidator.fallbackPlan(inv, 3, banned)
        assertTrue(p.days.flatMap { it.exercises }.none { Catalog[it.exerciseId]!!.joints.contains(Joint.KNEE) })
    }

    @Test fun validatorCatchesUnknown() {
        val p = ProgramPlan(listOf(PlanDay("A", listOf(PlanExercise("magic_press", 3, 8, 12)))))
        assertTrue(PlanValidator.validate(p, inv, 1, 60, emptySet()).isNotEmpty())
    }

    @Test fun deload() {
        assertTrue(Engine.isDeloadWeek(4)); assertTrue(!Engine.isDeloadWeek(3)); assertEquals(2, Engine.deloadSets(3))
    }
}
