# Graph Report - health-app  (2026-11-21)

## Corpus Check
- 56 files · ~54,414 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 16 file(s) not represented in the graph (top: .xml 8, .ttf 2, .properties 2)

## Summary
- 988 nodes · 2645 edges · 50 communities (31 shown, 19 thin omitted)
- Extraction: 92% EXTRACTED · 8% INFERRED · 0% AMBIGUOUS · INFERRED: 209 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `8ce96480`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- TrainingRepo
- Text
- TargetsTest
- .progress
- SmokeTest.kt
- GarminClient.kt
- FoodItem
- Inventory
- TrainingReminder.kt
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
- Query
- LabsRepo.kt
- .progress
- Entities.kt
- TodayScreen
- AppRoot.kt
- NutritionScreen
- NutritionRings
- .forName
- CloudTts
- AppRoot
- Load
- Quick

## God Nodes (most connected - your core abstractions)
1. `Text` - 46 edges
2. `AppDatabase` - 37 edges
3. `TrainingDao` - 28 edges
4. `FoodItem` - 28 edges
5. `NutritionScreen()` - 28 edges
6. `Settings` - 25 edges
7. `NutritionRepo` - 25 edges
8. `Muted()` - 24 edges
9. `TrainingScreen()` - 24 edges
10. `TodayScreen()` - 23 edges

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

## Communities (50 total, 19 thin omitted)

### Community 0 - "TrainingRepo"
Cohesion: 0.18
Nodes (3): ProgramEntity, TrainingMark, TrainingRepo

### Community 2 - "Text"
Cohesion: 0.19
Nodes (15): Text, BodyScreen(), CoachScreen(), Big(), CardHeader(), dayLabel(), MacroBar(), Muted() (+7 more)

### Community 3 - "TargetsTest"
Cohesion: 0.06
Nodes (21): EnergyState, Profile, Settings, Activity, HIGH, LIGHT, MODERATE, SEDENTARY (+13 more)

### Community 4 - ".progress"
Cohesion: 0.14
Nodes (11): Engine, ExState, Feel, EASY, HARD, NORMAL, Outcome, SessionItem (+3 more)

### Community 7 - "GarminClient.kt"
Cohesion: 0.14
Nodes (8): AuthFailed, GarminException, GarminTokens, login(), mobileLogin(), postJson(), Синхронизация Garmin (вручную и раз в 6 часов), python-библиотека garminconnect

### Community 9 - "FoodItem"
Cohesion: 0.06
Nodes (26): ClaudeClient, ClaudeException, Msg, CoachRepo, FoodItem, BaseFood, FoodBase, FoodOption (+18 more)

### Community 10 - "Inventory"
Cohesion: 0.15
Nodes (12): Inventory, Joint, ELBOW, HIP, KNEE, LOWER_BACK, SHOULDER, WRIST (+4 more)

### Community 11 - "TrainingReminder.kt"
Cohesion: 0.08
Nodes (3): App, GarminSyncWorker, TrainingReminder

### Community 12 - "Speaker.kt"
Cohesion: 0.08
Nodes (4): Speaker, UtteranceProgressListener, TtsText, TtsTextTest

### Community 13 - "TrainingScreen"
Cohesion: 0.22
Nodes (10): SectionTitle(), ScheduleDialog(), ScheduleHints(), WeekdayPicker(), CheckRow(), OtherActivityDialog(), ProgramSetup(), short() (+2 more)

### Community 15 - "Pattern"
Cohesion: 0.18
Nodes (11): Pattern, CARRY, CORE, HINGE, ISOLATION, LUNGE, PULL_H, PULL_V (+3 more)

### Community 17 - "DayStatus"
Cohesion: 0.09
Nodes (17): CalDay, DayStatus, DONE, DONE_MANUAL, EXCUSED, MISSED, MOVED, PLANNED (+9 more)

### Community 18 - "Query"
Cohesion: 0.14
Nodes (3): TrainingDao, OtherActivity, WorkoutEntity

### Community 19 - "RecognitionListener"
Cohesion: 0.06
Nodes (5): MainActivity, variable(), ZTheme(), VoiceInput, RecognitionListener

### Community 20 - "Inventory"
Cohesion: 0.09
Nodes (22): Catalog, Equipment, BANDS, BARBELL, BENCH, DUMBBELLS, KETTLEBELL, MACHINES (+14 more)

### Community 21 - "Tab"
Cohesion: 0.29
Nodes (6): Tab, BODY, COACH, FOOD, TODAY, TRAINING

### Community 26 - "Database.kt"
Cohesion: 0.15
Nodes (3): AppDatabase, ChatDao, ChatMessage

### Community 27 - "WeightEntry"
Cohesion: 0.18
Nodes (3): BodyDao, Measurement, WeightEntry

### Community 29 - "GarminDao"
Cohesion: 0.19
Nodes (3): GarminDao, GarminActivity, GarminDay

### Community 30 - "LabParser"
Cohesion: 0.10
Nodes (17): LabResult, LabParser, Parsed, LabRow, LabTable, LabsRepo, ImportResult, DynamicsTable() (+9 more)

### Community 31 - "Query"
Cohesion: 0.12
Nodes (5): DayKcal, FoodDao, LabDao, FoodEntry, LabReport

### Community 32 - "LabsRepo.kt"
Cohesion: 0.15
Nodes (4): Part, Image, Pdf, Images

### Community 34 - "Entities.kt"
Cohesion: 0.22
Nodes (4): ExerciseFeedback, ExerciseStateEntity, SetLog, toEntity()

### Community 35 - "TodayScreen"
Cohesion: 0.17
Nodes (14): today(), dayName(), TodayScreen(), CalendarState, DayDot(), DaySheet(), DisciplineRow(), Legend() (+6 more)

### Community 38 - "NutritionScreen"
Cohesion: 0.24
Nodes (9): TextField1(), defaultMeal(), FoodDialog(), FoodDraft, InputTile(), MealChips(), MealReviewDialog(), NutritionScreen() (+1 more)

### Community 39 - "NutritionRings"
Cohesion: 0.22
Nodes (11): lerpWhite(), MacroRow(), Macros, MealSplitBar(), NutritionRings(), Over, FINE, WARN (+3 more)

### Community 41 - ".forName"
Cohesion: 0.33
Nodes (3): Portion, Portions, PortionsTest

### Community 44 - "CloudTts"
Cohesion: 0.31
Nodes (4): CloudTts, GoogleCloudTts, TtsException, YandexTts

### Community 45 - "AppRoot"
Cohesion: 0.25
Nodes (6): AppRoot(), ChipRow(), SettingsScreen(), Здоровяк — личное приложение здоровья, Раздел «Сегодня», Раздел «Тело» (вес, ИМТ, замеры)

### Community 46 - "Load"
Cohesion: 0.29
Nodes (7): Load, BAND, BARBELL, BODYWEIGHT, DUMBBELL, KETTLEBELL, MACHINE

### Community 47 - "Quick"
Cohesion: 0.29
Nodes (7): Quick, FOOD_BARCODE, FOOD_MANUAL, FOOD_PHOTO, FOOD_PHRASE, OTHER_ACTIVITY, WEIGHT

## Knowledge Gaps
- **77 isolated node(s):** `DONE`, `DONE_MANUAL`, `REPLACED`, `SKIPPED`, `EXCUSED` (+72 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 251 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **19 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AppDatabase` connect `Database.kt` to `TrainingRepo`, `LabsRepo.kt`, `Entities.kt`, `NutritionRepo.kt`, `GarminClient.kt`, `FoodItem`, `TrainingReminder.kt`, `AppRoot`, `Query`, `WeightEntry`, `GarminDao`, `LabParser`, `Query`?**
  _High betweenness centrality (0.083) - this node is a cross-community bridge._
- **Are the 45 inferred relationships involving `Text` (e.g. with `AppRoot()` and `BodyScreen()`) actually correct?**
  _`Text` has 45 INFERRED edges - model-reasoned connections that need verification._
- **What connects `DONE`, `DONE_MANUAL`, `REPLACED` to the rest of the system?**
  _77 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `TrainingScreen.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.11219512195121951 - nodes in this community are weakly interconnected._
- **Why does `Settings` connect `TargetsTest` to `LabsRepo.kt`, `AppRoot.kt`, `NutritionRepo.kt`, `GarminClient.kt`, `ClaudeClient.kt`, `FoodItem`, `TrainingReminder.kt`, `Speaker.kt`, `LabParser`?**
  _High betweenness centrality (0.070) - this node is a cross-community bridge._
- **Should `TargetsTest` be split into smaller, more focused modules?**
  _Cohesion score 0.06497175141242938 - nodes in this community are weakly interconnected._
- **Why does `MainActivity` connect `RecognitionListener` to `TrainingReminder.kt`, `SmokeTest.kt`?**
  _High betweenness centrality (0.056) - this node is a cross-community bridge._