package io.github.yue56189.caloriediary.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 食物库条目。所有营养值统一以「每 100 克可食部」为基准。
 *
 * 内置数据来自 USDA FoodData Central（SR Legacy 数据集，公有领域）。
 * 用户自定义条目 [isCustom] 为 true。
 */
@Entity(
    tableName = "foods",
    indices = [Index("name"), Index("category"), Index("barcode")]
)
data class FoodItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nameEn: String = "",
    val category: String = "其他",
    val kcal: Double,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val fiber: Double = 0.0,
    val sodium: Double = 0.0,
    /** 常用份量名称，例如「1 个中等」。为空表示没有份量快捷项。 */
    val portionLabel: String = "",
    /** 对应 [portionLabel] 的克数。 */
    val portionGrams: Double = 0.0,
    val isCustom: Boolean = false,
    val barcode: String = "",
    /** 数据来源标识，展示在详情里。 */
    val source: String = "USDA FoodData Central"
)

/**
 * 一条打卡记录。营养值在写入时快照，避免食物库被修改后历史数据漂移。
 */
@Entity(
    tableName = "diary_entries",
    indices = [Index("date"), Index("meal")]
)
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 以 epochDay 表示的自然日。 */
    val date: Long,
    /** 0 早餐 / 1 午餐 / 2 晚餐 / 3 加餐 */
    val meal: Int,
    val foodId: Long? = null,
    val name: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 某一天的目标。查不到时回退到设置中的默认目标，
 * 这样既能「每天单独设目标」，也不必天天重复填写。
 */
@Entity(tableName = "daily_goals")
data class DailyGoal(
    @PrimaryKey val date: Long,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double
)

/** 一次摄入合计。 */
data class NutritionTotal(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0
) {
    operator fun plus(other: NutritionTotal) = NutritionTotal(
        kcal + other.kcal,
        protein + other.protein,
        fat + other.fat,
        carbs + other.carbs
    )

    companion object {
        fun of(entries: List<DiaryEntry>): NutritionTotal = entries.fold(NutritionTotal()) { acc, e ->
            acc + NutritionTotal(e.kcal, e.protein, e.fat, e.carbs)
        }
    }
}

/** 目标值。 */
data class NutritionGoal(
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double
)

/** 餐次。 */
enum class Meal(val code: Int, val title: String) {
    BREAKFAST(0, "早餐"),
    LUNCH(1, "午餐"),
    DINNER(2, "晚餐"),
    SNACK(3, "加餐");

    companion object {
        fun of(code: Int): Meal = entries.firstOrNull { it.code == code } ?: SNACK
    }
}
