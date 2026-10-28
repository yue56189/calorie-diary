package io.github.yue56189.caloriediary.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.yue56189.caloriediary.CalorieDiaryApp
import io.github.yue56189.caloriediary.data.ActivityLevel
import io.github.yue56189.caloriediary.data.AppSettings
import io.github.yue56189.caloriediary.data.DailyGoal
import io.github.yue56189.caloriediary.data.DiaryEntry
import io.github.yue56189.caloriediary.data.FoodItem
import io.github.yue56189.caloriediary.data.Meal
import io.github.yue56189.caloriediary.data.NutritionGoal
import io.github.yue56189.caloriediary.data.NutritionMath
import io.github.yue56189.caloriediary.data.NutritionTotal
import io.github.yue56189.caloriediary.data.Profile
import io.github.yue56189.caloriediary.data.RemoteFoodSource
import io.github.yue56189.caloriediary.data.Sex
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 联网搜索的界面状态。 */
data class RemoteState(
    val loading: Boolean = false,
    val items: List<FoodItem> = emptyList(),
    val error: String? = null,
    val note: String = "",
    val sourceQuery: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as CalorieDiaryApp).repository
    private val prefs = (app as CalorieDiaryApp).prefs

    val settings: StateFlow<AppSettings> = prefs.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val date = MutableStateFlow(LocalDate.now())

    val today: LocalDate = LocalDate.now()

    val entries: StateFlow<List<DiaryEntry>> = date
        .flatMapLatest { repo.entriesOf(it.toEpochDay()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dayGoal: StateFlow<DailyGoal?> = date
        .flatMapLatest { repo.goalOf(it.toEpochDay()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 当天填的运动消耗（千卡）。 */
    val exerciseKcal: StateFlow<Double> = date
        .flatMapLatest { repo.activityOf(it.toEpochDay()) }
        .map { it?.exerciseKcal ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    /** 生效目标：当日单独设置过就用当日的，否则用默认目标。 */
    val goal: StateFlow<NutritionGoal> = combine(dayGoal, settings) { daily, s ->
        if (daily != null) {
            NutritionGoal(daily.kcal, daily.protein, daily.fat, daily.carbs)
        } else {
            s.goal
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NutritionGoal(1800.0, 110.0, 50.0, 228.0))

    /**
     * 界面实际使用的目标。
     *
     * 手动模式直接用手填的目标；「基础代谢 + 运动消耗」模式下热量口径由
     * BMR 与当天运动量决定，三大营养素按原比例等比缩放。
     */
    val effectiveGoal: StateFlow<NutritionGoal> =
        combine(goal, exerciseKcal, settings) { base, exercise, s ->
            if (s.goalMode == 1) {
                NutritionMath.scaleTo(base, NutritionMath.budget(s.profile.bmr, exercise))
            } else {
                base
            }
        }.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            NutritionGoal(1800.0, 110.0, 50.0, 228.0)
        )

    /** 当日热量预算（含运动消耗），供「预算构成」卡片展示。 */
    val budgetKcal: StateFlow<Double> = combine(exerciseKcal, settings) { exercise, s ->
        if (s.goalMode == 1) NutritionMath.budget(s.profile.bmr, exercise) else 0.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val allFoods: StateFlow<List<FoodItem>> = repo.foods
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<String>> = repo.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 最近 30 天，用于统计页与连续打卡。 */
    val recentEntries: StateFlow<List<DiaryEntry>> = repo
        .entriesBetween(today.minusDays(29).toEpochDay(), today.toEpochDay())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentGoals: StateFlow<List<DailyGoal>> = repo.allGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var remote by mutableStateOf(RemoteState())
        private set

    var ready by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            repo.seedIfEmpty()
            ready = true
        }
    }

    // ------------------------------------------------------------- 日期

    fun shiftDay(days: Long) {
        date.value = date.value.plusDays(days)
    }

    fun goToday() {
        date.value = LocalDate.now()
    }

    /** 日历选择后直接跳到某一天。 */
    fun setDate(target: LocalDate) {
        date.value = target
    }

    // ------------------------------------------------------------- 运动消耗

    fun setExercise(kcal: Double) {
        viewModelScope.launch { repo.setExercise(date.value, kcal) }
    }

    // ------------------------------------------------------------- 打卡

    fun addFood(meal: Meal, food: FoodItem, grams: Double) {
        viewModelScope.launch {
            repo.addEntry(date.value, meal, food, grams)
        }
    }

    fun addRemoteToLibrary(food: FoodItem, thenAddTo: Pair<Meal, Double>? = null) {
        viewModelScope.launch {
            repo.importRemote(food)
            if (thenAddTo != null) {
                repo.addEntry(date.value, thenAddTo.first, food, thenAddTo.second)
            }
        }
    }

    fun deleteEntry(entry: DiaryEntry) {
        viewModelScope.launch { repo.deleteEntry(entry) }
    }

    fun clearMeal(meal: Meal) {
        viewModelScope.launch { repo.clearMeal(date.value, meal) }
    }

    fun updateEntryGrams(entry: DiaryEntry, grams: Double, food: FoodItem?) {
        if (food == null) return
        viewModelScope.launch { repo.updateEntry(entry, grams, food) }
    }

    // ------------------------------------------------------------- 食物库

    suspend fun searchLocal(query: String): List<FoodItem> = repo.searchLocal(query)

    suspend fun getFood(id: Long): FoodItem? = repo.foodById(id)

    fun saveFood(food: FoodItem, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch { onDone(repo.saveFood(food)) }
    }

    fun deleteFood(food: FoodItem) {
        viewModelScope.launch { repo.deleteFood(food) }
    }

    fun searchRemote(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        remote = RemoteState(loading = true, sourceQuery = q)
        viewModelScope.launch {
            remote = when (val r = RemoteFoodSource.search(q, sourcesOf(settings.value))) {
                is RemoteFoodSource.Result.Success -> RemoteState(
                    loading = false, items = r.items, note = r.note, sourceQuery = q
                )
                is RemoteFoodSource.Result.Failure -> RemoteState(
                    loading = false, error = r.message, sourceQuery = q
                )
            }
        }
    }

    private fun sourcesOf(s: AppSettings) = RemoteFoodSource.Sources(
        tianapiKey = s.tianapiKey,
        usdaKey = s.usdaApiKey,
        useOpenFoodFacts = s.useOpenFoodFacts
    )

    /**
     * 快速记录用的一次性查询：先本地库，没有命中再联网。
     * 与 [searchRemote] 的区别是它同步返回结果，不写界面状态。
     */
    suspend fun quickLookup(query: String): List<FoodItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val local = repo.searchLocal(q)
        if (local.isNotEmpty()) return local.take(20)
        return when (val r = RemoteFoodSource.search(q, sourcesOf(settings.value))) {
            is RemoteFoodSource.Result.Success -> r.items.take(20)
            is RemoteFoodSource.Result.Failure -> emptyList()
        }
    }

    /** 直接把一条（可能来自联网的）食物加进某一餐，不写入食物库。 */
    fun addEntryDirect(meal: Meal, food: FoodItem, grams: Double) {
        viewModelScope.launch { repo.addEntry(date.value, meal, food, grams) }
    }

    fun clearRemote() {
        remote = RemoteState()
    }

    // ------------------------------------------------------------- 目标

    fun saveDefaultGoal(goal: NutritionGoal) {
        viewModelScope.launch { prefs.saveGoal(goal) }
    }

    /** 只为某一天设置目标。 */
    fun saveGoalForDate(target: LocalDate, goal: NutritionGoal) {
        viewModelScope.launch { repo.setDailyGoal(target, goal) }
    }

    fun resetGoalForDate(target: LocalDate) {
        viewModelScope.launch { repo.clearDailyGoal(target) }
    }

    fun saveProfile(profile: Profile) {
        viewModelScope.launch { prefs.saveProfile(profile) }
    }

    fun saveProfileAndGoal(profile: Profile, goal: NutritionGoal) {
        viewModelScope.launch {
            prefs.saveProfile(profile)
            prefs.saveGoal(goal)
        }
    }

    fun setThemeMode(mode: Int) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { prefs.setDynamicColor(enabled) }
    }

    fun setUsdaKey(key: String) {
        viewModelScope.launch { prefs.setUsdaKey(key) }
    }

    fun setTianapiKey(key: String) {
        viewModelScope.launch { prefs.setTianapiKey(key) }
    }

    fun setUseOpenFoodFacts(enabled: Boolean) {
        viewModelScope.launch { prefs.setUseOpenFoodFacts(enabled) }
    }

    fun setGoalMode(mode: Int) {
        viewModelScope.launch { prefs.setGoalMode(mode) }
    }

    // ------------------------------------------------------------- 派生数据

    fun totalOf(list: List<DiaryEntry>): NutritionTotal = NutritionTotal.of(list)
}
