---
navigation:
  title: 主核心、外围单元与热度
  icon: ae2lt:matter_warping_matrix_stable_main_core
  parent: matrix/matrix-index.md
  position: 20
item_ids:
  - ae2lt:matter_warping_matrix_stable_main_core
  - ae2lt:matter_warping_matrix_quantum_main_core
  - ae2lt:matter_warping_matrix_overload_main_core
  - ae2lt:matter_warping_matrix_creative_main_core
  - ae2lt:matter_warping_matrix_blank_unit
  - ae2lt:matter_warping_matrix_thread_unit_t1
  - ae2lt:matter_warping_matrix_thread_unit_t2
  - ae2lt:tianshu_amplifier_unit
  - ae2lt:matter_warping_matrix_thermal_control_unit_t1
  - ae2lt:matter_warping_matrix_thermal_control_unit_t2
---

# 主核心、外围单元与热度

核心舱共有 81 个位置。几何中心必须放置一个主核心，其余 80 个位置必须使用外围单元填满。下图移除了外部结构，以展示一个示例配置；该配置仅用于区分核心位置，不代表唯一或通用的最优方案。

<GameScene zoom="3" background="transparent" interactive={true}>
  <ImportStructure src="../assets/assemblies/matter_warping_matrix_core.snbt" />
  <BoxAnnotation min="3 5 3" max="4 6 4" color="#f2d37a" alwaysOnTop={true}>唯一的主核心位置</BoxAnnotation>
  <IsometricCamera yaw="215" pitch="25" />
</GameScene>

## 主核心模式

| 主核心 | 每 tick 合成上限 | 热度策略 |
|--------|----------------:|----------|
| 稳态主核心 | 1,024 份 | 保持低温；热度升高时效率下降，最低保持 45% |
| 量子主核心 | 10,240 份 | 维持低温可获得更高效率 |
| 过载主核心 | 4,194,304 份 | 约 50% 热度达到峰值，维持在 42%–58% 甜点区 |
| 创造主核心 | 无上限 | 忽略热度；其余 80 格只能使用空白单元 |

矩阵每 tick 实际执行的合成份数按三步得出：

1. **基础能力** = `256 × 线程点 × 增幅系数`。线程点来自线程单元（T1 每个 1 点、T2 每个 2 点）。增幅系数由主核心决定：稳态恒为 1，量子为 `R²`，过载为 `R³`，其中 `R = 1 + 天枢增幅单元数量`。
2. 基础能力超过主核心的每 tick 上限时，按上限截断。
3. 再乘以当前温度效率，得到实际吞吐。

创造主核心的合成份数没有上限，但每 tick 实际发出的合成调用仍不超过 16,384 次。矩阵的吞吐只由自身核心配置与热度决定，与天枢超算阵列的派发、复制预算互不影响。

## 外围单元

| 外围单元 | 提供 | 作用 |
|----------|------|------|
| 线程单元 T1 | 1 线程点 | 提高每 tick 基础合成能力 |
| 线程单元 T2 | 2 线程点 | 单格提供两倍线程点，以更高造价节省核心槽 |
| 天枢增幅单元 | `R` +1 | 提高量子和过载主核心的增幅系数；与天枢超算阵列共用同一种方块 |
| 热控单元 T1 | 1 冷却点 | 提高热容量与冷却速度；实际效果随其到主核心的距离衰减 |
| 热控单元 T2 | 2 冷却点 | 单格提供两倍冷却点，同样受距离衰减 |
| 空白单元 | — | 仅用于填满核心槽，不提供性能属性 |

量子和过载配置最多放置 **15 个天枢增幅单元**，第 16 个会使结构无法成形；稳态与创造主核心不接受增幅单元。

## 热控距离

热控单元按到中央主核心的曼哈顿距离计算有效功率：

| 距离 | 有效热控功率 |
|-----:|---------------:|
| 1 格 | 100% |
| 2 格 | 75% |
| 3 格 | 50% |
| 4 格 | 25% |
| 5 格及以上 | 0% |
