package io.github.yue56189.caloriediary.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yue56189.caloriediary.ui.components.MacroGroup
import io.github.yue56189.caloriediary.ui.components.StatBlock
import io.github.yue56189.caloriediary.ui.theme.CarbColor
import io.github.yue56189.caloriediary.ui.theme.FatColor
import io.github.yue56189.caloriediary.ui.theme.ProteinColor
import java.time.LocalDate

private const val CHART_HEIGHT_DP = 150f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: MainViewModel) {
    val entries by vm.recentEntries.collectAsStateWithLifecycle()
    val goal by vm.goal.collectAsStateWithLifecycle()

    val today = LocalDate.now()
    val days = remember(today) { (0..6).map { today.minusDays((6 - it).toLong()) } }
    val byDay = remember(entries) { entries.groupBy { it.date } }

    val dailyKcal = days.map { d -> byDay[d.toEpochDay()]?.sumOf { it.kcal } ?: 0.0 }
    val maxKcal = maxOf(goal.kcal, dailyKcal.maxOrNull() ?: 0.0, 1.0) * 1.12

    val activeDays = dailyKcal.count { it > 0 }
    val avgKcal = if (activeDays == 0) 0.0 else dailyKcal.sum() / activeDays
    val onTarget = days.indices.count { i ->
        val k = dailyKcal[i]
        k > 0 && k <= goal.kcal * 1.05
    }
    val totalEntries = entries.size
    val weekTotal = entries.filter { it.date >= days.first().toEpochDay() }

    val barColor = MaterialTheme.colorScheme.primary
    val overColor = MaterialTheme.colorScheme.error
    val dashColor = MaterialTheme.colorScheme.outline

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("统计", fontWeight = FontWeight.SemiBold) },
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
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("最近 7 天", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "虚线为每日目标 ${goal.kcal.fmt0()} kcal",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "$onTarget / 7 天达标",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(Modifier.height(18.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(CHART_HEIGHT_DP.dp)
                        ) {
                            val goalRatio = (goal.kcal / maxKcal).toFloat().coerceIn(0f, 1f)
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val chartHeight = size.height
                                val colWidth = size.width / days.size

                                // 目标线
                                val lineY = chartHeight * (1f - goalRatio)
                                drawLine(
                                    color = dashColor,
                                    start = Offset(0f, lineY),
                                    end = Offset(size.width, lineY),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                )

                                val barWidth = 22.dp.toPx()
                                val corner = CornerRadius(6.dp.toPx(), 6.dp.toPx())

                                days.forEachIndexed { index, _ ->
                                    val kcal = dailyKcal[index]
                                    if (kcal <= 0) return@forEachIndexed
                                    val ratio = (kcal / maxKcal).toFloat().coerceIn(0f, 1f)
                                    val barHeight = chartHeight * ratio
                                    val left = colWidth * index + (colWidth - barWidth) / 2f
                                    val rect = Rect(left, chartHeight - barHeight, left + barWidth, chartHeight)
                                    val path = Path().apply {
                                        addRoundRect(
                                            RoundRect(
                                                rect,
                                                topLeft = corner,
                                                topRight = corner,
                                                bottomRight = CornerRadius.Zero,
                                                bottomLeft = CornerRadius.Zero
                                            )
                                        )
                                    }
                                    drawPath(
                                        path = path,
                                        color = if (kcal > goal.kcal * 1.05) overColor else barColor
                                    )
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth()) {
                            days.forEachIndexed { index, day ->
                                val kcal = dailyKcal[index]
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = day.shortLabel().replace("月", "/").replace("日", ""),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = if (kcal > 0) kcal.fmt0() else "—",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (kcal > goal.kcal * 1.05) overColor
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
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
                        Text("概览", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.SpaceEvenly) {
                            StatBlock(avgKcal.fmt0(), "平均 kcal / 天", Modifier.weight(1f))
                            StatBlock("$activeDays", "有记录的天数", Modifier.weight(1f))
                            StatBlock("$totalEntries", "累计打卡项", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(12.dp))
                        val weekTotalKcal = weekTotal.sumOf { it.kcal }
                        Text(
                            text = "本周共摄入 ${weekTotalKcal.fmt0()} kcal，平均每天 " +
                                    "${(weekTotalKcal / 7).fmt0()} kcal。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                val weekProtein = weekTotal.sumOf { it.protein }
                val weekFat = weekTotal.sumOf { it.fat }
                val weekCarbs = weekTotal.sumOf { it.carbs }
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("本周营养素累计", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(14.dp))
                        MacroGroup(
                            protein = weekProtein,
                            proteinGoal = goal.protein * 7,
                            fat = weekFat,
                            fatGoal = goal.fat * 7,
                            carbs = weekCarbs,
                            carbsGoal = goal.carbs * 7,
                            proteinColor = ProteinColor,
                            fatColor = FatColor,
                            carbColor = CarbColor
                        )
                    }
                }
            }

            item {
                Text(
                    text = "统计基于你在日记里记下的数据，漏记的日子不计入平均值。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }
        }
    }
}
