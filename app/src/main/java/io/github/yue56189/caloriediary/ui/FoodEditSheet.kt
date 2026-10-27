package io.github.yue56189.caloriediary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.yue56189.caloriediary.data.FoodItem

/**
 * 自定义食物录入 / 编辑。所有数值均以「每 100 克」为基准，
 * 与 USDA 数据库的口径一致，避免混用份量与克数造成误差。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEditSheet(
    initial: FoodItem?,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (FoodItem) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "自定义") }
    var kcal by remember { mutableStateOf(initial?.kcal?.fmtTrim() ?: "") }
    var protein by remember { mutableStateOf(initial?.protein?.fmtTrim() ?: "") }
    var fat by remember { mutableStateOf(initial?.fat?.fmtTrim() ?: "") }
    var carbs by remember { mutableStateOf(initial?.carbs?.fmtTrim() ?: "") }
    var portionLabel by remember { mutableStateOf(initial?.portionLabel ?: "") }
    var portionGrams by remember { mutableStateOf(initial?.portionGrams?.takeIf { it > 0 }?.fmtTrim() ?: "") }

    val valid = name.isNotBlank() && (kcal.toDoubleOrNull() ?: 0.0) > 0

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = if (initial == null) "新建自定义食物" else "编辑食物",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "营养值统一按每 100 克填写，和你平时看的营养成分表口径一致。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称 *") },
                placeholder = { Text("例如 妈妈牌番茄炒蛋") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            Text("分类", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            val allCategories = remember(categories) {
                (listOf("自定义") + categories).distinct()
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(allCategories) { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(c) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    value = kcal,
                    onChange = { kcal = it },
                    label = "热量 *",
                    suffix = "kcal",
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    value = protein,
                    onChange = { protein = it },
                    label = "蛋白质",
                    suffix = "g",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    value = fat,
                    onChange = { fat = it },
                    label = "脂肪",
                    suffix = "g",
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    value = carbs,
                    onChange = { carbs = it },
                    label = "碳水",
                    suffix = "g",
                    modifier = Modifier.weight(1f)
                )
            }

            val k = kcal.toDoubleOrNull() ?: 0.0
            val p = protein.toDoubleOrNull() ?: 0.0
            val f = fat.toDoubleOrNull() ?: 0.0
            val c = carbs.toDoubleOrNull() ?: 0.0
            if (k > 0) {
                Spacer(Modifier.height(8.dp))
                val calc = p * 4 + f * 9 + c * 4
                Text(
                    text = "三大营养素折算 ${calc.fmt0()} kcal" +
                            if (calc > 0) "，与填写热量相差 ${(k - calc).fmt0()} kcal" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("常用份量（可选）", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = portionLabel,
                    onValueChange = { portionLabel = it },
                    label = { Text("份量名称") },
                    placeholder = { Text("1 碗") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    value = portionGrams,
                    onChange = { portionGrams = it },
                    label = "克数",
                    suffix = "g",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        onSave(
                            FoodItem(
                                id = initial?.id ?: 0,
                                name = name.trim(),
                                nameEn = initial?.nameEn ?: "",
                                category = category,
                                kcal = k,
                                protein = p,
                                fat = f,
                                carbs = c,
                                fiber = initial?.fiber ?: 0.0,
                                sodium = initial?.sodium ?: 0.0,
                                portionLabel = portionLabel.trim(),
                                portionGrams = portionGrams.toDoubleOrNull() ?: 0.0,
                                isCustom = true,
                                barcode = initial?.barcode ?: "",
                                source = if (initial?.isCustom == true) initial.source else "自定义录入"
                            )
                        )
                    },
                    enabled = valid
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("保存")
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    suffix: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { it.isDigit() || it == '.' }.take(6)) },
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}
