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

| 主核心 | 统一增幅 | 热度策略 |
|--------|----------|----------|
| 稳态主核心 | `Gd=1`、`Gt=2`，最高 1,024 operations/t | 保持低温；热度升高时效率下降，最低保持 45% |
| 量子主核心 | `Gd=2R`、`Gt=R`，最高 10,240 operations/t | 维持低温可获得更高效率 |
| 过载主核心 | `Gd=2R`、`Gt=R²`，最高 4,194,304 operations/t | 约 50% 热度达到峰值，维持在 42%–58% 甜点区 |
| 创造主核心 | 逻辑 operations 无限，但 provider 调用仍受 16,384/t 熔断 | 忽略热度；其余 80 格只能使用空白子核心 |

其中 `R=1+N`，`N` 为增幅子核心数量。矩阵先按 `128×P×Gd×Gt` 计算基础 operations，再应用主核心上限与温度效率；它不维护 CPU 的成功派发预算 `D`，也没有单独的 batch 宽度 `q`。

## 子核心

| 子核心 | T1 原始贡献 | T2 原始贡献 | 作用 |
|--------|-------------:|-------------:|------|
| 并行子核心 | `P+1` | `P+1` | 每个固定提供 128 点原始发配，最终由主核心的 `Gd` 放大 |
| 增幅子核心 | `N+1` | `N+1` | 提高量子和过载核心的 `Gd` 与 `Gt` |
| 热控子核心 | 冷却 `+1` | 冷却 `+1` | 提高热容量与冷却速度；有效冷却仍受其到主核心距离影响 |
| 空白子核心 | — | — | 仅用于填满核心槽，不提供性能属性 |

T1/T2 方块保留为结构与科技树变体，但在统一算力模型中提供相同的原始贡献。量子和过载配置最多放置 **15 个增幅子核心**，第 16 个会使配置无效；稳态与创造主核心不接受增幅子核心。

## 热控距离

热控子核心按到中央主核心的曼哈顿距离计算有效功率：

| 距离 | 有效热控功率 |
|-----:|---------------:|
| 1 格 | 100% |
| 2 格 | 75% |
| 3 格 | 50% |
| 4 格 | 25% |
| 5 格及以上 | 0% |
