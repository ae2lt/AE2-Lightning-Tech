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

| 主核心 | 统一增幅 | 热度策略 |
|--------|----------|----------|
| 稳态主核心 | `Gd=1`、`Gt=2`，最高 1,024 operations/t | 保持低温；热度升高时效率下降，最低保持 45% |
| 量子主核心 | `Gd=2R`、`Gt=R`，最高 10,240 operations/t | 维持低温可获得更高效率 |
| 过载主核心 | `Gd=2R`、`Gt=R²`，最高 4,194,304 operations/t | 约 50% 热度达到峰值，维持在 42%–58% 甜点区 |
| 创造主核心 | 逻辑 operations 无限，但 provider 调用仍受 16,384/t 熔断 | 忽略热度；其余 80 格只能使用空白单元 |

其中 `R=1+N`，`N` 为天枢增幅单元数量，`P` 为所有线程单元提供的线程能力点总和。矩阵先按 `128×P×Gd×Gt` 计算基础 operations，再应用主核心上限与温度效率；它不维护 CPU 的成功派发预算 `D`，也没有单独的 batch 宽度 `q`。

## 外围单元

| 外围单元 | 原始贡献 | 作用 |
|----------|---------:|------|
| 线程单元 T1 | `P+1` | 固定提供 128 点原始线程能力，最终由主核心的 `Gd` 放大 |
| 线程单元 T2 | `P+2` | 固定提供 256 点原始线程能力，以更高造价节省核心槽 |
| 天枢增幅单元 | `N+1` | 提高量子和过载主核心的 `Gd` 与 `Gt`，由天枢与矩阵共用 |
| 热控单元 T1 | 热控 `+1` | 提高热容量与冷却速度；有效热控仍受其到主核心距离影响 |
| 热控单元 T2 | 热控 `+2` | 提供两倍原始热控能力，再应用相同的距离衰减 |
| 空白单元 | — | 仅用于填满核心槽，不提供性能属性 |

线程和热控单元的 T1/T2 都是同一逻辑类型，但分别固定提供 1 点和 2 点原始能力。增幅不再区分 T1/T2：量子和过载配置最多放置 **15 个天枢增幅单元**，第 16 个会使配置无效；稳态与创造主核心不接受增幅单元。

## 热控距离

热控单元按到中央主核心的曼哈顿距离计算有效功率：

| 距离 | 有效热控功率 |
|-----:|---------------:|
| 1 格 | 100% |
| 2 格 | 75% |
| 3 格 | 50% |
| 4 格 | 25% |
| 5 格及以上 | 0% |
