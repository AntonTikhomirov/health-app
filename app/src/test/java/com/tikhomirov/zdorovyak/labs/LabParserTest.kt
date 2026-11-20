package com.tikhomirov.zdorovyak.labs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LabParserTest {
    private val sample = """
{"date":"2026-11-15","lab":"Инвитро","title":"Биохимия","note":"",
 "results":[
  {"code":"glucose","name":"Глюкоза","value":6.3,"unit":"ммоль/л","ref_low":4.1,"ref_high":5.9,"flag":"N"},
  {"code":"LDL","name":"Холестерин ЛПНП","value":"3,1","unit":"ммоль/л","ref_low":null,"ref_high":3.0,"ref_text":"<3,0"},
  {"code":"hbs_ag","name":"HBsAg","value":null,"value_text":"не обнаружено","unit":"","category":"Другое"},
  {"code":"tsh","name":"ТТГ","value":2.1,"unit":"мМЕ/л","ref_low":0.4,"ref_high":4.0},
  {"code":"ferritin","name":"Ферритин","value":null,"value_text":"","unit":"нг/мл"}
 ]}"""

    @Test fun parsesAndFlags() {
        val p = LabParser.parse(Json.parseToJsonElement(sample).jsonObject, LocalDate.of(2026, 1, 1))
        assertEquals(LocalDate.of(2026, 11, 15), p.day)
        assertEquals(4, p.results.size) // пустой ферритин отброшен
        val g = p.results.first { it.code == "glucose" }
        assertEquals("H", g.flag) // сами считаем по референсу, а не верим "N" модели
        assertEquals(LabParser.GLUC, g.category)
        val ldl = p.results.first { it.code == "ldl" } // код нормализован, запятая разобрана
        assertEquals(3.1, ldl.value!!, 1e-9)
        assertEquals("H", ldl.flag)
        assertEquals("<3,0", LabParser.refDisplay(ldl))
        val hbs = p.results.first { it.code == "hbs_ag" }
        assertNull(hbs.value)
        assertEquals("не обнаружено", LabParser.display(hbs))
        assertEquals("N", p.results.first { it.code == "tsh" }.flag)
    }

    @Test fun missingDateFallsBack() {
        val p = LabParser.parse(Json.parseToJsonElement("""{"date":null,"results":[{"code":"hgb","value":150}]}""").jsonObject, LocalDate.of(2026, 1, 1))
        assertNull(p.day)
        assertEquals(LocalDate.of(2026, 1, 1).toEpochDay(), p.results[0].day)
        assertEquals("Гемоглобин", p.results[0].name)
        assertEquals("г/л", p.results[0].unit)
    }

    @Test fun tableGroupsByCodeAndDay() {
        val a = LabParser.parse(Json.parseToJsonElement(sample).jsonObject, LocalDate.now()).results.map { it.copy(reportId = 1) }
        val b = LabParser.parse(Json.parseToJsonElement("""{"date":"2026-05-10","results":[{"code":"glucose","value":5.5,"ref_low":4.1,"ref_high":5.9}]}""").jsonObject, LocalDate.now()).results.map { it.copy(reportId = 2) }
        val rows = LabTable.rows(a + b)
        val glu = rows.first { it.code == "glucose" }
        assertEquals(2, glu.byDay.size)
        assertEquals(6.3, glu.latest.value!!, 1e-9)
        assertEquals(5.5, glu.previous!!.value!!, 1e-9)
        assertEquals(listOf(LabParser.GLUC, LabParser.LIPIDS, LabParser.HORMONES, LabParser.OTHER), rows.map { it.category }.distinct())
        val s = LabTable.summary(a + b)
        assertTrue(s, s.contains("Глюкоза: 6,3 ммоль/л, норма 4,1–5,9 ↑ВЫШЕ (2026-11-15); ранее 5,5 (2026-05-10)"))
    }
}
