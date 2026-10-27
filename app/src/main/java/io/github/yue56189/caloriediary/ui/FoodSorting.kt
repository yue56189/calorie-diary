package io.github.yue56189.caloriediary.ui

import io.github.yue56189.caloriediary.data.FoodItem
import java.text.Collator
import java.util.Locale

/**
 * 食物库的分类展示顺序。中文名称在分类内按拼音排序（Collator + Locale.CHINA），
 * 而不是按 Unicode 码点，否则「白米饭」会排在「白面包」前面这种反直觉的结果。
 */
val FoodCategoryOrder = listOf(
    "主食", "肉蛋", "水产", "蔬菜", "水果", "奶制品",
    "豆制品", "坚果", "油脂", "饮料", "零食甜点", "快餐", "自定义"
)

fun foodCategoryRank(category: String): Int =
    FoodCategoryOrder.indexOf(category).let { if (it < 0) 99 else it }

private val chinaCollator: Collator = Collator.getInstance(Locale.CHINA)

fun List<FoodItem>.sortedForDisplay(): List<FoodItem> =
    sortedWith(
        compareBy<FoodItem> { foodCategoryRank(it.category) }
            .thenBy(chinaCollator) { it.name }
    )
