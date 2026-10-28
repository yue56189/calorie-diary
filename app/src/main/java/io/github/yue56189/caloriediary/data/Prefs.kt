package io.github.yue56189.caloriediary.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("settings")

/** 全局设置（默认目标、身体档案、外观、联网数据源）。 */
data class AppSettings(
    val goal: NutritionGoal = NutritionGoal(1800.0, 110.0, 50.0, 228.0),
    val profile: Profile = Profile(),
    /** 0 跟随系统 / 1 浅色 / 2 深色 */
    val themeMode: Int = 0,
    val dynamicColor: Boolean = false,
    /** 可选：USDA FoodData Central 的 API Key，为空则不查该源。 */
    val usdaApiKey: String = "",
    /** 可选：天行数据（国内·中国食物成分表体系）的 API Key。 */
    val tianapiKey: String = "",
    /** Open Food Facts 无需 Key，默认可关。 */
    val useOpenFoodFacts: Boolean = true,
    /** 热量目标口径：0 手动设定 / 1 基础代谢 + 运动消耗。 */
    val goalMode: Int = 0
) {
    val hasRemoteSource: Boolean
        get() = useOpenFoodFacts || usdaApiKey.isNotBlank() || tianapiKey.isNotBlank()
}

class Prefs(private val context: Context) {

    private object Keys {
        val GOAL_KCAL = doublePreferencesKey("goal_kcal")
        val GOAL_PROTEIN = doublePreferencesKey("goal_protein")
        val GOAL_FAT = doublePreferencesKey("goal_fat")
        val GOAL_CARBS = doublePreferencesKey("goal_carbs")

        val SEX = stringPreferencesKey("profile_sex")
        val AGE = intPreferencesKey("profile_age")
        val HEIGHT = doublePreferencesKey("profile_height")
        val WEIGHT = doublePreferencesKey("profile_weight")
        val ACTIVITY = stringPreferencesKey("profile_activity")
        val DEFICIT = intPreferencesKey("profile_deficit")

        val THEME = intPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val USDA_KEY = stringPreferencesKey("usda_key")
        val TIAN_KEY = stringPreferencesKey("tianapi_key")
        val USE_OFF = booleanPreferencesKey("use_open_food_facts")
        val GOAL_MODE = intPreferencesKey("goal_mode")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        val defaults = AppSettings()
        AppSettings(
            goal = NutritionGoal(
                kcal = p[Keys.GOAL_KCAL] ?: defaults.goal.kcal,
                protein = p[Keys.GOAL_PROTEIN] ?: defaults.goal.protein,
                fat = p[Keys.GOAL_FAT] ?: defaults.goal.fat,
                carbs = p[Keys.GOAL_CARBS] ?: defaults.goal.carbs
            ),
            profile = Profile(
                sex = runCatching { Sex.valueOf(p[Keys.SEX] ?: "FEMALE") }.getOrDefault(Sex.FEMALE),
                age = p[Keys.AGE] ?: defaults.profile.age,
                heightCm = p[Keys.HEIGHT] ?: defaults.profile.heightCm,
                weightKg = p[Keys.WEIGHT] ?: defaults.profile.weightKg,
                activity = runCatching { ActivityLevel.valueOf(p[Keys.ACTIVITY] ?: "LIGHT") }
                    .getOrDefault(ActivityLevel.LIGHT),
                deficitPercent = p[Keys.DEFICIT] ?: defaults.profile.deficitPercent
            ),
            themeMode = p[Keys.THEME] ?: 0,
            dynamicColor = p[Keys.DYNAMIC] ?: false,
            usdaApiKey = p[Keys.USDA_KEY] ?: "",
            tianapiKey = p[Keys.TIAN_KEY] ?: "",
            useOpenFoodFacts = p[Keys.USE_OFF] ?: true,
            goalMode = p[Keys.GOAL_MODE] ?: 0
        )
    }

    suspend fun saveGoal(goal: NutritionGoal) {
        context.settingsDataStore.edit { p ->
            p[Keys.GOAL_KCAL] = goal.kcal
            p[Keys.GOAL_PROTEIN] = goal.protein
            p[Keys.GOAL_FAT] = goal.fat
            p[Keys.GOAL_CARBS] = goal.carbs
        }
    }

    suspend fun saveProfile(profile: Profile) {
        context.settingsDataStore.edit { p ->
            p[Keys.SEX] = profile.sex.name
            p[Keys.AGE] = profile.age
            p[Keys.HEIGHT] = profile.heightCm
            p[Keys.WEIGHT] = profile.weightKg
            p[Keys.ACTIVITY] = profile.activity.name
            p[Keys.DEFICIT] = profile.deficitPercent
        }
    }

    suspend fun setThemeMode(mode: Int) {
        context.settingsDataStore.edit { it[Keys.THEME] = mode }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DYNAMIC] = enabled }
    }

    suspend fun setUsdaKey(key: String) {
        context.settingsDataStore.edit { it[Keys.USDA_KEY] = key.trim() }
    }

    suspend fun setTianapiKey(key: String) {
        context.settingsDataStore.edit { it[Keys.TIAN_KEY] = key.trim() }
    }

    suspend fun setUseOpenFoodFacts(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.USE_OFF] = enabled }
    }

    suspend fun setGoalMode(mode: Int) {
        context.settingsDataStore.edit { it[Keys.GOAL_MODE] = mode }
    }
}
