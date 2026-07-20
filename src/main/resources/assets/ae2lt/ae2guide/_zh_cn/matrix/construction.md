---
navigation:
  title: 结构与自动搭建
  icon: ae2lt:matter_warping_matrix_casing
  parent: matrix/matrix-index.md
  position: 10
item_ids:
  - ae2lt:matter_warping_matrix_casing
  - ae2lt:matter_warping_matrix_constraint_frame
  - ae2lt:matter_warping_matrix_glass
  - ae2lt:matter_warping_matrix_controller
  - ae2lt:matter_warping_matrix_port
---

# 结构与自动搭建

矩阵占用 **7×11×7** 的固定空间。控制器位于一个 7×11 侧面的正中央；结构从控制器沿其朝向延伸 6 格，并向左右各延伸 3 格、向上下各延伸 5 格。

下图为最低样板容量、使用稳态主核心与空白子核心的完整结构。场景可以拖动旋转；标记分别指向控制器、默认端口位置与一个样板仓。

<GameScene zoom="2.4" background="transparent" interactive={true}>
  <ImportStructure src="../assets/assemblies/matter_warping_matrix.snbt" />
  <DiamondAnnotation pos="0.5 5.5 3.5" color="#f2d37a">物质扭曲矩阵控制器</DiamondAnnotation>
  <DiamondAnnotation pos="6.5 5.5 3.5" color="#85f29e">默认端口位置</DiamondAnnotation>
  <DiamondAnnotation pos="1.5 1.5 1.5" color="#80c6ff">一个 T1 样板仓</DiamondAnnotation>
  <IsometricCamera yaw="215" pitch="25" />
</GameScene>

## 自动搭建材料清单

以下数量适用于只放置了控制器、其余目标位置均为空的结构。已有的正确方块会被保留，并从实际需求中扣除。

| 材料 | 数量 | 自动搭建行为 |
|------|-----:|--------------|
| <ItemLink id="ae2lt:matter_warping_matrix_casing" /> | 174 | 固定外壳 |
| <ItemLink id="ae2lt:matter_warping_matrix_constraint_frame" /> | 132 | 包括两个未安装端口的候选位置 |
| <ItemLink id="ae2lt:matter_warping_matrix_glass" /> | 44 | 固定观察层 |
| <ItemLink id="ae2lt:matter_warping_matrix_port" /> | 1 | 未预先放置端口时安装在控制器正对面 |
| <ItemLink id="ae2lt:matter_warping_matrix_pattern_storage_t1" /> 或 <ItemLink id="ae2lt:matter_warping_matrix_pattern_storage_t2" /> | 1–50 | 至少需要 1 个；继续消耗背包中的样板仓并填入空位，最多 50 个 |

控制器已经放置，因此不在按钮消耗的材料中。完整成形还必须手动准备并放置：

| 内部核心 | 数量 | 说明 |
|----------|-----:|------|
| 任意一种主核心 | 1 | 只能放在几何中心 |
| 任意子核心 | 80 | 每个子核心槽都必须填满；空白子核心可以作为占位块 |

因此，从空场地开始至少需要 **1 个控制器、352 个由按钮放置的结构方块、1 个主核心和 80 个子核心**。其中 352 个结构方块已经包含 1 个最低要求的样板仓。

## 使用自动搭建

1. 放置控制器，并按其朝向预留完整空间
2. 将外壳、框架、玻璃、端口和所需数量的样板仓放入**玩家背包**
3. 右击控制器并执行「自动搭建」
4. 自动搭建完成后，在中央核心舱手动放置 1 个主核心与 80 个子核心

自动搭建只读取玩家背包，不会从 ME 网络或相邻容器提取方块。若只需要一个样板仓，执行自动搭建时不应在背包中携带更多矩阵样板仓。

如果任一外壳、框架、玻璃、端口候选位或样板仓位被错误方块占据，本次自动搭建不会放置任何方块。应清除目标位置的阻挡后重试。

## 手动搭建规则

* 三个端口候选位中必须**恰好一个**放置矩阵端口，另外两个必须使用约束框架
* 50 个样板仓位置可以留空或安装 T1 / T2 样板仓，但总数不得为零
* 81 个核心位置不能留空：正中心必须是主核心，其余位置必须是子核心
* 量子和过载主核心最多允许 15 个增幅子核心；稳态主核心不接受增幅，创造主核心只能搭配空白子核心
* 展示结构中的开口不属于结构成员，不要求用方块封闭

最后一个必要方块放置后，矩阵会自动重新扫描并成形，无需再次按下自动搭建按钮。
