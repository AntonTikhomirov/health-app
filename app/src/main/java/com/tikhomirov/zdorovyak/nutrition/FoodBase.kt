package com.tikhomirov.zdorovyak.nutrition

import java.io.Reader

/** Продукт встроенной базы (assets/foods_ru.tsv), значения на 100 г. */
data class BaseFood(
    val name: String,
    val kcal100: Double,
    val protein100: Double,
    val fat100: Double,
    val carbs100: Double,
    val synonyms: List<String> = emptyList(),
)

/**
 * Офлайн-база продуктов на русском. Небольшая (сотни позиций), поэтому держим в памяти
 * и ищем простым ранжированием — без FTS и без отдельной таблицы в Room.
 */
class FoodBase(val foods: List<BaseFood>) {

    private val keys: List<List<String>> = foods.map { f -> (listOf(f.name) + f.synonyms).map(::norm) }

    /** Поиск: совпадение начала названия > начала слова > все слова запроса встречаются. */
    fun search(query: String, limit: Int = 8): List<BaseFood> {
        val q = norm(query)
        if (q.length < 2) return emptyList()
        val words = q.split(' ').filter { it.isNotEmpty() }
        val scored = foods.indices.mapNotNull { i ->
            var best = 0
            for (k in keys[i]) {
                val s = when {
                    k == q -> 100
                    k.startsWith(q) -> 80
                    k.split(' ', '(', ')', ',').any { it.startsWith(q) } -> 60
                    words.all { w -> k.split(' ', '(', ')', ',').any { it.startsWith(w) } } -> 40
                    words.all { w -> k.contains(w) } -> 20
                    else -> 0
                }
                // Основное название чуть важнее синонима
                val bonus = if (k === keys[i][0] && s > 0) 5 else 0
                best = maxOf(best, s + bonus)
            }
            // при равном счёте — порядок в файле: базовые продукты стоят выше блюд
            if (best > 0) Triple(i, best, i) else null
        }
        return scored.sortedWith(compareByDescending<Triple<Int, Int, Int>> { it.second }.thenBy { it.third })
            .take(limit).map { foods[it.first] }
    }

    companion object {
        fun norm(s: String): String = s.lowercase().replace('ё', 'е').replace(Regex("[^a-zа-я0-9%,()]+"), " ").trim()

        fun parse(reader: Reader): FoodBase = FoodBase(reader.readLines().mapNotNull { line ->
            if (line.isBlank() || line.startsWith("#")) return@mapNotNull null
            val c = line.split('\t')
            if (c.size < 5) return@mapNotNull null
            BaseFood(
                name = c[0].trim(),
                kcal100 = c[1].toDouble(), protein100 = c[2].toDouble(), fat100 = c[3].toDouble(), carbs100 = c[4].toDouble(),
                synonyms = c.getOrNull(5)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
            )
        })

        /** «гречка 180» / «гречка 180 г» → ("гречка", 180). */
        fun splitGrams(input: String): Pair<String, Double?> {
            val m = Regex("^(.*?)[\\s,]+(\\d+(?:[.,]\\d+)?)\\s*(г|гр|грамм)?\\.?$").find(input.trim()) ?: return input to null
            val name = m.groupValues[1].trim()
            if (name.isEmpty()) return input to null
            return name to m.groupValues[2].replace(',', '.').toDoubleOrNull()
        }
    }
}
