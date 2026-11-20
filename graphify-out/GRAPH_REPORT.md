# Graph Report - health-app  (2026-11-20)

## Corpus Check
- 48 files · ~41,976 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 13 file(s) not represented in the graph (top: .xml 7, .properties 2, (none) 1)

## Summary
- 822 nodes · 2096 edges · 38 communities (26 shown, 12 thin omitted)
- Extraction: 92% EXTRACTED · 8% INFERRED · 0% AMBIGUOUS · INFERRED: 161 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `4a25ef47`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- TrainingRepo.kt
- NutritionScreen.kt
- Common.kt
- TargetsTest
- .progress
- SmokeTest.kt
- NutritionRepo.kt
- GarminClient.kt
- Settings
- Msg
- Engine.kt
- GarminSyncWorker.kt
- Speaker.kt
- FoodItem
- Muscle
- Pattern
- FoodBase
- Joint
- Query
- RecognitionListener
- Inventory
- Tab
- Database.kt
- WeightEntry
- FoodDao
- GarminDao
- LabParser
- LabResult
- LabsRepo.kt
- .progress
- Entities.kt
- Синхронизация Garmin (вручную и раз в 6 часов)

## God Nodes (most connected - your core abstractions)
1. `AppDatabase` - 36 edges
2. `Text` - 29 edges
3. `NutritionScreen()` - 25 edges
4. `TrainingDao` - 24 edges
5. `FoodItem` - 24 edges
6. `Settings` - 24 edges
7. `LabResult` - 22 edges
8. `NutritionRepo` - 20 edges
9. `Muted()` - 20 edges
10. `Msg` - 19 edges

## Surprising Connections (you probably didn't know these)
- `AppDatabase` --implements--> `Здоровяк — личное приложение здоровья`  [INFERRED]
  app/src/main/java/com/tikhomirov/zdorovyak/data/Database.kt → README.md
- `WeightEntry` --implements--> `Раздел «Тело» (вес, ИМТ, замеры)`  [INFERRED]
  app/src/main/java/com/tikhomirov/zdorovyak/data/Entities.kt → README.md
- `1. Почему «автоматизация диетологии» сейчас не работает` --references--> `WeightEntry`  [INFERRED]
  docs/research/nutrition-improvements-plan.md → app/src/main/java/com/tikhomirov/zdorovyak/data/Entities.kt
- `Measurement` --implements--> `Раздел «Тело» (вес, ИМТ, замеры)`  [INFERRED]
  app/src/main/java/com/tikhomirov/zdorovyak/data/Entities.kt → README.md
- `FoodEntry` --implements--> `Раздел «Питание» (штрихкод, фото тарелки, справочник)`  [INFERRED]
  app/src/main/java/com/tikhomirov/zdorovyak/data/Entities.kt → README.md

## Import Cycles
- None detected.

## Communities (38 total, 12 thin omitted)

### Community 0 - "TrainingRepo.kt"
Cohesion: 0.25
Nodes (7): ExerciseStateEntity, SessionItem, SessionPlan, toEntity(), toModel(), TrainingRepo, ItemResult

### Community 2 - "Common.kt"
Cohesion: 0.06
Nodes (51): Text, LabRow, AppRoot(), Quick, FOOD_BARCODE, FOOD_MANUAL, FOOD_PHOTO, OTHER_ACTIVITY (+43 more)

### Community 3 - "TargetsTest"
Cohesion: 0.09
Nodes (17): Activity, HIGH, LIGHT, MODERATE, SEDENTARY, GoalMode, GAIN, LOSE (+9 more)

### Community 4 - ".progress"
Cohesion: 0.18
Nodes (7): Engine, ExState, Feel, EASY, HARD, NORMAL, Outcome

### Community 6 - "NutritionRepo.kt"
Cohesion: 0.16
Nodes (3): FoodSource, PhotoItem, PhotoResult

### Community 7 - "GarminClient.kt"
Cohesion: 0.13
Nodes (5): AuthFailed, GarminException, login(), mobileLogin(), postJson()

### Community 8 - "Settings"
Cohesion: 0.11
Nodes (11): EnergyState, Profile, Settings, 1. Почему «автоматизация диетологии» сейчас не работает, 2. Что делают аналоги (что брать, что нет), 3. Рекомендация: формула норм, 4. Рекомендация: откуда брать данные о продуктах, 5. Приоритизированный список доработок (+3 more)

### Community 9 - "Msg"
Cohesion: 0.17
Nodes (7): ClaudeClient, ClaudeException, Msg, CoachRepo, fmt(), Claude API, Раздел «Коуч» (чат с Claude, КПТ/коучинг)

### Community 10 - "Engine.kt"
Cohesion: 0.36
Nodes (4): PlanDay, PlanExercise, PlanValidator, ProgramPlan

### Community 12 - "Speaker.kt"
Cohesion: 0.06
Nodes (8): CloudTts, GoogleCloudTts, TtsException, YandexTts, Speaker, UtteranceProgressListener, TtsText, TtsTextTest

### Community 13 - "FoodItem"
Cohesion: 0.20
Nodes (7): FoodItem, FoodOption, NutritionRepo, BarcodeResult, short(), Open Food Facts, Раздел «Питание» (штрихкод, фото тарелки, справочник)

### Community 14 - "Muscle"
Cohesion: 0.18
Nodes (11): Muscle, BACK, BICEPS, CALVES, CHEST, CORE, GLUTES, HAMSTRINGS (+3 more)

### Community 15 - "Pattern"
Cohesion: 0.18
Nodes (11): Pattern, CARRY, CORE, HINGE, ISOLATION, LUNGE, PULL_H, PULL_V (+3 more)

### Community 16 - "FoodBase"
Cohesion: 0.10
Nodes (4): BaseFood, FoodBase, MigrationTest, FoodBaseTest

### Community 17 - "Joint"
Cohesion: 0.22
Nodes (7): Joint, ELBOW, HIP, KNEE, LOWER_BACK, SHOULDER, WRIST

### Community 18 - "Query"
Cohesion: 0.13
Nodes (3): TrainingDao, ProgramEntity, WorkoutEntity

### Community 19 - "RecognitionListener"
Cohesion: 0.08
Nodes (4): MainActivity, ZTheme(), VoiceInput, RecognitionListener

### Community 20 - "Inventory"
Cohesion: 0.10
Nodes (18): Catalog, Equipment, BANDS, BARBELL, BENCH, DUMBBELLS, KETTLEBELL, MACHINES (+10 more)

### Community 21 - "Tab"
Cohesion: 0.29
Nodes (6): Tab, BODY, COACH, FOOD, TODAY, TRAINING

### Community 26 - "Database.kt"
Cohesion: 0.14
Nodes (4): AppDatabase, ChatDao, DayKcal, ChatMessage

### Community 27 - "WeightEntry"
Cohesion: 0.19
Nodes (3): BodyDao, Measurement, WeightEntry

### Community 30 - "LabParser"
Cohesion: 0.14
Nodes (6): LabParser, Parsed, LabTable, LabsRepo, ImportResult, LabParserTest

### Community 31 - "LabResult"
Cohesion: 0.18
Nodes (3): LabDao, LabReport, LabResult

### Community 32 - "LabsRepo.kt"
Cohesion: 0.14
Nodes (4): Part, Image, Pdf, Images

### Community 34 - "Entities.kt"
Cohesion: 0.50
Nodes (5): ExerciseFeedback, GarminActivity, GarminDay, OtherActivity, SetLog

### Community 35 - "Синхронизация Garmin (вручную и раз в 6 часов)"
Cohesion: 0.40
Nodes (3): GarminTokens, Синхронизация Garmin (вручную и раз в 6 часов), python-библиотека garminconnect

## Knowledge Gaps
- **64 isolated node(s):** `Image`, `Pdf`, `TODAY`, `FOOD`, `BODY` (+59 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 219 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **12 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AppDatabase` connect `Database.kt` to `TrainingRepo.kt`, `LabsRepo.kt`, `Entities.kt`, `Common.kt`, `NutritionRepo.kt`, `GarminClient.kt`, `Msg`, `GarminSyncWorker.kt`, `FoodItem`, `Query`, `WeightEntry`, `FoodDao`, `GarminDao`, `LabParser`, `LabResult`?**
  _High betweenness centrality (0.114) - this node is a cross-community bridge._
- **Are the 28 inferred relationships involving `Text` (e.g. with `AppRoot()` and `BodyScreen()`) actually correct?**
  _`Text` has 28 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Image`, `Pdf`, `TODAY` to the rest of the system?**
  _64 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `NutritionScreen.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.05037481259370315 - nodes in this community are weakly interconnected._
- **Why does `AppRoot()` connect `Common.kt` to `GarminSyncWorker.kt`, `NutritionScreen.kt`, `RecognitionListener`?**
  _High betweenness centrality (0.101) - this node is a cross-community bridge._
- **Are the 12 inferred relationships involving `NutritionScreen()` (e.g. with `AppRoot()` and `Text`) actually correct?**
  _`NutritionScreen()` has 12 INFERRED edges - model-reasoned connections that need verification._
- **Should `Common.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.06205311542390194 - nodes in this community are weakly interconnected._