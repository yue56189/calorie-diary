package io.github.yue56189.caloriediary.data

/**
 * 基础代谢与每日能量需求估算。
 *
 * 公式：Mifflin-St Jeor（1990），目前临床与运动营养领域最常用的 BMR 估算式。
 * 男性：10 × 体重(kg) + 6.25 × 身高(cm) − 5 × 年龄 + 5
 * 女性：10 × 体重(kg) + 6.25 × 身高(cm) − 5 × 年龄 − 161
 *
 * 估算值仅作参考，个体差异很大。
 */
enum class Sex(val label: String) {
    MALE("男"), FEMALE("女")
}

enum class ActivityLevel(val factor: Double, val label: String, val hint: String) {
    SEDENTARY(1.2, "久坐", "几乎不运动，办公室为主"),
    LIGHT(1.375, "轻度活动", "每周运动 1–3 次"),
    MODERATE(1.55, "中度活动", "每周运动 3–5 次"),
    ACTIVE(1.725, "高度活动", "每周运动 6–7 次"),
    ATHLETE(1.9, "极高活动", "体力劳动或每天两练")
}

data class Profile(
    val sex: Sex = Sex.FEMALE,
    val age: Int = 28,
    val heightCm: Double = 165.0,
    val weightKg: Double = 62.0,
    val activity: ActivityLevel = ActivityLevel.LIGHT,
    /** 减脂热量缺口百分比（相对 TDEE）。 */
    val deficitPercent: Int = 15
) {
    val bmr: Double
        get() = 10 * weightKg + 6.25 * heightCm - 5 * age +
                if (sex == Sex.MALE) 5.0 else -161.0

    val tdee: Double
        get() = bmr * activity.factor

    /** 每日可摄入热量（已扣除缺口，且不低于 BMR 的 0.95 与 1200 kcal 下限）。 */
    val targetKcal: Double
        get() = (tdee * (1 - deficitPercent / 100.0)).coerceAtLeast(1200.0)
}

object NutritionMath {

    /**
     * 依据当前档案给出一个可用的营养目标：
     * 蛋白质 1.8 g/kg（减脂期保肌）、脂肪占总热量 25%、剩余热量给碳水。
     */
    fun recommend(profile: Profile): NutritionGoal {
        val kcal = profile.targetKcal
        val protein = 1.8 * profile.weightKg
        val fat = kcal * 0.25 / 9.0
        val carbs = ((kcal - protein * 4 - fat * 9) / 4.0).coerceAtLeast(0.0)
        return NutritionGoal(
            kcal = kcal.round1(),
            protein = protein.round1(),
            fat = fat.round1(),
            carbs = carbs.round1()
        )
    }

    /** 三大营养素折算的热量，用于校验用户手填的目标是否自洽。 */
    fun macroKcal(goal: NutritionGoal): Double =
        goal.protein * 4 + goal.fat * 9 + goal.carbs * 4

    /**
     * 「基础代谢 + 运动消耗」口径的每日热量预算。
     *
     * 不用活动系数去推 TDEE —— 本应用让用户自己填当天运动消耗，
     * 预算 = BMR + 运动消耗，缺口靠这个差值自然产生（想吃得更少就少填运动量）。
     */
    fun budget(bmr: Double, exerciseKcal: Double): Double =
        (bmr + exerciseKcal).coerceAtLeast(0.0)

    /**
     * 把默认营养目标等比缩放到新的热量口径，三者的热量占比保持不变。
     * 这样用户不用为了改口径重新配一遍蛋白/脂肪/碳水。
     */
    fun scaleTo(goal: NutritionGoal, kcal: Double): NutritionGoal {
        if (kcal <= 0.0) return goal.copy(kcal = 0.0)
        if (goal.kcal <= 0.0) return goal.copy(kcal = kcal.round1())
        val r = kcal / goal.kcal
        return NutritionGoal(
            kcal = kcal.round1(),
            protein = (goal.protein * r).round1(),
            fat = (goal.fat * r).round1(),
            carbs = (goal.carbs * r).round1()
        )
    }

    fun Double.round1(): Double = kotlin.math.round(this * 10) / 10.0
}

/** BMI，附带一个中文分级文案。 */
fun bmiOf(heightCm: Double, weightKg: Double): Double {
    if (heightCm <= 0) return 0.0
    val m = heightCm / 100.0
    return weightKg / (m * m)
}

fun bmiLabel(bmi: Double): String = when {
    bmi <= 0 -> "—"
    bmi < 18.5 -> "偏瘦"
    bmi < 24 -> "正常"
    bmi < 28 -> "超重"
    else -> "肥胖"
}
