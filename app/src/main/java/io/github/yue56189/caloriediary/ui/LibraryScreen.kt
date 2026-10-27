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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yue56189.caloriediary.data.FoodItem
import io.github.yue56189.caloriediary.ui.components.FoodRow
import io.github.yue56189.caloriediary.ui.components.MetaText
import io.github.yue56189.caloriediary.ui.components.PortionEditor
import io.github.yue56189.caloriediary.ui.theme.ProteinColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(vm: MainViewModel) {
    val foods by vm.allFoods.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val remote = vm.remote

    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var onlineMode by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FoodItem?>(null) }
    var creating by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<FoodItem?>(null) }

    val sortedCategories = remember(categories) {
        categories.sortedWith(compareBy<String> { foodCategoryRank(it) })
    }
    val filtered = remember(query, category, foods) {
        foods
            .filter { category == null || it.category == category }
            .filter {
                query.isBlank() ||
                        it.name.contains(query, ignoreCase = true) ||
                        it.nameEn.contains(query, ignoreCase = true)
            }
            .sortedForDisplay()
            .take(200)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("食物库", fontWeight = FontWeight.SemiBold) },
                actions = {
                    TextButton(onClick = { creating = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("自定义")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    if (!onlineMode && (remote.items.isNotEmpty() || remote.error != null)) vm.clearRemote()
                },
                placeholder = { Text("搜索食物名称") },
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !onlineMode,
                    onClick = { onlineMode = false },
                    label = { Text("本地库 ${foods.size}") }
                )
                FilterChip(
                    selected = onlineMode,
                    onClick = { onlineMode = true },
                    label = { Text("联网查询") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            if (onlineMode) {
                OnlinePane(vm = vm, remote = remote, query = query) { detail = it }
            } else {
                if (categories.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = category == null,
                                onClick = { category = null },
                                label = { Text("全部") }
                            )
                        }
                        items(sortedCategories) { c ->
                            FilterChip(
                                selected = category == c,
                                onClick = { category = if (category == c) null else c },
                                label = { Text(c) }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp
                    )
                ) {
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                text = "没有匹配的食物。可以点右上角「自定义」自己录入，或切到「联网查询」。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
                        }
                    }
                    items(filtered, key = { it.id }) { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                FoodRow(item = item, onClick = { detail = item }, trailing = {})
                            }
                            FoodMenu(food = item, onEdit = { editing = it }, onDelete = { vm.deleteFood(it) })
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        FoodEditSheet(
            initial = editing,
            categories = categories,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { food ->
                vm.saveFood(food)
                creating = false
                editing = null
            }
        )
    }

    detail?.let { food ->
        FoodDetailSheet(food = food, onDismiss = { detail = null })
    }
}

@Composable
private fun FoodMenu(
    food: FoodItem,
    onEdit: (FoodItem) -> Unit,
    onDelete: (FoodItem) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        if (food.isCustom) {
            DropdownMenuItem(
                text = { Text("编辑") },
                onClick = { open = false; onEdit(food) }
            )
            DropdownMenuItem(
                text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                onClick = { open = false; onDelete(food) }
            )
        } else {
            DropdownMenuItem(
                text = { Text("内置数据，不可编辑") },
                enabled = false,
                onClick = { open = false }
            )
        }
    }
}

@Composable
private fun OnlinePane(
    vm: MainViewModel,
    remote: io.github.yue56189.caloriediary.ui.RemoteState,
    query: String,
    onDetail: (FoodItem) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "数据来自 Open Food Facts（开源食品数据库）与 USDA FoodData Central。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
        TextButton(
            onClick = { vm.searchRemote(query) },
            enabled = query.isNotBlank() && !remote.loading,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(Icons.Outlined.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (query.isBlank()) "先输入关键词" else "联网搜索「$query」")
        }

        when {
            remote.loading -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("正在查询…", style = MaterialTheme.typography.bodyMedium)
            }
            remote.error != null -> Text(
                text = "查询失败：${remote.error}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
            remote.items.isEmpty() -> Text(
                text = "输入食物名称后点上面的按钮查询。\n中文关键词在 Open Food Facts 命中率有限，也可以试试英文。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)
            ) {
                item {
                    Text(
                        text = remote.note,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(remote.items, key = { "${it.name}-${it.kcal}-${it.barcode}" }) { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            FoodRow(item = item, onClick = { vm.addRemoteToLibrary(item) })
                        }
                        TextButton(onClick = { vm.addRemoteToLibrary(item) }) { Text("收藏") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodDetailSheet(food: FoodItem, onDismiss: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(food.name, style = MaterialTheme.typography.headlineSmall)
            if (food.nameEn.isNotBlank() && food.nameEn != food.name) {
                MetaText(food.nameEn)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                NutritionCell("热量", "${food.kcal.fmt0()}", "kcal")
                NutritionCell("蛋白", food.protein.fmtTrim(), "g")
                NutritionCell("脂肪", food.fat.fmtTrim(), "g")
                NutritionCell("碳水", food.carbs.fmtTrim(), "g")
            }
            Spacer(Modifier.height(16.dp))
            MetaText("以上均为每 100 克可食部。分类：${food.category}")
            if (food.portionGrams > 0 && food.portionLabel.isNotBlank()) {
                MetaText("常见份量：${food.portionLabel} ≈ ${food.portionGrams.fmt0()} g")
            }
            if (food.barcode.isNotBlank()) MetaText("条码：${food.barcode}")
            Spacer(Modifier.height(8.dp))
            MetaText("数据来源：${food.source}")
        }
    }
}

@Composable
private fun NutritionCell(label: String, value: String, unit: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = if (label == "热量") MaterialTheme.colorScheme.primary else ProteinColor
        )
        Text(
            text = "$label ($unit)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
