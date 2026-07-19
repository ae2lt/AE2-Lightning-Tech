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
  - ae2lt:amplifier_supercomputing_unit
  - ae2lt:closed_loop_pattern_storage
  - ae2lt:closed_loop_seed_storage
---

# 超算单元与性能

核心舱占用结构中央的 3×3×3 空间。正中心必须安装一个主超算单元，其余 26 格可以安装空白、存储、并行或增幅超算单元、闭环样板仓与闭环种子存储器。具体允许的组合由主超算单元等级决定。下图移除了外部结构，以展示主超算单元的中心位置。

<GameScene zoom="4" background="transparent" interactive={true}>
  <ImportStructure src="../assets/assemblies/tianshu_supercomputer_core.snbt" />
  <BoxAnnotation min="3 3 3" max="4 4 4" color="#f2d37a" alwaysOnTop={true}>唯一的主超算单元位置</BoxAnnotation>
  <IsometricCamera yaw="215" pitch="25" />
</GameScene>

## 主超算单元

主超算单元提供内置存储，并决定成功派发、外部存储和批量复制预算的上限。

| 主超算单元 | 内置存储 | 成功派发/t 上限 | 最大复制/t | 增幅单元 |
|------------|---------:|----------------:|-----------:|----------|
| <ItemLink id="ae2lt:baseline_supercomputing_unit" /> | 1 MiB | 512 | 1,024 | 不支持 |
| <ItemLink id="ae2lt:quantum_supercomputing_unit" /> | 256 MiB | 3,072 | 10,240 | 0–15 |
| <ItemLink id="ae2lt:overload_supercomputing_unit" /> | 64 GiB | 16,384 | 4,194,304 | 0–15 |
| <ItemLink id="ae2lt:multidimensional_supercomputing_unit" /> | 无限 | 16,384 | 无限 | 不支持 |

基础、量子和过载核心都至少需要一个并行超算单元；存储超算单元是可选的。多维核心完全使用主核心自身的预算，外围不能安装存储、并行或增幅单元，只能使用空白单元和两类闭环存储填满其余位置。

## 存储、派发与复制预算

设存储、并行和增幅超算单元数量分别为 **S**、**P**、**A**。每个存储单元提供 64 MiB 外部存储，每个并行单元提供 128 点基础派发能力。有限等级必须满足 `P ≥ 1`；量子和过载还必须满足 `0 ≤ A ≤ 15`。

| 主超算单元 | 派发增益 | 外部存储增益 | 每次成功派发的复制增益 |
|------------|---------:|---------------:|-------------------------:|
| 基准 | ×1 | ×1 | ×2 |
| 量子 | `×2(1+A)` | `×2(1+A)` | `×(1+A)` |
| 过载 | `×2(1+A)` | `×[2(1+A)]²` | `×(1+A)²` |

因此，成功派发数为 `128 × P × 派发增益`，再受主核心的成功派发上限约束；总合成容量为“内置存储 + `64 MiB × S × 外部存储增益`”；最大复制数为“成功派发数 × 复制增益”，再受主核心的复制上限约束。

“成功派发”表示一次被样板供应器接受的派发调用。一次成功派发可以携带多份配方复制，因此成功派发数和最大复制数是两个独立预算。它们都不会缩短加工设备自身的配方时间；要利用较高预算，网络中仍需要足够的样板供应器、加工设备、原料与传输能力。

## 增幅、空白与闭环单元

<ItemLink id="ae2lt:amplifier_supercomputing_unit" /> 用于提高量子或过载核心的派发、外部存储和批量复制能力。基础和多维核心不接受增幅单元，量子与过载最多安装 15 个。

## 空白与闭环单元

<ItemLink id="ae2lt:blank_supercomputing_unit" /> 是类似物质扭曲矩阵空白子核心的中性占位单元。它能使外围槽位满足结构要求，但不提供合成容量、派发能力、增幅、样板槽或种子容量。

闭环样板仓与闭环种子存储器也属于合法外围单元，分别提供对应的闭环实体存储能力，但不会计入 **S**、**P** 或 **A**。不需要额外属性或闭环存储的槽位可以使用空白超算单元填充。
