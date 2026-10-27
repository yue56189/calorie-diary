package io.github.yue56189.caloriediary.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.round

/**
 * 联网食物查询。
 *
 * 两个数据源：
 * 1. Open Food Facts —— 开源食品数据库（ODbL 许可），覆盖全球包装食品，含中文商品名。无需 Key。
 * 2. USDA FoodData Central —— 美国农业部食物成分库，数据质量最高，需要 API Key
 *    （可在 https://fdc.nal.usda.gov/api-key-signup.html 免费申请，或先用 DEMO_KEY 试用）。
 */
object RemoteFoodSource {

    private const val USER_AGENT =
        "CalorieDiary/1.0.0 (Android; +https://github.com/yue56189/calorie-diary)"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .callTimeout(28, TimeUnit.SECONDS)
            .build()
    }

    sealed interface Result {
        data class Success(val items: List<FoodItem>, val note: String) : Result
        data class Failure(val message: String) : Result
    }

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")

    /**
     * 并行查询两个数据源后合并。任一源失败不影响另一个源的结果。
     */
    suspend fun search(query: String, usdaKey: String): Result = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext Result.Failure("请输入食物名称")

        val errors = mutableListOf<String>()
        val items = mutableListOf<FoodItem>()

        val off = runCatching { openFoodFacts(q) }
        off.onSuccess { items += it }
            .onFailure { errors += "Open Food Facts：${it.message ?: "请求失败"}" }

        val key = usdaKey.trim()
        if (key.isNotEmpty()) {
            val usda = runCatching { usda(q, key) }
            usda.onSuccess { items += it }
                .onFailure { errors += "USDA：${it.message ?: "请求失败"}" }
        }

        when {
            items.isNotEmpty() -> {
                // 去重：同名且热量接近的只留一条
                val seen = HashSet<String>()
                val unique = items.filter { seen.add("${it.name.take(24)}|${it.kcal.toInt()}") }
                    .take(60)
                Result.Success(unique, "共找到 ${unique.size} 条联网结果")
            }
            errors.isNotEmpty() -> Result.Failure(errors.joinToString("；"))
            else -> Result.Failure("没有找到匹配的食物，换个关键词试试")
        }
    }

    // ---------------------------------------------------------------- OFF

    private fun openFoodFacts(query: String): List<FoodItem> {
        val url = "https://world.openfoodfacts.org/cgi/search.pl" +
                "?search_terms=${encode(query)}" +
                "&search_simple=1&action=process&json=1&page_size=40" +
                "&fields=code,product_name,product_name_zh,product_name_en,brands,quantity,nutriments"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            if (!body.startsWith("{")) error("返回内容不是 JSON")
            val root = JSONObject(body)
            val products: JSONArray = root.optJSONArray("products") ?: JSONArray()
            val result = mutableListOf<FoodItem>()
            for (i in 0 until products.length()) {
                val p = products.optJSONObject(i) ?: continue
                val name = sequenceOf(
                    p.optString("product_name_zh"),
                    p.optString("product_name"),
                    p.optString("product_name_en")
                ).firstOrNull { it.isNotBlank() } ?: continue

                val n = p.optJSONObject("nutriments") ?: continue
                var kcal = n.optDouble("energy-kcal_100g", Double.NaN)
                if (kcal.isNaN() || kcal <= 0.0) {
                    val kj = n.optDouble("energy_100g", Double.NaN)
                    if (!kj.isNaN() && kj > 0) kcal = kj / 4.184
                }
                if (kcal.isNaN() || kcal <= 0.0 || kcal > 900) continue

                val brand = p.optString("brands").substringBefore(",").trim()
                val display = if (brand.isNotBlank() && !name.contains(brand)) "$name · $brand" else name

                result += FoodItem(
                    name = display,
                    nameEn = p.optString("product_name_en"),
                    category = "包装食品",
                    kcal = round(kcal * 10) / 10,
                    protein = n.optDouble("proteins_100g", 0.0).safe(),
                    fat = n.optDouble("fat_100g", 0.0).safe(),
                    carbs = n.optDouble("carbohydrates_100g", 0.0).safe(),
                    fiber = n.optDouble("fiber_100g", 0.0).safe(),
                    sodium = (n.optDouble("sodium_100g", 0.0) * 1000).safe(),
                    barcode = p.optString("code"),
                    source = "Open Food Facts"
                )
            }
            return result
        }
    }

    private fun Double.safe(): Double = if (isNaN() || isInfinite() || this < 0) 0.0 else round(this * 10) / 10

    // ---------------------------------------------------------------- USDA

    private fun usda(query: String, apiKey: String): List<FoodItem> {
        val url = "https://api.nal.usda.gov/fdc/v1/foods/search" +
                "?api_key=${encode(apiKey)}" +
                "&query=${encode(query)}" +
                "&pageSize=30&pageNumber=1"

        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()

        client.newCall(request).execute().use { response ->
            if (response.code == 429) error("免费额度已用完，请在设置中填入自己的 API Key")
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            val root = JSONObject(body)
            val foods: JSONArray = root.optJSONArray("foods") ?: JSONArray()
            val result = mutableListOf<FoodItem>()
            for (i in 0 until foods.length()) {
                val f = foods.optJSONObject(i) ?: continue
                val name = f.optString("description").trim()
                if (name.isBlank()) continue

                var kcal = 0.0
                var protein = 0.0
                var fat = 0.0
                var carbs = 0.0
                val nutrients = f.optJSONArray("foodNutrients") ?: continue
                for (j in 0 until nutrients.length()) {
                    val n = nutrients.optJSONObject(j) ?: continue
                    val nutrient = n.optString("nutrientName")
                    val unit = n.optString("unitName")
                    val value = n.optDouble("value", 0.0)
                    when {
                        nutrient.equals("Energy", true) && unit.equals("KCAL", true) -> kcal = value
                        nutrient.equals("Energy", true) && unit.equals("kJ", true) && kcal == 0.0 ->
                            kcal = value / 4.184
                        nutrient.startsWith("Protein", true) -> protein = value
                        nutrient.startsWith("Total lipid (fat)", true) -> fat = value
                        nutrient.startsWith("Carbohydrate, by difference", true) -> carbs = value
                    }
                }
                if (kcal <= 0 || kcal > 900) continue

                result += FoodItem(
                    name = name.lowercase().replaceFirstChar { it.uppercase() },
                    nameEn = name,
                    category = "联网食物",
                    kcal = round(kcal * 10) / 10,
                    protein = protein.safe(),
                    fat = fat.safe(),
                    carbs = carbs.safe(),
                    source = "USDA FoodData Central"
                )
            }
            return result
        }
    }
}
