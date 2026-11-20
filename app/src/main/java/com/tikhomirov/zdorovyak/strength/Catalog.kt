package com.tikhomirov.zdorovyak.strength

import kotlinx.serialization.Serializable

enum class Pattern { SQUAT, HINGE, LUNGE, PUSH_H, PUSH_V, PULL_H, PULL_V, CORE, CARRY, ISOLATION }
enum class Muscle { QUADS, GLUTES, HAMSTRINGS, CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, CORE, CALVES }
enum class Joint { KNEE, LOWER_BACK, SHOULDER, ELBOW, WRIST, HIP }

/** Чем нагружается упражнение: от этого зависит набор доступных весов и шаг. */
enum class Load { DUMBBELL, KETTLEBELL, BARBELL, MACHINE, BODYWEIGHT, BAND }

enum class Equipment { DUMBBELLS, KETTLEBELL, BARBELL, BENCH, PULLUP_BAR, BANDS, MACHINES }

data class Exercise(
    val id: String,
    val nameRu: String,
    val pattern: Pattern,
    val muscles: List<Muscle>,
    val equipment: Set<Equipment>,
    val load: Load,
    val isLarge: Boolean,
    val easier: String? = null,
    val harder: String? = null,
    val joints: Set<Joint> = emptySet(),
    val cue: String = "",
)

/** Инвентарь человека. Без конкретных весов и шага прогрессию не посчитать. */
@Serializable
data class Inventory(
    val dumbbells: Boolean = true,
    val dbMin: Double = 2.0,
    val dbMax: Double = 24.0,
    val dbStep: Double = 2.0,
    val kettlebells: List<Double> = emptyList(),
    val barbell: Boolean = false,
    val barbellMin: Double = 20.0,
    val barbellMax: Double = 120.0,
    val barbellStep: Double = 2.5,
    val bench: Boolean = true,
    val pullupBar: Boolean = false,
    val bands: Boolean = false,
    val machines: Boolean = false,
    val machineStep: Double = 5.0,
) {
    fun equipment(): Set<Equipment> = buildSet {
        if (dumbbells) add(Equipment.DUMBBELLS)
        if (kettlebells.isNotEmpty()) add(Equipment.KETTLEBELL)
        if (barbell) add(Equipment.BARBELL)
        if (bench) add(Equipment.BENCH)
        if (pullupBar) add(Equipment.PULLUP_BAR)
        if (bands) add(Equipment.BANDS)
        if (machines) add(Equipment.MACHINES)
    }

    /** Все доступные веса для типа нагрузки, по возрастанию. 0 = без отягощения. */
    fun weightsFor(load: Load): List<Double> = when (load) {
        Load.DUMBBELL -> steps(dbMin, dbMax, dbStep)
        Load.KETTLEBELL -> kettlebells.sorted()
        Load.BARBELL -> steps(barbellMin, barbellMax, barbellStep)
        Load.MACHINE -> steps(machineStep, 150.0, machineStep)
        Load.BODYWEIGHT, Load.BAND -> listOf(0.0)
    }

    private fun steps(from: Double, to: Double, step: Double): List<Double> {
        if (step <= 0 || to < from) return listOf(from)
        val out = mutableListOf<Double>()
        var w = from
        while (w <= to + 1e-6) { out += Math.round(w * 100) / 100.0; w += step }
        return out
    }

    fun describe(): String = buildList {
        if (dumbbells) add("гантели разборные ${fmt(dbMin)}–${fmt(dbMax)} кг, шаг ${fmt(dbStep)} кг")
        if (kettlebells.isNotEmpty()) add("гири: ${kettlebells.joinToString { fmt(it) }} кг")
        if (barbell) add("штанга ${fmt(barbellMin)}–${fmt(barbellMax)} кг, шаг ${fmt(barbellStep)} кг")
        if (bench) add("скамья")
        if (pullupBar) add("турник")
        if (bands) add("резинки")
        if (machines) add("тренажёры/блоки, шаг ${fmt(machineStep)} кг")
    }.joinToString("; ").ifEmpty { "только вес тела" }
}

fun fmt(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else "%.1f".format(d).replace(',', '.')

object Catalog {
    private val E = Equipment.entries.associateBy { it }
    private fun eq(vararg e: Equipment) = e.toSet()

    val all: List<Exercise> = listOf(
        // Присед / колено-доминантные
        Exercise("goblet_squat", "Гоблет-присед с гантелью", Pattern.SQUAT, listOf(Muscle.QUADS, Muscle.GLUTES), eq(Equipment.DUMBBELLS), Load.DUMBBELL, true,
            easier = "box_squat_bw", harder = "front_squat_bb", joints = setOf(Joint.KNEE), cue = "Гантель у груди, колени по линии носков, спина ровная"),
        Exercise("goblet_squat_kb", "Гоблет-присед с гирей", Pattern.SQUAT, listOf(Muscle.QUADS, Muscle.GLUTES), eq(Equipment.KETTLEBELL), Load.KETTLEBELL, true,
            easier = "box_squat_bw", joints = setOf(Joint.KNEE)),
        Exercise("box_squat_bw", "Присед на скамью/стул без веса", Pattern.SQUAT, listOf(Muscle.QUADS, Muscle.GLUTES), emptySet(), Load.BODYWEIGHT, true,
            harder = "goblet_squat", joints = setOf(Joint.KNEE), cue = "Садимся до касания, встаём без рывка"),
        Exercise("bw_squat", "Присед с весом тела", Pattern.SQUAT, listOf(Muscle.QUADS, Muscle.GLUTES), emptySet(), Load.BODYWEIGHT, true,
            easier = "box_squat_bw", joints = setOf(Joint.KNEE)),
        Exercise("back_squat_bb", "Присед со штангой", Pattern.SQUAT, listOf(Muscle.QUADS, Muscle.GLUTES), eq(Equipment.BARBELL), Load.BARBELL, true,
            easier = "goblet_squat", joints = setOf(Joint.KNEE, Joint.LOWER_BACK)),
        Exercise("front_squat_bb", "Фронтальный присед со штангой", Pattern.SQUAT, listOf(Muscle.QUADS, Muscle.GLUTES), eq(Equipment.BARBELL), Load.BARBELL, true,
            easier = "goblet_squat", joints = setOf(Joint.KNEE)),
        Exercise("leg_press", "Жим ногами в тренажёре", Pattern.SQUAT, listOf(Muscle.QUADS, Muscle.GLUTES), eq(Equipment.MACHINES), Load.MACHINE, true,
            joints = setOf(Joint.KNEE)),
        // Выпады
        Exercise("split_squat_db", "Сплит-присед с гантелями", Pattern.LUNGE, listOf(Muscle.QUADS, Muscle.GLUTES), eq(Equipment.DUMBBELLS), Load.DUMBBELL, true,
            easier = "split_squat_bw", harder = "bulgarian_db", joints = setOf(Joint.KNEE)),
        Exercise("split_squat_bw", "Сплит-присед без веса", Pattern.LUNGE, listOf(Muscle.QUADS, Muscle.GLUTES), emptySet(), Load.BODYWEIGHT, true,
            harder = "split_squat_db", joints = setOf(Joint.KNEE)),
        Exercise("reverse_lunge_db", "Выпады назад с гантелями", Pattern.LUNGE, listOf(Muscle.QUADS, Muscle.GLUTES), eq(Equipment.DUMBBELLS), Load.DUMBBELL, true,
            easier = "split_squat_bw", joints = setOf(Joint.KNEE)),
        Exercise("bulgarian_db", "Болгарский сплит-присед с гантелями", Pattern.LUNGE, listOf(Muscle.QUADS, Muscle.GLUTES), eq(Equipment.DUMBBELLS, Equipment.BENCH), Load.DUMBBELL, true,
            easier = "split_squat_db", joints = setOf(Joint.KNEE)),
        Exercise("step_up_db", "Зашагивания на скамью с гантелями", Pattern.LUNGE, listOf(Muscle.QUADS, Muscle.GLUTES), eq(Equipment.DUMBBELLS, Equipment.BENCH), Load.DUMBBELL, true,
            easier = "split_squat_bw", joints = setOf(Joint.KNEE)),
        // Наклон (hinge)
        Exercise("rdl_db", "Румынская тяга с гантелями", Pattern.HINGE, listOf(Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.BACK), eq(Equipment.DUMBBELLS), Load.DUMBBELL, true,
            easier = "hip_bridge", harder = "rdl_bb", joints = setOf(Joint.LOWER_BACK), cue = "Таз назад, гантели скользят по бёдрам, спина нейтральна"),
        Exercise("rdl_kb", "Румынская тяга с гирей", Pattern.HINGE, listOf(Muscle.HAMSTRINGS, Muscle.GLUTES), eq(Equipment.KETTLEBELL), Load.KETTLEBELL, true,
            easier = "hip_bridge", joints = setOf(Joint.LOWER_BACK)),
        Exercise("kb_swing", "Махи гирей", Pattern.HINGE, listOf(Muscle.GLUTES, Muscle.HAMSTRINGS), eq(Equipment.KETTLEBELL), Load.KETTLEBELL, true,
            easier = "hip_bridge", joints = setOf(Joint.LOWER_BACK)),
        Exercise("rdl_bb", "Румынская тяга со штангой", Pattern.HINGE, listOf(Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.BACK), eq(Equipment.BARBELL), Load.BARBELL, true,
            easier = "rdl_db", joints = setOf(Joint.LOWER_BACK)),
        Exercise("hip_bridge", "Ягодичный мост", Pattern.HINGE, listOf(Muscle.GLUTES, Muscle.HAMSTRINGS), emptySet(), Load.BODYWEIGHT, true,
            harder = "hip_thrust_db"),
        Exercise("hip_thrust_db", "Ягодичный мост с гантелью от скамьи", Pattern.HINGE, listOf(Muscle.GLUTES, Muscle.HAMSTRINGS), eq(Equipment.DUMBBELLS, Equipment.BENCH), Load.DUMBBELL, true,
            easier = "hip_bridge"),
        Exercise("single_leg_rdl_db", "Тяга на одной ноге с гантелью", Pattern.HINGE, listOf(Muscle.HAMSTRINGS, Muscle.GLUTES), eq(Equipment.DUMBBELLS), Load.DUMBBELL, false,
            easier = "hip_bridge"),
        // Жим горизонтальный
        Exercise("db_bench", "Жим гантелей лёжа", Pattern.PUSH_H, listOf(Muscle.CHEST, Muscle.TRICEPS, Muscle.SHOULDERS), eq(Equipment.DUMBBELLS, Equipment.BENCH), Load.DUMBBELL, true,
            easier = "pushup_incline", joints = setOf(Joint.SHOULDER), cue = "Лопатки сведены, локти под ~45° к корпусу"),
        Exercise("db_floor_press", "Жим гантелей лёжа на полу", Pattern.PUSH_H, listOf(Muscle.CHEST, Muscle.TRICEPS), eq(Equipment.DUMBBELLS), Load.DUMBBELL, true,
            easier = "pushup_incline", joints = setOf(Joint.SHOULDER)),
        Exercise("bb_bench", "Жим штанги лёжа", Pattern.PUSH_H, listOf(Muscle.CHEST, Muscle.TRICEPS, Muscle.SHOULDERS), eq(Equipment.BARBELL, Equipment.BENCH), Load.BARBELL, true,
            easier = "db_bench", joints = setOf(Joint.SHOULDER)),
        Exercise("pushup", "Отжимания", Pattern.PUSH_H, listOf(Muscle.CHEST, Muscle.TRICEPS), emptySet(), Load.BODYWEIGHT, true,
            easier = "pushup_incline", harder = "pushup_feet_elevated", joints = setOf(Joint.SHOULDER, Joint.WRIST)),
        Exercise("pushup_incline", "Отжимания от скамьи/стола", Pattern.PUSH_H, listOf(Muscle.CHEST, Muscle.TRICEPS), emptySet(), Load.BODYWEIGHT, true,
            harder = "pushup", joints = setOf(Joint.WRIST)),
        Exercise("pushup_feet_elevated", "Отжимания с ногами на опоре", Pattern.PUSH_H, listOf(Muscle.CHEST, Muscle.TRICEPS, Muscle.SHOULDERS), emptySet(), Load.BODYWEIGHT, true,
            easier = "pushup", joints = setOf(Joint.SHOULDER, Joint.WRIST)),
        Exercise("chest_press_machine", "Жим в тренажёре от груди", Pattern.PUSH_H, listOf(Muscle.CHEST, Muscle.TRICEPS), eq(Equipment.MACHINES), Load.MACHINE, true),
        // Жим вертикальный
        Exercise("db_ohp_seated", "Жим гантелей сидя", Pattern.PUSH_V, listOf(Muscle.SHOULDERS, Muscle.TRICEPS), eq(Equipment.DUMBBELLS, Equipment.BENCH), Load.DUMBBELL, false,
            easier = "landmine_like_db", joints = setOf(Joint.SHOULDER)),
        Exercise("db_ohp_standing", "Жим гантелей стоя", Pattern.PUSH_V, listOf(Muscle.SHOULDERS, Muscle.TRICEPS, Muscle.CORE), eq(Equipment.DUMBBELLS), Load.DUMBBELL, false,
            easier = "landmine_like_db", joints = setOf(Joint.SHOULDER, Joint.LOWER_BACK)),
        Exercise("landmine_like_db", "Жим одной гантели под углом (нейтральный хват)", Pattern.PUSH_V, listOf(Muscle.SHOULDERS, Muscle.CHEST), eq(Equipment.DUMBBELLS), Load.DUMBBELL, false,
            harder = "db_ohp_standing"),
        Exercise("kb_press", "Жим гири стоя", Pattern.PUSH_V, listOf(Muscle.SHOULDERS, Muscle.TRICEPS), eq(Equipment.KETTLEBELL), Load.KETTLEBELL, false,
            joints = setOf(Joint.SHOULDER)),
        Exercise("pike_pushup", "Отжимания «домиком»", Pattern.PUSH_V, listOf(Muscle.SHOULDERS, Muscle.TRICEPS), emptySet(), Load.BODYWEIGHT, false,
            easier = "pushup_incline", joints = setOf(Joint.SHOULDER, Joint.WRIST)),
        Exercise("bb_ohp", "Жим штанги стоя", Pattern.PUSH_V, listOf(Muscle.SHOULDERS, Muscle.TRICEPS), eq(Equipment.BARBELL), Load.BARBELL, true,
            easier = "db_ohp_seated", joints = setOf(Joint.SHOULDER, Joint.LOWER_BACK)),
        Exercise("band_press_v", "Жим резинки вверх", Pattern.PUSH_V, listOf(Muscle.SHOULDERS), eq(Equipment.BANDS), Load.BAND, false),
        // Тяга горизонтальная
        Exercise("db_row", "Тяга гантели в наклоне с опорой", Pattern.PULL_H, listOf(Muscle.BACK, Muscle.BICEPS), eq(Equipment.DUMBBELLS), Load.DUMBBELL, true,
            cue = "Опора на скамью, тянем локоть к бедру"),
        Exercise("db_row_2arm", "Тяга двух гантелей в наклоне", Pattern.PULL_H, listOf(Muscle.BACK, Muscle.BICEPS), eq(Equipment.DUMBBELLS), Load.DUMBBELL, true,
            easier = "db_row", joints = setOf(Joint.LOWER_BACK)),
        Exercise("chest_supported_row", "Тяга гантелей лёжа грудью на наклонной скамье", Pattern.PULL_H, listOf(Muscle.BACK, Muscle.BICEPS), eq(Equipment.DUMBBELLS, Equipment.BENCH), Load.DUMBBELL, true),
        Exercise("bb_row", "Тяга штанги в наклоне", Pattern.PULL_H, listOf(Muscle.BACK, Muscle.BICEPS), eq(Equipment.BARBELL), Load.BARBELL, true,
            easier = "db_row", joints = setOf(Joint.LOWER_BACK)),
        Exercise("kb_row", "Тяга гири в наклоне", Pattern.PULL_H, listOf(Muscle.BACK, Muscle.BICEPS), eq(Equipment.KETTLEBELL), Load.KETTLEBELL, true),
        Exercise("band_row", "Тяга резинки к поясу", Pattern.PULL_H, listOf(Muscle.BACK, Muscle.BICEPS), eq(Equipment.BANDS), Load.BAND, false),
        Exercise("inverted_row", "Австралийские подтягивания", Pattern.PULL_H, listOf(Muscle.BACK, Muscle.BICEPS), eq(Equipment.PULLUP_BAR), Load.BODYWEIGHT, true),
        Exercise("cable_row", "Тяга блока к поясу", Pattern.PULL_H, listOf(Muscle.BACK, Muscle.BICEPS), eq(Equipment.MACHINES), Load.MACHINE, true),
        // Тяга вертикальная
        Exercise("pullup", "Подтягивания", Pattern.PULL_V, listOf(Muscle.BACK, Muscle.BICEPS), eq(Equipment.PULLUP_BAR), Load.BODYWEIGHT, true,
            easier = "pullup_band", joints = setOf(Joint.SHOULDER, Joint.ELBOW)),
        Exercise("pullup_band", "Подтягивания с резинкой", Pattern.PULL_V, listOf(Muscle.BACK, Muscle.BICEPS), eq(Equipment.PULLUP_BAR, Equipment.BANDS), Load.BODYWEIGHT, true,
            easier = "pullup_negative", harder = "pullup", joints = setOf(Joint.SHOULDER)),
        Exercise("pullup_negative", "Негативные подтягивания", Pattern.PULL_V, listOf(Muscle.BACK, Muscle.BICEPS), eq(Equipment.PULLUP_BAR), Load.BODYWEIGHT, true,
            harder = "pullup", joints = setOf(Joint.SHOULDER)),
        Exercise("band_pulldown", "Тяга резинки сверху", Pattern.PULL_V, listOf(Muscle.BACK, Muscle.BICEPS), eq(Equipment.BANDS), Load.BAND, false),
        Exercise("db_pullover", "Пуловер с гантелью", Pattern.PULL_V, listOf(Muscle.BACK, Muscle.CHEST), eq(Equipment.DUMBBELLS, Equipment.BENCH), Load.DUMBBELL, false,
            joints = setOf(Joint.SHOULDER)),
        Exercise("lat_pulldown", "Тяга верхнего блока", Pattern.PULL_V, listOf(Muscle.BACK, Muscle.BICEPS), eq(Equipment.MACHINES), Load.MACHINE, true),
        // Кор
        Exercise("plank", "Планка (сек)", Pattern.CORE, listOf(Muscle.CORE), emptySet(), Load.BODYWEIGHT, false,
            harder = "dead_bug", cue = "Повторы = секунды удержания / 5"),
        Exercise("dead_bug", "«Мёртвый жук»", Pattern.CORE, listOf(Muscle.CORE), emptySet(), Load.BODYWEIGHT, false, easier = "plank"),
        Exercise("side_plank", "Боковая планка (сек)", Pattern.CORE, listOf(Muscle.CORE), emptySet(), Load.BODYWEIGHT, false),
        Exercise("bird_dog", "«Птица-собака»", Pattern.CORE, listOf(Muscle.CORE, Muscle.BACK), emptySet(), Load.BODYWEIGHT, false),
        Exercise("pallof_band", "Паллоф-пресс с резинкой", Pattern.CORE, listOf(Muscle.CORE), eq(Equipment.BANDS), Load.BAND, false),
        // Переноски
        Exercise("farmer_carry_db", "Прогулка фермера с гантелями", Pattern.CARRY, listOf(Muscle.CORE, Muscle.BACK), eq(Equipment.DUMBBELLS), Load.DUMBBELL, true),
        Exercise("suitcase_carry_db", "Перенос гантели в одной руке", Pattern.CARRY, listOf(Muscle.CORE), eq(Equipment.DUMBBELLS), Load.DUMBBELL, false),
        // Изоляция
        Exercise("db_curl", "Сгибания рук с гантелями", Pattern.ISOLATION, listOf(Muscle.BICEPS), eq(Equipment.DUMBBELLS), Load.DUMBBELL, false, joints = setOf(Joint.ELBOW)),
        Exercise("hammer_curl", "Молотковые сгибания", Pattern.ISOLATION, listOf(Muscle.BICEPS), eq(Equipment.DUMBBELLS), Load.DUMBBELL, false),
        Exercise("db_triceps_ext", "Разгибания рук с гантелью из-за головы", Pattern.ISOLATION, listOf(Muscle.TRICEPS), eq(Equipment.DUMBBELLS), Load.DUMBBELL, false, joints = setOf(Joint.ELBOW, Joint.SHOULDER)),
        Exercise("bench_dips", "Обратные отжимания от скамьи", Pattern.ISOLATION, listOf(Muscle.TRICEPS), eq(Equipment.BENCH), Load.BODYWEIGHT, false, joints = setOf(Joint.SHOULDER)),
        Exercise("lateral_raise", "Махи гантелями в стороны", Pattern.ISOLATION, listOf(Muscle.SHOULDERS), eq(Equipment.DUMBBELLS), Load.DUMBBELL, false, joints = setOf(Joint.SHOULDER)),
        Exercise("rear_delt_fly", "Разведения в наклоне", Pattern.ISOLATION, listOf(Muscle.SHOULDERS, Muscle.BACK), eq(Equipment.DUMBBELLS), Load.DUMBBELL, false),
        Exercise("band_face_pull", "Тяга резинки к лицу", Pattern.ISOLATION, listOf(Muscle.SHOULDERS, Muscle.BACK), eq(Equipment.BANDS), Load.BAND, false),
        Exercise("calf_raise_db", "Подъёмы на носки с гантелями", Pattern.ISOLATION, listOf(Muscle.CALVES), eq(Equipment.DUMBBELLS), Load.DUMBBELL, false),
    )

    private val byId = all.associateBy { it.id }
    operator fun get(id: String): Exercise? = byId[id]

    fun available(inv: Inventory): List<Exercise> {
        val have = inv.equipment()
        return all.filter { have.containsAll(it.equipment) }
    }

    /** Грубое сопоставление текста ограничений с суставами. */
    fun jointsFromText(text: String): Set<Joint> {
        val t = text.lowercase()
        return buildSet {
            if ("колен" in t) add(Joint.KNEE)
            if ("поясниц" in t || "спин" in t || "грыж" in t) add(Joint.LOWER_BACK)
            if ("плеч" in t) add(Joint.SHOULDER)
            if ("локот" in t || "локт" in t) add(Joint.ELBOW)
            if ("запяст" in t || "кист" in t) add(Joint.WRIST)
            if ("тазобед" in t || "бедр" in t) add(Joint.HIP)
        }
    }
}
