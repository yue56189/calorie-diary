package io.github.yue56189.caloriediary.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yue56189.caloriediary.data.DiaryEntry
import io.github.yue56189.caloriediary.data.FoodItem
import io.github.yue56189.caloriediary.data.Meal
import io.github.yue56189.caloriediary.data.NutritionTotal
import io.github.yue56189.caloriediary.ui.components.CalorieRing
import io.github.yue56189.caloriediary.ui.components.FoodPickerSheet
import io.github.yue56189.caloriediary.ui.components.MacroGroup
import io.github.yue56189.caloriediary.ui.components.PortionEditor
import io.github.yue56189.caloriediary.ui.components.StatBlock
import io.github.yue56189.caloriediary.ui.theme.CarbColor
import io.github.yue56189.caloriediary.ui.theme.FatColor
import io.github.yue56189.caloriediary.ui.theme.ProteinColor
import java.time.LocalDate

fun mealIcon(meal: Meal): ImageVector = when (meal) {
    Meal.BREAKFAST -> Icons.Outlined.WbTwilight
    Meal.LUNCH -> Icons.Outlined.WbSunny
    Meal.DINNER -> Icons.Outlined.Nightlight
    Meal.SNACK -> Icons.Outlined.Fastfood
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    vm: MainViewModel,
    onOpenGoal: () -> Unit
) {
    val date by vm.date.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val goal by vm.goal.collectAsStateWithLifecycle()
    val total = NutritionTotal.of(entries)

    var pickerMeal by remember { mutableStateOf<Meal?>(null) }
    var editing by remember { mutableStateOf<DiaryEntry?>(null) }

    // 编辑已有条目时需要原始食物的每 100g 数据
    var editingFood by remember { mutableStateOf<FoodItem?>(null) }
    androidx.compose.runtime.LaunchedEffect(editing) {
        val e = editing
        editingFood = if (e == null) null else e.foodId?.let { vm.getFood(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("卡路里日记", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onOpenGoal) {
                        Icon(Icons.Outlined.Flag, contentDescription = "每日目标")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { DateSwitcher(date = date, onPrev = { vm.shiftDay(-1) }, onNext = { vm.shiftDay(1) }, onToday = { vm.goToday() }) }

            item { CalorieCard(total = total, goal = goal) }

            item {
                MacroCard(total = total, goal = goal)
            }

            Meal.entries.forEach { meal ->
                item(key = "meal-${meal.code}") {
                    MealCard(
                        meal = meal,
                        entries = entries.filter { it.meal == meal.code },
                        onAdd = { pickerMeal = meal },
                        onEntryClick = { editing = it }
                    )
                }
            }

            item {
                val remaining = goal.kcal - total.kcal
                Text(
                    text = when {
                        remaining > 0 -> "还可以吃 ${remaining.fmt0()} 千卡。坚持下去，节奏比强度更重要。"
                        remaining > -100 -> "今天刚好达标，差一点点不算差。"
                        else -> "今天超出 ${(-remaining).fmt0()} 千卡，明天正常吃就好，不用补偿。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
                )
            }
        }
    }

    pickerMeal?.let { meal ->
        FoodPickerSheet(meal = meal, vm = vm, onDismiss = { pickerMeal = null })
    }

    editing?.let { entry ->
        EntryEditDialog(
            entry = entry,
            food = editingFood,
            onDismiss = { editing = null },
            onDelete = {
                vm.deleteEntry(entry)
                editing = null
            },
            onSave = { grams ->
                vm.updateEntryGrams(entry, grams, editingFood)
                editing = null
            }
        )
    }
}

@Composable
private fun DateSwitcher(
    date: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "前一天")
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onToday),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${date.title()} · ${date.shortLabel()}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = date.subtitle(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "后一天")
        }
    }
}

@Composable
private fun CalorieCard(total: NutritionTotal, goal: io.github.yue56189.caloriediary.data.NutritionGoal) {
    val progress = if (goal.kcal <= 0) 0f else (total.kcal / goal.kcal).toFloat()
    val remaining = goal.kcal - total.kcal
    val over = remaining < 0

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                CalorieRing(progress = progress, modifier = Modifier.size(210.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = remaining.fmt0().replace("-", ""),
                            style = MaterialTheme.typography.displaySmall,
                            color = if (over) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (over) "千卡超出" else "千卡剩余",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "${total.kcal.fmt0()} / ${goal.kcal.fmt0()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatBlock(
                    value = total.kcal.fmt0(),
                    label = "已摄入 kcal",
                    modifier = Modifier.weight(1f)
                )
                StatBlock(
                    value = goal.kcal.fmt0(),
                    label = "目标 kcal",
                    modifier = Modifier.weight(1f)
                )
                StatBlock(
                    value = "${(progress * 100).fmt0()}%",
                    label = "完成度",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MacroCard(
    total: NutritionTotal,
    goal: io.github.yue56189.caloriediary.data.NutritionGoal
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "三大营养素",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            MacroGroup(
                protein = total.protein,
                proteinGoal = goal.protein,
                fat = total.fat,
                fatGoal = goal.fat,
                carbs = total.carbs,
                carbsGoal = goal.carbs,
                proteinColor = ProteinColor,
                fatColor = FatColor,
                carbColor = CarbColor
            )
        }
    }
}

@Composable
private fun MealCard(
    meal: Meal,
    entries: List<DiaryEntry>,
    onAdd: () -> Unit,
    onEntryClick: (DiaryEntry) -> Unit
) {
    val kcal = entries.sumOf { it.kcal }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = mealIcon(meal),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(meal.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (entries.isEmpty()) "还没有记录"
                        else "${entries.size} 项 · ${kcal.fmt0()} kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalIconButton(onClick = onAdd, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Add, contentDescription = "添加${meal.title}")
                }
            }

            if (entries.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                entries.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEntryClick(entry) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                            Text(
                                text = "${entry.grams.fmtTrim()}g · 蛋白 ${entry.protein.fmtTrim()} · 脂肪 ${entry.fat.fmtTrim()} · 碳水 ${entry.carbs.fmtTrim()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${entry.kcal.fmt0()}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryEditDialog(
    entry: DiaryEntry,
    food: FoodItem?,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (Double) -> Unit
) {
    var grams by remember(entry.id) { mutableStateOf(entry.grams) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(entry.name, maxLines = 2)
                Text(
                    text = "修改克数或删除这条记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            if (food == null) {
                Text("这条记录对应的食物已从食物库删除，只能删除本条记录。")
            } else {
                PortionEditor(
                    food = food,
                    confirmLabel = "保存",
                    compact = true,
                    onGramsChange = { grams = it },
                    onConfirm = onSave
                )
            }
        },
        confirmButton = {
            if (food == null) {
                TextButton(onClick = onDismiss) { Text("关闭") }
            } else {
                TextButton(onClick = { onSave(grams) }, enabled = grams > 0) { Text("保存") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(6.dp))
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}
