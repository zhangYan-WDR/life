const fs = require("fs");
const path = require("path");

const OUTPUT_V5 = path.resolve(__dirname, "../src/main/resources/db/migration/V5__bulk_system_ingredients.sql");
const OUTPUT_V6 = path.resolve(__dirname, "../src/main/resources/db/migration/V6__fresh_ingredients_extension.sql");

let nextId = 100000;
let nextSort = 100000;

function normalizeName(parts) {
  return parts.filter(Boolean).join(" ").replace(/\s+/g, " ").trim();
}

function escapeSql(value) {
  return String(value).replace(/'/g, "''");
}

function makeRows(category, secondaryCategory, unit, brands, products, variants, sizes) {
  const rows = [];
  for (const brand of brands) {
    for (const product of products) {
      for (const variant of variants) {
        for (const size of sizes) {
          const name = normalizeName([brand, product, variant, size]);
          if (!name || name.length > 64) {
            continue;
          }
          rows.push({
            id: nextId++,
            name,
            category,
            secondaryCategory,
            defaultUnit: unit,
            enabled: 1,
            sortOrder: nextSort++,
          });
        }
      }
    }
  }
  return rows;
}

function makeSimpleRows(category, secondaryCategory, defaultUnit, items) {
  return (items || []).map((item) => {
    const row = typeof item === "string"
      ? { name: item, unit: defaultUnit }
      : { name: item.name, unit: item.unit || defaultUnit };
    return {
      id: nextId++,
      name: row.name,
      category,
      secondaryCategory,
      defaultUnit: row.unit,
      enabled: 1,
      sortOrder: nextSort++,
    };
  }).filter((row) => row.name && row.name.length <= 64);
}

const catalogDefinitions = [
  {
    category: "饮品",
    secondaryCategory: "茶饮",
    unit: "瓶",
    brands: ["统一", "康师傅", "农夫山泉", "元气森林", "三得利", "维他", "今麦郎", "娃哈哈", "东鹏", "可口可乐", "伊藤园", "奈雪", "喜茶", "茶π"],
    products: ["冰红茶", "冰绿茶", "茉莉清茶", "乌龙茶", "绿茶", "红茶", "柠檬茶", "无糖茶"],
    variants: ["经典", "低糖", "无糖", "轻乳", "青柠", "蜜桃", "柚子", "桂花"],
    sizes: ["330ml", "500ml", "550ml", "600ml", "1L", "1.25L"],
  },
  {
    category: "饮品",
    secondaryCategory: "碳酸饮料",
    unit: "瓶",
    brands: ["可口可乐", "百事", "雪碧", "芬达", "美年达", "元气森林", "北冰洋", "健力宝", "屈臣氏", "怡泉", "七喜", "天府可乐"],
    products: ["可乐", "苏打水", "气泡水", "橙味汽水", "柠檬汽水", "葡萄汽水"],
    variants: ["经典", "无糖", "零卡", "青柠", "白桃", "西柚", "葡萄", "橙味"],
    sizes: ["330ml", "500ml", "550ml", "600ml", "1L", "1.25L"],
  },
  {
    category: "饮品",
    secondaryCategory: "果汁",
    unit: "盒",
    brands: ["汇源", "农夫果园", "味全", "美汁源", "统一", "康师傅", "椰树", "味动力", "褚橙", "都乐"],
    products: ["橙汁", "苹果汁", "葡萄汁", "芒果汁", "椰汁", "复合果汁", "NFC果汁"],
    variants: ["经典", "低糖", "纯果汁", "高纤", "鲜榨风味", "混合果味"],
    sizes: ["250ml", "300ml", "450ml", "500ml", "1L"],
  },
  {
    category: "饮品",
    secondaryCategory: "瓶装水",
    unit: "瓶",
    brands: ["农夫山泉", "怡宝", "百岁山", "景田", "娃哈哈", "昆仑山", "依云", "5100", "冰露", "雀巢"],
    products: ["矿泉水", "纯净水", "天然水", "苏打水"],
    variants: ["经典", "弱碱性", "含气", "无气", "高偏硅酸", "低钠"],
    sizes: ["350ml", "380ml", "500ml", "550ml", "1L", "1.5L"],
  },
  {
    category: "饮品",
    secondaryCategory: "功能饮料",
    unit: "罐",
    brands: ["红牛", "东鹏特饮", "乐虎", "魔爪", "战马", "脉动", "佳得乐", "宝矿力"],
    products: ["能量饮料", "维生素饮料", "电解质饮料", "运动饮料"],
    variants: ["经典", "低糖", "无糖", "青柠", "柠檬", "西柚"],
    sizes: ["250ml", "330ml", "500ml", "600ml"],
  },
  {
    category: "酒类",
    secondaryCategory: "啤酒",
    unit: "罐",
    brands: ["青岛", "乌苏", "雪花", "燕京", "百威", "哈尔滨", "珠江", "喜力", "科罗娜", "嘉士伯", "蓝妹", "福佳"],
    products: ["啤酒", "原浆啤酒", "精酿啤酒", "小麦啤酒"],
    variants: ["经典", "纯生", "清爽", "醇厚", "白啤", "黑啤", "原味", "全麦"],
    sizes: ["330ml", "355ml", "500ml", "550ml", "1L"],
  },
  {
    category: "酒类",
    secondaryCategory: "白酒",
    unit: "瓶",
    brands: ["茅台", "五粮液", "泸州老窖", "洋河", "汾酒", "剑南春", "古井贡", "郎酒", "习酒", "牛栏山"],
    products: ["白酒", "浓香型白酒", "酱香型白酒", "清香型白酒"],
    variants: ["经典", "珍藏", "特曲", "陈酿", "柔和", "清雅"],
    sizes: ["100ml", "250ml", "375ml", "500ml", "750ml"],
  },
  {
    category: "乳品",
    secondaryCategory: "常温奶",
    unit: "盒",
    brands: ["伊利", "蒙牛", "光明", "三元", "认养一头牛", "君乐宝", "特仑苏", "金典", "欧德堡", "德亚"],
    products: ["纯牛奶", "高钙奶", "有机纯牛奶", "A2牛奶", "早餐奶"],
    variants: ["经典", "全脂", "低脂", "脱脂", "高蛋白", "无乳糖"],
    sizes: ["200ml", "250ml", "330ml", "500ml", "1L"],
  },
  {
    category: "乳品",
    secondaryCategory: "酸奶",
    unit: "杯",
    brands: ["安慕希", "纯甄", "莫斯利安", "简爱", "君乐宝", "乐纯", "卡士", "光明", "和润", "优诺"],
    products: ["酸奶", "希腊酸奶", "风味酸奶", "常温酸奶"],
    variants: ["原味", "草莓", "黄桃", "蓝莓", "低糖", "无糖", "高蛋白"],
    sizes: ["100g", "135g", "200g", "230g", "250g"],
  },
  {
    category: "零食",
    secondaryCategory: "薯片",
    unit: "袋",
    brands: ["乐事", "可比克", "上好佳", "旺旺", "好丽友", "盼盼", "艾比利", "三只松鼠"],
    products: ["薯片", "厚切薯片", "波浪薯片", "烘焙薯片"],
    variants: ["原味", "番茄味", "黄瓜味", "烧烤味", "香辣味", "海盐味", "麻辣小龙虾味", "黑胡椒味"],
    sizes: ["40g", "70g", "90g", "104g", "135g"],
  },
  {
    category: "零食",
    secondaryCategory: "饼干",
    unit: "盒",
    brands: ["奥利奥", "达能", "康师傅", "良品铺子", "三只松鼠", "嘉士利", "太平", "趣多多", "皇冠"],
    products: ["夹心饼干", "苏打饼干", "曲奇饼干", "威化饼干", "消化饼干"],
    variants: ["原味", "巧克力味", "草莓味", "芝士味", "海盐味", "蔓越莓味", "全麦"],
    sizes: ["80g", "100g", "120g", "200g", "300g"],
  },
  {
    category: "零食",
    secondaryCategory: "坚果",
    unit: "袋",
    brands: ["三只松鼠", "百草味", "良品铺子", "洽洽", "沃隆", "来伊份", "华味亨", "甘源"],
    products: ["混合坚果", "每日坚果", "腰果", "开心果", "巴旦木", "核桃仁", "夏威夷果"],
    variants: ["原味", "盐焗", "炭烧", "奶油味", "蜂蜜味", "轻盐"],
    sizes: ["25g", "40g", "75g", "100g", "200g", "500g"],
  },
  {
    category: "零食",
    secondaryCategory: "肉干",
    unit: "袋",
    brands: ["良品铺子", "三只松鼠", "百草味", "卫龙", "来伊份", "周黑鸭", "绝味"],
    products: ["牛肉干", "猪肉脯", "鸭脖", "鸭锁骨", "鸡胸肉干"],
    variants: ["原味", "香辣味", "孜然味", "蜜汁味", "黑椒味"],
    sizes: ["40g", "60g", "80g", "100g", "150g"],
  },
  {
    category: "调料",
    secondaryCategory: "酱油醋",
    unit: "瓶",
    brands: ["海天", "李锦记", "厨邦", "千禾", "欣和", "加加", "东古", "恒顺"],
    products: ["生抽", "老抽", "味极鲜", "米醋", "陈醋", "香醋"],
    variants: ["经典", "薄盐", "零添加", "特级", "头道"],
    sizes: ["150ml", "280ml", "500ml", "750ml", "1L"],
  },
  {
    category: "调料",
    secondaryCategory: "复合调味料",
    unit: "袋",
    brands: ["海底捞", "桥头", "王守义", "李锦记", "家乐", "太太乐", "好人家", "名扬"],
    products: ["火锅底料", "烧烤料", "炖肉料", "酸菜鱼调料", "麻婆豆腐调料", "卤料包"],
    variants: ["微辣", "中辣", "特辣", "藤椒味", "番茄味", "菌汤味"],
    sizes: ["50g", "80g", "120g", "150g", "220g"],
  },
  {
    category: "方便食品",
    secondaryCategory: "方便面",
    unit: "桶",
    brands: ["康师傅", "统一", "白象", "今麦郎", "日清", "农心", "三养"],
    products: ["方便面", "拌面", "汤面", "拉面", "粉丝"],
    variants: ["红烧牛肉味", "老坛酸菜味", "香辣牛肉味", "番茄鸡蛋味", "藤椒牛肉味", "酸汤肥牛味", "海鲜味"],
    sizes: ["80g", "100g", "110g", "120g", "150g"],
  },
  {
    category: "方便食品",
    secondaryCategory: "速食米饭",
    unit: "盒",
    brands: ["自热锅", "海底捞", "莫小仙", "三全", "得益绿色", "统一开小灶", "饭乎"],
    products: ["自热米饭", "自热火锅", "煲仔饭", "盖浇饭", "拌饭"],
    variants: ["咖喱鸡肉", "红烧牛肉", "梅菜扣肉", "番茄肥牛", "香菇卤肉", "麻辣鸭血"],
    sizes: ["180g", "220g", "250g", "300g", "400g"],
  },
  {
    category: "冷冻食品",
    secondaryCategory: "速冻面点",
    unit: "袋",
    brands: ["三全", "思念", "湾仔码头", "安井", "广州酒家", "正大", "科迪"],
    products: ["水饺", "馄饨", "汤圆", "包子", "手抓饼", "烧麦", "小笼包"],
    variants: ["猪肉玉米", "三鲜", "韭菜鸡蛋", "芝麻", "豆沙", "奶黄", "牛肉味"],
    sizes: ["300g", "500g", "600g", "800g", "1kg"],
  },
  {
    category: "冷冻食品",
    secondaryCategory: "冷冻肉制品",
    unit: "袋",
    brands: ["正大", "双汇", "雨润", "安井", "海霸王", "大成", "圣农"],
    products: ["鸡翅中", "鸡排", "牛肉丸", "虾滑", "培根", "火腿肠", "鸡米花"],
    variants: ["原味", "奥尔良味", "黑椒味", "香辣味", "蒜香味"],
    sizes: ["200g", "300g", "500g", "800g", "1kg"],
  },
  {
    category: "烘焙",
    secondaryCategory: "面包蛋糕",
    unit: "袋",
    brands: ["达利园", "桃李", "盼盼", "曼可顿", "好丽友", "豪士", "三只松鼠"],
    products: ["吐司面包", "餐包", "蛋糕", "蒸蛋糕", "肉松面包", "夹心面包"],
    variants: ["原味", "奶香", "巧克力味", "肉松味", "红豆味", "芝士味"],
    sizes: ["80g", "100g", "200g", "400g", "600g"],
  },
];

function buildSql(rows) {
  const chunks = [];
  const batchSize = 400;
  for (let i = 0; i < rows.length; i += batchSize) {
    chunks.push(rows.slice(i, i + batchSize));
  }

  return `${chunks.map((chunk) => {
    const values = chunk
      .map((row) => `(${row.id}, '${escapeSql(row.name)}', '${escapeSql(row.category)}', '${escapeSql(row.secondaryCategory)}', '${escapeSql(row.defaultUnit)}', ${row.enabled}, ${row.sortOrder})`)
      .join(",\n");
    return `INSERT INTO ingredient_catalog_system (id, name, category, secondary_category, default_unit, enabled, sort_order)
VALUES
${values} AS incoming
ON DUPLICATE KEY UPDATE
    name = incoming.name,
    category = incoming.category,
    secondary_category = incoming.secondary_category,
    default_unit = incoming.default_unit,
    enabled = incoming.enabled,
    sort_order = incoming.sort_order;`;
  }).join("\n\n")}\n`;
}

const packagedRows = catalogDefinitions.flatMap((definition) =>
  makeRows(
    definition.category,
    definition.secondaryCategory,
    definition.unit,
    definition.brands,
    definition.products,
    definition.variants,
    definition.sizes,
  ),
);

const freshRows = [
  ...makeSimpleRows("蔬菜", "叶菜类", "把", [
    "上海青",
    "菠菜",
    "生菜",
    "油麦菜",
    "小白菜",
    "奶白菜",
    "空心菜",
    "茼蒿",
    "芹菜",
    "韭菜",
    "娃娃菜",
    "大白菜",
    "西生菜",
    "羽衣甘蓝",
    "苋菜",
    "菜心",
    "芥蓝",
    "苦菊",
    "香菜",
  ]),
  ...makeSimpleRows("蔬菜", "瓜果类", "个", [
    { name: "黄瓜", unit: "根" },
    { name: "丝瓜", unit: "根" },
    { name: "西葫芦", unit: "根" },
    { name: "苦瓜", unit: "根" },
    { name: "冬瓜", unit: "块" },
    { name: "南瓜", unit: "块" },
    { name: "番茄", unit: "个" },
    { name: "圣女果", unit: "盒" },
    { name: "茄子", unit: "根" },
    { name: "青椒", unit: "个" },
    { name: "红椒", unit: "个" },
    { name: "彩椒", unit: "个" },
    { name: "尖椒", unit: "个" },
    { name: "柿子椒", unit: "个" },
    { name: "玉米", unit: "根" },
    { name: "秋葵", unit: "盒" },
  ]),
  ...makeSimpleRows("蔬菜", "根茎类", "个", [
    "土豆",
    "红薯",
    "紫薯",
    "胡萝卜",
    "白萝卜",
    "山药",
    "芋头",
    "莲藕",
    "荸荠",
    "芦笋",
    "牛蒡",
  ]),
  ...makeSimpleRows("蔬菜", "葱姜蒜", "个", [
    { name: "大葱", unit: "根" },
    { name: "小葱", unit: "把" },
    { name: "洋葱", unit: "个" },
    { name: "紫洋葱", unit: "个" },
    { name: "生姜", unit: "块" },
    { name: "大蒜", unit: "头" },
    { name: "蒜苗", unit: "把" },
    { name: "蒜苔", unit: "把" },
    { name: "姜蒜末", unit: "份" },
  ]),
  ...makeSimpleRows("蔬菜", "菌菇类", "盒", [
    "香菇",
    "平菇",
    "金针菇",
    "杏鲍菇",
    "白玉菇",
    "蟹味菇",
    "口蘑",
    "茶树菇",
    "海鲜菇",
    "木耳",
    "银耳",
  ]),
  ...makeSimpleRows("豆制品", "豆腐类", "盒", [
    "北豆腐",
    "南豆腐",
    "嫩豆腐",
    "老豆腐",
    "内酯豆腐",
    "千页豆腐",
    "豆腐皮",
    "油豆腐",
    "腐竹",
    "豆泡",
    "豆干",
    "香干",
  ]),
  ...makeSimpleRows("肉禽", "猪肉", "斤", [
    "五花肉",
    "猪里脊",
    "猪前腿肉",
    "猪后腿肉",
    "猪梅花肉",
    "猪排骨",
    "猪筒骨",
    "猪蹄",
    "猪肝",
    "猪肚",
    "猪耳朵",
    "猪肉馅",
  ]),
  ...makeSimpleRows("肉禽", "牛羊肉", "斤", [
    "牛腩",
    "牛里脊",
    "牛腱子",
    "肥牛卷",
    "牛排",
    "牛肉馅",
    "羊腿肉",
    "羊排",
    "羊蝎子",
    "羊肉卷",
    "羊后腿肉",
    "羊杂",
  ]),
  ...makeSimpleRows("肉禽", "鸡鸭禽蛋", "斤", [
    "鸡胸肉",
    "鸡腿",
    "鸡翅中",
    "鸡全翅",
    "鸡爪",
    "三黄鸡",
    "老母鸡",
    "鸭腿",
    "鸭翅",
    "鸭胗",
    { name: "鸡蛋", unit: "个" },
    { name: "鸭蛋", unit: "个" },
    { name: "鹌鹑蛋", unit: "盒" },
  ]),
  ...makeSimpleRows("水产", "鱼虾蟹贝", "斤", [
    "鲈鱼",
    "草鱼",
    "黑鱼",
    "鳕鱼",
    "带鱼",
    "黄花鱼",
    "鲫鱼",
    "大虾",
    "基围虾",
    "虾仁",
    "扇贝",
    "花蛤",
    "蛏子",
    "生蚝",
    "鱿鱼",
    "章鱼",
    "蟹柳",
  ]),
  ...makeSimpleRows("主食", "米面粮油", "袋", [
    "大米",
    "小米",
    "糯米",
    "面粉",
    "高筋面粉",
    "低筋面粉",
    "挂面",
    "手擀面",
    "意大利面",
    "饺子皮",
    "馄饨皮",
    "面包糠",
  ]),
];

fs.writeFileSync(OUTPUT_V5, buildSql(packagedRows), "utf8");
fs.writeFileSync(OUTPUT_V6, buildSql(freshRows), "utf8");
console.log(`generated ${packagedRows.length} packaged rows to ${OUTPUT_V5}`);
console.log(`generated ${freshRows.length} fresh rows to ${OUTPUT_V6}`);
