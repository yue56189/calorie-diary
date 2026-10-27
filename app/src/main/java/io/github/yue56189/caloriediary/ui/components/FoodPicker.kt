package io.github.yue56189.caloriediary.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yue56189.caloriediary.data.FoodItem
import io.github.yue56189.caloriediary.data.Meal
import io.github.yue56189.caloriediary.ui.MainViewModel
import io.github.yue56189.caloriediary.ui.fmt0
import io.github.yue56189.caloriediary.ui.fmtTrim
import io.github.yue56189.caloriediary.ui.sortedForDisplay

/** 小标签：以 · 分隔的元信息。 */
@Composable
fun MetaText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/**
 * 食物选择器：搜索 → 选食物 → 输入份量。
 * 底部固定的「联网搜索」会把关键词丢给 Open Food Facts / USDA。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodPickerSheet(
    meal: Meal,
    vm: MainViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val foods by vm.allFoods.collectAsStateWithLifecycle()
    val remote = vm.remote

    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<FoodItem?>(null) }

    val filtered = remember(query, foods) {
        val base = if (query.isBlank()) foods
        else foods.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.nameEn.contains(query, ignoreCase = true) ||
                    it.category.contains(query, ignoreCase = true)
        }
        base.sortedForDisplay().take(80)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "添加到${meal.title}",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(12.dp))

            val current = picked
            if (current == null) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        if (remote.items.isNotEmpty() || remote.error != null) vm.clearRemote()
                    },
                    placeholder = { Text("搜索食物，如 鸡胸肉 / 米饭 / 苹果") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = "清除")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (remote.loading) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("正在联网查询…", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    if (remote.items.isNotEmpty()) {
                        item {
                            Text(
                                text = remote.note.ifBlank { "联网结果" },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                        items(remote.items, key = { "r-${it.name}-${it.kcal}" }) { item ->
                            FoodRow(item = item, onClick = { picked = item })
                        }
                        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                    }

                    if (remote.error != null && !remote.loading) {
                        item {
                            Text(
                                text = "联网查询失败：${remote.error}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    if (filtered.isEmpty() && query.isNotBlank() && remote.items.isEmpty() && !remote.loading) {
                        item {
                            Text(
                                text = "本地食物库里没有「$query」，试试联网搜索。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    }

                    items(filtered, key = { "l-${it.id}" }) { item ->
                        FoodRow(item = item, onClick = { picked = item })
                    }

                    if (query.isNotBlank() || filtered.isEmpty()) {
                        item {
                            TextButton(
                                onClick = { vm.searchRemote(query) },
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Icon(Icons.Outlined.Cloud, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (query.isBlank()) "联网搜索常见食物" else "联网搜索「$query」"
                                )
                            }
                        }
                    }
                }
            } else {
                PortionEditor(
                    food = current,
                    confirmLabel = "加入${meal.title}",
                    onBack = { picked = null },
                    onConfirm = { grams ->
                        vm.addFood(meal, current, grams)
                        onDismiss()
                    }
                )
            }
        }
    }
}

/** 食物列表的一行。 */
@Composable
fun FoodRow(
    item: FoodItem,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )
            Spacer(Modifier.height(2.dp))
            MetaText(
                "${item.kcal.fmt0()} kcal/100g · 蛋白 ${item.protein.fmtTrim()} · 脂肪 ${item.fat.fmtTrim()} · 碳水 ${item.carbs.fmtTrim()}"
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            IconButton(onClick = onClick) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = "添加",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** 份量输入步骤。 */
@Composable
fun PortionEditor(
    food: FoodItem,
    confirmLabel: String,
    onBack: (() -> Unit)? = null,
    compact: Boolean = false,
    onGramsChange: ((Double) -> Unit)? = null,
    onConfirm: (Double) -> Unit
) {
    var text by remember(food.id) {
        mutableStateOf(
            if (food.portionGrams > 0) food.portionGrams.fmtTrim() else "100"
        )
    }
    val grams = text.toDoubleOrNull() ?: 0.0
    val ratio = grams / 100.0

    androidx.compose.runtime.LaunchedEffect(grams) {
        onGramsChange?.invoke(grams)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (!compact) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.Close, contentDescription = "返回")
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(food.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    MetaText(food.source.ifBlank { "自定义" })
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input.filter { it.isDigit() || it == '.' }.take(6)
            },
            label = { Text("重量（克）") },
            suffix = { Text("g") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(50.0, 100.0, 150.0, 200.0).forEach { g ->
                AssistChip(
                    onClick = { text = g.fmtTrim() },
                    label = { Text("${g.fmt0()}g") }
                )
            }
        }
        if (food.portionGrams > 0 && food.portionLabel.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            AssistChip(
                onClick = { text = food.portionGrams.fmtTrim() },
                label = { Text("${food.portionLabel}（${food.portionGrams.fmt0()}g）") }
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${(food.kcal * ratio).fmt0()} kcal",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                MetaText(
                    "蛋白 ${(food.protein * ratio).fmtTrim()}g · " +
                            "脂肪 ${(food.fat * ratio).fmtTrim()}g · " +
                            "碳水 ${(food.carbs * ratio).fmtTrim()}g"
                )
            }
            if (!compact) {
                Button(
                    onClick = { onConfirm(grams) },
                    enabled = grams > 0
                ) {
                    Text(confirmLabel)
                }
            }
        }
        if (!compact) Spacer(Modifier.height(24.dp))
    }
}
