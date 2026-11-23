# Graph Report - health-app  (2026-11-23)

## Corpus Check
- 63 files · ~63,338 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 16 file(s) not represented in the graph (top: .xml 8, .ttf 2, .properties 2)

## Summary
- 1123 nodes · 3061 edges · 68 communities (36 shown, 32 thin omitted)
- Extraction: 92% EXTRACTED · 8% INFERRED · 0% AMBIGUOUS · INFERRED: 238 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `fedea870`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- AppDatabase
- TrainingScreen.kt
- Text
- TargetsTest
- .progress
- SmokeTest.kt
- PhotoScreens.kt
- GarminClient.kt
- NutritionRepo
- Inventory
- GarminSyncWorker.kt
- Speaker.kt
- TrainingScreen
- Pattern
- MigrationTest.kt
- DayStatus
- Query
- RecognitionListener
- Inventory
- Tab
- Database.kt
- WeightEntry
- GarminDao
- LabParser
- LabResult
- LabsRepo.kt
- .progress
- FoodItem
- TodayScreen
- NutritionScreen
- NutritionRings
- NutritionRepo.kt
- .forName
- Msg
- AppRoot
- PhotoCompareScreen
- Quick
- Settings
- ActivityBurn
- BodyPhotos.kt
- FoodBase
- TrainingReminder.kt
- Muscle
- Muted
- TrainingReminder
- BodyPhotos
- Joint
- Питание: что сломано и что доработать
- 0.5.0: редизайн, активность в норме, фото прогресса
- Pose
- PhotoMode

## God Nodes (most connected - your core abstractions)
1. `Text` - 54 edges
2. `AppDatabase` - 40 edges
3. `TrainingDao` - 30 edges
4. `NutritionScreen()` - 29 edges
5. `FoodItem` - 28 edges
6. `NutritionRepo` - 27 edges
7. `Muted()` - 27 edges
8. `TodayScreen()` - 25 edges
9. `Settings` - 24 edges
10. `TrainingScreen()` - 24 edges

## Surprising Connections (you probably didn't know these)
- `TodayScreen()` --implements--> `Раздел «Сегодня»`  [INFERRED]
  app/src/main/java/com/tikhomirov/zdorovyak/ui/TodayScreen.kt → README.md
- `AppDatabase` --implements--> `Здоровяк — личное приложение здоровья`  [INFERRED]
  app/src/main/java/com/tikhomirov/zdorovyak/data/Database.kt → README.md
- `WeightEntry` --implements--> `Раздел «Тело» (вес, ИМТ, замеры)`  [INFERRED]
  app/src/main/java/com/tikhomirov/zdorovyak/data/Entities.kt → README.md
- `1. Почему «автоматизация диетологии» сейчас не работает` --references--> `WeightEntry`  [INFERRED]
  docs/research/nutrition-improvements-plan.md → app/src/main/java/com/tikhomirov/zdorovyak/data/Entities.kt
- `Measurement` --implements--> `Раздел «Тело» (вес, ИМТ, замеры)`  [INFERRED]
  app/src/main/java/com/tikhomirov/zdorovyak/data/Entities.kt → README.md

## Import Cycles
- None detected.

## Communities (68 total, 32 thin omitted)

### Community 0 - "AppDatabase"
Cohesion: 0.17
Nodes (19): AppDatabase, BodyPhoto, ChatMessage, ExerciseFeedback, ExerciseStateEntity, Measurement, OtherActivity, ProgramEntity (+11 more)

### Community 2 - "Text"
Cohesion: 0.23
Nodes (9): BodyScreen(), Big(), CardHeader(), MacroBar(), NumField(), Pill(), Sparkline(), ZCard() (+1 more)

### Community 3 - "TargetsTest"
Cohesion: 0.06
Nodes (19): EnergyState, Activity, HIGH, LIGHT, MODERATE, SEDENTARY, GoalMode, GAIN (+11 more)

### Community 4 - ".progress"
Cohesion: 0.24
Nodes (8): fmt(), Engine, ExState, Feel, EASY, HARD, NORMAL, Outcome

### Community 7 - "GarminClient.kt"
Cohesion: 0.14
Nodes (7): AuthFailed, GarminException, GarminTokens, login(), mobileLogin(), postJson(), python-библиотека garminconnect

### Community 9 - "NutritionRepo"
Cohesion: 0.16
Nodes (9): FoodOption, FoodSource, MealItem, NutritionRepo, BarcodeResult, ParsedMeal, toItem(), Open Food Facts (+1 more)

### Community 10 - "Inventory"
Cohesion: 0.30
Nodes (4): PlanDay, PlanExercise, PlanValidator, ProgramPlan

### Community 12 - "Speaker.kt"
Cohesion: 0.06
Nodes (8): CloudTts, GoogleCloudTts, TtsException, YandexTts, Speaker, UtteranceProgressListener, TtsText, TtsTextTest

### Community 13 - "TrainingScreen"
Cohesion: 0.22
Nodes (12): Text, SectionTitle(), ScheduleDialog(), ScheduleHints(), WeekdayPicker(), CheckRow(), ExerciseCard(), ItemInput (+4 more)

### Community 15 - "Pattern"
Cohesion: 0.18
Nodes (11): Pattern, CARRY, CORE, HINGE, ISOLATION, LUNGE, PULL_H, PULL_V (+3 more)

### Community 17 - "DayStatus"
Cohesion: 0.09
Nodes (16): DayStatus, DONE, DONE_MANUAL, EXCUSED, MISSED, MOVED, PLANNED, REPLACED (+8 more)

### Community 19 - "RecognitionListener"
Cohesion: 0.06
Nodes (4): MainActivity, ZTheme(), VoiceInput, RecognitionListener

### Community 20 - "Inventory"
Cohesion: 0.10
Nodes (18): Catalog, Equipment, BANDS, BARBELL, BENCH, DUMBBELLS, KETTLEBELL, MACHINES (+10 more)

### Community 21 - "Tab"
Cohesion: 0.22
Nodes (8): FloatingNav(), NavItem(), Tab, BODY, COACH, FOOD, TODAY, TRAINING

### Community 29 - "GarminDao"
Cohesion: 0.20
Nodes (4): GarminDao, GarminActivity, GarminDay, Синхронизация Garmin (вручную и раз в 6 часов)

### Community 30 - "LabParser"
Cohesion: 0.16
Nodes (5): LabParser, Parsed, LabRow, LabTable, LabParserTest

### Community 31 - "LabResult"
Cohesion: 0.20
Nodes (3): LabDao, LabReport, LabResult

### Community 32 - "LabsRepo.kt"
Cohesion: 0.14
Nodes (4): Images, LabsRepo, ImportResult, newPhotoFile()

### Community 34 - "FoodItem"
Cohesion: 0.21
Nodes (3): FoodDao, FoodEntry, FoodItem

### Community 35 - "TodayScreen"
Cohesion: 0.20
Nodes (15): CalDay, today(), dayName(), CalendarState, DayDot(), DaySheet(), DisciplineRow(), Legend() (+7 more)

### Community 38 - "NutritionScreen"
Cohesion: 0.18
Nodes (12): DayTarget, rememberDayTarget(), TextField1(), defaultMeal(), FoodDialog(), FoodDraft, InputTile(), MealChips() (+4 more)

### Community 39 - "NutritionRings"
Cohesion: 0.24
Nodes (10): lerpWhite(), MacroRow(), MealSplitBar(), NutritionRings(), Over, FINE, WARN, pct() (+2 more)

### Community 41 - ".forName"
Cohesion: 0.33
Nodes (3): Portion, Portions, PortionsTest

### Community 44 - "Msg"
Cohesion: 0.19
Nodes (6): ClaudeClient, ClaudeException, Msg, CoachRepo, Claude API, Раздел «Коуч» (чат с Claude, КПТ/коучинг)

### Community 45 - "AppRoot"
Cohesion: 0.18
Nodes (8): AppRoot(), CoachScreen(), ChipRow(), SettingsScreen(), Здоровяк — личное приложение здоровья, Настройки (ключ Claude API, нормы КБЖУ, рост, цель, вход в Garmin), Раздел «Сегодня», Раздел «Тело» (вес, ИМТ, замеры)

### Community 46 - "PhotoCompareScreen"
Cohesion: 0.25
Nodes (13): Image, dayLabel(), BeforeAfter(), BodyPhotosCard(), DayChips(), PhotoCaptureScreen(), PhotoCompareScreen(), PoseGuide() (+5 more)

### Community 47 - "Quick"
Cohesion: 0.29
Nodes (7): Quick, FOOD_BARCODE, FOOD_MANUAL, FOOD_PHOTO, FOOD_PHRASE, OTHER_ACTIVITY, WEIGHT

### Community 51 - "ActivityBurn"
Cohesion: 0.25
Nodes (9): ActivityBurn, Day, Item, Labour, Source, ESTIMATE, GARMIN, Strength (+1 more)

### Community 53 - "FoodBase"
Cohesion: 0.26
Nodes (3): BaseFood, FoodBase, FoodBaseTest

### Community 55 - "Muscle"
Cohesion: 0.18
Nodes (11): Muscle, BACK, BICEPS, CALVES, CHEST, CORE, GLUTES, HAMSTRINGS (+3 more)

### Community 56 - "Muted"
Cohesion: 0.44
Nodes (9): Muted(), DynamicsTable(), flagColor(), flagMark(), HeaderCells(), labDate(), LabsScreen(), ReportDialog() (+1 more)

### Community 61 - "Joint"
Cohesion: 0.29
Nodes (7): Joint, ELBOW, HIP, KNEE, LOWER_BACK, SHOULDER, WRIST

### Community 62 - "Питание: что сломано и что доработать"
Cohesion: 0.29
Nodes (6): 2. Что делают аналоги (что брать, что нет), 3. Рекомендация: формула норм, 4. Рекомендация: откуда брать данные о продуктах, 5. Приоритизированный список доработок, 6. Что от вас нужно для согласования, Питание: что сломано и что доработать

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
- **83 isolated node(s):** `FRONT`, `SIDE`, `BACK`, `GARMIN`, `ESTIMATE` (+78 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 296 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **32 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AppDatabase` connect `AppDatabase` to `LabsRepo.kt`, `FoodItem`, `GarminClient.kt`, `WeightEntry`, `NutritionRepo.kt`, `NutritionRepo`, `Msg`, `AppRoot`, `BodyPhotos.kt`, `.burnRange`, `Database.kt`, `BodyPhotos`, `GarminDao`, `LabResult`?**
  _High betweenness centrality (0.083) - this node is a cross-community bridge._
- **Are the 53 inferred relationships involving `Text` (e.g. with `AppRoot()` and `NavItem()`) actually correct?**
  _`Text` has 53 INFERRED edges - model-reasoned connections that need verification._
- **What connects `FRONT`, `SIDE`, `BACK` to the rest of the system?**
  _83 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `TrainingScreen.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.11538461538461539 - nodes in this community are weakly interconnected._
- **Why does `FoodItem` connect `FoodItem` to `AppDatabase`, `NutritionScreen`, `NutritionRepo.kt`, `NutritionRepo`, `NutritionScreen.kt`?**
  _High betweenness centrality (0.046) - this node is a cross-community bridge._
- **Are the 15 inferred relationships involving `NutritionScreen()` (e.g. with `AppRoot()` and `Text`) actually correct?**
  _`NutritionScreen()` has 15 INFERRED edges - model-reasoned connections that need verification._
- **Should `TargetsTest` be split into smaller, more focused modules?**
  _Cohesion score 0.06497175141242938 - nodes in this community are weakly interconnected._