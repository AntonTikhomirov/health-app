package com.tikhomirov.zdorovyak.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TargetsTest {
    private val anton = TargetInput(
        sex = Sex.M, ageYears = 50, heightCm = 180, weightKg = 90.0, targetWeightKg = 82.0,
        activity = Activity.LIGHT, goal = GoalMode.LOSE, rateKgPerWeek = 0.5, proteinPerKg = 1.8,
    )

    @Test fun mifflinMatchesReference() {
        // 10×90 + 6,25×180 − 5×50 + 5 = 1780
        assertEquals(1780.0, NutritionMath.bmrMifflin(Sex.M, 90.0, 180, 50), 1e-9)
        // женщина: −161 вместо +5
        assertEquals(1614.0, NutritionMath.bmrMifflin(Sex.F, 90.0, 180, 50), 1e-9)
    }

    @Test fun loseTargetsAreSane() {
        val t = NutritionMath.targets(anton)
        // TDEE = 1780 × 1,375 = 2447,5; дефицит 0,5 кг/нед = 550 ккал → ≈ 1900
        assertEquals(2448, t.tdee)
        assertEquals(1900, t.kcal)
        assertEquals(162, t.protein)                  // 1,8 × 90
        assertTrue(t.fat >= 72)                        // ≥ 0,8 г/кг
        val sum = t.protein * 4 + t.fat * 9 + t.carbs * 4
        assertTrue("макросы сходятся с калориями: $sum", abs(sum - t.kcal) <= 10)
    }

    @Test fun deficitIsCappedAndFloored() {
        val t = NutritionMath.targets(anton.copy(rateKgPerWeek = 1.5))
        // 1,5 кг/нед = 1650 ккал/сут > 25 % расхода → ограничение
        assertTrue(t.note.contains("25 %"))
        assertTrue(t.kcal >= 1780 - 10)
    }

    @Test fun maintainEqualsTdee() {
        val t = NutritionMath.targets(anton.copy(goal = GoalMode.MAINTAIN))
        assertEquals(2450, t.kcal)
    }

    @Test fun proteinFromTargetWeightWhenObese() {
        val t = NutritionMath.targets(anton.copy(weightKg = 110.0)) // ИМТ 34
        assertEquals((1.8 * 82).toInt() + 1, t.protein)              // 147,6 → 148
    }

    @Test fun trendSmoothsNoise() {
        val w = listOf(0L to 90.0, 1L to 91.5, 2L to 89.6, 3L to 90.2, 4L to 92.0)
        val tr = NutritionMath.weightTrend(w)
        assertEquals(5, tr.size)
        tr.forEach { assertTrue(abs(it.second - 90.0) < 0.5) }
    }

    @Test fun trendHandlesGapsAndDuplicates() {
        val tr = NutritionMath.weightTrend(listOf(0L to 90.0, 0L to 91.0, 30L to 85.0))
        assertEquals(90.5, tr.first().second, 1e-9)
        // за 30 дней пропуска тренд почти догоняет новый вес
        assertTrue(tr.last().second < 86.5)
    }

    @Test fun adaptiveNeedsEnoughData() {
        val a = NutritionMath.adaptiveTdee(mapOf(10L to 2000.0), listOf(10L to 90.0), today = 21, prevTdee = 2400.0, fullDayKcal = 1100.0)
        assertNull(a.tdee)
        assertTrue(a.explanation.contains("не хватает"))
    }

    /** Синтетика: реальный расход 2600, человек ест 2100 → теряет 500×7/7700 ≈ 0,45 кг/нед. */
    private fun simulate(days: Int, trueTdee: Double, intake: Double, noise: Boolean = true): Pair<Map<Long, Double>, List<Pair<Long, Double>>> {
        val rnd = java.util.Random(42)
        var mass = 90.0
        val food = HashMap<Long, Double>()
        val weights = ArrayList<Pair<Long, Double>>()
        for (d in 0 until days) {
            food[d.toLong()] = intake
            weights += d.toLong() to mass + if (noise) rnd.nextGaussian() * 0.5 else 0.0
            mass -= (trueTdee - intake) / 7700.0
        }
        return food to weights
    }

    @Test fun adaptiveMovesTowardsTruth() {
        val (food, weights) = simulate(60, trueTdee = 2600.0, intake = 2100.0)
        // Формула ошиблась: считает расход 2300
        var tdee = 2300.0
        var first = true
        for (day in 21L..60L step 7) {
            val a = NutritionMath.adaptiveTdee(food, weights, today = day, prevTdee = tdee, fullDayKcal = 1200.0, firstTime = first)
            assertNotNull(a.tdee)
            assertTrue("шаг не больше лимита", abs(a.tdee!! - tdee) <= (if (first) 300.0 else 150.0) + 1e-6)
            tdee = a.tdee!!; first = false
        }
        assertTrue("оценка $tdee должна подойти к 2600", abs(tdee - 2600) < 200)
    }

    @Test fun adaptiveIgnoresPartialDays() {
        val (food, weights) = simulate(30, trueTdee = 2500.0, intake = 2500.0, noise = false)
        val withPartial = food.toMutableMap().apply { this[25L] = 300.0; this[26L] = 250.0 }
        val a = NutritionMath.adaptiveTdee(withPartial, weights, today = 30, prevTdee = 2500.0, fullDayKcal = 1200.0)
        assertEquals(2500.0, a.observedTdee!!, 30.0)
    }
}
