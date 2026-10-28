package io.github.yue56189.caloriediary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yue56189.caloriediary.data.NutritionGoal
import io.github.yue56189.caloriediary.data.NutritionMath
import kotlin.math.abs

/**
 * 每日目标设置。两种作用域：只改某一天，或改掉每天沿用的默认值。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalScreen(vm: MainViewModel, onBack: () -> Unit) {
    val date by vm.date.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val dailyGoal by vm.dayGoal.collectAsStateWithLifecycle()
    val exercise by vm.exerciseKcal.collectAsStateWithLifecycle()

    var onlyToday by remember { mutableStateOf(dailyGoal != null) }
    val autoMode = settings.goalMode == 1
    val autoBudget = NutritionMath.budget(settings.profile.bmr, exercise)

    val source = if (onlyToday && dailyGoal != null) {
        NutritionGoal(dailyGoal!!.kcal, dailyGoal!!.protein, dailyGoal!!.fat, dailyGoal!!.carbs)
    } else {
        settings.goal
    }

    var kcalText by remember(onlyToday, dailyGoal?.kcal, settings.goal.kcal) {
        mutableStateOf(source.kcal.fmtTrim())
    }
    var proteinText by remember(onlyToday, dailyGoal?.kcal, settings.goal.kcal) {
        mutableStateOf(source.protein.fmtTrim())
    }
    var fatText by remember(onlyToday, dailyGoal?.kcal, settings.goal.kcal) {
        mutableStateOf(source.fat.fmtTrim())
    }
    var carbsText by remember(onlyToday, dailyGoal?.kcal, settings.goal.kcal) {
        mutableStateOf(source.carbs.fmtTrim())
    }

    val kcal = kcalText.toDoubleOrNull() ?: 0.0
    val protein = proteinText.toDoubleOrNull() ?: 0.0
    val fat = fatText.toDoubleOrNull() ?: 0.0
    val carbs = carbsText.toDoubleOrNull() ?: 0.0
    /** 实际生效的热量口径：自动模式下由 BMR + 当天运动消耗决定。 */
    val effectiveKcal = if (autoMode) autoBudget else kcal
    val macroKcal = protein * 4 + fat * 9 + carbs * 4
    val diff = effectiveKcal - macroKcal

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("每日目标", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = onlyToday,
                        onClick = { onlyToday = true },
                        label = { Text("仅 ${date.title()}") }
                    )
                    FilterChip(
                        selected = !onlyToday,
                        onClick = { onlyToday = false },
                        label = { Text("每天默认") }
                    )
                }
            }

            item {
                Text(
                    text = if (onlyToday)
                        "只改 ${date.title()}（${date.shortLabel()}）的目标，其他日期不受影响。"
                    else "改掉每天沿用的默认目标；已经单独设置过的日期仍保留自己的值。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Column {
                    Text("热量口径", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !autoMode,
                            onClick = { vm.setGoalMode(0) },
                            label = { Text("手动设定") }
                        )
                        FilterChip(
                            selected = autoMode,
                            onClick = { vm.setGoalMode(1) },
                            label = { Text("基础代谢 + 运动消耗") }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (autoMode)
                            "预算 = 基础代谢 ${settings.profile.bmr.fmt0()} + 当天运动消耗 ${exercise.fmt0()} = " +
                                    "${autoBudget.fmt0()} kcal。运动消耗在首页「热量预算」里填，改口径立即生效。"
                        else "自己定一个热量目标，不随运动和体重变化。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(Modifier.padding(20.dp)) {
                        GoalNumberField(
                            value = if (autoMode) effectiveKcal.fmt0() else kcalText,
                            onChange = { kcalText = it },
                            label = if (autoMode) "热量目标（自动）" else "热量目标",
                            suffix = "kcal",
                            enabled = !autoMode
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GoalNumberField(
                                value = proteinText,
                                onChange = { proteinText = it },
                                label = "蛋白质",
                                suffix = "g",
                                modifier = Modifier.weight(1f)
                            )
                            GoalNumberField(
                                value = fatText,
                                onChange = { fatText = it },
                                label = "脂肪",
                                suffix = "g",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        GoalNumberField(
                            value = carbsText,
                            onChange = { carbsText = it },
                            label = "碳水化合物",
                            suffix = "g"
                        )

                        Spacer(Modifier.height(14.dp))
                        if (autoMode) {
                            val ratio = if (kcal > 0) effectiveKcal / kcal else 1.0
                            Text(
                                text = "当前配置折算 ${macroKcal.fmt0()} kcal。" +
                                        "实际会按 ${effectiveKcal.fmt0()} kcal 等比缩放：" +
                                        "蛋白 ${(protein * ratio).fmt0()} g · 脂肪 ${(fat * ratio).fmt0()} g · " +
                                        "碳水 ${(carbs * ratio).fmt0()} g。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "三大营养素折算 ${macroKcal.fmt0()} kcal" +
                                        if (effectiveKcal > 0) "（与热量目标相差 ${diff.fmt0()} kcal）" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (effectiveKcal > 0 && abs(diff) > 60) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (effectiveKcal > 0 && abs(diff) > 60) {
                                Text(
                                    text = "差得多说明热量和营养素对不上，建议调一下脂肪或碳水。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            item {
                val recommended = remember(settings.profile) {
                    NutritionMath.recommend(settings.profile)
                }
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("按我的身体数据推算", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "基础代谢 ${settings.profile.bmr.fmt0()} kcal；" +
                                    "按常见活动量估算日常消耗 ${settings.profile.tdee.fmt0()} kcal，" +
                                    "其中运动部分约 ${(settings.profile.tdee - settings.profile.bmr).fmt0()} kcal。" +
                                    "减脂建议先按 ${settings.profile.deficitPercent}% 缺口，即 " +
                                    "${recommended.kcal.fmt0()} kcal。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = {
                            kcalText = recommended.kcal.fmtTrim()
                            proteinText = recommended.protein.fmtTrim()
                            fatText = recommended.fat.fmtTrim()
                            carbsText = recommended.carbs.fmtTrim()
                        }) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("填入推荐值")
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val goal = NutritionGoal(
                            if (autoMode) autoBudget else kcal,
                            protein, fat, carbs
                        )
                        if (onlyToday) vm.saveGoalForDate(date, goal) else vm.saveDefaultGoal(goal)
                        onBack()
                    },
                    enabled = effectiveKcal > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (onlyToday) "保存今天的目标" else "保存为默认目标")
                }
            }

            if (onlyToday && dailyGoal != null) {
                item {
                    TextButton(
                        onClick = {
                            vm.resetGoalForDate(date)
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("恢复为默认目标（${settings.goal.kcal.fmt0()} kcal）")
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalNumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    suffix: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { it.isDigit() || it == '.' }.take(6)) },
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth()
    )
}
