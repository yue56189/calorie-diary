package io.github.yue56189.caloriediary.data

/**
 * 徒手估量单位。减脂期最实用的不是厨房秤，而是「拳头、手掌」这类体积参照——
 * 在外吃饭、食堂打菜时也能用。
 */
enum class PortionUnit(val label: String, val hint: String) {
    FIST("一拳", "约一个拳头体积，主食/水果常用"),
    PALM("一掌", "掌心大小、厚度与小指相当，肉蛋常用"),
    HANDFUL("一把", "两手捧起，蔬菜常用"),
    PINCH("一捧", "单手抓起一把，坚果常用"),
    THUMB("一拇指", "拇指第一个指节，油脂常用"),
    CUP("一杯", "标准水杯，约 240 ml"),
    SPOON("一汤匙", "中式汤勺 / 餐勺"),
    TEASPOON("一茶匙", "调味用小勺，约 5 ml"),
    SLICE("一片", "切片面包、火腿那样的单片"),
    PIECE("一个", "整个食物，如一个鸡蛋、一个苹果")
}

/**
 * 单位 → 克数的换算表。
 *
 * 每个单位对应多少克取决于食物类别（一拳米饭和一掌青菜显然不是一个量）。
 * 数值取常见家庭/食堂份量的中位数，用作估算而不是精确计量。
 * `0` 表示该单位不适用于这类食物，界面上就不显示。
 */
object PortionUnits {

    private const val STAPLE = "主食"
    private const val MEAT = "肉蛋"
    private const val FISH = "水产"
    private const val VEG = "蔬菜"
    private const val FRUIT = "水果"
    private const val DAIRY = "奶制品"
    private const val BEAN = "豆制品"
    private const val NUT = "坚果"
    private const val FAT = "油脂"
    private const val DRINK = "饮料"
    private const val SNACK = "零食"
    private const val FASTFOOD = "快餐"

    private val TABLE: Map<PortionUnit, Map<String, Double>> = mapOf(
        PortionUnit.FIST to mapOf(
            STAPLE to 130.0, FRUIT to 150.0, VEG to 80.0,
            MEAT to 100.0, FISH to 100.0, BEAN to 100.0, FASTFOOD to 100.0
        ),
        PortionUnit.PALM to mapOf(
            MEAT to 100.0, FISH to 100.0, BEAN to 110.0, FASTFOOD to 100.0
        ),
        PortionUnit.HANDFUL to mapOf(
            VEG to 100.0, FRUIT to 120.0, SNACK to 40.0
        ),
        PortionUnit.PINCH to mapOf(
            NUT to 15.0, SNACK to 25.0
        ),
        PortionUnit.THUMB to mapOf(
            FAT to 10.0, NUT to 8.0, SNACK to 12.0
        ),
        PortionUnit.CUP to mapOf(
            DAIRY to 240.0, DRINK to 240.0
        ),
        PortionUnit.SPOON to mapOf(
            FAT to 13.0, DAIRY to 15.0, SNACK to 15.0
        ),
        PortionUnit.TEASPOON to mapOf(
            FAT to 4.5, SNACK to 5.0
        ),
        PortionUnit.SLICE to mapOf(
            STAPLE to 30.0, MEAT to 20.0, FISH to 20.0
        ),
        PortionUnit.PIECE to mapOf(
            STAPLE to 100.0, MEAT to 50.0, FRUIT to 180.0, SNACK to 30.0, FASTFOOD to 120.0
        )
    )

    /** 把库里的分类（含联网来源的「包装食品」「联网食物」）归一到 12 类。 */
    private fun normalize(category: String): String? = when {
        category.contains("主食") || category.contains("薯") -> STAPLE
        category.contains("肉") || category.contains("蛋") -> MEAT
        category.contains("水产") || category.contains("鱼") || category.contains("海鲜") -> FISH
        category.contains("蔬菜") || category.contains("菌") || category.contains("藻") -> VEG
        category.contains("水果") -> FRUIT
        category.contains("奶") || category.contains("乳") -> DAIRY
        category.contains("豆") -> BEAN
        category.contains("坚果") -> NUT
        category.contains("油脂") || category.contains("调味") -> FAT
        category.contains("饮料") || category.contains("酒") -> DRINK
        category.contains("零食") || category.contains("甜") -> SNACK
        category.contains("快餐") || category.contains("速食") -> FASTFOOD
        else -> null
    }

    /** 「一片」「一个」这两类只对形状明确的食物有意义，按名称再筛一道。 */
    private fun nameAllows(unit: PortionUnit, name: String): Boolean = when (unit) {
        PortionUnit.SLICE -> listOf(
            "面包", "吐司", "馒头", "饼", "火腿", "培根", "奶酪", "芝士", "午餐肉", "鲑鱼", "三文鱼"
        ).any { name.contains(it) }

        PortionUnit.PIECE -> listOf(
            "馒头", "包子", "饺子", "烧麦", "粽子", "面包", "吐司", "饼", "玉米",
            "红薯", "土豆", "紫薯", "蛋", "鸡腿", "鸡翅", "香蕉", "苹果", "梨",
            "橙", "橘", "桃", "牛油果", "猕猴桃"
        ).any { name.contains(it) }

        else -> true
    }

    /**
     * 该类别下可用的单位与折算克数，按枚举顺序（常用的在前）返回。
     * [name] 用于过滤「一片 / 一个」这类需要形状匹配的单位。
     */
    fun availableFor(category: String, name: String = ""): List<Pair<PortionUnit, Double>> {
        val key = normalize(category)
        val source = if (key == null) emptyList()
        else TABLE.mapNotNull { (unit, m) -> m[key]?.let { unit to it } }
        val list = source.ifEmpty {
            listOf(
                PortionUnit.FIST to 100.0,
                PortionUnit.PALM to 100.0,
                PortionUnit.HANDFUL to 100.0,
                PortionUnit.CUP to 240.0
            )
        }
        val filtered = if (name.isBlank()) list
        else list.filter { (unit, _) -> nameAllows(unit, name) }
        return (filtered.ifEmpty { list }).sortedBy { it.first.ordinal }
    }

    /** 单个单位的克数；类别不匹配时退回 100 g。 */
    fun gramsFor(unit: PortionUnit, category: String): Double {
        val key = normalize(category) ?: return 100.0
        return TABLE[unit]?.get(key) ?: 100.0
    }
}
