package com.tikhomirov.zdorovyak.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortionsTest {
    private fun first(name: String) = Portions.forName(name).firstOrNull()

    @Test fun typicalPortions() {
        assertEquals(Portion("шт", 55.0), first("Яйцо куриное"))
        assertEquals(Portion("шт", 11.0), first("Яйцо перепелиное"))
        assertEquals("кусок", first("Хлеб ржаной")!!.label)
        assertEquals(Portion("шт", 90.0), first("Котлета говяжья жареная"))
        assertEquals("ч. ложка", first("Масло сливочное 82,5%")!!.label)
        assertEquals("стакан", first("Кефир 2,5%")!!.label)
        assertEquals("тарелка", Portions.forName("Борщ").last().label)
    }

    @Test fun headNounWins() {
        // «в соусе», «с молоком», «мёд» внутри «медовика» не должны перебивать главное слово
        assertEquals("шт", first("Тефтели в соусе")!!.label)
        assertEquals("порция", Portions.forName("Картофельное пюре с молоком и маслом").last().label)
        assertEquals("кусок", first("Медовик")!!.label)
        assertEquals("ст. ложка", first("Рис белый (сухой)")!!.label)
        assertTrue(Portions.forName("Барбарис").isEmpty())
    }

    @Test fun everyBaseFoodIsSane() {
        val base = FoodBase.parse(java.io.File("src/main/assets/foods_ru.tsv").reader())
        val covered = base.foods.count { Portions.forName(it.name).isNotEmpty() }
        // Порции есть у большинства продуктов, и все веса разумные
        assertTrue("покрытие $covered из ${base.foods.size}", covered > base.foods.size * 0.6)
        base.foods.flatMap { Portions.forName(it.name) }.forEach { assertTrue(it.toString(), it.grams in 3.0..600.0) }
    }
}
