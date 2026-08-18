---
navigation:
  title: 苍穹材料
  icon: ae2lt:firmament_alloy_ingot
  parent: materials/materials-index.md
item_ids:
  - ae2lt:firmament_dust
  - ae2lt:firmament_essence
  - ae2lt:firmament_mixture
  - ae2lt:firmament_alloy_ingot
  - ae2lt:firmament_superconducting_wire
  - ae2lt:inactive_firmament_spirit_core
  - ae2lt:firmament_spirit_core_oculus
  - ae2lt:firmament_spirit_core_core
  - ae2lt:firmament_spirit_core_conduit
  - ae2lt:firmament_spirit_core_stride
---

# 苍穹材料

<ItemImage id="ae2lt:firmament_alloy_ingot" scale="2" float="left" />

苍穹材料是 AE2 闪电科技面向**末地**的高阶材料体系。一切始于只能在末地获取的苍穹粉，再经苍穹转换核心与过载处理工厂逐级精炼，最终成为苍穹织雷护甲与电磁炮的核心组件。

<ItemGrid>
  <ItemIcon id="ae2lt:firmament_dust" />
  <ItemIcon id="ae2lt:firmament_essence" />
  <ItemIcon id="ae2lt:firmament_mixture" />
  <ItemIcon id="ae2lt:firmament_alloy_ingot" />
  <ItemIcon id="ae2lt:firmament_superconducting_wire" />
</ItemGrid>

## 苍穹粉

苍穹粉是整个体系的根源，**只能在末地生成**。

将一块 <ItemLink id="ae2:annihilation_plane" /> **朝上**安放在末地**建造高度的最顶层**，并接入已供电的 ME 网络。条件满足后，破坏面板无需破坏任何方块便会自行生成苍穹粉，约每 10 秒向网络存储注入一份。

生成条件：

* 维度必须为末地
* 破坏面板朝向必须为上
* 面板必须位于建造高度的最顶层（默认末地为 y 255；若世界高度上限被修改，则以实际上限为准）
* 破坏面板需接入已供电且处于激活状态的网络

> 每块满足条件的破坏面板各自独立生成，可并联多块以提高产量。

## 苍穹转换核心

多数精炼步骤都依赖**苍穹转换核心**。该方块无法合成，仅存在于漂浮在末地外岛上空的**苍穹星舰**之中——需前往主岛之外的外环岛屿寻找这座星舰。核心坚不可摧，无法挖取或移动，也只在星舰结构内运转，请直接就地使用。

苍穹转换核心不消耗能量或闪电，只需短暂等待即可完成加工：手持材料对其右键放入，加工完成后**空手**右键取回产物。它最多可同时接受三种输入，并按配方产出多种结果。核心的任意面都能用漏斗或管道自动存取材料与产物，可以直接在星舰上搭建自动化产线。

## 精炼材料

苍穹粉沿两条产线加工：

| 材料 | 加工设备 | 主要用途 |
|------|---------|---------|
| 苍穹源质 | 苍穹转换核心 | 苍穹超导丝及更高阶配方 |
| 苍穹混合物 | [过载处理工厂](../machines/overload-processing-factory.md) | 精炼为苍穹合金锭 |
| 苍穹合金锭 | 苍穹转换核心 | 苍穹织雷与电磁炮的主料 |
| 苍穹超导丝 | [过载处理工厂](../machines/overload-processing-factory.md) | 苍穹织雷的导能部件 |

这些配方除苍穹材料外，还会用到过载合金锭、下界合金碎片、幻翼膜等辅料。苍穹合金锭是本模组最高阶的结构材料，苍穹超导丝则承担苍穹织雷的能量传导。

## 苍穹灵核

苍穹灵核是苍穹织雷护甲的核心。

<ItemLink id="ae2lt:inactive_firmament_spirit_core" /> 有一定几率出现在末地城的宝藏箱中。将其投入苍穹转换核心加工即可**激活**，一次性分离出四种灵核各一枚：

<ItemGrid>
  <ItemIcon id="ae2lt:firmament_spirit_core_oculus" />
  <ItemIcon id="ae2lt:firmament_spirit_core_core" />
  <ItemIcon id="ae2lt:firmament_spirit_core_conduit" />
  <ItemIcon id="ae2lt:firmament_spirit_core_stride" />
</ItemGrid>

四枚灵核分别对应 [苍穹织雷](../celestweave.md) 的瞳、心、径、阶四个部位，是在[闪电装配室](../machines/lightning-assembly-chamber.md)制作对应护甲部件所必需的核心组件。
