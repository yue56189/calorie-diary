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

    /** 生效目标：当日单独设置过就用当日的，否则用默认目标。 */
    val goal: StateFlow<NutritionGoal> = combine(dayGoal, settings) { daily, s ->
        if (daily != null) {
            NutritionGoal(daily.kcal, daily.protein, daily.fat, daily.carbs)
        } else {
            s.goal
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NutritionGoal(1800.0, 110.0, 50.0, 228.0))

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
            remote = when (val r = RemoteFoodSource.search(q, settings.value.usdaApiKey)) {
                is RemoteFoodSource.Result.Success -> RemoteState(
                    loading = false, items = r.items, note = r.note, sourceQuery = q
                )
                is RemoteFoodSource.Result.Failure -> RemoteState(
                    loading = false, error = r.message, sourceQuery = q
                )
            }
        }
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

    // ------------------------------------------------------------- 派生数据

    fun totalOf(list: List<DiaryEntry>): NutritionTotal = NutritionTotal.of(list)
}
