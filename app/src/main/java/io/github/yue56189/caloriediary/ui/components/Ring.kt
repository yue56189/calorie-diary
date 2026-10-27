package io.github.yue56189.caloriediary.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 270° 环形进度（Material 3 风格：圆角端点、留缺口在正下方）。
 * 超过 100% 时用 [overColor] 提示超标。
 */
@Composable
fun CalorieRing(
    progress: Float,
    modifier: Modifier = Modifier,
    ringColor: Color = MaterialTheme.colorScheme.primary,
    overColor: Color = MaterialTheme.colorScheme.error,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val safe = if (progress.isNaN()) 0f else progress
    val active = safe.coerceIn(0f, 1f)
    val color = if (safe > 1f) overColor else ringColor

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.085f
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)

            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (active > 0.001f) {
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = 270f * active,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}

/** 自绘的圆角进度条，避免不同 Material3 版本 API 差异。 */
@Composable
fun ThinProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    height: androidx.compose.ui.unit.Dp = 8.dp
) {
    val p = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
    Canvas(modifier = modifier) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(color = trackColor, size = size, cornerRadius = r)
        if (p > 0.001f) {
            drawRoundRect(
                color = color,
                size = Size(size.width * p, size.height),
                cornerRadius = r
            )
        }
    }
}
