package io.github.yue56189.caloriediary.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FoodItem::class, DiaryEntry::class, DailyGoal::class, DailyActivity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun foodDao(): FoodDao
    abstract fun diaryDao(): DiaryDao
    abstract fun goalDao(): GoalDao
    abstract fun activityDao(): ActivityDao

    companion object {

        /**
         * v1 → v2：新增每日运动消耗表。
         *
         * 餐次从 4 段扩到 6 段是纯代码层改动（meal 仍是 Int），不需要迁移；
         * v1 里 meal=3 的「加餐」记录会自动落到新的「早餐后加餐」。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `daily_activity` (" +
                            "`date` INTEGER NOT NULL, " +
                            "`exerciseKcal` REAL NOT NULL, " +
                            "PRIMARY KEY(`date`))"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "calorie-diary.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
