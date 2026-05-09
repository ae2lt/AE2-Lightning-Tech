# AE2 闪电科技

[English](README.md)

一个 [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) 的附属模组，添加了一套闪电能源系统、进阶机器以及过载 ME 网络组件。

> 依赖 AE2 · 适用于 Minecraft 1.21.1 / NeoForge

## 关于

AE2 闪电科技 把闪电变成一种可用的资源。收集自然雷电，精炼成 **高压闪电** 与 **极高压闪电**，再交给机器培育 **过载水晶** —— 一切过载 ME 网络的基础。它带来了远超原版 AE2 的传输能力、无线样板路由，以及全新的加工流水线。

## 特性

### 闪电能源系统
两个等级的闪电能源 —— **高压闪电 / 极高压闪电** —— 与 FE 并列，能像物品和液体一样被存储、传输和参与合成。

### 闪电收集
- **闪电收集器** —— 捕获击中附近避雷针的雷电。
- **大气电离仪** —— 多方块天气调节装置，产出晴天 / 雨天 / 雷暴凝液。
- **特斯拉线圈** —— 消耗过载水晶粉与 FE，将闪电提纯为高压 / 极高压。

### 过载水晶
基于 AE2 赛特斯石英分级体系的成长线 —— 含 **受损 / 破裂 / 瑕疵 / 完美** 四级母岩、衰变机制、支持水晶生长加速器，最终结晶为过载水晶簇。

### 闪电机械
- **闪电装配室** —— 用原料与闪电装配过载组件。
- **闪电模拟室** —— 通过 闪电塌缩矩阵 在室内运行雷击转化配方。
- **过载处理工厂** —— 高吞吐并行处理器，量产过载合金、合金板与核心。
- **水晶催化器** —— 多催化剂并行加速水晶产出，闪电塌缩矩阵 可成倍放大产量。

### 过载 ME 网络
- **过载ME控制器**、**过载ME接口**、**过载样板供应器** 以及 16 种染色的 **过载ME线缆** —— AE2 网络的高吞吐升级版。
- **过载样板编码器** 支持副产物槽位与 *忽略 NBT* 模式。
- **无线过载控制器** + **无线接收器** —— 不用线缆即可把样板供应器对接到远程机器，可选 轮询 / 均衡 两种分配策略。
- **过载无线连接工具** —— 在世界中直接绑定样板供应器和电源供应器与目标机器。

## Addon 开发者 API

`com.moakiee.ae2lt.api.*` 是本 mod 对第三方模组暴露的唯一稳定接口。Addon 可以 `compileOnly` 依赖本 jar 并 import：

- **`AE2LTCapabilities.LIGHTNING_ENERGY_BLOCK`** —— 方块侧 capability，返回 `ILightningEnergyHandler`。已为 5 个接入闪电网格的方块实体注册：闪电收集器、闪电模拟室、闪电装配室、过载处理工厂、特斯拉线圈。Handler 直接桥接到 AE2 网格闪电存储，不需要任何反射。
- **`LightningTier`** —— `HIGH_VOLTAGE` / `EXTREME_HIGH_VOLTAGE`。序列化名固化为 `"high_voltage"` / `"extreme_high_voltage"`。
- **`LightningCollectedEvent`** —— 在 `NeoForge.EVENT_BUS` 上发布的可取消事件，于 `LightningCollectorBlockEntity.captureLightning(boolean)` 内部、roll 出数量之后、写入网格之前触发。订阅者可以取消捕获或改写入库数量。
- **`AE2LTBlockEntityIds`** / **`AE2LTRecipeIds`** —— 公开方块实体与配方类型的固化 `ResourceLocation` 常量。

`com.moakiee.ae2lt.api.*` 之外的所有代码都是内部实现，可能在小版本之间变更。完整契约与"发布即冻结"清单见 `package-info.java`。

## 依赖

| 模组 | 是否必需 |
|------|----------|
| Applied Energistics 2 | 必需 |
| Jade、Flywheel、Ponder | 可选 |
| Advanced AE、ExtendedAE、Applied Flux、AE2 JEI Integration | 可选联动 |

## 问题反馈

发现 bug 或有建议？欢迎在项目 issue 跟踪器中提交，请附上 Minecraft / NeoForge / AE2LT 的版本号，清晰的描述以及必要的日志。

## 许可证

![源码许可证](https://img.shields.io/badge/source-LGPL--3.0-blue)
![材质许可证](https://img.shields.io/badge/assets-CC%20BY--NC--SA%203.0-lightgrey)

AE2 闪电科技对源码和材质使用不同的许可证：

- 源码以 [GNU LGPL 3.0](https://www.gnu.org/licenses/lgpl-3.0.html) 协议开源。
- 材质与其他视觉资产以 [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/) 协议授权。

## 鸣谢

由 **MOAKIEE**、**CystrySU**、**gjmhmm8**、**_leng**、**TedXenon**、**MHanHanBing** 开发。

特别感谢 Applied Energistics 2 团队 —— 没有 AE2 就没有这个附属。
