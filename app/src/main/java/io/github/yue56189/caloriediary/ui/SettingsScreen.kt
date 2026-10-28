package io.github.yue56189.caloriediary.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yue56189.caloriediary.BuildConfig
import io.github.yue56189.caloriediary.data.ActivityLevel
import io.github.yue56189.caloriediary.data.NutritionMath
import io.github.yue56189.caloriediary.data.Profile
import io.github.yue56189.caloriediary.data.Sex
import io.github.yue56189.caloriediary.data.bmiLabel
import io.github.yue56189.caloriediary.data.bmiOf

private const val REPO_URL = "https://github.com/yue56189/calorie-diary"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onOpenGoal: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editingProfile by remember { mutableStateOf(false) }
    var usdaDraft by remember(settings.usdaApiKey) { mutableStateOf(settings.usdaApiKey) }
    var tianDraft by remember(settings.tianapiKey) { mutableStateOf(settings.tianapiKey) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的", fontWeight = FontWeight.SemiBold) },
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
                SectionCard(title = "身体档案") {
                    val bmi = bmiOf(settings.profile.heightCm, settings.profile.weightKg)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "${settings.profile.sex.label} · ${settings.profile.age} 岁 · " +
                                        "${settings.profile.heightCm.fmt0()} cm · " +
                                        "${settings.profile.weightKg.fmt0()} kg",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "BMI ${bmi.fmt1()}（${bmiLabel(bmi)}）· " +
                                        settings.profile.activity.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { editingProfile = true }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "编辑身体档案")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        MiniStat("基础代谢", "${settings.profile.bmr.fmt0()} kcal")
                        MiniStat("日常消耗", "${settings.profile.tdee.fmt0()} kcal")
                        MiniStat("建议摄入", "${settings.profile.targetKcal.fmt0()} kcal")
                    }
                }
            }

            item {
                SectionCard(title = "每日营养目标") {
                    val goal = settings.goal
                    Text(
                        text = "${goal.kcal.fmt0()} kcal  ·  蛋白 ${goal.protein.fmt0()} g  ·  " +
                                "脂肪 ${goal.fat.fmt0()} g  ·  碳水 ${goal.carbs.fmt0()} g",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "三大营养素折算 ${NutritionMath.macroKcal(goal).fmt0()} kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (settings.goalMode == 1)
                            "热量口径：基础代谢 ${settings.profile.bmr.fmt0()} + 当天运动消耗；上面是比例基准"
                        else "热量口径：手动设定",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onOpenGoal) {
                        Icon(Icons.Outlined.Edit, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("修改目标")
                    }
                }
            }

            item {
                SectionCard(title = "外观") {
                    Text("主题", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("跟随系统" to 0, "浅色" to 1, "深色" to 2).forEach { (label, mode) ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { vm.setThemeMode(mode) },
                                label = { Text(label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("动态取色", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Android 12 及以上跟随壁纸配色（Material You）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.dynamicColor,
                            onCheckedChange = { vm.setDynamicColor(it) }
                        )
                    }
                }
            }

            item {
                SectionCard(title = "联网查询数据源") {
                    Text(
                        text = "三个源可以同时开，搜一次并行查，结果合并去重。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(14.dp))
                    Text("天行数据 · 营养成分表（国内）", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "数据体系是《中国食物成分表》（谷类 / 畜肉类 / 蔬菜类 …），" +
                                "中文食物名命中率最高，推荐优先启用。到 tianapi.com 注册后免费申请「营养成分表」接口，" +
                                "在「我的密钥」里复制 Key 填下面。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tianDraft,
                        onValueChange = { tianDraft = it.trim() },
                        label = { Text("天行数据 API Key") },
                        placeholder = { Text("留空则不查国内源") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.setTianapiKey(tianDraft) }) { Text("保存") }
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tianapi.com/apiview/121"))
                                )
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("去申请")
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(14.dp))

                    Text("USDA FoodData Central（美国）", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "英文食物名命中率更高，Key 可在 fdc.nal.usda.gov 免费申请，也可先填 DEMO_KEY 试用（限速）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = usdaDraft,
                        onValueChange = { usdaDraft = it.trim() },
                        label = { Text("USDA API Key（可选）") },
                        placeholder = { Text("留空则不查 USDA") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.setUsdaKey(usdaDraft) }) { Text("保存") }
                        TextButton(onClick = { vm.setUsdaKey("DEMO_KEY"); usdaDraft = "DEMO_KEY" }) {
                            Text("用 DEMO_KEY 试用")
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Open Food Facts（无需 Key）", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "开源包装食品库，扫条码数据多，中文商品覆盖率一般。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.useOpenFoodFacts,
                            onCheckedChange = { vm.setUseOpenFoodFacts(it) }
                        )
                    }
                }
            }

            item {
                SectionCard(title = "数据来源") {
                    SourceLine(
                        "天行数据 · 营养成分表",
                        "国内联网查询，数据体系为《中国食物成分表》，需自备 API Key。"
                    )
                    SourceLine(
                        "USDA FoodData Central（SR Legacy）",
                        "内置食物库的热量与营养素数据，美国农业部，公有领域。"
                    )
                    SourceLine(
                        "Open Food Facts",
                        "联网查询包装食品，ODbL 许可。"
                    )
                    SourceLine(
                        "Mifflin-St Jeor 公式",
                        "基础代谢估算，1990 年发表于 Am J Clin Nutr。"
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "营养数据均为每 100 克可食部的平均值，实际食物会因品种、产地、\n" +
                                "烹饪方式有较大差异，仅供参考，不构成医疗建议。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SectionCard(title = "关于") {
                    Text(
                        text = "卡路里日记 v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "开源项目，MIT 许可。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)))
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("在 GitHub 上查看源码")
                    }
                }
            }
        }
    }

    if (editingProfile) {
        ProfileSheet(
            initial = settings.profile,
            onDismiss = { editingProfile = false },
            onSave = {
                vm.saveProfile(it)
                editingProfile = false
            },
            onSaveAndApply = {
                vm.saveProfileAndGoal(it, NutritionMath.recommend(it))
                editingProfile = false
            }
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleSmall)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SourceLine(title: String, desc: String) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(
            desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ProfileSheet(
    initial: Profile,
    onDismiss: () -> Unit,
    onSave: (Profile) -> Unit,
    onSaveAndApply: (Profile) -> Unit
) {
    var sex by remember { mutableStateOf(initial.sex) }
    var age by remember { mutableStateOf(initial.age.toString()) }
    var height by remember { mutableStateOf(initial.heightCm.fmtTrim()) }
    var weight by remember { mutableStateOf(initial.weightKg.fmtTrim()) }
    var activity by remember { mutableStateOf(initial.activity) }
    var deficit by remember { mutableStateOf(initial.deficitPercent) }

    val profile = Profile(
        sex = sex,
        age = age.toIntOrNull() ?: initial.age,
        heightCm = height.toDoubleOrNull() ?: initial.heightCm,
        weightKg = weight.toDoubleOrNull() ?: initial.weightKg,
        activity = activity,
        deficitPercent = deficit
    )
    val recommended = NutritionMath.recommend(profile)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("身体档案", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "用于估算基础代谢和推荐热量，只存在本机。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sex.entries.forEach { s ->
                    FilterChip(
                        selected = sex == s,
                        onClick = { sex = s },
                        label = { Text(s.label) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsNumberField(age, { age = it }, "年龄", "岁", Modifier.weight(1f), decimal = false)
                SettingsNumberField(height, { height = it }, "身高", "cm", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            SettingsNumberField(weight, { weight = it }, "体重", "kg", Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))
            Text("活动量", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityLevel.entries.forEach { a ->
                    FilterChip(
                        selected = activity == a,
                        onClick = { activity = a },
                        label = { Text(a.label) }
                    )
                }
            }
            Text(
                text = activity.hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
            Text("减脂热量缺口", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 15, 20, 25).forEach { d ->
                    FilterChip(
                        selected = deficit == d,
                        onClick = { deficit = d },
                        label = { Text("$d%") }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "基础代谢 ${profile.bmr.fmt0()} kcal · 日常消耗 ${profile.tdee.fmt0()} kcal",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "推荐每日 ${recommended.kcal.fmt0()} kcal（蛋白 ${recommended.protein.fmt0()} g / " +
                                "脂肪 ${recommended.fat.fmt0()} g / 碳水 ${recommended.carbs.fmt0()} g）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onSaveAndApply(profile) }) { Text("保存并应用推荐目标") }
            }
            TextButton(onClick = { onSave(profile) }) { Text("只保存档案，目标我自己定") }
        }
    }
}

@Composable
private fun SettingsNumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    suffix: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            onChange(input.filter { it.isDigit() || (decimal && it == '.') }.take(6))
        },
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number
        ),
        modifier = modifier
    )
}
