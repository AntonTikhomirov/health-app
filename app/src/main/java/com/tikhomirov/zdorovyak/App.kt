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
    lateinit var labs: com.tikhomirov.zdorovyak.labs.LabsRepo; private set
    lateinit var speaker: com.tikhomirov.zdorovyak.voice.Speaker; private set
    lateinit var photos: com.tikhomirov.zdorovyak.body.BodyPhotos; private set

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
        labs = com.tikhomirov.zdorovyak.labs.LabsRepo(this, db, claude, settings) { coach }
        speaker = com.tikhomirov.zdorovyak.voice.Speaker(this, settings)
        photos = com.tikhomirov.zdorovyak.body.BodyPhotos(this, db)
        runCatching { com.tikhomirov.zdorovyak.garmin.GarminSyncWorker.schedule(this) }
        runCatching { com.tikhomirov.zdorovyak.strength.TrainingReminder.schedule(this, settings.profile.value.remindHour) }
        runCatching { com.tikhomirov.zdorovyak.body.PhotoReminder.schedule(this, settings.profile.value.photoEveryDays) }
    }
}
