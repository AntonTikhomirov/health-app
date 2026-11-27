# Graph Report - health-app  (2026-11-27)

## Corpus Check
- 70 files · ~70,057 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 16 file(s) not represented in the graph (top: .xml 8, .ttf 2, .properties 2)

## Summary
- 1223 nodes · 3408 edges · 69 communities (39 shown, 30 thin omitted)
- Extraction: 92% EXTRACTED · 8% INFERRED · 0% AMBIGUOUS · INFERRED: 268 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `08e1f277`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- TrainingRepo
- TrainingScreen.kt
- Text
- TargetsTest
- .progress
- SmokeTest.kt
- PhotoScreens.kt
- GarminClient.kt
- NutritionRepo
- Inventory
- .notify
- Speaker.kt
- TrainingScreen
- Pattern
- MigrationTest.kt
- DayStatus
- TrainingDao
- Theme.kt
- Inventory
- Tab
- AppDatabase
- WeightEntry
- NutritionScreen.kt
- Entities.kt
- LabParser
- LabResult
- LabsRepo.kt
- .progress
- FoodItem
- TodayScreen
- CalDay
- NutritionScreen
- NutritionRings
- NutritionRepo.kt
- .forName
- TrainingRepo.kt
- Msg
- AppRoot
- PhotoCompareScreen
- Quick
- Settings
- ActivityBurn
- .doWork
- FoodBase
- BodyPhotos.kt
- Muscle
- Muted
- Силовой модуль: прогрессия, прикидка, план под инвентарь
- TrainingReminder
- Muted
- VoiceInput
- Питание: что сломано и что доработать
- 0.5.0: редизайн, активность в норме, фото прогресса
- Pose
- PhotoMode
- RecognitionListener

## God Nodes (most connected - your core abstractions)
1. `Text` - 58 edges
2. `AppDatabase` - 42 edges
3. `TrainingDao` - 34 edges
4. `Muted()` - 32 edges
5. `NutritionRepo` - 29 edges
6. `NutritionScreen()` - 29 edges
7. `Settings` - 28 edges
8. `FoodItem` - 28 edges
9. `TrainingScreen()` - 26 edges
10. `TrainingRepo` - 25 edges

## Surprising Connections (you probably didn't know these)
- `TodayScreen()` --implements--> `Раздел «Сегодня»`  [INFERRED]
  app/src/main/java/com/tikhomirov/zdorovyak/ui/TodayScreen.kt → README.md
- `AppDatabase` --implements--> `Здоровяк — личное приложение здоровья`  [INFERRED]
  app/src/main/java/com/tikhomirov/zdorovyak/data/Database.kt → README.md
- `1. Почему «автоматизация диетологии» сейчас не работает` --references--> `Profile`  [INFERRED]
  docs/research/nutrition-improvements-plan.md → app/src/main/java/com/tikhomirov/zdorovyak/data/Settings.kt
- `Engine` --implements--> `Правила прогрессии (strength/Engine.kt)`  [EXTRACTED]
  app/src/main/java/com/tikhomirov/zdorovyak/strength/Engine.kt → README.md
- `SettingsScreen()` --implements--> `Настройки (ключ Claude API, нормы КБЖУ, рост, цель, вход в Garmin)`  [INFERRED]
  app/src/main/java/com/tikhomirov/zdorovyak/ui/SettingsScreen.kt → README.md

## Import Cycles
- None detected.

## Communities (69 total, 30 thin omitted)

### Community 0 - "TrainingRepo"
Cohesion: 0.16
Nodes (4): ProgramEntity, TrainingMark, TrainingRepo, PlannedExercise

### Community 2 - "Text"
Cohesion: 0.23
Nodes (10): BodyScreen(), Big(), CardHeader(), MacroBar(), NumField(), Sparkline(), ZCard(), ExerciseCard() (+2 more)

### Community 3 - "TargetsTest"
Cohesion: 0.05
Nodes (19): Activity, HIGH, LIGHT, MODERATE, SEDENTARY, GoalMode, GAIN, LOSE (+11 more)

### Community 4 - ".progress"
Cohesion: 0.32
Nodes (3): fmt(), Engine, Outcome

### Community 7 - "GarminClient.kt"
Cohesion: 0.11
Nodes (8): AuthFailed, GarminException, GarminTokens, login(), mobileLogin(), postJson(), Синхронизация Garmin (вручную и раз в 6 часов), python-библиотека garminconnect

### Community 9 - "NutritionRepo"
Cohesion: 0.21
Nodes (10): FoodItem, FoodOption, MealItem, NutritionRepo, BarcodeResult, ParsedMeal, toItem(), Claude API (+2 more)

### Community 10 - "Inventory"
Cohesion: 0.18
Nodes (11): Joint, ELBOW, HIP, KNEE, LOWER_BACK, SHOULDER, WRIST, PlanDay (+3 more)

### Community 12 - "Speaker.kt"
Cohesion: 0.06
Nodes (8): CloudTts, GoogleCloudTts, TtsException, YandexTts, Speaker, UtteranceProgressListener, TtsText, TtsTextTest

### Community 13 - "TrainingScreen"
Cohesion: 0.27
Nodes (9): SectionTitle(), ScheduleDialog(), ScheduleHints(), WeekdayPicker(), CheckRow(), OtherActivityDialog(), ProgramSetup(), TrainingScreen() (+1 more)

### Community 15 - "Pattern"
Cohesion: 0.18
Nodes (11): Pattern, CARRY, CORE, HINGE, ISOLATION, LUNGE, PULL_H, PULL_V (+3 more)

### Community 17 - "DayStatus"
Cohesion: 0.09
Nodes (16): DayStatus, DONE, DONE_MANUAL, EXCUSED, MISSED, MOVED, PLANNED, REPLACED (+8 more)

### Community 18 - "TrainingDao"
Cohesion: 0.10
Nodes (7): TrainingDao, ExerciseFeedback, OtherActivity, SetLog, WorkoutEntity, LoggedExercise, WorkoutLog

### Community 20 - "Inventory"
Cohesion: 0.10
Nodes (18): Catalog, Equipment, BANDS, BARBELL, BENCH, DUMBBELLS, KETTLEBELL, MACHINES (+10 more)

### Community 21 - "Tab"
Cohesion: 0.22
Nodes (8): FloatingNav(), NavItem(), Tab, BODY, COACH, FOOD, TODAY, TRAINING

### Community 27 - "WeightEntry"
Cohesion: 0.14
Nodes (5): BodyPhotos, BodyDao, BodyPhoto, Measurement, WeightEntry

### Community 29 - "Entities.kt"
Cohesion: 0.11
Nodes (6): ChatDao, GarminDao, ChatMessage, ExerciseStateEntity, GarminActivity, GarminDay

### Community 30 - "LabParser"
Cohesion: 0.15
Nodes (6): LabResult, LabParser, Parsed, LabRow, LabTable, LabParserTest

### Community 32 - "LabsRepo.kt"
Cohesion: 0.13
Nodes (4): Images, LabsRepo, ImportResult, newPhotoFile()

### Community 33 - ".progress"
Cohesion: 0.32
Nodes (3): ExResult, ExState, EngineTest

### Community 35 - "TodayScreen"
Cohesion: 0.22
Nodes (12): today(), dayName(), TodayScreen(), CalendarState, DayDot(), DisciplineRow(), Legend(), rememberCalendar() (+4 more)

### Community 36 - "CalDay"
Cohesion: 0.11
Nodes (21): NudgeRepo, FoodDay, NudgeCandidate, NudgeGroup, BODY, FOOD, PRAISE, TRAINING (+13 more)

### Community 38 - "NutritionScreen"
Cohesion: 0.18
Nodes (12): DayTarget, rememberDayTarget(), TextField1(), defaultMeal(), FoodDialog(), FoodDraft, InputTile(), MealChips() (+4 more)

### Community 39 - "NutritionRings"
Cohesion: 0.24
Nodes (10): lerpWhite(), MacroRow(), MealSplitBar(), NutritionRings(), Over, FINE, WARN, pct() (+2 more)

### Community 41 - ".forName"
Cohesion: 0.33
Nodes (3): Portion, Portions, PortionsTest

### Community 42 - "TrainingRepo.kt"
Cohesion: 0.18
Nodes (10): Feel, EASY, HARD, NORMAL, SessionItem, SessionPlan, toEntity(), toModel() (+2 more)

### Community 44 - "Msg"
Cohesion: 0.18
Nodes (5): ClaudeClient, ClaudeException, Msg, CoachRepo, Раздел «Коуч» (чат с Claude, КПТ/коучинг)

### Community 45 - "AppRoot"
Cohesion: 0.18
Nodes (7): AppRoot(), CoachScreen(), ChipRow(), SettingsScreen(), Здоровяк — личное приложение здоровья, Раздел «Сегодня», Раздел «Тело» (вес, ИМТ, замеры)

### Community 46 - "PhotoCompareScreen"
Cohesion: 0.27
Nodes (16): Part, Image, Pdf, Text, dayLabel(), BeforeAfter(), BodyPhotosCard(), DayChips() (+8 more)

### Community 47 - "Quick"
Cohesion: 0.29
Nodes (7): Quick, FOOD_BARCODE, FOOD_MANUAL, FOOD_PHOTO, FOOD_PHRASE, OTHER_ACTIVITY, WEIGHT

### Community 50 - "Settings"
Cohesion: 0.17
Nodes (4): EnergyState, Profile, Settings, Настройки (ключ Claude API, нормы КБЖУ, рост, цель, вход в Garmin)

### Community 51 - "ActivityBurn"
Cohesion: 0.25
Nodes (9): ActivityBurn, Day, Item, Labour, Source, ESTIMATE, GARMIN, Strength (+1 more)

### Community 55 - "Muscle"
Cohesion: 0.18
Nodes (11): Muscle, BACK, BICEPS, CALVES, CHEST, CORE, GLUTES, HAMSTRINGS (+3 more)

### Community 56 - "Muted"
Cohesion: 0.40
Nodes (9): Pill(), DynamicsTable(), flagColor(), flagMark(), HeaderCells(), labDate(), LabsScreen(), ReportDialog() (+1 more)

### Community 57 - "Силовой модуль: прогрессия, прикидка, план под инвентарь"
Cohesion: 0.12
Nodes (16): 1. Что говорят источники (и чего не говорят), 2. Правила прогрессии, 3. Прикидочные тренировки (неделя 0), 4. Как ИИ составляет план, 5. Модели данных (Room), 6. Риски и что не делать сейчас, Базовая схема: двойная прогрессия, Валидация движком (при нарушении — повторный запрос с перечнем ошибок, до 3 раз) (+8 more)

### Community 60 - "Muted"
Cohesion: 0.28
Nodes (9): Muted(), DaySheet(), SkipButtons(), LoggedExerciseRow(), PlannedPreview(), setsText(), WorkoutLogContent(), WorkoutLogRow() (+1 more)

### Community 62 - "Питание: что сломано и что доработать"
Cohesion: 0.25
Nodes (7): 1. Почему «автоматизация диетологии» сейчас не работает, 2. Что делают аналоги (что брать, что нет), 3. Рекомендация: формула норм, 4. Рекомендация: откуда брать данные о продуктах, 5. Приоритизированный список доработок, 6. Что от вас нужно для согласования, Питание: что сломано и что доработать

### Community 63 - "0.5.0: редизайн, активность в норме, фото прогресса"
Cohesion: 0.33
Nodes (5): 0.5.0: редизайн, активность в норме, фото прогресса, Активность в норме калорий, Контраст WCAG 2.1 (на слоях Bg / Surface / Surface2 / Surface3), Редизайн, Фото прогресса

### Community 64 - "Pose"
Cohesion: 0.50
Nodes (4): Pose, BACK, FRONT, SIDE

### Community 65 - "PhotoMode"
Cohesion: 0.67
Nodes (3): PhotoMode, CAPTURE, COMPARE

## Knowledge Gaps
- **94 isolated node(s):** `OVEREAT`, `UNDEREAT`, `LOW_PROTEIN`, `NO_LOGGING`, `MISSED_TRAINING` (+89 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 317 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **30 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AppDatabase` connect `AppDatabase` to `TrainingRepo`, `LabsRepo.kt`, `FoodItem`, `CalDay`, `GarminClient.kt`, `NutritionRepo.kt`, `NutritionRepo`, `TrainingRepo.kt`, `Msg`, `AppRoot`, `TrainingDao`, `BodyPhotos.kt`, `WeightEntry`, `Entities.kt`, `LabParser`, `LabResult`?**
  _High betweenness centrality (0.073) - this node is a cross-community bridge._
- **Are the 57 inferred relationships involving `Text` (e.g. with `AppRoot()` and `NavItem()`) actually correct?**
  _`Text` has 57 INFERRED edges - model-reasoned connections that need verification._
- **What connects `OVEREAT`, `UNDEREAT`, `LOW_PROTEIN` to the rest of the system?**
  _94 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `TrainingScreen.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.1048780487804878 - nodes in this community are weakly interconnected._
- **Why does `MainActivity` connect `Theme.kt` to `SmokeTest.kt`, `BodyPhotos.kt`?**
  _High betweenness centrality (0.062) - this node is a cross-community bridge._
- **Are the 29 inferred relationships involving `Muted()` (e.g. with `BodyScreen()` and `CoachScreen()`) actually correct?**
  _`Muted()` has 29 INFERRED edges - model-reasoned connections that need verification._
- **Should `TargetsTest` be split into smaller, more focused modules?**
  _Cohesion score 0.052943354313217325 - nodes in this community are weakly interconnected._