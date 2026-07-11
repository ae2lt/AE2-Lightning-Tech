---
navigation:
  title: 主核心、子核心与热度
  icon: ae2lt:matter_warping_matrix_stable_main_core
  parent: matrix/matrix-index.md
  position: 20
item_ids:
  - ae2lt:matter_warping_matrix_stable_main_core
  - ae2lt:matter_warping_matrix_quantum_main_core
  - ae2lt:matter_warping_matrix_overload_main_core
  - ae2lt:matter_warping_matrix_creative_main_core
  - ae2lt:matter_warping_matrix_blank_sub_core
  - ae2lt:matter_warping_matrix_thread_sub_core_t1
  - ae2lt:matter_warping_matrix_thread_sub_core_t2
  - ae2lt:matter_warping_matrix_multiplier_sub_core_t1
  - ae2lt:matter_warping_matrix_multiplier_sub_core_t2
  - ae2lt:matter_warping_matrix_cooling_sub_core_t1
  - ae2lt:matter_warping_matrix_cooling_sub_core_t2
---

# 主核心、子核心与热度

核心舱共有 81 个位置。几何中心必须放置一个主核心，其余 80 个位置必须使用子核心填满。下图移除了外部结构，以展示一个示例配置；该配置仅用于区分核心位置，不代表唯一或通用的最优方案。

<GameScene zoom="3" background="transparent" interactive={true}>
  <ImportStructure src="../assets/assemblies/matter_warping_matrix_core.snbt" />
  <BoxAnnotation min="3 5 3" max="4 6 4" color="#f2d37a" alwaysOnTop={true}>唯一的主核心位置</BoxAnnotation>
  <IsometricCamera yaw="215" pitch="25" />
</GameScene>

## 主核心模式

| 主核心 | 运行特性 | 热度策略 |
|--------|----------|----------|
| 稳态主核心 | 以基础批量运行；热度升高时效率逐渐下降，但最低保持 45% | 保持低温即可稳定获得较高吞吐 |
| 量子主核心 | 在基础批量上获得最高 64 倍量子倍率；高温时倍率最低为 28.8 倍 | 维持低温可获得更高倍率 |
| 过载主核心 | 以热度驱动过载倍率；约 50% 热度时倍率达到峰值，过冷与过热都会显著衰减 | 将热度维持在 42%–58% 甜点区 |
| 创造主核心 | 无限发配，忽略子核心属性、热度和批量增幅数量限制 | 不需要热度管理 |

主核心决定批量倍率的计算方式，但实际吞吐仍由子核心配置共同决定。

## 子核心

| 子核心 | T1 | T2 | 作用 |
|--------|---:|---:|------|
| 并行子核心 | 并行功率 2 | 并行功率 4 | 提升每 tick 的基础发配能力；增益存在软上限，并非线性增长 |
| 批量增幅子核心 | 批量功率 1 | 批量功率 2 | 每点功率使基础批量增加 0.4；基础值为 4 |
| 热控子核心 | 热控功率 1 | 热控功率 2 | 提高热容量与冷却速度；效果受其到主核心距离影响 |
| 空白子核心 | — | — | 仅用于填满核心槽，不提供性能属性 |

非创造配置最多放置 **10 个批量增幅子核心**。放置第 11 个会使核心配置无效，矩阵无法正常提供合成能力。

## 热控距离

热控子核心按到中央主核心的曼哈顿距离计算有效功率：

| 距离 | 有效热控功率 |
|-----:|---------------:|
| 1 格 | 100% |
| 2 格 | 75% |
| 3 格 | 50% |
| 4 格 | 25% |
| 5 格及以上 | 0% |
