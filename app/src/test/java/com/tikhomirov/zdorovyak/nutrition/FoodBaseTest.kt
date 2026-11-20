package com.tikhomirov.zdorovyak.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class FoodBaseTest {
    private val base: FoodBase = FoodBase.parse(File("src/main/assets/foods_ru.tsv").reader(Charsets.UTF_8))

    @Test fun baseLoadsAndIsConsistent() {
        assertTrue("в базе ${base.foods.size} позиций", base.foods.size >= 350)
        assertEquals("названия уникальны", base.foods.size, base.foods.map { it.name.lowercase() }.toSet().size)
        // Калорийность сходится с БЖУ (Этуотер) — ловит опечатки. Исключения: алкоголь, кислые ягоды, семена с клетчаткой.
        val skip = listOf("водка", "коньяк", "вино", "пиво", "лимон", "чиа", "льна")
        val bad = base.foods.filter { f -> f.kcal100 > 30 && skip.none { f.name.lowercase().contains(it) } }
            .filter { f -> abs(4 * f.protein100 + 9 * f.fat100 + 4 * f.carbs100 - f.kcal100) / f.kcal100 > 0.2 }
        assertTrue("расходятся: ${bad.map { it.name }}", bad.isEmpty())
    }

    @Test fun searchFindsRussianAndLocalFood() {
        assertEquals("Гречка отварная", base.search("гречка").first().name)
        assertTrue(base.search("бешбармак").first().name.startsWith("Бешбармак"))
        assertTrue(base.search("плов").first().name.startsWith("Плов"))
        // синоним и «ё»
        assertTrue(base.search("филе курицы").any { it.name.startsWith("Куриная грудка") })
        assertTrue(base.search("сёмга").any { it.name.startsWith("Лосось") })
        assertTrue(base.search("семга").any { it.name.startsWith("Лосось") })
        assertTrue(base.search("творог 5").first().name == "Творог 5%")
        assertTrue(base.search("x").isEmpty())
    }

    @Test fun splitGrams() {
        assertEquals("гречка" to 180.0, FoodBase.splitGrams("гречка 180"))
        assertEquals("гречка" to 180.0, FoodBase.splitGrams("гречка 180 г"))
        assertEquals("творог 5%" to 200.0, FoodBase.splitGrams("творог 5% 200г"))
        assertEquals("кефир" to 250.5, FoodBase.splitGrams("кефир, 250,5"))
        assertNull(FoodBase.splitGrams("творог 5%").second)
        assertNull(FoodBase.splitGrams("180").second)
    }
}
