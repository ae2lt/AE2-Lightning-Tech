---
navigation:
  title: 过载线缆
  icon: ae2lt:overloaded_cable
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:overloaded_cable
  - ae2lt:overloaded_cable_white
  - ae2lt:overloaded_cable_orange
  - ae2lt:overloaded_cable_magenta
  - ae2lt:overloaded_cable_light_blue
  - ae2lt:overloaded_cable_yellow
  - ae2lt:overloaded_cable_lime
  - ae2lt:overloaded_cable_pink
  - ae2lt:overloaded_cable_gray
  - ae2lt:overloaded_cable_light_gray
  - ae2lt:overloaded_cable_cyan
  - ae2lt:overloaded_cable_purple
  - ae2lt:overloaded_cable_blue
  - ae2lt:overloaded_cable_brown
  - ae2lt:overloaded_cable_green
  - ae2lt:overloaded_cable_red
  - ae2lt:overloaded_cable_black
---

# 过载线缆

**过载线缆**是原版 AE2 密集线缆的增强版本，本身不设频道上限。

## 核心特性

* **无频道上限**：单根过载线缆本身无频道瓶颈，可承载任意数量的频道
* **由控制器决定网络总频道**：整个过载网络的频道总量取决于网络中**过载 ME 控制器**的数量——每个过载控制器为网络提供 128 频道（默认值，可配置）
* **智能频道分配**：过载网络使用**最大流算法**替代原版 AE2 的 BFS 频道分配，在复杂拓扑下能更高效地利用总频道容量
* **密集线缆外观**：视觉上与原版密集线缆一致
* **支持染色**：共有 17 种颜色变体，不同颜色互不连接（与原版 AE2 线缆染色机制一致）

## 频道机制

与原版 AE2 线缆的固定频道上限（普通线缆 8 频道、密集线缆 32 频道）不同，过载线缆采用以下规则：

1. **线缆本身无频道瓶颈**：每根过载线缆都可以承载任意数量的频道
2. **网络总频道 = 过载控制器数 × 128**：整个网络可用的频道总量由过载 ME 控制器的数量决定
3. **最大流分配**：可用频道在网络中按最大流算法智能分配，而非沿路径逐段扣减

对玩家而言意味着：

* 不再需要担心某条主干线缆的频道被用完
* 只要网络总频道足够，所有设备都能获得频道
* 增加过载 ME 控制器即可扩展网络的频道总量

> **重要**：过载线缆只有在**两端都是过载设备**（过载线缆、过载 ME 控制器等）时才能发挥完整的无限容量特性。如果过载线缆与原版 AE2 设备直接相连，该连接仍会按原版规则计入频道上限。

## 颜色变体

过载线缆共有 17 种颜色变体，包括福鲁伊克斯色（默认）以及全部 16 种 Minecraft 染料颜色：

白色、橙色、品红色、淡蓝色、黄色、黄绿色、粉色、灰色、淡灰色、青色、紫色、蓝色、棕色、绿色、红色、黑色。

不同颜色的过载线缆互不连接（与原版 AE2 线缆染色机制一致）。

## 使用方式

过载线缆的使用方式与原版密集线缆一致。直接放置在方块侧面即可接入网络。

## 适用场景

* 大型基地主干线路，一根过载线缆可承担多根密集线缆的频道负载
* 以过载 ME 控制器为核心的高容量网络
* 简化网络拓扑，不再需要针对频道瓶颈进行布线设计
