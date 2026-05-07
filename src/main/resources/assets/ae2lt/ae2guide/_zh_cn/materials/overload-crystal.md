---
navigation:
  title: 过载水晶
  icon: ae2lt:overload_crystal
  parent: materials/materials-index.md
item_ids:
  - ae2lt:overload_crystal
  - ae2lt:overload_crystal_dust
  - ae2lt:overload_crystal_block
  - ae2lt:flawless_budding_overload_crystal
  - ae2lt:flawed_budding_overload_crystal
  - ae2lt:cracked_budding_overload_crystal
  - ae2lt:damaged_budding_overload_crystal
  - ae2lt:small_overload_crystal_bud
  - ae2lt:medium_overload_crystal_bud
  - ae2lt:large_overload_crystal_bud
  - ae2lt:overload_crystal_cluster
---

# 过载水晶

<ItemImage id="ae2lt:overload_crystal" scale="2" float="left" />

**过载水晶**是 AE2 闪电科技中最基础也最重要的材料，几乎所有中后期配方都需要它或其衍生产物。

## 获取方式

### 培育过载水晶母岩

过载水晶的主要来源是**过载水晶母岩**表面自然生长的水晶簇。

过载水晶母岩通过搭建多方块结构并使用**雷击**转化对应等级的 AE2 赛特斯石英母岩获得。详细配方参见下文"获取过载水晶母岩"一节。

### 母岩等级

过载水晶母岩共有四个等级：

| 等级 | 名称 | 衰减 |
|------|------|------|
| 无瑕 | 无瑕的过载水晶母岩 | 永不衰减 |
| 有瑕 | 有瑕的过载水晶母岩 | 较低概率衰减 |
| 开裂 | 开裂的过载水晶母岩 | 中等概率衰减 |
| 损坏 | 损坏的过载水晶母岩 | 较高概率衰减 |

每当过载水晶芽在母岩上生长一个阶段，母岩都有一定概率衰减一个等级。当损坏的母岩继续衰减时，它将变为普通的过载水晶块。

> **精准采集**可以防止不完美的母岩在被破坏时衰减。**无瑕的过载水晶母岩**永不衰减。

### 水晶芽的生长阶段

过载水晶芽的生长分为四个阶段：

1. **小型过载水晶芽** → 破坏掉落过载水晶粉
2. **中型过载水晶芽** → 破坏掉落过载水晶粉
3. **大型过载水晶芽** → 破坏掉落过载水晶粉
4. **过载水晶簇**（完全长成）→ 破坏掉落**过载水晶**（时运生效）

### 加速生长

<ItemLink id="ae2:growth_accelerator" />（水晶生长加速器）对过载水晶芽同样有效。在母岩周围放置加速器可显著提升水晶的生长速率。

## 获取过载水晶母岩

过载水晶母岩通过搭建 3×3 多方块结构并在中心正上方的避雷针处雷击触发转化。有**精制**与**简化**两种结构。

### 精制结构（自然雷击，同级转化）

<GameScene zoom="4" background="transparent">
  <ImportStructure src="../assets/assemblies/flawless_budding_overload.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

结构要求：

* 中心放置对应等级的 AE2 赛特斯石英母岩
* 东 / 西 / 南 / 北四个正方向同一高度各放置一个 <ItemLink id="ae2:fluix_block" />
* 四个对角各放置一个 <ItemLink id="ae2lt:overload_crystal_block" />
* 中心正上方放置一个避雷针

搭建完成后，等待**自然雷击**命中避雷针即可**同级**转化：

| 输入（中心） | 输出 |
|-----------|------|
| <ItemLink id="ae2:damaged_budding_quartz" /> | <ItemLink id="ae2lt:damaged_budding_overload_crystal" /> |
| <ItemLink id="ae2:chipped_budding_quartz" /> | <ItemLink id="ae2lt:cracked_budding_overload_crystal" /> |
| <ItemLink id="ae2:flawed_budding_quartz" /> | <ItemLink id="ae2lt:flawed_budding_overload_crystal" /> |
| <ItemLink id="ae2:flawless_budding_quartz" /> | <ItemLink id="ae2lt:flawless_budding_overload_crystal" /> |

> 精制结构只承认**自然雷击**。由玩家携带过载水晶引来的人工闪电不会触发该结构。

### 简化结构（任意闪电，产物降一级）

如果手头缺少过载水晶块，可以用简化结构生产除无瑕外的 3 个等级母岩：

* 中心放置对应等级的 AE2 赛特斯石英母岩
* 四个对角各放置一个 <ItemLink id="ae2:quartz_block" />（赛特斯石英块）
* 东 / 西 / 南 / 北四个正方向同一高度各放置一个 <ItemLink id="ae2:fluix_block" />
* 中心正上方放置一个避雷针

**任意闪电**命中避雷针即可触发，产物比输入等级低一级：

| 输入（中心） | 输出 |
|-----------|------|
| <ItemLink id="ae2:chipped_budding_quartz" /> | <ItemLink id="ae2lt:damaged_budding_overload_crystal" /> |
| <ItemLink id="ae2:flawed_budding_quartz" /> | <ItemLink id="ae2lt:cracked_budding_overload_crystal" /> |
| <ItemLink id="ae2:flawless_budding_quartz" /> | <ItemLink id="ae2lt:flawed_budding_overload_crystal" /> |

> 无瑕母岩无法通过简化结构获取——想要无瑕只能走精制结构 + 自然雷击。

雷击命中后，周围八个外围材料方块会被消耗，中心方块转化为对应等级的过载水晶母岩。

## 衍生产物

| 物品 | 用途 |
|------|------|
| 过载水晶粉 | 特斯拉线圈高压模式的消耗品，也用于部分配方 |
| 过载水晶块 | 建筑 / 装饰方块，也用于搭建无瑕母岩结构 |
