package io.github.yue56189.caloriediary.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.yue56189.caloriediary.data.FoodItem
import io.github.yue56189.caloriediary.data.Meal
import io.github.yue56189.caloriediary.data.PortionUnit
import io.github.yue56189.caloriediary.data.PortionUnits
import io.github.yue56189.caloriediary.ui.MainViewModel
import io.github.yue56189.caloriediary.ui.fmt0
import io.github.yue56189.caloriediary.ui.fmtTrim
import kotlinx.coroutines.delay

/** 「米饭 150」「鸡胸肉一掌」「苹果」这类输入的解析结果。 */
private data class QuickParse(
    val name: String,
    val grams: Double?,
    val unit: PortionUnit?
) {
    val hasPortion: Boolean get() = grams != null || unit != null
}

private val NUMBER_TAIL = Regex("""^(.*?)[\s,，]*([0-9]+(?:\.[0-9]+)?)\s*(g|G|克|ml|ML|毫升)?$""")

private fun parseQuick(text: String): QuickParse {
    val t = text.trim()
    if (t.isEmpty()) return QuickParse("", null, null)

    // 先看结尾是不是徒手单位（一拳 / 一掌 / 一捧 …）
    PortionUnit.entries.firstOrNull { t.endsWith(it.label) }?.let { unit ->
        val name = t.removeSuffix(unit.label).trim().removeSuffix("一").trim()
        return QuickParse(name.ifBlank { t.removeSuffix(unit.label).trim() }, null, unit)
    }

    NUMBER_TAIL.find(t)?.let { m ->
        val name = m.groupValues[1].trim()
        val grams = m.groupValues[2].toDoubleOrNull()
        if (name.isNotEmpty() && grams != null && grams > 0) return QuickParse(name, grams, null)
    }
    return QuickParse(t, null, null)
}

/**
 * 快速记录：一句话搞定「食物 + 份量」。
 *
 * 输入「米饭 150」或「鸡胸肉 一掌」，App 自动在本地库/联网源里找最接近的食物，
 * 折算热量后一键记入某一餐。没写份量时用该食物的常用份量。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickAddSheet(
    vm: MainViewModel,
    initialMeal: Meal,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var text by remember { mutableStateOf("") }
    var meal by remember { mutableStateOf(initialMeal) }
    var candidates by remember { mutableStateOf<List<FoodItem>>(emptyList()) }
    var pickedIndex by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var loadedFor by remember { mutableStateOf("") }
    var gramsText by remember { mutableStateOf("") }
    var userEditedGrams by remember { mutableStateOf(false) }

    val parsed = parseQuick(text)
    val picked = candidates.getOrNull(pickedIndex)

    // 输入停止 400ms 后再查，避免每敲一个字就发一次请求
    LaunchedEffect(parsed.name) {
        val name = parsed.name
        if (name.length < 1) {
            candidates = emptyList(); pickedIndex = 0; loadedFor = ""; loading = false
            return@LaunchedEffect
        }
        if (name == loadedFor) return@LaunchedEffect
        delay(400)
        loading = true
        val result = runCatching { vm.quickLookup(name) }.getOrDefault(emptyList())
        candidates = result
        pickedIndex = 0
        loadedFor = name
        loading = false
        userEditedGrams = false
    }

    // 换食物时刷新份量显示（用户手动改过就不覆盖）
    LaunchedEffect(picked?.id, picked?.name, parsed.grams, parsed.unit) {
        val food = picked ?: return@LaunchedEffect
        if (userEditedGrams && gramsText.isNotBlank()) return@LaunchedEffect
        val g = when {
            parsed.grams != null -> parsed.grams
            parsed.unit != null -> PortionUnits.gramsFor(parsed.unit, food.category)
            food.portionGrams > 0 -> food.portionGrams
            else -> 100.0
        }
        gramsText = g.fmtTrim()
    }

    val grams = gramsText.toDoubleOrNull() ?: 0.0
    val ratio = grams / 100.0

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("快速记录", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "输入「食物名 + 份量」，例如「米饭 150」「鸡胸肉 一掌」「苹果」。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(40); userEditedGrams = false },
                placeholder = { Text("米饭 150") },
                label = { Text("食物与份量") },
                singleLine = true,
                trailingIcon = {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (parsed.name.isNotBlank() && parsed.hasPortion) {
                Spacer(Modifier.height(6.dp))
                MetaText(
                    "识别为：${parsed.name} · " +
                            (parsed.grams?.let { "${it.fmtTrim()} g" }
                                ?: "${parsed.unit?.label}（按食物类别换算）")
                )
            }

            // ---------------------------------------------------- 候选
            if (candidates.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "匹配到 ${candidates.size} 条，点一下换一条",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                candidates.take(6).forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pickedIndex = index }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (index == pickedIndex) Icons.Outlined.CheckCircle
                            else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (index == pickedIndex) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (index == pickedIndex) FontWeight.Medium else FontWeight.Normal,
                                maxLines = 1
                            )
                            MetaText("${item.kcal.fmt0()} kcal/100g · ${item.category} · ${item.source}")
                        }
                    }
                }
            } else if (!loading && parsed.name.isNotBlank() && loadedFor == parsed.name) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "没找到「${parsed.name}」。换个说法，或到食物库里自己录一条。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---------------------------------------------------- 份量
            if (picked != null) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = gramsText,
                        onValueChange = { input ->
                            gramsText = input.filter { it.isDigit() || it == '.' }.take(6)
                            userEditedGrams = true
                        },
                        label = { Text("份量") },
                        suffix = { Text("g") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${(picked.kcal * ratio).fmt0()} kcal",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        MetaText(
                            "蛋白 ${(picked.protein * ratio).fmtTrim()} · " +
                                    "脂肪 ${(picked.fat * ratio).fmtTrim()} · " +
                                    "碳水 ${(picked.carbs * ratio).fmtTrim()}"
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PortionUnits.availableFor(picked.category, picked.name).forEach { (unit, gramsOfUnit) ->
                        FilterChip(
                            selected = parsed.unit == unit,
                            onClick = {
                                gramsText = gramsOfUnit.fmtTrim()
                                userEditedGrams = true
                            },
                            label = { Text("${unit.label} ${gramsOfUnit.fmt0()}g") }
                        )
                    }
                }
            }

            // ---------------------------------------------------- 餐次
            Spacer(Modifier.height(16.dp))
            Text("记到哪一餐", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Meal.entries.forEach { m ->
                    FilterChip(
                        selected = meal == m,
                        onClick = { meal = m },
                        label = { Text(m.title) }
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    picked?.let {
                        vm.addEntryDirect(meal, it, grams)
                        onDismiss()
                    }
                },
                enabled = picked != null && grams > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (picked != null) "加入${meal.title} · ${(picked.kcal * ratio).fmt0()} kcal"
                    else "先输入食物名"
                )
            }
        }
    }
}
