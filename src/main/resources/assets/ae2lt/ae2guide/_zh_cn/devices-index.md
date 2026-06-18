---
navigation:
  title: 过载设备
  icon: ae2lt:overload_device_workbench
  parent: index.md
  position: 50
item_ids:
  - ae2lt:overload_device_workbench
  - ae2lt:electromagnetic_railgun
  - ae2lt:celestweave_oculus
  - ae2lt:celestweave_core
  - ae2lt:celestweave_conduit
  - ae2lt:celestweave_stride
---

# 过载设备

过载设备是通过 <ItemLink id="ae2lt:overload_device_workbench" /> 装配的高级装备。它们使用本体 FE 缓存、绑定的 ME 网络，以及各自专用的模块系统。

## 可用设备

* [电磁炮](electromagnetic-railgun.md) — 具备光束、蓄力射击、链式电弧与战术模块的远程武器
* [苍穹织雷](celestweave.md) — 用于移动、防御与辅助效果的模块化护甲

## 装配流程

1. 将过载装备工作站接入 ME 网络
2. 放入过载设备；工作站会自动将设备绑定到自身所在的 ME 网络
3. 在核心槽安装 <ItemLink id="ae2lt:ultimate_overload_core" />
4. 将兼容模块放入模块输入槽，约 20 tick 后会安装 1 个模块
5. 默认按 G 打开过载设备中枢，切换模块和设备设置

## 能量与网络

每件设备都有独立的 FE 缓存，可以作为 FE 物品直接充能。若绑定 ME 网络中可用 Applied Flux 的 FE 存储，设备也可以从网络自动补充 FE。

战斗与护甲模块可能从绑定网络中消耗**高压闪电**或**极高压闪电**。使用这些设备前，请确认网络中有足够的闪电库存。
