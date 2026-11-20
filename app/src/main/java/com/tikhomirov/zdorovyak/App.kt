package com.tikhomirov.zdorovyak

import android.app.Application
import com.tikhomirov.zdorovyak.ai.ClaudeClient
import com.tikhomirov.zdorovyak.coach.CoachRepo
import com.tikhomirov.zdorovyak.data.AppDatabase
import com.tikhomirov.zdorovyak.data.Settings
import com.tikhomirov.zdorovyak.garmin.GarminClient
import com.tikhomirov.zdorovyak.nutrition.NutritionRepo
import com.tikhomirov.zdorovyak.strength.TrainingRepo

/** Простой контейнер зависимостей: приложение личное, DI-фреймворк не нужен. */
class App : Application() {
    lateinit var db: AppDatabase; private set
    lateinit var settings: Settings; private set
    lateinit var claude: ClaudeClient; private set
    lateinit var nutrition: NutritionRepo; private set
    lateinit var training: TrainingRepo; private set
    lateinit var coach: CoachRepo; private set
    lateinit var garmin: GarminClient; private set

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.build(this)
        settings = Settings(this)
        claude = ClaudeClient(settings)
        nutrition = NutritionRepo(db, claude, settings) {
            com.tikhomirov.zdorovyak.nutrition.FoodBase.parse(assets.open("foods_ru.tsv").reader(Charsets.UTF_8))
        }
        training = TrainingRepo(db, claude)
        coach = CoachRepo(db, claude, settings)
        garmin = GarminClient(settings, db)
        runCatching { com.tikhomirov.zdorovyak.garmin.GarminSyncWorker.schedule(this) }
    }
}
