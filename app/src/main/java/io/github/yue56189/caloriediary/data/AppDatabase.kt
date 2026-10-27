package io.github.yue56189.caloriediary.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FoodItem::class, DiaryEntry::class, DailyGoal::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun foodDao(): FoodDao
    abstract fun diaryDao(): DiaryDao
    abstract fun goalDao(): GoalDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "calorie-diary.db")
                .build()
    }
}
