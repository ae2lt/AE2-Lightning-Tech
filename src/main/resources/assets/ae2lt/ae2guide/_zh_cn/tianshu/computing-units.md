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

核心舱占用结构中央的 3×3×3 空间。正中心必须安装一个主超算单元，其余 26 格只能安装空白、存储、并行或增幅超算单元。闭环样板仓与闭环种子存储器安装在外壳的冷却兼容位，与相变冷却单元共用位置规则。具体允许的核心组合由主超算单元等级决定。下图移除了外部结构，以展示主超算单元的中心位置。

<GameScene zoom="4" background="transparent" interactive={true}>
  <ImportStructure src="../assets/assemblies/tianshu_supercomputer_core.snbt" />
  <BoxAnnotation min="3 3 3" max="4 4 4" color="#f2d37a" alwaysOnTop={true}>唯一的主超算单元位置</BoxAnnotation>
  <IsometricCamera yaw="215" pitch="25" />
</GameScene>

## 主超算单元

主超算单元提供内置合成存储，并决定派发并行、外部存储和批量复制预算的上限。这三项参数分别控制任务容量、AE2 样板派发次数和兼容批量执行路径的单次处理规模，不能互相替代。

| 主超算单元 | 内置存储 | 成功派发/t 上限 | 最大复制/t | 增幅单元 |
|------------|---------:|----------------:|-----------:|----------|
| <ItemLink id="ae2lt:baseline_supercomputing_unit" /> | 1 MiB | 512 | 1,024 | 不支持 |
| <ItemLink id="ae2lt:quantum_supercomputing_unit" /> | 256 MiB | 3,072 | 10,240 | 0–15 |
| <ItemLink id="ae2lt:overload_supercomputing_unit" /> | 64 GiB | 16,384 | 4,194,304 | 0–15 |
| <ItemLink id="ae2lt:multidimensional_supercomputing_unit" /> | 无限 | 16,384 | 无限 | 不支持 |

基础、量子和过载核心都至少需要一个并行超算单元；存储超算单元是可选的。多维核心完全使用主核心自身的预算，其 26 个外围格不能安装存储、并行或增幅单元，必须全部使用空白超算单元。

## 三项性能参数

### 合成存储

合成存储用于保存合成计划、中间产物和任务状态。天枢可以同时运行多个合成任务；每个任务开始时会从总容量中预留其计划所需的字节，任务完成或取消后再释放。

合成存储只决定任务能否被接收以及可同时保留多少任务，不会提高样板派发速度或加工设备速度。

### 成功派发

“成功派发”是指一次被样板供应器接受的 AE2 样板派发调用。该参数是天枢对 AE2 原生并行能力的直接表示：数值越高，每 tick 可以向更多可用供应器提交样板。

天枢会将成功派发能力映射为 AE2 合成 CPU 的基础操作与协处理器。AE2 的 CPU 选择列表读取的是这项能力，不会把下文的批量复制预算计入并行数。

成功派发仍受实际执行环境限制。供应器、加工设备、原料或网络传输不足时，未被接受的调用不计为成功派发，也不会因为预算较高而使设备缩短配方时间。

### 批量复制

“一份复制”表示执行一次样板配方。批量复制预算限制兼容执行路径在每 tick 内能够提交的样板总份数。例如，一次供应器调用若提交同一样板的 32 份执行，会消耗 1 次成功派发和 32 份复制预算。

批量复制不是物品复制，不会免除输入、能量、设备处理时间或输出空间要求。每份执行仍按样板正常消耗输入并产生输出。

批量复制属于扩展能力，不是 AE2 原生样板派发接口的通用功能。分子装配室兼容样板、物质扭曲矩阵、受支持的闭环批量样板以及提供专用批量适配器的执行端可以使用该预算。普通 AE2 处理样板若通过原生单份派发路径发送到一般加工设备，每次调用只接受一份样板；此时实际吞吐主要由成功派发数决定，未使用的复制预算不会转换为额外派发。

## 单元数量与计算公式

设存储、并行和增幅超算单元数量分别为 **S**、**P**、**A**。每个存储单元提供 64 MiB 外部存储，每个并行单元提供 128 点基础派发能力。有限等级必须满足 `P ≥ 1`；量子和过载还必须满足 `0 ≤ A ≤ 15`。

| 主超算单元 | 派发增益 | 外部存储增益 | 每次成功派发的复制增益 |
|------------|---------:|---------------:|-------------------------:|
| 基准 | ×1 | ×1 | ×2 |
| 量子 | `×2(1+A)` | `×2(1+A)` | `×(1+A)` |
| 过载 | `×2(1+A)` | `×[2(1+A)]²` | `×(1+A)²` |

因此，各项参数按以下方式计算：

* 成功派发数为 `128 × P × 派发增益`，再受主核心的成功派发上限约束。
* 总合成容量为“内置存储 + `64 MiB × S × 外部存储增益`”。
* 最大复制数为“成功派发数 × 复制增益”，再受主核心的复制上限约束。

例如，一项配置具有每 tick 512 次成功派发和 1,024 份最大复制。原生单份派发路径最多利用其中 512 份；兼容批量执行路径可以在不超过 512 次成功调用的前提下，将同一样板的多份执行合并提交，并使用最多 1,024 份复制预算。批量分组方式由供应器和执行端共同决定，不保证单个设备能够接受全部预算。

## 增幅与空白核心单元

<ItemLink id="ae2lt:amplifier_supercomputing_unit" /> 用于提高量子或过载核心的派发、外部存储和批量复制能力。基础和多维核心不接受增幅单元，量子与过载最多安装 15 个。

<ItemLink id="ae2lt:blank_supercomputing_unit" /> 是类似物质扭曲矩阵空白子核心的中性占位单元。它能使外围槽位满足结构要求，但不提供合成容量、派发能力、增幅、样板槽或种子容量。

## 冷却层闭环存储

<ItemLink id="ae2lt:closed_loop_pattern_storage" /> 与 <ItemLink id="ae2lt:closed_loop_seed_storage" /> 属于冷却兼容结构块，不是外围核心单元。它们可以替代任意相变冷却单元位置，包括两个端口候选位中未安装端口的那个位置；每个替换分别提供闭环样板或闭环种子实体存储能力。

两类闭环存储不会计入 **S**、**P** 或 **A**，也不能满足核心舱的 26 格填充要求。移除闭环存储并换回相变冷却单元不会破坏结构完整性，但会立即移除相应的样板容量或种子存储能力。
