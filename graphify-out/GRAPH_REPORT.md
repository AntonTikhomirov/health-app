# Graph Report - health-app  (2026-11-20)

## Corpus Check
- 37 files · ~31,331 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 13 file(s) not represented in the graph (top: .xml 7, .properties 2, (none) 1)

## Summary
- 661 nodes · 1640 edges · 33 communities (22 shown, 11 thin omitted)
- Extraction: 92% EXTRACTED · 8% INFERRED · 0% AMBIGUOUS · INFERRED: 136 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `b0250289`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- AppDatabase
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
- Equipment
- FoodItem
- Muscle
- Pattern
- FoodBase
- Joint
- Query
- ClaudeClient
- Inventory
- Tab
- Database.kt
- WeightEntry
- FoodDao
- GarminDao
- Питание: что сломано и что доработать
- Exercise

## God Nodes (most connected - your core abstractions)
1. `AppDatabase` - 31 edges
2. `NutritionScreen()` - 25 edges
3. `Text` - 24 edges
4. `TrainingDao` - 24 edges
5. `FoodItem` - 24 edges
6. `NutritionRepo` - 20 edges
7. `Settings` - 18 edges
8. `EngineTest` - 18 edges
9. `Engine` - 17 edges
10. `BodyScreen()` - 17 edges

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

## Communities (33 total, 11 thin omitted)

### Community 0 - "AppDatabase"
Cohesion: 0.23
Nodes (15): AppDatabase, ChatMessage, ExerciseFeedback, ExerciseStateEntity, GarminActivity, OtherActivity, ProgramEntity, SetLog (+7 more)

### Community 2 - "Common.kt"
Cohesion: 0.07
Nodes (42): Text, AppRoot(), Quick, FOOD_BARCODE, FOOD_MANUAL, FOOD_PHOTO, OTHER_ACTIVITY, WEIGHT (+34 more)

### Community 3 - "TargetsTest"
Cohesion: 0.09
Nodes (17): Activity, HIGH, LIGHT, MODERATE, SEDENTARY, GoalMode, GAIN, LOSE (+9 more)

### Community 4 - ".progress"
Cohesion: 0.15
Nodes (10): fmt(), Engine, ExResult, ExState, Feel, EASY, HARD, NORMAL (+2 more)

### Community 5 - "SmokeTest.kt"
Cohesion: 0.07
Nodes (3): MainActivity, ZTheme(), SmokeTest

### Community 6 - "NutritionRepo.kt"
Cohesion: 0.10
Nodes (4): Part, Image, PhotoItem, PhotoResult

### Community 7 - "GarminClient.kt"
Cohesion: 0.13
Nodes (8): AuthFailed, GarminException, GarminTokens, login(), mobileLogin(), postJson(), Синхронизация Garmin (вручную и раз в 6 часов), python-библиотека garminconnect

### Community 8 - "Settings"
Cohesion: 0.19
Nodes (4): EnergyState, Profile, Settings, Настройки (ключ Claude API, нормы КБЖУ, рост, цель, вход в Garmin)

### Community 9 - "Msg"
Cohesion: 0.23
Nodes (4): Msg, CoachRepo, Claude API, Раздел «Коуч» (чат с Claude, КПТ/коучинг)

### Community 10 - "Engine.kt"
Cohesion: 0.40
Nodes (4): PlanDay, PlanExercise, PlanValidator, ProgramPlan

### Community 12 - "Equipment"
Cohesion: 0.20
Nodes (8): Equipment, BANDS, BARBELL, BENCH, DUMBBELLS, KETTLEBELL, MACHINES, PULLUP_BAR

### Community 13 - "FoodItem"
Cohesion: 0.17
Nodes (8): FoodItem, FoodOption, FoodSource, NutritionRepo, BarcodeResult, short(), Open Food Facts, Раздел «Питание» (штрихкод, фото тарелки, справочник)

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
Cohesion: 0.29
Nodes (7): Joint, ELBOW, HIP, KNEE, LOWER_BACK, SHOULDER, WRIST

### Community 19 - "ClaudeClient"
Cohesion: 0.25
Nodes (3): ClaudeClient, ClaudeException, App

### Community 20 - "Inventory"
Cohesion: 0.21
Nodes (8): Inventory, Load, BAND, BARBELL, BODYWEIGHT, DUMBBELL, KETTLEBELL, MACHINE

### Community 21 - "Tab"
Cohesion: 0.29
Nodes (6): Tab, BODY, COACH, FOOD, TODAY, TRAINING

### Community 27 - "WeightEntry"
Cohesion: 0.19
Nodes (3): BodyDao, Measurement, WeightEntry

### Community 28 - "FoodDao"
Cohesion: 0.18
Nodes (3): DayKcal, FoodDao, FoodEntry

### Community 30 - "Питание: что сломано и что доработать"
Cohesion: 0.25
Nodes (7): 1. Почему «автоматизация диетологии» сейчас не работает, 2. Что делают аналоги (что брать, что нет), 3. Рекомендация: формула норм, 4. Рекомендация: откуда брать данные о продуктах, 5. Приоритизированный список доработок, 6. Что от вас нужно для согласования, Питание: что сломано и что доработать

## Knowledge Gaps
- **63 isolated node(s):** `Image`, `M`, `F`, `SEDENTARY`, `LIGHT` (+58 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 192 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **11 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AppDatabase` connect `AppDatabase` to `Common.kt`, `NutritionRepo.kt`, `GarminClient.kt`, `Msg`, `FoodItem`, `Query`, `ClaudeClient`, `Database.kt`, `WeightEntry`, `FoodDao`, `GarminDao`?**
  _High betweenness centrality (0.124) - this node is a cross-community bridge._
- **Are the 12 inferred relationships involving `NutritionScreen()` (e.g. with `AppRoot()` and `Text`) actually correct?**
  _`NutritionScreen()` has 12 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Image`, `M`, `F` to the rest of the system?**
  _63 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `NutritionScreen.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.05073260073260073 - nodes in this community are weakly interconnected._
- **Why does `AppRoot()` connect `Common.kt` to `NutritionScreen.kt`, `ClaudeClient`, `SmokeTest.kt`?**
  _High betweenness centrality (0.113) - this node is a cross-community bridge._
- **Are the 23 inferred relationships involving `Text` (e.g. with `AppRoot()` and `BodyScreen()`) actually correct?**
  _`Text` has 23 INFERRED edges - model-reasoned connections that need verification._
- **Should `Common.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.0673274094326726 - nodes in this community are weakly interconnected._