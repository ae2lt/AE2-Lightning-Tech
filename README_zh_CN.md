# AE2 闪电科技 — Forge 1.20.1 移植版

[English](README.md)

这是 [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) 的附属模组，添加闪电能源、进阶机器和过载 ME 网络组件。

> 必需依赖 AE2 与 Thunderbolt Core · 适用于 Minecraft 1.20.1 / Forge 47.1.3+

本分支是持续维护的 Forge 1.20.1 移植。主项目面向更新的 Minecraft 与 NeoForge；在 1.20.1 API 能支持的范围内，本分支会同步其行为修复和功能改进。

## 主要内容

- 高压闪电与极高压闪电两级能源。
- 闪电收集器、大气电离仪和特斯拉线圈能源链。
- 闪电装配室、闪电模拟室、过载处理工厂和水晶催化器加工链。
- 过载水晶生长、衰变和批量加工体系。
- 高吞吐的过载 ME 控制器、接口、样板供应器与线缆。
- 无线样板路由、天枢超级计算机自动化和可配置批量调度。
- 苍穹织雷模块化护甲与电磁炮系统。

## 依赖

| 模组 | 要求 |
|------|------|
| Minecraft 1.20.1 | 必需 |
| Forge 47.1.3+ | 必需 |
| Applied Energistics 2 15.4.10+ | 必需 |
| Thunderbolt Core 2.0.0-beta.1 至 `<2.1.0` | 必需 |
| JEI 或 EMI、Jade | 可选联动 |
| AdvancedAE、ExtendedAE、Applied Flux、AE2WTLib | 可选联动 |
| Mekanism、Curios、Flux Networks、Polymorph | 可选联动 |

所有可选联动均已隔离，不安装时不应影响正常启动。

## 构建

使用 Java 17 并执行：

```powershell
.\gradlew.bat test build
```

可发布制品位于 `build/libs/ae2lt-forge-1.20.1-2.1.0-beta.1.jar`。
带 `-slim.jar` 后缀的是开发中间制品，不应对外发布。

## 公开 API

`com.moakiee.ae2lt.api.*` 是提供给附属模组作者的稳定接口，包括闪电能源 capability 与事件、固定的方块实体和配方 ID，以及服务器权威的无线频率 API。Forge 事件订阅应使用 `MinecraftForge.EVENT_BUS`。

该包之外的实现均视为内部接口，可能在小版本更新时变化。

## 许可证

- 源代码：[GNU LGPL 3.0](LICENSE)
- 材质与视觉资源：[CC BY-NC-SA 3.0](LICENSE_ASSETS.md)
- 改编或内置代码：[第三方声明](THIRD_PARTY_NOTICES.md)

开发者：**MOAKIEE**、**CystrySU**、**gjmhmm8**、**_leng**、**TedXenon**、**MHanHanBing**。
