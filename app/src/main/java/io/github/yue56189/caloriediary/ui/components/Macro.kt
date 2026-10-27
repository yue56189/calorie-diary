package io.github.yue56189.caloriediary.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.yue56189.caloriediary.ui.fmt0

/**
 * 单个营养素的进度行：色点 + 名称 + 「已摄入 / 目标 g」 + 圆角进度条。
 */
@Composable
fun MacroRow(
    label: String,
    value: Double,
    goal: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    val target = if (goal <= 0) 1.0 else goal
    val raw = (value / target).toFloat()
    val animated by animateFloatAsState(
        targetValue = if (raw.isNaN()) 0f else raw.coerceIn(0f, 1f),
        animationSpec = tween(500),
        label = "macro"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${value.fmt0()} / ${goal.fmt0()} g",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(6.dp))
        ThinProgressBar(
            progress = animated,
            color = if (raw > 1f) MaterialTheme.colorScheme.error else color,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        )
    }
}

/** 一组营养素（蛋白 / 脂肪 / 碳水）。 */
@Composable
fun MacroGroup(
    protein: Double,
    proteinGoal: Double,
    fat: Double,
    fatGoal: Double,
    carbs: Double,
    carbsGoal: Double,
    modifier: Modifier = Modifier,
    proteinColor: Color,
    fatColor: Color,
    carbColor: Color
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MacroRow("蛋白质", protein, proteinGoal, proteinColor)
        MacroRow("脂肪", fat, fatGoal, fatColor)
        MacroRow("碳水", carbs, carbsGoal, carbColor)
    }
}

/** 小号统计块：上方大数字，下方说明。 */
@Composable
fun StatBlock(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = valueColor
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
