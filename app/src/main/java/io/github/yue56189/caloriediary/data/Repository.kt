package io.github.yue56189.caloriediary.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.time.LocalDate

class Repository(
    private val db: AppDatabase,
    private val context: Context
) {
    private val foodDao = db.foodDao()
    private val diaryDao = db.diaryDao()
    private val goalDao = db.goalDao()

    val foods: Flow<List<FoodItem>> = foodDao.observeAll()
    val categories: Flow<List<String>> = foodDao.observeCategories()

    fun entriesOf(date: Long): Flow<List<DiaryEntry>> = diaryDao.observeByDate(date)
    fun entriesBetween(from: Long, to: Long): Flow<List<DiaryEntry>> =
        diaryDao.observeRange(from, to)
    fun goalOf(date: Long): Flow<DailyGoal?> = goalDao.observeByDate(date)
    fun allGoals(): Flow<List<DailyGoal>> = goalDao.observeAll()

    suspend fun searchLocal(query: String): List<FoodItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) emptyList() else foodDao.search(query.trim())
    }

    suspend fun addEntry(
        date: LocalDate,
        meal: Meal,
        food: FoodItem,
        grams: Double
    ): Long = withContext(Dispatchers.IO) {
        val ratio = grams / 100.0
        diaryDao.insert(
            DiaryEntry(
                date = date.toEpochDay(),
                meal = meal.code,
                foodId = if (food.id > 0) food.id else null,
                name = food.name,
                grams = grams,
                kcal = food.kcal * ratio,
                protein = food.protein * ratio,
                fat = food.fat * ratio,
                carbs = food.carbs * ratio
            )
        )
    }

    suspend fun updateEntry(entry: DiaryEntry, grams: Double, food: FoodItem?) =
        withContext(Dispatchers.IO) {
            val base = food ?: return@withContext
            val ratio = grams / 100.0
            diaryDao.update(
                entry.copy(
                    grams = grams,
                    kcal = base.kcal * ratio,
                    protein = base.protein * ratio,
                    fat = base.fat * ratio,
                    carbs = base.carbs * ratio
                )
            )
        }

    suspend fun deleteEntry(entry: DiaryEntry) = withContext(Dispatchers.IO) {
        diaryDao.delete(entry)
    }

    suspend fun clearMeal(date: LocalDate, meal: Meal) = withContext(Dispatchers.IO) {
        diaryDao.clearMeal(date.toEpochDay(), meal.code)
    }

    suspend fun saveFood(food: FoodItem): Long = withContext(Dispatchers.IO) {
        if (food.id == 0L) foodDao.insert(food) else {
            foodDao.update(food); food.id
        }
    }

    suspend fun foodById(id: Long): FoodItem? = withContext(Dispatchers.IO) { foodDao.byId(id) }

    suspend fun deleteFood(food: FoodItem) = withContext(Dispatchers.IO) { foodDao.delete(food) }

    suspend fun setDailyGoal(date: LocalDate, goal: NutritionGoal) = withContext(Dispatchers.IO) {
        goalDao.upsert(
            DailyGoal(
                date = date.toEpochDay(),
                kcal = goal.kcal,
                protein = goal.protein,
                fat = goal.fat,
                carbs = goal.carbs
            )
        )
    }

    suspend fun clearDailyGoal(date: LocalDate) = withContext(Dispatchers.IO) {
        goalDao.clear(date.toEpochDay())
    }

    suspend fun importRemote(food: FoodItem): Long = withContext(Dispatchers.IO) {
        foodDao.insert(food.copy(isCustom = true))
    }

    /** 首次启动时把内置食物库写进数据库。 */
    suspend fun seedIfEmpty() = withContext(Dispatchers.IO) {
        if (foodDao.count() > 0) return@withContext
        val json = runCatching {
            context.assets.open("foods.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return@withContext

        val array = runCatching { JSONArray(json) }.getOrNull() ?: return@withContext
        val items = ArrayList<FoodItem>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            items += FoodItem(
                name = o.optString("name"),
                nameEn = o.optString("nameEn"),
                category = o.optString("category", "其他"),
                kcal = o.optDouble("kcal", 0.0),
                protein = o.optDouble("protein", 0.0),
                fat = o.optDouble("fat", 0.0),
                carbs = o.optDouble("carbs", 0.0),
                fiber = o.optDouble("fiber", 0.0),
                sodium = o.optDouble("sodium", 0.0),
                portionLabel = o.optString("portionLabel"),
                portionGrams = o.optDouble("portionGrams", 0.0),
                source = o.optString("source", "USDA FoodData Central")
            )
            if (items.last().name.isBlank() || items.last().kcal <= 0) items.removeAt(items.lastIndex)
        }
        if (items.isNotEmpty()) foodDao.insertAll(items)
    }
}
