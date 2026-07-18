---
navigation:
  title: 超算单元与性能
  icon: ae2lt:baseline_supercomputing_unit
  parent: tianshu/tianshu-index.md
  position: 20
item_ids:
  - ae2lt:baseline_supercomputing_unit
  - ae2lt:quantum_supercomputing_unit
  - ae2lt:overload_supercomputing_unit
  - ae2lt:multidimensional_supercomputing_unit
  - ae2lt:blank_supercomputing_unit
  - ae2lt:storage_supercomputing_unit
  - ae2lt:parallel_supercomputing_unit
  - ae2lt:closed_loop_pattern_storage
  - ae2lt:closed_loop_seed_storage
---

# 超算单元与性能

核心舱占用结构中央的 3×3×3 空间。正中心必须安装一个主超算单元，其余 26 格可以安装空白、存储或并行超算单元、闭环样板仓与闭环种子存储器。下图移除了外部结构，以展示主超算单元的中心位置。

<GameScene zoom="4" background="transparent" interactive={true}>
  <ImportStructure src="../assets/assemblies/tianshu_supercomputer_core.snbt" />
  <BoxAnnotation min="3 3 3" max="4 4 4" color="#f2d37a" alwaysOnTop={true}>唯一的主超算单元位置</BoxAnnotation>
  <IsometricCamera yaw="215" pitch="25" />
</GameScene>

## 主超算单元

主超算单元同时决定合成容量倍率与并行倍率。

| 主超算单元 | 存储倍率 | 并行倍率 | 配置特性 |
|------------|---------:|---------:|----------|
| <ItemLink id="ae2lt:baseline_supercomputing_unit" /> | ×1 | ×1 | 基础性能 |
| <ItemLink id="ae2lt:quantum_supercomputing_unit" /> | ×16 | ×3 | 显著提高合成容量 |
| <ItemLink id="ae2lt:overload_supercomputing_unit" /> | ×256 | ×6 | 面向超大规模合成计划 |
| <ItemLink id="ae2lt:multidimensional_supercomputing_unit" /> | 无限 | ×8 | 合成容量不受字节限制；并行数仍受全局上限约束 |

即使使用多维超算单元，结构仍要求至少安装一个存储超算单元。

## 存储与并行计算

设存储超算单元数量为 **S**，并行超算单元数量为 **P**，其余合法外围单元总数为 **O**。有效配置必须满足 `S + P + O = 26`，并且 `S ≥ 1`、`P ≥ 1`。

* 基础合成容量为 `64 MiB × S`，再乘以主超算单元的存储倍率
* 并行数为 `128 × P × 主超算单元并行倍率`
* 并行数最高为 **16,384**；超过上限的部分不会继续提高性能

下表列出安装对应主超算单元后，每个外围单元的实际贡献，数值已经包含主超算单元倍率。

| 主超算单元 | 每个存储单元提供 | 每个并行单元提供 |
|------------|-----------------:|-----------------:|
| 基准 | 64 MiB | 128 |
| 量子 | 1 GiB | 384 |
| 过载 | 16 GiB | 768 |
| 多维 | 总容量固定为无限 | 1,024 |

并行数提高合成步骤的发配能力，但不会缩短加工设备自身的配方时间。要利用较高并行数，网络中还需要足够的样板供应器、加工设备、原料与传输能力。

## 空白与闭环单元

<ItemLink id="ae2lt:blank_supercomputing_unit" /> 是类似物质扭曲矩阵空白子核心的中性占位单元。它能使外围槽位满足结构要求，但不提供合成容量、并行数、样板槽或种子容量。

闭环样板仓与闭环种子存储器也属于合法外围单元，分别提供对应的闭环实体存储能力，但不会计入 **S** 或 **P**。不需要额外属性或闭环存储的槽位可以使用空白超算单元填充。
