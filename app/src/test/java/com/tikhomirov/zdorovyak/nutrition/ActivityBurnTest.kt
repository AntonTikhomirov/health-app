package com.tikhomirov.zdorovyak.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityBurnTest {
    private val bmr = 1900.0  // ≈ 1,32 ккал/мин

    @Test fun strengthNormalFiftyMinutes() {
        // (5,0 − 1) × 100 кг × 50/60 ч = 333
        val d = ActivityBurn.day(100.0, bmr, listOf(ActivityBurn.Strength(50, listOf("NORMAL", "NORMAL"))), emptyList(), emptyList())
        assertEquals(333, d.kcal)
    }

    @Test fun feelChangesMet() {
        val easy = ActivityBurn.day(100.0, bmr, listOf(ActivityBurn.Strength(60, listOf("EASY"))), emptyList(), emptyList()).kcal
        val hard = ActivityBurn.day(100.0, bmr, listOf(ActivityBurn.Strength(60, listOf("HARD"))), emptyList(), emptyList()).kcal
        assertEquals(250, easy)   // (3,5−1)×100
        assertEquals(500, hard)   // (6−1)×100
    }

    @Test fun forgottenWorkoutIsCapped() {
        // забыл завершить тренировку — 5 часов «силовой» не засчитываем
        val d = ActivityBurn.day(100.0, bmr, listOf(ActivityBurn.Strength(300, listOf("NORMAL"))), emptyList(), emptyList())
        assertEquals(800, d.kcal)  // 120 мин
    }

    @Test fun labourByRpe() {
        // RPE 7 → MET 5,35; 90 мин × 95 кг: 4,35 × 95 × 1,5 = 620
        val d = ActivityBurn.day(95.0, bmr, emptyList(), listOf(ActivityBurn.Labour("Дрова", 90, 7)), emptyList())
        assertEquals(620, d.kcal)
    }

    @Test fun garminReplacesAppStrengthAndSubtractsRest() {
        val d = ActivityBurn.day(100.0, bmr,
            listOf(ActivityBurn.Strength(50, listOf("NORMAL"))), emptyList(),
            listOf(ActivityBurn.Watch("strength_training", "Силовая", 50.0, 400)))
        assertEquals(1, d.items.size)
        // 400 − 1900/1440 × 50 = 334
        assertEquals(334, d.kcal)
        assertEquals(ActivityBurn.Source.GARMIN, d.items[0].source)
    }

    @Test fun garminWalkAddsToAppStrength() {
        val d = ActivityBurn.day(100.0, bmr,
            listOf(ActivityBurn.Strength(50, listOf("NORMAL"))), emptyList(),
            listOf(ActivityBurn.Watch("walking", "Прогулка", 60.0, 300)))
        assertEquals(2, d.items.size)
        assertTrue(d.kcal > 333)
    }

    @Test fun dayTargetsAddToCarbs() {
        val base = Targets(2000, 170, 70, 170, 1900, 2400)
        val t = ActivityBurn.dayTargets(base, 300)
        assertEquals(2300, t.kcal)
        assertEquals(245, t.carbs)
        assertEquals(170, t.protein)
    }

    @Test fun baseFactorExcludesTraining() {
        val i = TargetInput(Sex.M, 50, 180, 100.0, null, Activity.LIGHT, GoalMode.MAINTAIN, 0.0, 1.8)
        val full = NutritionMath.formulaTdee(i)
        val base = NutritionMath.formulaTdee(i.copy(activitySeparate = true))
        assertTrue(base < full)
        assertEquals(NutritionMath.bmrMifflin(Sex.M, 100.0, 180, 50) * 1.3, base, 0.01)
    }

    @Test fun adaptiveSubtractsAverageBurn() {
        val today = 20000L
        val intake = (today - 21 until today).associateWith { 2500.0 }
        val weighIns = (today - 21 until today step 3).map { it to 100.0 }
        val plain = NutritionMath.adaptiveTdee(intake, weighIns, today, 2500.0, 1000.0, firstTime = true)
        val burn = (today - 21 until today step 2).associateWith { 300 }  // 11 тренировок × 300 / 21 ≈ 157 в день
        val base = NutritionMath.adaptiveTdee(intake, weighIns, today, 2500.0, 1000.0, firstTime = true, burnByDay = burn)
        assertEquals(2500.0, plain.observedTdee!!, 1.0)
        assertEquals(2500.0 - 11 * 300 / 21.0, base.observedTdee!!, 1.0)
    }
}
