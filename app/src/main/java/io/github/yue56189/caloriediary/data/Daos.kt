package io.github.yue56189.caloriediary.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {

    @Query("SELECT * FROM foods ORDER BY isCustom DESC, category, name")
    fun observeAll(): Flow<List<FoodItem>>

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun count(): Int

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun byId(id: Long): FoodItem?

    /**
     * 本地模糊搜索：完全匹配 > 前缀匹配 > 包含匹配，短的优先。
     */
    @Query(
        """
        SELECT * FROM foods
        WHERE name LIKE '%' || :q || '%'
           OR nameEn LIKE '%' || :q || '%'
           OR category LIKE '%' || :q || '%'
        ORDER BY
            CASE
                WHEN name = :q THEN 0
                WHEN name LIKE :q || '%' THEN 1
                WHEN nameEn LIKE :q || '%' THEN 2
                ELSE 3
            END,
            length(name),
            name
        LIMIT 100
        """
    )
    suspend fun search(q: String): List<FoodItem>

    @Query("SELECT * FROM foods WHERE barcode = :code LIMIT 1")
    suspend fun byBarcode(code: String): FoodItem?

    @Query("SELECT DISTINCT category FROM foods ORDER BY category")
    fun observeCategories(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(food: FoodItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<FoodItem>)

    @Update
    suspend fun update(food: FoodItem)

    @Delete
    suspend fun delete(food: FoodItem)
}

@Dao
interface DiaryDao {

    @Query("SELECT * FROM diary_entries WHERE date = :date ORDER BY meal, createdAt")
    fun observeByDate(date: Long): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE date BETWEEN :from AND :to ORDER BY date, meal, createdAt")
    fun observeRange(from: Long, to: Long): Flow<List<DiaryEntry>>

    @Query("SELECT DISTINCT date FROM diary_entries ORDER BY date DESC")
    fun observeDates(): Flow<List<Long>>

    @Insert
    suspend fun insert(entry: DiaryEntry): Long

    @Update
    suspend fun update(entry: DiaryEntry)

    @Delete
    suspend fun delete(entry: DiaryEntry)

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM diary_entries WHERE date = :date AND meal = :meal")
    suspend fun clearMeal(date: Long, meal: Int)
}

@Dao
interface GoalDao {

    @Query("SELECT * FROM daily_goals WHERE date = :date")
    suspend fun byDate(date: Long): DailyGoal?

    @Query("SELECT * FROM daily_goals WHERE date = :date")
    fun observeByDate(date: Long): Flow<DailyGoal?>

    @Query("SELECT * FROM daily_goals")
    fun observeAll(): Flow<List<DailyGoal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: DailyGoal)

    @Query("DELETE FROM daily_goals WHERE date = :date")
    suspend fun clear(date: Long)
}
