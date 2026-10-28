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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Icecream
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yue56189.caloriediary.data.DiaryEntry
import io.github.yue56189.caloriediary.data.FoodItem
import io.github.yue56189.caloriediary.data.Meal
import io.github.yue56189.caloriediary.data.NutritionGoal
import io.github.yue56189.caloriediary.data.NutritionTotal
import io.github.yue56189.caloriediary.ui.components.CalorieRing
import io.github.yue56189.caloriediary.ui.components.FoodPickerSheet
import io.github.yue56189.caloriediary.ui.components.MacroGroup
import io.github.yue56189.caloriediary.ui.components.PortionEditor
import io.github.yue56189.caloriediary.ui.components.QuickAddSheet
import io.github.yue56189.caloriediary.ui.components.StatBlock
import io.github.yue56189.caloriediary.ui.theme.CarbColor
import io.github.yue56189.caloriediary.ui.theme.FatColor
import io.github.yue56189.caloriediary.ui.theme.ProteinColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

fun mealIcon(meal: Meal): ImageVector = when (meal) {
    Meal.BREAKFAST -> Icons.Outlined.WbTwilight
    Meal.LUNCH -> Icons.Outlined.WbSunny
    Meal.DINNER -> Icons.Outlined.Nightlight
    Meal.SNACK_MORNING -> Icons.Outlined.Coffee
    Meal.SNACK_AFTERNOON -> Icons.Outlined.Icecream
    Meal.SNACK_EVENING -> Icons.Outlined.LocalCafe
}

/** 按当前钟点猜一个餐次，作为快速记录的默认值。 */
private fun defaultMealNow(): Meal {    val h = java.time.LocalTime.now().hour
    return when {
        h < 10 -> Meal.BREAKFAST
        h < 14 -> Meal.LUNCH
        h < 18 -> Meal.SNACK_AFTERNOON
        h < 21 -> Meal.DINNER
        else -> Meal.SNACK_EVENING
    }
}

/**
 * 没有关联食物（联网查询直接记入、或食物后来被删掉）的记录，
 * 用写入时的营养快照反推每 100 g 数值，这样仍然能改份量。
 */
private fun DiaryEntry.snapshotAsFood(): FoodItem {
    val r = if (grams > 0) grams / 100.0 else 1.0
    return FoodItem(
        id = -id,
        name = name,
        category = "",
        kcal = kcal / r,
        protein = protein / r,
        fat = fat / r,
        carbs = carbs / r,
        portionLabel = "上次",
        portionGrams = grams,
        source = "来自这条打卡记录"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(    vm: MainViewModel,
    onOpenGoal: () -> Unit
) {
    val date by vm.date.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val goal by vm.effectiveGoal.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val exercise by vm.exerciseKcal.collectAsStateWithLifecycle()
    val total = NutritionTotal.of(entries)

    var pickerMeal by remember { mutableStateOf<Meal?>(null) }
    var editing by remember { mutableStateOf<DiaryEntry?>(null) }
    var showCalendar by remember { mutableStateOf(false) }
    var editingExercise by remember { mutableStateOf(false) }
    var showQuickAdd by remember { mutableStateOf(false) }

    // 编辑已有条目时需要原始食物的每 100g 数据
    var editingFood by remember { mutableStateOf<FoodItem?>(null) }
    androidx.compose.runtime.LaunchedEffect(editing) {
        val e = editing
        editingFood = if (e == null) null else {
            e.foodId?.let { vm.getFood(it) } ?: e.snapshotAsFood()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("卡路里日记", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { showQuickAdd = true }) {
                        Icon(Icons.Outlined.Bolt, contentDescription = "快速记录")
                    }
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
            item {
                DateSwitcher(
                    date = date,
                    onPrev = { vm.shiftDay(-1) },
                    onNext = { vm.shiftDay(1) },
                    onPick = { showCalendar = true }
                )
            }

            item { CalorieCard(total = total, goal = goal) }

            item {
                BudgetCard(
                    mode = settings.goalMode,
                    bmr = settings.profile.bmr,
                    exercise = exercise,
                    budget = goal.kcal,
                    onEditExercise = { editingExercise = true },
                    onSwitchMode = onOpenGoal
                )
            }

            item { MacroCard(total = total, goal = goal) }

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
                        remaining > 0 -> "还可以吃 ${remaining.fmt0()} 千卡。钱要花在有用的地方，热量也一样。"
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

    if (showQuickAdd) {
        QuickAddSheet(
            vm = vm,
            initialMeal = defaultMealNow(),
            onDismiss = { showQuickAdd = false }
        )
    }

    if (showCalendar) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            vm.setDate(
                                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            )
                        }
                        showCalendar = false
                    }
                ) { Text("跳到这天") }
            },
            dismissButton = {
                TextButton(onClick = { showCalendar = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = state, title = { Text("选择日期", modifier = Modifier.padding(16.dp)) })
        }
    }

    if (editingExercise) {
        ExerciseDialog(
            current = exercise,
            onDismiss = { editingExercise = false },
            onSave = {
                vm.setExercise(it)
                editingExercise = false
            }
        )
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
    onPick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "前一天")
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onPick)
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.CalendarMonth,
                contentDescription = "选择日期",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "后一天")
        }
    }
}

@Composable
private fun CalorieCard(total: NutritionTotal, goal: NutritionGoal) {
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
                    label = "预算 kcal",
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

/** 预算构成：手动目标，或「基础代谢 + 运动消耗」。 */
@Composable
private fun BudgetCard(
    mode: Int,
    bmr: Double,
    exercise: Double,
    budget: Double,
    onEditExercise: () -> Unit,
    onSwitchMode: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Outlined.DirectionsRun,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "热量预算",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = if (mode == 1) onEditExercise else onSwitchMode) {
                    Text(if (mode == 1) "填运动消耗" else "改口径")
                }
            }
            if (mode == 1) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatBlock(value = bmr.fmt0(), label = "基础代谢", modifier = Modifier.weight(1f))
                    StatBlock(value = exercise.fmt0(), label = "运动消耗", modifier = Modifier.weight(1f))
                    StatBlock(value = budget.fmt0(), label = "预算合计", modifier = Modifier.weight(1f))
                }
            } else {
                Text(
                    text = "当前是手动目标。改成「基础代谢 + 运动消耗」后，预算会随每天填的运动量变化。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MacroCard(total: NutritionTotal, goal: NutritionGoal) {
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
                    color = if (meal.isSnack) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = mealIcon(meal),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (meal.isSnack) MaterialTheme.colorScheme.onTertiaryContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = meal.title,
                        style = if (meal.isSnack) MaterialTheme.typography.titleSmall
                        else MaterialTheme.typography.titleMedium
                    )
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

/** 填当天运动消耗。 */
@Composable
private fun ExerciseDialog(
    current: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var text by remember { mutableStateOf(if (current > 0) current.fmtTrim() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("今天运动消耗") },
        text = {
            Column {
                Text(
                    text = "填写今天运动额外消耗的热量，预算 = 基础代谢 + 这个值。填 0 表示没运动。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter { it.isDigit() || it == '.' }.take(5) },
                    label = { Text("运动消耗") },
                    suffix = { Text("kcal") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.0, 200.0, 300.0, 500.0).forEach { v ->
                        androidx.compose.material3.AssistChip(
                            onClick = { text = if (v > 0) v.fmtTrim() else "0" },
                            label = { Text(if (v > 0) "${v.fmt0()}" else "没运动") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text.toDoubleOrNull() ?: 0.0) }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
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
                    text = "修改份量或删除这条记录",
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
