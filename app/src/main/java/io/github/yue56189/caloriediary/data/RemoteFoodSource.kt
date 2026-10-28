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
 * 联网食物查询。三个数据源，全部按「每 100 克可食部」返回：
 *
 * 1. **天行数据 · 营养成分表** —— 国内接口，数据体系即《中国食物成分表》
 *    （谷类 / 蔬菜类 / 畜肉类 … 分类一致），中文食物名命中率最高。需要 API Key。
 * 2. **USDA FoodData Central** —— 美国农业部食物成分库，数据质量最高，需要 API Key
 *    （https://fdc.nal.usda.gov/api-key-signup.html 免费申请，或先用 DEMO_KEY 试用）。
 * 3. **Open Food Facts** —— 开源包装食品库（ODbL），无需 Key，中文商品覆盖率一般。
 *
 * 国内目前没有免费开放的国家级食物成分 API；《中国食物成分表》正版数据本身不公开，
 * 所以这里走聚合接口，Key 由用户自己申请、自己承担调用额度。
 */
object RemoteFoodSource {

    private const val USER_AGENT =
        "CalorieDiary/1.1.0 (Android; +https://github.com/yue56189/calorie-diary)"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .callTimeout(28, TimeUnit.SECONDS)
            .build()
    }

    /** 一次搜索用到的数据源配置。 */
    data class Sources(
        val tianapiKey: String = "",
        val usdaKey: String = "",
        val useOpenFoodFacts: Boolean = true
    ) {
        val any: Boolean get() = useOpenFoodFacts || usdaKey.isNotBlank() || tianapiKey.isNotBlank()
    }

    sealed interface Result {
        data class Success(val items: List<FoodItem>, val note: String) : Result
        data class Failure(val message: String) : Result
    }

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")

    /**
     * 并行查询所有已启用的数据源后合并。任一源失败不影响其他源的结果。
     * 国内源（天行）排在前面，界面上中文食物优先展示。
     */
    suspend fun search(query: String, sources: Sources): Result = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext Result.Failure("请输入食物名称")
        if (!sources.any) {
            return@withContext Result.Failure("还没有启用任何联网数据源，请到「我的 → 联网数据源」填写 API Key")
        }

        val errors = mutableListOf<String>()
        val items = mutableListOf<FoodItem>()

        if (sources.tianapiKey.isNotBlank()) {
            runCatching { tianapi(q, sources.tianapiKey) }
                .onSuccess { items += it }
                .onFailure { errors += "中国食物成分表：${it.message ?: "请求失败"}" }
        }
        if (sources.useOpenFoodFacts) {
            runCatching { openFoodFacts(q) }
                .onSuccess { items += it }
                .onFailure { errors += "Open Food Facts：${it.message ?: "请求失败"}" }
        }
        if (sources.usdaKey.isNotBlank()) {
            runCatching { usda(q, sources.usdaKey) }
                .onSuccess { items += it }
                .onFailure { errors += "USDA：${it.message ?: "请求失败"}" }
        }

        when {
            items.isNotEmpty() -> {
                // 去重：同名且热量接近的只留一条（先出现的源优先保留）
                val seen = HashSet<String>()
                val unique = items.filter { seen.add("${it.name.take(24)}|${it.kcal.toInt()}") }
                    .take(60)
                Result.Success(unique, "共找到 ${unique.size} 条联网结果")
            }
            errors.isNotEmpty() -> Result.Failure(errors.joinToString("；"))
            else -> Result.Failure("没有找到匹配的食物，换个关键词试试")
        }
    }

    // ------------------------------------------------------- 天行数据（国内）

    /** 《中国食物成分表》分类 → 本应用的 12 个分类。 */
    private fun tianCategory(type: String): String = when {
        type.contains("谷") || type.contains("薯") -> "主食"
        type.contains("畜肉") || type.contains("禽肉") || type.contains("蛋") -> "肉蛋"
        type.contains("鱼") || type.contains("虾") || type.contains("蟹") || type.contains("贝") -> "水产"
        type.contains("蔬菜") || type.contains("菌") || type.contains("藻") -> "蔬菜"
        type.contains("水果") -> "水果"
        type.contains("乳") || type.contains("奶") -> "奶制品"
        type.contains("豆") -> "豆制品"
        type.contains("坚果") || type.contains("种子") -> "坚果"
        type.contains("油脂") || type.contains("调味") -> "油脂"
        type.contains("饮料") || type.contains("酒") -> "饮料"
        type.contains("小吃") || type.contains("甜") || type.contains("糖") -> "零食甜点"
        type.contains("速食") || type.contains("快餐") -> "快餐"
        else -> "其他"
    }

    /**
     * 接口：GET https://apis.tianapi.com/nutrient/index?key=&word=米饭&mode=0
     * 返回 result 为单条对象（按名称查询）或对象数组（按分类查询）。
     */
    private fun tianapi(query: String, apiKey: String): List<FoodItem> {
        val url = "https://apis.tianapi.com/nutrient/index" +
                "?key=${encode(apiKey)}&word=${encode(query)}&mode=0"

        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            if (!body.startsWith("{")) error("返回内容不是 JSON")
            val root = JSONObject(body)
            when (val code = root.optInt("code", -1)) {
                200 -> Unit
                150 -> error("调用次数已用完，可到天行数据控制台充值或换用其他数据源")
                230, 240 -> error("API Key 无效或未填写")
                160 -> error("账号还没申请「营养成分表」这个接口")
                250 -> return emptyList()
                130 -> error("调用频率超限，稍等几秒再试")
                else -> error(root.optString("msg").ifBlank { "接口返回 $code" })
            }

            val result = root.opt("result") ?: return emptyList()
            val objects: List<JSONObject> = when (result) {
                is JSONArray -> (0 until result.length()).mapNotNull { result.optJSONObject(it) }
                is JSONObject -> listOf(result)
                else -> emptyList()
            }

            val out = mutableListOf<FoodItem>()
            for (o in objects) {
                val name = o.optString("name").trim()
                if (name.isBlank()) continue
                val kcal = o.optDouble("rl", 0.0)
                if (kcal <= 0.0 || kcal > 950.0) continue
                out += FoodItem(
                    name = name,
                    category = tianCategory(o.optString("type")),
                    kcal = round(kcal * 10) / 10,
                    protein = o.optDouble("dbz", 0.0).safe(),
                    fat = o.optDouble("zf", 0.0).safe(),
                    carbs = o.optDouble("shhf", 0.0).safe(),
                    fiber = o.optDouble("ssxw", 0.0).safe(),
                    sodium = o.optDouble("la", 0.0).safe(),
                    source = "中国食物成分表 · 天行数据"
                )
            }
            return out
        }
    }

    // ------------------------------------------------------------ OFF

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

    private fun Double.safe(): Double =
        if (isNaN() || isInfinite() || this < 0) 0.0 else round(this * 10) / 10

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
