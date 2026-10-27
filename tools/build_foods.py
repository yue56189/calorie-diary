"""
从 USDA FoodData Central (SR Legacy) 生成 App 内置食物库。

用法：
    python tools/build_foods.py <SR_Legacy_JSON路径> <输出foods.json路径>

数据来源：USDA FoodData Central, SR Legacy（公有领域）。
所有营养值统一为「每 100 克可食部」。

SPEC 里每一项 = (中文名, 分类, 必须命中的词, 必须排除的词, 份量标签, 份量克数)。
匹配规则：
  1. 描述必须命中 inc 里每一个词（按单词边界，避免 oats 命中 groats）；
  2. 命中 exc 任一词即淘汰；
  3. 剩下的按描述长度升序取第一条（描述越短，越接近「基础食材」而非某道成品菜）。
"""

import json
import re
import sys
import unicodedata

N_ENERGY = "208"
N_PROTEIN = "203"
N_FAT = "204"
N_CARB = "205"
N_FIBER = "291"
N_SODIUM = "307"

GLOBAL_EXCLUDE = ("babyfood", "infant formula", "junior", "toddler", "usda commodity")


def norm(s: str) -> str:
    return unicodedata.normalize("NFKD", s or "").lower()


def has_term(desc: str, term: str) -> bool:
    """按单词边界匹配，避免 oats 命中 groats、wing 命中 stewing。"""
    return re.search(r"(?<![a-z])" + re.escape(term) + r"(?![a-z])", desc) is not None


# (中文名, 分类, 必须命中, 必须排除, 份量标签, 份量克数)
SPEC = [
    # ---------------- 主食 ----------------
    ("白米饭（熟）", "主食", ("rice", "white", "cooked"), ("glutinous", "instant"), "1 碗", 150),
    ("糙米饭（熟）", "主食", ("rice", "brown", "cooked"),
     ("instant", "parboiled", "sausage", "pork", "beef", "chicken", "soup", "pasta", "flour", "bread"), "1 碗", 150),
    ("大米（生）", "主食", ("rice", "white", "long-grain", "regular", "raw"), (), "1 碗生米", 80),
    ("燕麦片（干）", "主食", ("oats",), ("ready-to-eat", "granola", "groats", "instant", "honey", "breakfast",
                                  "quaker", "fortified", "bar", "chewy", "formulated", "cereal", "snack"),
     "1 份", 40),
    ("燕麦粥（冲泡）", "主食", ("oats", "instant", "prepared", "water"),
     ("ready-to-eat", "raisins", "dates", "apples", "maple", "cinnamon", "spice"), "1 碗", 240),
    ("面条（煮熟）", "主食", ("pasta", "cooked"),
     ("gluten-free", "homemade", "whole-wheat", "whole grain", "fresh-refrigerated", "spinach", "corn", "rice",
      "protein"), "1 碗", 200),
    ("荞麦（熟）", "主食", ("buckwheat groats", "cooked"), (), "1 碗", 200),
    ("米线（熟）", "主食", ("rice noodles", "cooked"), (), "1 碗", 200),
    ("白面包", "主食", ("bread", "white", "commercially"), ("low sodium", "no salt", "toasted", "toasting"), "1 片", 30),
    ("全麦面包", "主食", ("bread", "whole-wheat", "commercially"), ("toasted",), "1 片", 30),
    ("玉米（鲜）", "主食", ("corn", "sweet", "yellow", "raw"), (), "1 根", 150),
    ("玉米粒（熟）", "主食", ("corn", "sweet", "yellow", "cooked", "drained"), ("canned", "cream"), "1 碗", 150),
    ("红薯（生）", "主食", ("sweet potato", "raw", "unprepared"), ("leaves",), "1 个", 150),
    ("红薯（烤）", "主食", ("sweet potato", "baked in skin", "flesh", "without salt"), (), "1 个", 150),
    ("土豆（煮）", "主食", ("potatoes", "boiled", "flesh", "without salt"), ("without skin",), "1 个", 150),
    ("藜麦（熟）", "主食", ("quinoa", "cooked"), (), "1 碗", 185),
    ("小米（生）", "主食", ("millet", "raw"), (), "1 份", 50),

    # ---------------- 肉蛋 ----------------
    ("鸡蛋", "肉蛋", ("egg", "whole", "raw", "fresh"), ("duck", "goose", "quail", "dried", "powder"), "1 个", 50),
    ("鸡蛋（煮）", "肉蛋", ("egg", "whole", "cooked", "hard-boiled"), (), "1 个", 50),
    ("鸡蛋白", "肉蛋", ("egg", "white", "raw", "fresh"), ("dried", "powder"), "1 个蛋白", 33),
    ("鸡蛋黄", "肉蛋", ("egg", "yolk", "raw", "fresh"), ("dried",), "1 个蛋黄", 17),
    ("鸡胸肉（熟）", "肉蛋", ("chicken", "breast", "meat only", "cooked"),
     ("solution", "canned", "fried", "stewed", "braised"), "1 块", 120),
    ("鸡胸肉（生）", "肉蛋", ("chicken", "breast", "meat only", "raw"), ("solution", "canned"), "1 块", 120),
    ("鸡腿肉（熟）", "肉蛋", ("chicken", "thigh", "meat only", "cooked"), ("solution", "canned"), "1 个", 100),
    ("鸡翅（熟）", "肉蛋", ("chicken", "wing", "meat only", "cooked"), ("solution", "canned"), "1 个", 30),
    ("鸡爪", "肉蛋", ("chicken", "feet", "boiled"), (), "1 个", 20),
    ("猪里脊（生）", "肉蛋", ("pork", "tenderloin", "raw"), ("enhanced", "solution", "separable lean and fat"),
     "1 份", 100),
    ("猪里脊（熟）", "肉蛋", ("pork", "tenderloin", "cooked"), ("enhanced", "solution", "separable lean and fat"),
     "1 份", 100),
    ("五花肉（生）", "肉蛋", ("pork", "belly", "raw"), ("enhanced", "solution"), "1 份", 100),
    ("猪肉馅（生）", "肉蛋", ("pork", "ground", "raw"), ("enhanced", "solution", "cooked"), "1 份", 100),
    ("牛肉（后腿，生）", "肉蛋", ("beef", "round", "raw"),
     ("ground", "separable lean and fat", "new zealand"), "1 份", 100),
    ("牛排（上后腰，熟）", "肉蛋", ("beef", "top sirloin", "separable lean only", "cooked"), ("1/8",), "1 块", 150),
    ("牛肉馅（85% 瘦）", "肉蛋", ("beef", "ground", "85% lean meat", "raw"), ("patty",), "1 份", 100),
    ("羊肉（腿，生）", "肉蛋", ("lamb", "leg", "separable lean only", "raw"), ("new zealand", "frozen"), "1 份", 100),
    ("鸭肉（熟）", "肉蛋", ("duck", "meat only", "cooked"), ("skin",), "1 份", 100),
    ("培根", "肉蛋", ("pork", "cured", "bacon", "unprepared"), ("canadian",), "1 片", 15),
    ("火腿", "肉蛋", ("ham", "sliced", "regular"), ("canned", "honey"), "1 片", 30),
    ("香肠（猪牛肉）", "肉蛋", ("sausage", "pork and beef", "fresh", "cooked"), (), "1 根", 35),
    ("猪肝", "肉蛋", ("pork", "liver", "raw"), ("braised",), "1 份", 100),
    ("火鸡胸（熟）", "肉蛋", ("turkey", "breast", "meat only", "cooked"), ("skin", "solution"), "1 份", 100),

    # ---------------- 水产 ----------------
    ("三文鱼（生）", "水产", ("fish", "salmon", "atlantic", "farmed", "raw"), ("smoked",), "1 份", 100),
    ("三文鱼（熟）", "水产", ("fish", "salmon", "atlantic", "farmed", "cooked"), ("smoked",), "1 份", 100),
    ("虾（熟）", "水产", ("crustaceans", "shrimp", "cooked"), ("canned", "breaded"), "6 只", 85),
    ("虾（生）", "水产", ("crustaceans", "shrimp", "raw"), ("breaded",), "6 只", 85),
    ("鲈鱼", "水产", ("fish", "bass", "raw"), ("sea bass",), "1 份", 100),
    ("鳕鱼（生）", "水产", ("fish", "cod", "atlantic", "raw"), ("dried",), "1 份", 100),
    ("金枪鱼罐头（水浸）", "水产", ("fish", "tuna", "light", "canned in water"), ("oil",), "1 罐", 100),
    ("罗非鱼", "水产", ("fish", "tilapia", "raw"), (), "1 份", 100),
    ("鲭鱼", "水产", ("fish", "mackerel", "raw"), (), "1 份", 100),
    ("扇贝", "水产", ("mollusks", "scallop"), ("squash", "potatoes"), "5 个", 80),
    ("蛤蜊", "水产", ("mollusks", "clam"), ("soup", "breaded", "juice"), "1 份", 100),
    ("章鱼", "水产", ("mollusks", "octopus", "raw"), (), "1 份", 100),
    ("蟹肉", "水产", ("crustaceans", "crab", "blue", "raw"), ("imitation",), "1 份", 100),

    # ---------------- 蔬菜 ----------------
    ("西兰花", "蔬菜", ("broccoli", "raw"), ("leaves",), "1 份", 100),
    ("菠菜", "蔬菜", ("spinach", "raw"), (), "1 份", 100),
    ("生菜", "蔬菜", ("lettuce", "green leaf", "raw"), (), "1 份", 80),
    ("番茄", "蔬菜", ("tomatoes", "red", "ripe", "raw"), ("canned", "juice"), "1 个", 120),
    ("黄瓜", "蔬菜", ("cucumber", "with peel", "raw"), (), "1 根", 150),
    ("胡萝卜", "蔬菜", ("carrots", "raw"), ("juice",), "1 根", 80),
    ("大白菜", "蔬菜", ("cabbage", "chinese", "pe-tsai", "raw"), (), "1 份", 100),
    ("小白菜", "蔬菜", ("cabbage", "chinese", "pak-choi", "raw"), (), "1 份", 100),
    ("卷心菜", "蔬菜", ("cabbage", "raw"), ("chinese", "red", "savoy", "napa"), "1 份", 100),
    ("紫甘蓝", "蔬菜", ("cabbage", "red", "raw"), (), "1 份", 100),
    ("茄子", "蔬菜", ("eggplant", "raw"), (), "1 根", 150),
    ("青椒", "蔬菜", ("peppers", "sweet", "green", "raw"), (), "1 个", 100),
    ("红椒", "蔬菜", ("peppers", "sweet", "red", "raw"), (), "1 个", 100),
    ("洋葱", "蔬菜", ("onions", "raw"), ("cooked", "dehydrated", "welsh", "spring"), "1 个", 110),
    ("白蘑菇", "蔬菜", ("mushrooms", "white", "raw"), ("cooked",), "1 份", 70),
    ("南瓜", "蔬菜", ("pumpkin", "raw"), ("seeds", "leaves"), "1 份", 100),
    ("芦笋", "蔬菜", ("asparagus", "raw"), (), "1 份", 100),
    ("芹菜", "蔬菜", ("celery", "raw"), (), "1 根", 40),
    ("四季豆", "蔬菜", ("beans", "snap", "green", "raw"), ("canned",), "1 份", 100),
    ("豌豆", "蔬菜", ("peas", "green", "raw"), ("split", "canned"), "1 份", 100),
    ("菜花", "蔬菜", ("cauliflower", "raw"), (), "1 份", 100),
    ("秋葵", "蔬菜", ("okra", "raw"), (), "1 份", 100),
    ("绿豆芽", "蔬菜", ("mung beans", "sprouted", "raw"), (), "1 份", 100),
    ("生姜", "蔬菜", ("ginger root", "raw"), (), "1 片", 5),
    ("大蒜", "蔬菜", ("garlic", "raw"), (), "1 瓣", 3),
    ("大葱", "蔬菜", ("onions", "spring", "raw"), (), "1 根", 15),
    ("甜菜根", "蔬菜", ("beets", "raw"), (), "1 个", 100),

    # ---------------- 水果 ----------------
    ("苹果", "水果", ("apples", "raw", "with skin"), ("dried", "juice"), "1 个中等", 180),
    ("香蕉", "水果", ("bananas", "raw"), ("dehydrated",), "1 根", 120),
    ("橙子", "水果", ("oranges", "raw", "all commercial"), (), "1 个", 140),
    ("葡萄", "水果", ("grapes", "red or green", "raw"), (), "1 份", 100),
    ("西瓜", "水果", ("watermelon", "raw"), (), "1 块", 200),
    ("草莓", "水果", ("strawberries", "raw"), ("frozen",), "6 颗", 90),
    ("蓝莓", "水果", ("blueberries", "raw"), ("frozen",), "1 份", 100),
    ("梨", "水果", ("pears", "raw"), ("dried",), "1 个", 180),
    ("猕猴桃", "水果", ("kiwifruit", "green", "raw"), (), "1 个", 75),
    ("芒果", "水果", ("mangos", "raw"), ("dried", "nectar"), "1 个", 200),
    ("桃子", "水果", ("peaches", "yellow", "raw"), ("dried",), "1 个", 150),
    ("菠萝", "水果", ("pineapple", "raw"), ("juice",), "1 份", 150),
    ("樱桃", "水果", ("cherries", "sweet", "raw"), (), "1 份", 100),
    ("柠檬", "水果", ("lemons", "raw", "without peel"), ("juice",), "1 个", 60),
    ("牛油果", "水果", ("avocados", "raw"), ("florida",), "1 个", 150),
    ("木瓜", "水果", ("papayas", "raw"), (), "1 份", 150),
    ("哈密瓜", "水果", ("melons", "cantaloupe", "raw"), (), "1 份", 150),
    ("李子", "水果", ("plums", "raw"), ("dried",), "1 个", 70),
    ("石榴", "水果", ("pomegranates", "raw"), (), "1 份", 100),
    ("无花果", "水果", ("figs", "raw"), ("dried",), "1 个", 50),
    ("柿子", "水果", ("persimmons", "japanese", "raw"), ("dried",), "1 个", 170),
    ("荔枝", "水果", ("litchis", "raw"), ("dried",), "1 份", 100),
    ("杨桃", "水果", ("carambola", "raw"), (), "1 个", 130),
    ("西柚", "水果", ("grapefruit", "raw", "pink and red"), ("juice",), "半个", 120),

    # ---------------- 奶制品 ----------------
    ("全脂牛奶", "奶制品", ("milk", "whole", "3.25% milkfat"), ("chocolate", "dry", "canned"), "1 杯", 240),
    ("低脂牛奶", "奶制品", ("milk", "lowfat", "1% milkfat"), ("chocolate",), "1 杯", 240),
    ("脱脂牛奶", "奶制品", ("milk", "nonfat", "fluid"), ("dry", "chocolate"), "1 杯", 240),
    ("原味酸奶", "奶制品", ("yogurt", "plain", "whole milk"), ("frozen", "greek"), "1 杯", 200),
    ("希腊酸奶（低脂）", "奶制品", ("yogurt", "greek", "plain", "lowfat"), (), "1 杯", 200),
    ("切达奶酪", "奶制品", ("cheese", "cheddar"), ("low", "reduced"), "1 片", 25),
    ("马苏里拉奶酪", "奶制品", ("cheese", "mozzarella", "whole milk"), ("low",), "1 份", 30),
    ("淡奶油", "奶制品", ("cream", "fluid", "heavy whipping"), (), "1 份", 30),
    ("豆浆", "奶制品", ("soymilk", "original", "unfortified"), ("chocolate", "light"), "1 杯", 240),

    # ---------------- 豆制品 ----------------
    ("北豆腐", "豆制品", ("tofu", "firm", "prepared with calcium sulfate"), ("fried", "dried"), "1 块", 100),
    ("南豆腐", "豆制品", ("tofu", "regular", "prepared with calcium sulfate"), ("fried", "dried", "firm"), "1 块", 100),
    ("黄豆（干）", "豆制品", ("soybeans", "mature seeds", "raw"), ("sprouted",), "1 份", 50),
    ("红豆（干）", "豆制品", ("beans", "adzuki", "mature seeds", "raw"), (), "1 份", 50),
    ("绿豆（干）", "豆制品", ("mung beans", "mature seeds", "raw"), ("sprouted",), "1 份", 50),
    ("黑豆（干）", "豆制品", ("beans", "black", "mature seeds", "raw"), (), "1 份", 50),
    ("鹰嘴豆（干）", "豆制品", ("chickpeas", "mature seeds", "raw"), (), "1 份", 50),
    ("腐竹（干）", "豆制品", ("tofu", "dried-frozen"), (), "1 份", 30),

    # ---------------- 坚果 ----------------
    ("核桃", "坚果", ("nuts", "walnuts", "english"), (), "3 个", 15),
    ("杏仁", "坚果", ("nuts", "almonds"), ("honey", "dry roasted"), "10 颗", 15),
    ("花生（烤）", "坚果", ("peanuts", "all types", "dry-roasted"), ("spanish", "valencia"), "1 把", 25),
    ("腰果（生）", "坚果", ("nuts", "cashew nuts", "raw"), ("oil roasted",), "10 颗", 15),
    ("开心果（生）", "坚果", ("nuts", "pistachio nuts", "raw"), (), "1 把", 25),
    ("葵花籽仁", "坚果", ("seeds", "sunflower seed kernels", "dried"), ("toasted",), "1 把", 20),
    ("南瓜子仁", "坚果", ("seeds", "pumpkin and squash seed kernels", "dried"), (), "1 把", 20),
    ("白芝麻", "坚果", ("seeds", "sesame seeds", "whole", "dried"), (), "1 汤匙", 8),
    ("榛子", "坚果", ("nuts", "hazelnuts or filberts"), (), "10 颗", 15),
    ("松子", "坚果", ("nuts", "pine nuts", "dried"), (), "1 汤匙", 10),

    # ---------------- 油脂 ----------------
    ("橄榄油", "油脂", ("oil", "olive", "salad or cooking"), ("extra virgin",), "1 汤匙", 15),
    ("花生油", "油脂", ("oil", "peanut", "salad or cooking"), (), "1 汤匙", 15),
    ("菜籽油", "油脂", ("oil", "canola"), (), "1 汤匙", 15),
    ("黄油", "油脂", ("butter", "without salt"), ("whipped",), "1 汤匙", 15),
    ("蛋黄酱", "油脂", ("salad dressing", "mayonnaise", "regular"), ("light", "fat-free"), "1 汤匙", 15),

    # ---------------- 饮料 ----------------
    ("可乐", "饮料", ("carbonated", "cola"), ("low calorie", "diet", "without caffeine"), "1 罐", 330),
    ("橙汁", "饮料", ("orange juice", "raw"), (), "1 杯", 240),
    ("黑咖啡", "饮料", ("coffee", "brewed"), ("decaffeinated", "instant", "distilled water"), "1 杯", 240),
    ("啤酒", "饮料", ("alcoholic beverage", "beer", "regular"), ("light",), "1 罐", 330),
    ("白酒（40 度蒸馏酒）", "饮料", ("alcoholic beverage", "distilled", "all", "80 proof"), (), "1 两", 50),
    ("红酒", "饮料", ("alcoholic beverage", "wine", "table", "red"), (), "1 杯", 150),
    ("蜂蜜", "饮料", ("honey",), ("roll", "ham", "baked", "candy", "cereal", "dressing"), "1 汤匙", 21),
    ("白砂糖", "饮料", ("sugars", "granulated"), (), "1 茶匙", 4),

    # ---------------- 零食甜点 ----------------
    ("黑巧克力（70%）", "零食甜点", ("chocolate", "dark", "70-85% cacao"), (), "1 块", 10),
    ("牛奶巧克力", "零食甜点", ("candies", "milk chocolate"), ("with almonds",), "1 块", 10),
    ("薯片", "零食甜点", ("snacks", "potato chips", "plain", "salted"), (), "1 小包", 30),
    ("爆米花（无油）", "零食甜点", ("snacks", "popcorn", "air-popped"), (), "1 份", 20),
    ("香草冰淇淋", "零食甜点", ("ice creams", "vanilla"), ("fat free", "light"), "1 球", 65),
    ("曲奇饼干", "零食甜点", ("cookies", "butter", "commercially"), ("peanut", "sugar free"), "1 块", 15),
    ("甜甜圈", "零食甜点", ("doughnuts", "cake-type", "plain"), (), "1 个", 60),
    ("海绵蛋糕", "零食甜点", ("cake", "sponge"), ("snack", "recipe"), "1 块", 60),
    ("花生酱", "零食甜点", ("peanut butter", "smooth"), ("reduced",), "1 汤匙", 16),

    # ---------------- 快餐 ----------------
    ("汉堡", "快餐", ("fast foods", "hamburger", "single", "regular patty"), ("double", "large"), "1 个", 110),
    ("芝士披萨", "快餐", ("pizza", "cheese topping", "regular crust"), ("school", "stuffed"), "1 份", 100),
    ("薯条", "快餐", ("fast foods", "potato", "french fried"), ("with salt",), "1 中份", 115),
    ("炸鸡块", "快餐", ("chicken", "nuggets", "precooked", "white meat"), (), "4 块", 70),
    ("热狗", "快餐", ("frankfurter", "beef", "unheated"), (), "1 个", 100),
    ("三明治（冷切）", "快餐", ("fast foods", "submarine", "cold cut"), ("tuna",), "1 个", 200),
]


def pick_nutrient(food, number):
    for fn in food.get("foodNutrients", []):
        nut = fn.get("nutrient") or {}
        if str(nut.get("number")) == number:
            return fn.get("amount")
    return None


def energy_kcal(food):
    for fn in food.get("foodNutrients", []):
        nut = fn.get("nutrient") or {}
        if str(nut.get("number")) == N_ENERGY:
            unit = (nut.get("unitName") or "").lower()
            amount = fn.get("amount") or 0
            if unit in ("kcal", "kcals"):
                return float(amount)
            if unit in ("kj", "kjs"):
                return float(amount) / 4.184
    return None


def main():
    src, dst = sys.argv[1], sys.argv[2]
    with open(src, encoding="utf-8") as f:
        data = json.load(f)

    foods = data.get("SRLegacyFoods") or data.get("FoundationFoods") or []
    print(f"载入 {len(foods)} 条 USDA 记录")

    index = [(norm(f.get("description", "")), f) for f in foods]
    out, missing = [], []

    for name, category, inc, exc, portion_label, portion_grams in SPEC:
        candidates = []
        for desc, f in index:
            if any(has_term(desc, g) for g in GLOBAL_EXCLUDE):
                continue
            if any(has_term(desc, e) for e in exc):
                continue
            if not all(has_term(desc, i) for i in inc):
                continue
            kcal = energy_kcal(f)
            if not kcal or kcal <= 0 or kcal > 900:
                continue
            candidates.append((len(desc), desc, f, kcal))

        if not candidates:
            missing.append(name)
            continue

        candidates.sort(key=lambda x: x[0])
        _, desc, f, kcal = candidates[0]

        def val(num):
            v = pick_nutrient(f, num)
            return round(float(v), 1) if v is not None else 0.0

        out.append({
            "name": name,
            "nameEn": re.sub(r"\s*\([^)]*(?:includes|usda)[^)]*\)", "", desc,
                             flags=re.I).strip(" ,").replace(", ", " ").capitalize(),
            "category": category,
            "kcal": round(kcal, 1),
            "protein": val(N_PROTEIN),
            "fat": val(N_FAT),
            "carbs": val(N_CARB),
            "fiber": val(N_FIBER),
            "sodium": val(N_SODIUM),
            "portionLabel": portion_label,
            "portionGrams": float(portion_grams),
            "source": "USDA FoodData Central",
        })

    with open(dst, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)

    print(f"生成 {len(out)} 条 -> {dst}")
    if missing:
        print(f"未匹配 {len(missing)} 条：{', '.join(missing)}")
    print("\n=== 全部条目（中文名 | USDA 描述 | kcal | 蛋白 | 脂肪 | 碳水）===")
    for it in out:
        print(f"{it['name']:<20} | {it['nameEn'][:44]:<44} | {it['kcal']:>6.1f} | "
              f"{it['protein']:>5.1f} | {it['fat']:>5.1f} | {it['carbs']:>5.1f}")


if __name__ == "__main__":
    main()
