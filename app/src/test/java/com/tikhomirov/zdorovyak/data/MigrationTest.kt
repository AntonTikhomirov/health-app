package com.tikhomirov.zdorovyak.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Обновление базы 0.1.0 → 0.2.0 не теряет данные и правильно помечает источники продуктов. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun migrate1to2() {
        helper.createDatabase("m.db", 1).apply {
            execSQL("INSERT INTO food_item (name, kcal100, protein100, fat100, carbs100, barcode, lastUsed) VALUES ('Творог с пачки', 121, 17, 5, 2, NULL, 1)")
            execSQL("INSERT INTO food_item (name, kcal100, protein100, fat100, carbs100, barcode, lastUsed) VALUES ('Кефир', 53, 3, 2.5, 4, '4600000000001', 1)")
            execSQL("INSERT INTO food_item (name, kcal100, protein100, fat100, carbs100, barcode, lastUsed) VALUES ('плов', 190, 6, 9, 21, NULL, 1)")
            execSQL("INSERT INTO food (day, time, meal, name, grams, kcal, protein, fat, carbs, source, barcode) VALUES (1, 1, 'Обед', 'плов', 300, 570, 18, 27, 63, 'photo', NULL)")
            close()
        }
        val db = helper.runMigrationsAndValidate("m.db", 2, true, AppDatabase.MIGRATION_1_2)
        val c = db.query("SELECT name, source FROM food_item ORDER BY name")
        val got = buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
        assertEquals(mapOf("Кефир" to "off", "Творог с пачки" to "user", "плов" to "ai"), got)
        val f = db.query("SELECT COUNT(*) FROM food"); f.moveToFirst()
        assertEquals(1, f.getInt(0))
    }

    @Test fun migrate2to3() {
        helper.createDatabase("m3.db", 2).apply {
            execSQL("INSERT INTO weight (day, weightKg, bodyFatPct, note) VALUES (20000, 92.5, NULL, '')")
            close()
        }
        val db = helper.runMigrationsAndValidate("m3.db", 3, true, AppDatabase.MIGRATION_2_3)
        val w = db.query("SELECT COUNT(*) FROM weight"); w.moveToFirst()
        assertEquals(1, w.getInt(0))
        db.execSQL("INSERT INTO lab_report (day, lab, title, filePath, mime, addedAt) VALUES (20400, 'Инвитро', 'Биохимия', '', 'application/pdf', 1)")
        val r = db.query("SELECT COUNT(*) FROM lab_result"); r.moveToFirst()
        assertEquals(0, r.getInt(0))
    }

    @Test fun migrate3to4() {
        helper.createDatabase("m4.db", 3).apply {
            execSQL("INSERT INTO workout (day, startedAt, finishedAt, dayName, calibration, note) VALUES (20400, 1, 2, 'A', 0, '')")
            close()
        }
        val db = helper.runMigrationsAndValidate("m4.db", 4, true, AppDatabase.MIGRATION_3_4)
        val w = db.query("SELECT COUNT(*) FROM workout"); w.moveToFirst()
        assertEquals(1, w.getInt(0))
        db.execSQL("INSERT INTO training_mark (day, status, note, createdAt) VALUES (20401, 'SKIPPED', '', 1)")
        val m = db.query("SELECT status FROM training_mark WHERE day = 20401"); m.moveToFirst()
        assertEquals("SKIPPED", m.getString(0))
    }
}
