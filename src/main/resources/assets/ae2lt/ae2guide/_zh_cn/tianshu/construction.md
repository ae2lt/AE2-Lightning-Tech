---
navigation:
  title: 结构与自动搭建
  icon: ae2lt:tianshu_supercomputer_casing
  parent: tianshu/tianshu-index.md
  position: 10
item_ids:
  - ae2lt:tianshu_supercomputer_casing
  - ae2lt:phase_change_cooling_unit
  - ae2lt:tianshu_supercomputer_glass
  - ae2lt:tianshu_supercomputer_controller
  - ae2lt:tianshu_supercomputer_port
  - ae2lt:blank_supercomputing_unit
  - ae2lt:storage_supercomputing_unit
  - ae2lt:parallel_supercomputing_unit
  - ae2lt:tianshu_amplifier_unit
  - ae2lt:closed_loop_pattern_storage
  - ae2lt:closed_loop_seed_storage
---

# 结构与自动搭建

天枢超算阵列占用 **7×7×7** 的固定空间。控制器位于一个侧面的底边中央；结构从控制器沿其朝向延伸 6 格、向上延伸 6 格，并向左右各延伸 3 格。

下图展示使用天枢基准主核心的一种完整结构。场景可以拖动旋转；底部与顶部中央是两个端口候选位。

<GameScene zoom="2.8" background="transparent" interactive={true}>
  <ImportStructure src="../assets/assemblies/tianshu_supercomputer.snbt" />
  <DiamondAnnotation pos="6.5 0.5 3.5" color="#f2d37a">天枢超算阵列控制器</DiamondAnnotation>
  <DiamondAnnotation pos="3.5 0.5 3.5" color="#85f29e">默认端口位置</DiamondAnnotation>
  <DiamondAnnotation pos="3.5 6.5 3.5" color="#80c6ff">另一端口候选位</DiamondAnnotation>
  <DiamondAnnotation pos="2.5 6.5 2.5" color="#ff9fd7">闭环样板仓示例位置</DiamondAnnotation>
  <DiamondAnnotation pos="4.5 6.5 2.5" color="#9fffe1">闭环种子存储器示例位置</DiamondAnnotation>
  <BoxAnnotation min="2 2 2" max="5 5 5" color="#d58cff" alwaysOnTop={true}>3×3×3 核心舱</BoxAnnotation>
  <IsometricCamera yaw="215" pitch="25" />
</GameScene>

## 自动搭建材料清单

以下数量适用于只放置了控制器、其余目标位置均为空的结构。已有的正确方块会被保留，并从实际需求中扣除。

| 材料 | 数量 | 自动搭建行为 |
|------|-----:|--------------|
| <ItemLink id="ae2lt:tianshu_supercomputer_casing" /> | 99 | 固定外壳 |
| <ItemLink id="ae2lt:phase_change_cooling_unit" /> | 17 | 默认填满 16 个固定冷却位与未安装端口的候选位；闭环存储可替代这些位置 |
| <ItemLink id="ae2lt:tianshu_supercomputer_glass" /> | 98 | 固定核心舱观察层 |
| <ItemLink id="ae2lt:tianshu_supercomputer_port" /> | 1 | 未预先放置端口时安装在底部候选位 |

控制器已经放置，因此不在按钮消耗的材料中。自动搭建也不会填充核心舱；完整成形还必须手动准备并放置：

| 核心舱单元 | 数量 | 说明 |
|------------|-----:|------|
| 任意一种天枢主核心 | 1 | 只能放在核心舱正中心 |
| <ItemLink id="ae2lt:blank_supercomputing_unit" /> | 0–26 | 填充外围槽位，但不提供任何属性 |
| <ItemLink id="ae2lt:storage_supercomputing_unit" /> | 0–25 | 可选；增加有限等级的合成容量 |
| <ItemLink id="ae2lt:parallel_supercomputing_unit" /> | 0–26 | 基础、量子和过载核心至少需要 1 个；多维核心不允许使用 |
| <ItemLink id="ae2lt:tianshu_amplifier_unit" /> | 0–15 | 仅量子和过载核心允许使用 |

以上四类天枢单元共同填满核心舱中心以外的 26 格。基准主核心不允许增幅单元；量子和过载主核心最多允许 15 个增幅单元，并且三种有限主核心都至少需要 1 个并行单元。多维主核心的 26 个外围格只能使用空白单元。闭环样板仓与闭环种子存储器不属于核心舱单元，而是安装在外壳的冷却兼容位。

外壳共有 **17 个冷却兼容位**：16 个固定冷却位，以及两个端口候选位中未安装端口的那个位置。每个冷却兼容位可以放置相变冷却单元、闭环样板仓或闭环种子存储器；三者不能同时占用同一位置。预先放置的闭环存储会被自动搭建识别为正确方块，不会被替换为相变冷却单元。

汇总：从空场地开始，完整结构共 **243 个方块**——1 个手动放置的控制器、215 个可由自动搭建放置的非核心方块（99 外壳 + 98 玻璃 + 17 冷却兼容位 + 1 端口），以及 27 个手动安装的核心舱单元。冷却兼容位改用闭环存储时，所需相变冷却单元的数量相应减少。

## 使用自动搭建

1. 放置控制器，并按其朝向预留完整的 7×7×7 空间
2. 将外壳、相变冷却单元、玻璃与端口放入**玩家背包**；也可以先在冷却兼容位放好闭环存储
3. 右击控制器并执行「自动搭建外壳」
4. 外壳搭建完成后，手动安装天枢主核心与 26 个核心舱外围单元，并按需在冷却兼容位安装闭环存储

自动搭建只读取玩家背包，不会从 ME 网络或相邻容器提取方块。开始前会核对完整材料，并以每 tick 一个方块的速度逐步放置；搭建期间应将所需材料保留在背包内。

已有端口可以位于任一候选位。若没有端口、一个候选位已经放置相变冷却单元或闭环存储且另一个为空，自动搭建会保留已有方块并把端口放入空位；两处均为空时默认将端口放在底部候选位。若两个候选位都被非端口方块占用，必须先腾出一个位置。

如果任一由自动搭建负责的外壳、冷却兼容位、玻璃或端口位置被错误方块占据，本次自动搭建不会放置任何方块。应根据提示坐标清除阻挡后重试。若搭建开始后材料被移走或目标位置受到阻挡，流程会中断，且只消耗已经成功放置的方块。

## 手动搭建规则

* 两个端口候选位中必须**恰好一个**放置天枢超算阵列端口，另一个必须使用相变冷却单元、闭环样板仓或闭环种子存储器
* 核心舱中心必须放置一种天枢主核心；主核心不能出现在其余 26 格
* 其余 26 个核心位置必须安装当前主核心支持的天枢空白、存储、并行或增幅单元；闭环存储不能放入核心舱
* 基础、量子和过载核心至少需要一个并行单元；存储单元可选
* 基础核心不支持增幅；量子和过载最多使用 15 个增幅单元
* 多维主核心的 26 个外围格只能搭配天枢空白单元；闭环存储安装在外壳冷却兼容位
* 16 个固定冷却位和未安装端口的候选位都必须填入相变冷却单元或一种闭环存储
* 展示结构中省略的 100 个位置属于必要空气区域，不能放置装饰方块、线缆或其他设备

最后一个必要方块放置后，结构会自动重新扫描并成形，无需再次执行自动搭建。
