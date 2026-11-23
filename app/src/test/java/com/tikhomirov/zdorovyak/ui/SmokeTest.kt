package com.tikhomirov.zdorovyak.ui

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import com.tikhomirov.zdorovyak.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Прокликивает приложение без телефона: все вкладки, составление программы без ИИ,
 * полную тренировку-прикидку с сохранением, коуч, настройки.
 * Диалоги с полями ввода здесь не открываем: в Robolectric они не «засыпают» (проблема среды, не приложения).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w393dp-h873dp")
class SmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun tab(name: String) = rule.onNode(hasContentDescription(name), useUnmergedTree = true).performClick()
    private fun hasVerticalRange() = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
    private fun has(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test fun clickThroughApp() {
        rule.onNodeWithText("Здоровяк").assertExists()
        rule.onNodeWithText("Начать тренировку").assertDoesNotExist()

        tab("Тело"); rule.onNodeWithText("Записать").assertExists()
        tab("Питание"); rule.onNodeWithText("Фото").assertExists(); rule.onNodeWithText("Сказать").assertExists()

        // Тренировки: экран настройки → базовый план (ключа Claude нет)
        tab("Тренировки")
        rule.onNode(hasScrollAction() and hasVerticalRange()).performScrollToNode(hasText("Составить программу"))
        rule.onNodeWithText("Составить программу").performClick()
        // Календарь сверху: расписание по умолчанию Пн-Ср-Пт, плитки дисциплины; программа ниже
        // Без ключа Claude собирается базовый план, экран настройки показывает почему — закрываем
        rule.waitUntil(15_000) { has("Ключ Claude не задан — использую базовый план.") }
        // Ждём, пока план сохранится (снэкбар), иначе «Закрыть» успевает раньше записи в базу и экран остаётся пустым
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Собран базовый план (без ИИ)").fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasScrollAction() and hasVerticalRange()).performScrollToNode(hasText("Закрыть"))
        rule.onNodeWithText("Закрыть").performSemanticsAction(SemanticsActions.OnClick)  // снэкбар может лежать поверх кнопки
        rule.waitUntil(15_000) { has("Дни: Пн-Ср-Пт") }
        rule.onNodeWithText("на этой неделе").assertExists()
        rule.waitUntil(15_000) { runCatching { rule.onNode(hasScrollAction() and hasVerticalRange()).performScrollToNode(hasText("Пересобрать")) }.isSuccess }
        rule.onNodeWithText("базовая").assertExists()

        // Тренировка-прикидка: оценить все упражнения «Нормально» и завершить
        rule.onNode(hasScrollAction() and hasVerticalRange()).performScrollToNode(hasText("Тренировка вне плана"))
        rule.onNodeWithText("Тренировка вне плана").performClick()
        rule.waitUntil(10_000) { has("Нормально") }
        for (i in 0..12) {
            runCatching { rule.onNode(hasScrollAction() and hasVerticalRange()).performScrollToIndex(i) }
            val n = rule.onAllNodesWithText("Нормально").fetchSemanticsNodes().size
            for (j in 0 until n) runCatching { rule.onAllNodesWithText("Нормально")[j].performSemanticsAction(SemanticsActions.OnClick) }
        }
        rule.onNode(hasScrollAction() and hasVerticalRange()).performScrollToNode(hasText("Завершить тренировку"))
        rule.onNodeWithText("Завершить тренировку").performClick()
        rule.waitUntil(10_000) { has("Готово. Что дальше") }
        rule.onAllNodesWithText("рабочий вес", substring = true).onFirst().assertExists()
        rule.onNodeWithText("OK").performClick()

        // История появилась
        // история приходит из Room асинхронно — ждём, пока строка появится в списке
        rule.waitUntil(10_000) { runCatching { rule.onNode(hasScrollAction() and hasVerticalRange()).performScrollToNode(hasText("прикидка")) }.isSuccess }

        // Коуч
        tab("Коуч")
        rule.onNodeWithText("Что коуч помнит").assertExists()
        rule.onNodeWithText("Напишите или надиктуйте…").performTextInput("Привет")
        rule.onNodeWithContentDescription("Диктовать").assertExists()

        // Анализы: из «Тела» открывается отдельный экран
        tab("Тело")
        rule.onNode(hasScrollAction() and hasVerticalRange()).performScrollToNode(hasText("Анализы"))
        rule.onNodeWithText("Загрузить").performClick()
        rule.waitUntil(5_000) { has("PDF или фото") }
        rule.onNodeWithText("Не диагноз", substring = true).assertExists()
        tab("Назад")
        rule.waitUntil(5_000) { has("Вес") }

        // Сегодня: тренировка уже записана — календарь показывает «сделано»; кольца КБЖУ на месте
        tab("Сегодня")
        rule.waitUntil(5_000) { has("Сегодня сделано. Так держать.") }
        rule.onNodeWithText("из 2200 ккал", substring = true).assertExists()
        tab("Быстрый ввод")
        rule.waitUntil(5_000) { has("Еда по фото") }
        rule.onNodeWithText("Написать коучу").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntil(5_000) { has("Что коуч помнит") }

        // Настройки
        tab("Настройки")
        rule.onNodeWithText("CLAUDE API").assertExists()
        rule.onNode(hasScrollAction() and hasVerticalRange()).performScrollToNode(hasText("ГОЛОС КОУЧА"))
        rule.onNodeWithText("Google на телефоне").assertExists()
        rule.onNode(hasScrollAction() and hasVerticalRange()).performScrollToNode(hasText("Войти в Garmin"))
        tab("Назад")
        rule.onNodeWithText("Здоровяк").assertDoesNotExist()
    }
}
