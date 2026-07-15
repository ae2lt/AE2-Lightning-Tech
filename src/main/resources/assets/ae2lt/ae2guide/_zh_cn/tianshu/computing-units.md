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
  - ae2lt:storage_supercomputing_unit
  - ae2lt:parallel_supercomputing_unit
---

# 超算单元与性能

核心舱占用结构中央的 3×3×3 空间。正中心必须安装一个主超算单元，其余 26 格必须由存储与并行超算单元填满。下图移除了外部结构，以展示中心位置与一种 16∶10 的示例配置。

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
| <ItemLink id="ae2lt:quantum_supercomputing_unit" /> | ×16 | ×2 | 显著提高合成容量 |
| <ItemLink id="ae2lt:overload_supercomputing_unit" /> | ×256 | ×4 | 面向超大规模合成计划 |
| <ItemLink id="ae2lt:multidimensional_supercomputing_unit" /> | 无限 | ×8 | 合成容量不受字节限制；并行数仍受全局上限约束 |

即使使用多维超算单元，结构仍要求至少安装一个存储超算单元。

## 存储与并行计算

设存储超算单元数量为 **S**，并行超算单元数量为 **P**。有效配置必须满足 `S + P = 26`，并且 `S ≥ 1`、`P ≥ 1`。

* 基础合成容量为 `64 MiB × S`，再乘以主超算单元的存储倍率
* 并行数为 `128 × P × 主超算单元并行倍率`
* 并行数最高为 **16,384**；超过上限的部分不会继续提高性能

| 主超算单元 | 每个存储单元提供 | 25 个存储单元时 | 每个并行单元提供 | 25 个并行单元时 |
|------------|-----------------:|-----------------:|-----------------:|-----------------:|
| 基准 | 64 MiB | 1,600 MiB（1.56 GiB） | 128 | 3,200 |
| 量子 | 1 GiB | 25 GiB | 256 | 6,400 |
| 过载 | 16 GiB | 400 GiB | 512 | 12,800 |
| 多维 | 总容量固定为无限 | 无限 | 1,024 | 16,384（16 个时达到） |

并行数提高合成步骤的发配能力，但不会缩短加工设备自身的配方时间。要利用较高并行数，网络中还需要足够的样板供应器、加工设备、原料与传输能力。

## 配置示例

| 配置 | 基准 | 量子 | 过载 | 多维 |
|------|------|------|------|------|
| 25 存储 + 1 并行 | 1.56 GiB / 128 | 25 GiB / 256 | 400 GiB / 512 | 无限 / 1,024 |
| 16 存储 + 10 并行 | 1 GiB / 1,280 | 16 GiB / 2,560 | 256 GiB / 5,120 | 无限 / 10,240 |
| 1 存储 + 25 并行 | 64 MiB / 3,200 | 1 GiB / 6,400 | 16 GiB / 12,800 | 无限 / 16,384 |

表格中的每组数值依次表示“合成容量 / 并行数”。多维主超算单元只需 16 个并行超算单元即可达到并行上限；继续增加并行超算单元不会产生额外增益。
