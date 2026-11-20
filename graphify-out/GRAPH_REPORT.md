# Graph Report - health-app  (2026-11-20)

## Corpus Check
- Corpus is ~25,037 words - fits in a single context window. You may not need a graph.

## Summary
- 617 nodes · 1535 edges · 26 communities (17 shown, 9 thin omitted)
- Extraction: 88% EXTRACTED · 12% INFERRED · 0% AMBIGUOUS · INFERRED: 181 edges (avg confidence: 0.85)
- Token cost: 0 input · 97,859 output

## Community Hubs (Navigation)
- Room: база и DAO
- UI: экраны и Compose-импорты
- UI: навигация и экраны
- JogJoy: движок плана бега
- Силовой движок: прогрессия
- Activity, тема и смоук-тест
- Claude Vision: фото еды
- Garmin: вход и токены
- Настройки и профиль
- ИИ-коуч и память
- Валидация силового плана
- Garmin: фоновая синхронизация
- Каталог: инвентарь
- Питание: штрихкод и фото
- Мышечные группы
- Двигательные паттерны
- Инвентарь пользователя
- Суставы и ограничения
- Спека силового модуля
- Claude API клиент
- Типы нагрузки
- Вкладки приложения

## God Nodes (most connected - your core abstractions)
1. `AppDatabase` - 32 edges
2. `TrainingDao` - 24 edges
3. `Text` - 23 edges
4. `Engine` - 22 edges
5. `NutritionScreen()` - 22 edges
6. `EngineTest` - 18 edges
7. `BodyScreen()` - 17 edges
8. `ExResult` - 16 edges
9. `Muted()` - 16 edges
10. `FoodItem` - 15 edges

## Surprising Connections (you probably didn't know these)
- `TodayScreen()` --implements--> `Раздел «Сегодня»`  [INFERRED]
  app/src/main/java/com/tikhomirov/zdorovyak/ui/TodayScreen.kt → README.md
- `ProgramSetup()` --implements--> `enum Equipment / инвентарь с шагом веса`  [INFERRED]
  app/src/main/java/com/tikhomirov/zdorovyak/ui/TrainingScreen.kt → docs/research/strength-module-spec.md
- `ClaudeClient` --implements--> `Принцип «ИИ выбирает, движок считает»`  [INFERRED]
  app/src/main/java/com/tikhomirov/zdorovyak/ai/ClaudeClient.kt → docs/research/strength-module-spec.md
- `AppDatabase` --implements--> `Модели данных (Room) силового модуля`  [INFERRED]
  app/src/main/java/com/tikhomirov/zdorovyak/data/Database.kt → docs/research/strength-module-spec.md
- `FoodEntry` --implements--> `Раздел «Питание» (штрихкод, фото тарелки, справочник)`  [INFERRED]
  app/src/main/java/com/tikhomirov/zdorovyak/data/Entities.kt → README.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **ИИ выбирает, движок считает: генерация силового плана** — docs_research_strength_module_spec_ии_выбирает_движок_считает, docs_research_strength_module_spec_системный_промпт, docs_research_strength_module_spec_json_схема_плана, docs_research_strength_module_spec_валидация_плана, docs_research_strength_module_spec_каталог_упражнений, app_src_main_java_com_tikhomirov_zdorovyak_strength_engine_planvalidator, app_src_main_java_com_tikhomirov_zdorovyak_ai_claudeclient_claudeclient [INFERRED 0.85]
- **Цикл обратной связи и прогрессии силового модуля** — docs_research_strength_module_spec_обратная_связь_тяжело_нормально_легко, docs_research_strength_module_spec_правила_прогрессии_по_упражнению, docs_research_strength_module_spec_exercise_state, docs_research_strength_module_spec_progress_function, app_src_main_java_com_tikhomirov_zdorovyak_strength_engine_engine, app_src_main_java_com_tikhomirov_zdorovyak_data_entities_exercisefeedback [INFERRED 0.85]
- **Адаптация плана JogJoy по логам** — docs_research_jogjoy_training_engine_proposeadaptation, docs_research_jogjoy_training_engine_красные_флаги, docs_research_jogjoy_training_engine_пропуски, docs_research_jogjoy_training_engine_усталость_по_rpe, docs_research_jogjoy_training_engine_боль_и_болезнь, docs_research_jogjoy_training_engine_workoutlog, docs_research_jogjoy_training_engine_adapt_ts [EXTRACTED 1.00]

## Communities (26 total, 9 thin omitted)

### Community 0 - "Room: база и DAO"
Cohesion: 0.05
Nodes (28): AppDatabase, BodyDao, ChatDao, FoodDao, GarminDao, TrainingDao, ChatMessage, ExerciseFeedback (+20 more)

### Community 2 - "UI: навигация и экраны"
Cohesion: 0.08
Nodes (38): Text, AppRoot(), Quick, FOOD_BARCODE, FOOD_MANUAL, FOOD_PHOTO, OTHER_ACTIVITY, WEIGHT (+30 more)

### Community 3 - "JogJoy: движок плана бега"
Cohesion: 0.06
Nodes (40): src/engine/adapt.ts, src/app/workout/done.tsx, src/engine/generate.ts, Health Connect (ExerciseSessionRecord, DistanceRecord), src/lib/healthImport.ts, src/lib/hr.ts, JogJoy (antontikhomirov/jogjoy_app), SYSTEM_PROMPT чата-коуча (server/jogjoy_chat.py) (+32 more)

### Community 4 - "Силовой движок: прогрессия"
Cohesion: 0.12
Nodes (11): fmt(), Engine, ExResult, ExState, Feel, EASY, HARD, NORMAL (+3 more)

### Community 5 - "Activity, тема и смоук-тест"
Cohesion: 0.07
Nodes (3): MainActivity, ZTheme(), SmokeTest

### Community 7 - "Garmin: вход и токены"
Cohesion: 0.14
Nodes (7): AuthFailed, GarminException, GarminTokens, login(), mobileLogin(), postJson(), python-библиотека garminconnect

### Community 8 - "Настройки и профиль"
Cohesion: 0.20
Nodes (3): Profile, Settings, Настройки (ключ Claude API, нормы КБЖУ, рост, цель, вход в Garmin)

### Community 9 - "ИИ-коуч и память"
Cohesion: 0.21
Nodes (4): Msg, CoachRepo, Claude API, Раздел «Коуч» (чат с Claude, КПТ/коучинг)

### Community 10 - "Валидация силового плана"
Cohesion: 0.31
Nodes (4): PlanDay, PlanExercise, PlanValidator, ProgramPlan

### Community 12 - "Каталог: инвентарь"
Cohesion: 0.20
Nodes (10): Catalog, Equipment, BANDS, BARBELL, BENCH, DUMBBELLS, KETTLEBELL, MACHINES (+2 more)

### Community 13 - "Питание: штрихкод и фото"
Cohesion: 0.25
Nodes (6): App, NutritionRepo, PhotoItem, PhotoResult, Open Food Facts, Раздел «Питание» (штрихкод, фото тарелки, справочник)

### Community 14 - "Мышечные группы"
Cohesion: 0.18
Nodes (11): Muscle, BACK, BICEPS, CALVES, CHEST, CORE, GLUTES, HAMSTRINGS (+3 more)

### Community 15 - "Двигательные паттерны"
Cohesion: 0.18
Nodes (11): Pattern, CARRY, CORE, HINGE, ISOLATION, LUNGE, PULL_H, PULL_V (+3 more)

### Community 17 - "Суставы и ограничения"
Cohesion: 0.22
Nodes (7): Joint, ELBOW, HIP, KNEE, LOWER_BACK, SHOULDER, WRIST

### Community 18 - "Спека силового модуля"
Cohesion: 0.33
Nodes (6): enum Equipment / инвентарь с шагом веса, data class Exercise (isLarge, easier/harder, jointStress), JSON-схема плана от LLM (days, exercises, alternativeId, rationale), enum Pattern (SQUAT, HINGE, PUSH_H…), Каталог упражнений (локальный, ~60–80), Системный промпт составления силовой программы

### Community 20 - "Типы нагрузки"
Cohesion: 0.29
Nodes (7): Load, BAND, BARBELL, BODYWEIGHT, DUMBBELL, KETTLEBELL, MACHINE

### Community 21 - "Вкладки приложения"
Cohesion: 0.29
Nodes (6): Tab, BODY, COACH, FOOD, TODAY, TRAINING

## Knowledge Gaps
- **64 isolated node(s):** `Image`, `SQUAT`, `HINGE`, `LUNGE`, `PUSH_H` (+59 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 180 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **9 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AppDatabase` connect `Room: база и DAO` to `JogJoy: движок плана бега`, `Claude Vision: фото еды`, `Garmin: вход и токены`, `ИИ-коуч и память`, `Питание: штрихкод и фото`?**
  _High betweenness centrality (0.142) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `AppDatabase` (e.g. with `Модели данных (Room) силового модуля` and `Здоровяк — личное приложение здоровья`) actually correct?**
  _`AppDatabase` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Image`, `SQUAT`, `HINGE` to the rest of the system?**
  _64 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Room: база и DAO` be split into smaller, more focused modules?**
  _Cohesion score 0.051392632524708 - nodes in this community are weakly interconnected._
- **Why does `AppRoot()` connect `UI: навигация и экраны` to `Room: база и DAO`, `UI: экраны и Compose-импорты`, `Питание: штрихкод и фото`, `Activity, тема и смоук-тест`?**
  _High betweenness centrality (0.129) - this node is a cross-community bridge._
- **Are the 22 inferred relationships involving `Text` (e.g. with `AppRoot()` and `BodyScreen()`) actually correct?**
  _`Text` has 22 INFERRED edges - model-reasoned connections that need verification._
- **Should `UI: экраны и Compose-импорты` be split into smaller, more focused modules?**
  _Cohesion score 0.055333473595623815 - nodes in this community are weakly interconnected._