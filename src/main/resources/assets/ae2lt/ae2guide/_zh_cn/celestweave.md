---
navigation:
  title: 苍穹织雷
  icon: ae2lt:celestweave_core
  parent: devices-index.md
  position: 20
item_ids:
  - ae2lt:celestweave_oculus
  - ae2lt:celestweave_core
  - ae2lt:celestweave_conduit
  - ae2lt:celestweave_stride
  - ae2lt:energy_module_t1
  - ae2lt:energy_module_t2
  - ae2lt:energy_module_t3
  - ae2lt:module_night_vision
  - ae2lt:module_water_breathing
  - ae2lt:module_saturation
  - ae2lt:module_reach_extension
  - ae2lt:module_matrix_shield
  - ae2lt:module_phase_shield
  - ae2lt:module_reflect
  - ae2lt:module_undying
  - ae2lt:module_purification
  - ae2lt:module_creative_flight
  - ae2lt:module_phase_flight
  - ae2lt:module_dash
  - ae2lt:module_dig_affinity
---

# 苍穹织雷

<ItemGrid>
  <ItemIcon id="ae2lt:celestweave_oculus" />
  <ItemIcon id="ae2lt:celestweave_core" />
  <ItemIcon id="ae2lt:celestweave_conduit" />
  <ItemIcon id="ae2lt:celestweave_stride" />
</ItemGrid>

**苍穹织雷**是模块化护甲。每件护甲都有独立的 FE 缓存、网络绑定、核心槽与模块槽。

| 部件 | 护甲栏位 | 模块槽 |
|------|---------|--------|
| 苍穹织雷·瞳 | 头盔 | 4 |
| 苍穹织雷·心 | 胸甲 | 5 |
| 苍穹织雷·径 | 护腿 | 4 |
| 苍穹织雷·阶 | 靴子 | 4 |

## 装配

每件护甲都需要在 <ItemLink id="ae2lt:overload_device_workbench" /> 中单独装配：

1. 放入护甲；护甲会绑定到工作站所在的 ME 网络
2. 在核心槽安装 <ItemLink id="ae2lt:ultimate_overload_core" />
3. 安装 1 个可选能量模块，以及对应部件可用的功能模块
4. 穿戴护甲后，默认按 G 打开过载设备中枢，启用、停用或配置模块

能量模块也占用 1 个模块槽。未安装能量模块时，每件护甲可存储 10,000,000 FE。T1 / T2 / T3 能量模块会将护甲容量提升至 1,000,000,000 / 5,000,000,000 / 20,000,000,000 FE。

## 运行条件

模块只有在护甲已穿戴、安装核心、模块已启用时才会进入运行态。运行中的模块会消耗已穿戴苍穹织雷部件中的 FE，并通常从绑定 ME 网络中消耗闪电。

当 FE 或所需闪电不足时，对应效果无法维持。

## 头部模块

| 模块 | 作用 |
|------|------|
| 夜视模块 | 运行时持续维持夜视效果 |
| 水下呼吸模块 | 运行时持续维持水下呼吸效果 |
| 饱和模块 | 周期性将饥饿值与饱和度恢复至满值，恢复时消耗高压闪电 |

## 胸部模块

| 模块 | 作用 |
|------|------|
| 触及扩展模块 | 增加方块 / 实体交互距离，可配置为 1x、2x 或 4x |
| 矩阵护盾模块 | 免疫环境伤害，普通伤害约 80% 减免，硬伤害约 50% 减免 |
| 相位护盾模块 | 完全阻止受到的伤害，并按阻止量消耗极高压闪电 |
| 反伤模块 | 将来自攻击者的部分伤害反弹，消耗 FE 与高压闪电 |
| 不死模块 | 拦截致死伤害，消耗大量 FE 与极高压闪电 |
| 净化模块 | 移除并阻止配置允许的状态效果；默认只处理负面效果 |

矩阵护盾与相位护盾属于同一安装组，胸甲上只能安装其中一种。

## 腿部模块

| 模块 | 作用 |
|------|------|
| 创造飞行模块 | 提供类似创造模式的飞行能力，可配置速度与飞行惯性 |
| 相位飞行模块 | 不切换游戏模式即可穿过方块飞行，可配置速度与飞行惯性 |

创造飞行与相位飞行属于同一安装组，护腿上只能安装其中一种。相位飞行安装后默认停用，需要在设备中枢中启用。

## 足部模块

| 模块 | 作用 |
|------|------|
| 冲刺模块 | 默认按 V 向视角方向冲刺，冷却 40 tick |
| 挖掘适应模块 | 减轻水下与空中挖掘惩罚 |

## 消耗说明

多数运行中的普通模块每 tick 额外消耗 1 高压闪电。创造飞行在飞行时消耗更多高压闪电；相位飞行每 tick 消耗极高压闪电。护盾、净化与不死在短时间内连续触发时，消耗会逐次提高。
