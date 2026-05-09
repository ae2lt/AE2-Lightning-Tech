# main 分支功能板块与 26.1.2 移植核查计划

本文用于把 `main` 分支的 1.21.1 功能拆成后续可逐项核查的板块，并作为 `port/26.1.2-neoforge` 是否完整移植的验收索引。

- 基准分支：`main`，当前观察到的提交为 `4555be4bc2a880118a16f9acb92ffa59f8a3f273`
- 当前移植分支：`port/26.1.2-neoforge`，当前观察到的提交为 `e3c0fa7b2ec9d902462b247e1521baf16f075610`
- 本文状态：第一轮拆板块与文件级初筛，尚未逐项判定功能是否等价
- 使用原则：后续每个板块都要看 `main` 的设计/代码/资源/配方，再对照 26.1.2 的实现、资源、数据包和游戏内行为

## 核查记录格式

后续详细核查每个板块时，建议统一记录以下字段：

| 字段 | 说明 |
| --- | --- |
| main 功能点 | 1.21.1 中实际存在的行为，不只看文件名 |
| main 关键文件 | 入口、逻辑、菜单、网络包、资源、配方、指南 |
| 26.1.2 对应实现 | 当前分支中的对应文件或替代实现 |
| 状态 | `已实现` / `部分实现` / `缺失` / `需游戏内验证` / `版本差异替代` |
| 风险 | API 变更、资源路径变更、行为语义变化、兼容模组缺失 |
| 验证方式 | 编译、资源加载、JEI/Jade、单机实测、服务端重启、跨维度/区块卸载等 |

## 板块总览

| 编号 | 板块 | main 中的主要范围 | 核查优先级 |
| --- | --- | --- | --- |
| 01 | 基础注册、启动流程与资源骨架 | `AE2LightningTech`、`registry`、`api`、`config`、语言、模型、战利品、标签、指南 | P0 |
| 02 | 闪电类型、闪电存储与公开能力 | `me/key`、`me/cell`、`api/lightning`、`GridLightningEnergyHandler`、存储元件 | P0 |
| 03 | 闪电采集、转化与天气链路 | 闪电收集器、自然/人工雷击、特斯拉线圈、过载 TNT、大气电离仪、天气凝核 | P0 |
| 04 | 核心配方机器 | 闪电模拟室、闪电装配室、过载处理工厂、水晶催化器、通用机器逻辑 | P0 |
| 05 | 过载材料、水晶成长与雷劈仪式 | 过载水晶、母岩/晶芽/晶簇、雷劈转化、雷劈多方块仪式、材料链 | P1 |
| 06 | 过载网络与频道扩容 | 过载控制器、过载线缆、频道 mixin、全局频道容量 | P0 |
| 07 | 过载样板、样板供应器、接口与自动合成 | 过载样板编码器、36 槽供应器/接口、CPU 状态、返回/弹出/过滤逻辑 | P0 |
| 08 | 无线频率网络 | 无线过载控制器、高级无线控制器、无线接收器、频率/成员/权限/跨维度 | P1 |
| 09 | AppFlux 过载供电仪 | `overloaded_power_supply`、AppFlux 桥接、无线供电、感应卡兼容 | P0，当前初筛疑似缺口 |
| 10 | JEI、Jade 与可选兼容 | JEI 分类、Jade provider、AE2 JEI Integration、AdvancedAE/ExtendedAE/MEGA/AppFlux/Mek 配方 | P1 |
| 11 | 研究笔记、神秘元件、无限元件与彩蛋 | 研究仪式、神秘元件、无限存储、Fumo、彩蛋 overlay/packet | P2 |
| 12 | 客户端渲染、GUI 与资源迁移 | 各机器 screen/widget/renderer、线缆渲染、item model 路径、GUI sprite 路径 | P1 |

## 01 基础注册、启动流程与资源骨架

main 需要确认的能力：

- `AE2LightningTech` 完成所有注册：方块、方块实体、实体、物品、菜单、配方类型、数据组件、AEKey 类型、创造标签。
- `commonSetup` 绑定所有 `AEBaseEntityBlock` 的方块实体、服务端 tick、方块实体物品映射。
- `RegisterCapabilitiesEvent` 注册 ItemHandler、FluidHandler、EnergyStorage、AE2 grid node、公开闪电能力。
- 注册 AE2 升级卡：速度卡、模糊卡、反转卡、合成卡、AppFlux 感应卡。
- 注册 `OverloadPatternDecoder`、`InfiniteCellHandler`、存储单元模型、机器适配器、TNT 发射器行为。
- 资源包覆盖完整：`lang`、`screens`、`blockstates`、`models`/`items`、贴图、loot table、tags、recipes、AE2 Guide。

main 关键路径：

- `src/main/java/com/moakiee/ae2lt/AE2LightningTech.java`
- `src/main/java/com/moakiee/ae2lt/registry/*`
- `src/main/java/com/moakiee/ae2lt/api/*`
- `src/main/resources/assets/ae2lt/lang/*`
- `src/main/resources/assets/ae2lt/ae2guide/**`
- `src/main/resources/data/ae2lt/**`

26.1.2 核查点：

- 是否因 1.21.1 到 26.1.2 的注册 API、资源路径、loot table 路径、item model 路径变更而做了等价迁移。
- `assets/ae2lt/models/item` 与 `assets/ae2lt/items` 的迁移关系要逐项确认，不能只按文件名差异判定缺失。
- `textures/block/slot` 与 `textures/gui/sprites/block/slot` 的迁移关系要确认 GUI 是否实际加载。
- 启动日志中不能有 missing registry、missing texture、missing model、missing recipe serializer。

## 02 闪电类型、闪电存储与公开能力

main 需要确认的能力：

- 新增 AEKey 类型：`LightningKeyType`、`LightningKey`，支持高压闪电和极高压闪电在 ME 网络中显示、存储、提取。
- `LightningTier`、`ILightningEnergyHandler`、`AE2LTCapabilities.LIGHTNING_ENERGY_BLOCK` 提供对外 API。
- 闪电存储元件 I-V：有容量、idle drain、模型注册、配方、拆解配方。
- 无限存储元件和固定无限元件可接入 AE2 StorageCells。
- `GridLightningEnergyHandler` 把方块实体所在 ME 网络中的闪电库存桥接成公开能力。

main 关键路径：

- `src/main/java/com/moakiee/ae2lt/me/key/*`
- `src/main/java/com/moakiee/ae2lt/me/cell/*`
- `src/main/java/com/moakiee/ae2lt/me/GridLightningEnergyHandler.java`
- `src/main/java/com/moakiee/ae2lt/api/lightning/*`
- `src/main/resources/data/ae2lt/recipe/cell_disassembly/*`
- `src/main/resources/data/ae2lt/recipe/lightning_storage_component_*.json`

26.1.2 核查点：

- AE2 API 变更后，`AEKey`、StorageCell、cell inventory、saved data 的读写语义是否一致。
- 闪电库存是否能在终端显示，是否能被机器消耗，是否能被无限元件无限供应。
- 服务端重启后库存、神秘元件解析状态、研究状态是否持久化正常。
- 公开能力是否只注册到 main 中声明的机器，水晶催化器不应误暴露闪电能力。

## 03 闪电采集、转化与天气链路

main 需要确认的能力：

- 闪电收集器接入 ME 网络，被人工雷击产出高压闪电，被自然闪电产出极高压闪电。
- 人工雷击来源包括玩家携带过载水晶触发、特斯拉线圈放电、过载 TNT。
- 特斯拉线圈为双高方块，有高压/极高压模式，能消耗 FE、过载水晶粉、高压闪电并向 ME 网络写入目标闪电等级。
- 大气电离仪消耗 ME 网络 AE 与天气凝核，改变天气；维度不支持天气时应拒绝。
- 调试避雷针只作为调试工具，逻辑与创造/非创造消耗一致。

main 关键路径：

- `src/main/java/com/moakiee/ae2lt/blockentity/LightningCollectorBlockEntity.java`
- `src/main/java/com/moakiee/ae2lt/machine/lightningcollector/*`
- `src/main/java/com/moakiee/ae2lt/machine/teslacoil/*`
- `src/main/java/com/moakiee/ae2lt/machine/atmosphericionizer/*`
- `src/main/java/com/moakiee/ae2lt/event/*Lightning*`
- `src/main/java/com/moakiee/ae2lt/entity/OverloadTntEntity.java`
- `src/main/java/com/moakiee/ae2lt/item/WeatherCondensateItem.java`
- `src/main/resources/data/ae2lt/recipe/rain_condensate.json`
- `src/main/resources/data/ae2lt/recipe/thunderstorm_condensate.json`

26.1.2 核查点：

- 自然雷与人工雷的判定不能混淆，否则高压/极高压产线会错位。
- 特斯拉线圈上半部分 capability 转发到下半部分的逻辑要保留。
- 过载 TNT 的发射器行为、实体同步、雷击批量触发与掉落/破坏行为要实测。
- 大气电离仪 GUI 状态、能量消耗、天气持续时间、目标天气已生效时的状态要一致。

## 04 核心配方机器

main 机器清单：

- 闪电模拟室：大批量输入、FE 消耗、ME 闪电消耗、矩阵代偿、速度卡、自动弹出。
- 闪电装配室：多输入装配、FE/闪电消耗、矩阵代偿、速度卡、自动弹出。
- 过载处理工厂：物品/流体输入、FE/闪电消耗、配方锁定、速度卡、自动弹出、输出面配置。
- 水晶催化器：水流体槽、晶体/粉化模式、催化剂不消耗、并行产量、矩阵倍率、自动导出。
- 大气电离仪和特斯拉线圈虽然也属于机器，但主要行为放在 03 中核查。

main 关键路径：

- `src/main/java/com/moakiee/ae2lt/machine/common/*`
- `src/main/java/com/moakiee/ae2lt/machine/lightningchamber/*`
- `src/main/java/com/moakiee/ae2lt/machine/lightningassembly/*`
- `src/main/java/com/moakiee/ae2lt/machine/overloadfactory/*`
- `src/main/java/com/moakiee/ae2lt/machine/crystalcatalyzer/*`
- `src/main/java/com/moakiee/ae2lt/blockentity/*ChamberBlockEntity.java`
- `src/main/java/com/moakiee/ae2lt/blockentity/OverloadProcessingFactoryBlockEntity.java`
- `src/main/java/com/moakiee/ae2lt/blockentity/CrystalCatalyzerBlockEntity.java`
- `src/main/resources/data/ae2lt/recipe/lightning_simulation/**`
- `src/main/resources/data/ae2lt/recipe/lightning_assembly/**`
- `src/main/resources/data/ae2lt/recipe/overload_processing/**`
- `src/main/resources/data/ae2lt/recipe/crystal_catalyzer/**`

26.1.2 核查点：

- 菜单 slot、ghost output、输出面配置 screen、自动弹出保存/读取是否正常。
- FE、AE、闪电三类能量不要混用，尤其水晶催化器 main 中消耗 AE/FE 表述和代码实现要逐项确认。
- 大堆叠输入、流体输入、锁配方、输出不足时暂停、区块卸载后恢复，均需实测。
- `OverloadProcessingFluidStackTemplate.java` 是当前分支新增的迁移/修复线索，要确认与 main 的流体配方语义等价。
- 兼容配方是否按模组存在条件加载，缺少对应模组时不能导致数据包报错。

## 05 过载材料、水晶成长与雷劈仪式

main 需要确认的能力：

- 过载水晶、过载水晶粉、过载合金、过载合金板、过载奇点、极限过载核心、闪电坍缩矩阵等材料链完整。
- 过载母岩分四个品质：无瑕、有瑕、开裂、损坏；可随机生长晶芽/晶簇。
- 非无瑕母岩存在衰减机制，破坏时衰减，精准采集可避免部分衰减。
- 小/中/大过载水晶芽和晶簇掉落、时运、光照/碰撞形状应与 main 一致。
- `lightning_transform` 和 `lightning_strike` 两种雷劈配方均存在，后者带多方块结构要求。

main 关键路径：

- `src/main/java/com/moakiee/ae2lt/block/BuddingOverloadCrystalBlock.java`
- `src/main/java/com/moakiee/ae2lt/block/OverloadCrystalClusterBlock.java`
- `src/main/java/com/moakiee/ae2lt/lightning/**`
- `src/main/java/com/moakiee/ae2lt/lightning/strike/**`
- `src/main/java/com/moakiee/ae2lt/integration/jei/category/OverloadGrowthCategory.java`
- `src/main/java/com/moakiee/ae2lt/integration/jei/category/LightningTransformCategory.java`
- `src/main/java/com/moakiee/ae2lt/integration/jei/category/LightningStrikeCategory.java`
- `src/main/resources/data/ae2lt/recipe/lightning_transform/**`
- `src/main/resources/data/ae2lt/recipe/lightning_strike/**`
- `src/main/resources/data/ae2/tags/block/growth_acceleratable.json`

26.1.2 核查点：

- 方块状态、随机 tick、生长方向、替换规则、掉落表和标签都要对照。
- 多方块雷劈仪式需要验证自然雷限制、材料顺序、错误顺序湮灭、残片掉落。
- JEI 的成长说明、雷劈转化、雷劈仪式分类要显示所有配方。

## 06 过载网络与频道扩容

main 需要确认的能力：

- 过载 ME 控制器每个提供 128 频道。
- 过载线缆有所有 AE2 颜色，线缆自身不再形成原版每线缆频道瓶颈，频道按过载网络总容量分配。
- 混入 AE2 grid/pathing/channel 计算以支持过载频道容量。
- 过载控制器方块状态、冲突/在线/离线渲染和控制器结构校验一致。

main 关键路径：

- `src/main/java/com/moakiee/ae2lt/block/OverloadedControllerBlock.java`
- `src/main/java/com/moakiee/ae2lt/blockentity/OverloadedControllerBlockEntity.java`
- `src/main/java/com/moakiee/ae2lt/part/OverloadedCablePart.java`
- `src/main/java/com/moakiee/ae2lt/grid/*`
- `src/main/java/com/moakiee/ae2lt/mixin/GridNodeMaxChannelsMixin.java`
- `src/main/java/com/moakiee/ae2lt/mixin/GridConnectionMaxChannelsMixin.java`
- `src/main/java/com/moakiee/ae2lt/mixin/PathingCalculationCapMixin.java`
- `src/main/java/com/moakiee/ae2lt/mixin/GridGetMachineNodesMixin.java`
- `src/main/java/com/moakiee/ae2lt/mixin/ControllerValidatorMixin.java`
- `src/main/resources/assets/ae2lt/textures/part/cable/**`
- `src/main/resources/data/ae2lt/recipe/network/cables/**`

26.1.2 核查点：

- AE2 26.1.2 的 pathing/channel 内部类名和字段可能已变，mixin 目标要逐个确认。
- `CableBusBakedModelMixin` 在当前分支疑似替换为 `CableBusModelMixin`，这是版本差异候选，不应直接判缺失。
- 大规模网络、多个控制器、普通 AE2 线缆混接、断线重连、区块加载顺序都需要游戏内验证。

## 07 过载样板、样板供应器、接口与自动合成

main 需要确认的能力：

- 过载样板编码器可把普通样板/输入输出解析成过载样板，支持严格匹配与只按 ID 匹配。
- 过载样板可被 AE2 样板详情解码器识别，部分样板只能由过载样板供应器执行。
- 过载样板供应器和过载 ME 接口均为 36 槽，支持普通模式/无线模式。
- 供应器支持返回库存、回收模式、无线连接目标、分配策略、快速探针、输出过滤。
- 接口支持导入/导出模式、弹出模式、过滤元件、分页、合成卡/模糊卡升级。
- 混入 AE2 Crafting CPU、PatternProviderLogic，以处理过载输出认领、延迟输出、阻塞兼容。

main 关键路径：

- `src/main/java/com/moakiee/ae2lt/item/OverloadPatternItem.java`
- `src/main/java/com/moakiee/ae2lt/item/OverloadPatternEncoderItem.java`
- `src/main/java/com/moakiee/ae2lt/overload/pattern/**`
- `src/main/java/com/moakiee/ae2lt/overload/model/**`
- `src/main/java/com/moakiee/ae2lt/overload/cpu/**`
- `src/main/java/com/moakiee/ae2lt/logic/OverloadedPatternProviderLogic.java`
- `src/main/java/com/moakiee/ae2lt/logic/OverloadedInterfaceLogic.java`
- `src/main/java/com/moakiee/ae2lt/logic/MachineAdapterRegistry.java`
- `src/main/java/com/moakiee/ae2lt/logic/AllowedOutputFilter.java`
- `src/main/java/com/moakiee/ae2lt/menu/OverloadPatternEncoderMenu.java`
- `src/main/java/com/moakiee/ae2lt/menu/OverloadedPatternProviderMenu.java`
- `src/main/java/com/moakiee/ae2lt/menu/OverloadedInterfaceMenu.java`
- `src/main/java/com/moakiee/ae2lt/mixin/PatternProviderLogicMixin.java`
- `src/main/java/com/moakiee/ae2lt/mixin/CraftingCpuLogicMixin.java`

26.1.2 核查点：

- 当前分支最近提交涉及过载处理流体栈延迟处理，需重点验证流体输入/输出样板。
- 样板 NBT、数据组件、AE2 pattern details API 在新版本中可能有结构变化，要用实际样板互转验证。
- 阻塞行为、重复输出、输出不足、返还库存满、目标机器不可访问、无线目标卸载都要测。
- `AdvancedAECompat`、`SmartDoublingCompat`、`AdvancedBlockingCompat`、`AdvCraftingCpu*`、`ECOCraftingCpu*` 在当前分支初筛缺失，可能影响可选兼容和高级 CPU 行为。

## 08 无线频率网络

main 需要确认的能力：

- 无线过载控制器、高级无线过载控制器、无线接收器可绑定频率并形成无线频道连接。
- 频率有名称、颜色、安全等级、密码、成员、权限：所有者/管理员/用户/封禁。
- 支持频率创建、删除、编辑、成员管理、选择/绑定、服务端持久化、客户端缓存同步。
- 支持跨维度设置、加载/未加载设备状态显示、剩余频道统计。
- 频率绑定界面和客户端光标/选择状态清理逻辑完整。

main 关键路径：

- `src/main/java/com/moakiee/ae2lt/grid/WirelessFrequency*.java`
- `src/main/java/com/moakiee/ae2lt/grid/Frequency*.java`
- `src/main/java/com/moakiee/ae2lt/blockentity/WirelessOverloadedControllerBlockEntity.java`
- `src/main/java/com/moakiee/ae2lt/blockentity/AdvancedWirelessOverloadedControllerBlockEntity.java`
- `src/main/java/com/moakiee/ae2lt/blockentity/WirelessReceiverBlockEntity.java`
- `src/main/java/com/moakiee/ae2lt/menu/FrequencyMenu.java`
- `src/main/java/com/moakiee/ae2lt/menu/FrequencyBindingMenu.java`
- `src/main/java/com/moakiee/ae2lt/client/gui/FrequencyScreen.java`
- `src/main/java/com/moakiee/ae2lt/client/FrequencyBindingClient.java`
- `src/main/java/com/moakiee/ae2lt/network/*Frequency*Packet.java`

26.1.2 核查点：

- 频率 saved data 是否正常迁移，服务端启动/停止时 manager 初始化和清理是否完整。
- 成员权限、密码校验、公共/私有/加密三种安全等级必须实测。
- 跨维度、目标卸载、接收器破坏、控制器冲突、频道借用计算要验证。
- 2026-05-09 已按 26.1.2 API 恢复原生过载无线连接工具：`OverloadedWirelessConnectorItem`、`WirelessConnectorUsePacket`、`WirelessConnectorRenderer`、注册、创造标签、语言、item model、闪电装配配方均已补齐。当前恢复范围只覆盖本分支已有的过载样板供应器和过载 ME 接口；`main` 中依赖 AppFlux 供电仪、ExtendedAE Wireless Connector/Hub 的分支因 `Source Code/26.1` 没有对应 26.1 参考模组，暂不移植。
- 验证：`./gradlew.bat compileJava` 通过；仍需游戏内验证选择主机、Ctrl 连片绑定、断开/换面、客户端 overlay、服务端专用启动。

## 09 AppFlux 过载供电仪

main 需要确认的能力：

- 当 AppFlux 加载时注册 `overloaded_power_supply` 方块、方块实体、菜单、screen、配方、loot、指南。
- 过载供电仪接入 ME 网络，可从 AppFlux/ME 能量体系中给绑定目标供电。
- 支持普通/过载模式、无线目标列表、缓冲、活跃票据、目标不可用状态、目标满电/阻塞状态。
- 过载无线连接工具可选择供电仪并绑定机器目标。
- AppFlux 感应卡作为升级卡加入过载样板供应器和过载接口。
- 未安装 AppFlux 时不应注册方块/物品/配方，也不应崩溃。

main 关键路径：

- `src/main/java/com/moakiee/ae2lt/block/OverloadedPowerSupplyBlock.java`
- `src/main/java/com/moakiee/ae2lt/blockentity/OverloadedPowerSupplyBlockEntity.java`
- `src/main/java/com/moakiee/ae2lt/logic/OverloadedPowerSupplyLogic.java`
- `src/main/java/com/moakiee/ae2lt/menu/OverloadedPowerSupplyMenu.java`
- `src/main/java/com/moakiee/ae2lt/client/OverloadedPowerSupplyScreen.java`
- `src/main/java/com/moakiee/ae2lt/logic/energy/AppFluxAccess.java`
- `src/main/java/com/moakiee/ae2lt/logic/energy/AppFluxBridge.java`
- `src/main/java/com/moakiee/ae2lt/logic/energy/WirelessEnergyDistributor.java`
- `src/main/resources/assets/ae2/screens/overloaded_power_supply.json`
- `src/main/resources/data/ae2lt/recipe/lightning_assembly/overloaded_power_supply.json`

当前初筛结论：

- 以上供电仪相关 Java 和资源文件在当前分支相对 `main` 显示为 main-only，疑似尚未移植或被整体改名/替换。
- 这是 P0 缺口，因为 `main` 的注册入口、创造标签、指南、语言和无线连接工具都把它当成独立功能板块。
- 2026-05-09 复核：`Source Code/26.1` 当前只有 AE2、AE2 Omni Cells、Jade、JEI、Powah 可作为 26.1.2 参考；没有 AppFlux 26.1 API/源码。当前分支的 `AppFluxBridge` 仍应保持无硬依赖的降级/禁用形态，过载供电仪和感应卡相关功能暂按“外部 API 缺失，跳过实现”记录。

## 10 JEI、Jade 与可选兼容

main 需要确认的能力：

- JEI 显示所有自定义配方：闪电模拟、闪电装配、过载处理、水晶催化、特斯拉线圈、过载成长、闪电转化、多方块雷劈仪式。
- JEI 支持大数量渲染、闪电自定义 ingredient、配方同步缓存、多方块预览。
- Jade 显示闪电收集器冷却信息。
- 可选兼容配方覆盖 AE2、AppFlux、AdvancedAE、ExtendedAE、MEGA Cells、AE2 Omni Cells、Mekanism 等。
- AE2 JEI Integration 兼容用于显示/转换特定样板信息。

main 关键路径：

- `src/main/java/com/moakiee/ae2lt/integration/jei/**`
- `src/main/java/com/moakiee/ae2lt/integration/jade/**`
- `src/main/java/com/moakiee/ae2lt/logic/AdvancedAECompat.java`
- `src/main/java/com/moakiee/ae2lt/logic/SmartDoublingCompat.java`
- `src/main/java/com/moakiee/ae2lt/logic/AdvancedBlockingCompat.java`
- `src/main/resources/data/ae2lt/recipe/overload_processing/**`
- `src/main/resources/data/ae2lt/recipe/mek/**`

26.1.2 核查点：

- 2026-05-09 已按 Jade 26.1 API 恢复 `LightningCollectorJadeProvider`，闪电收集器冷却提示已重新注册并通过 `compileJava`。`AE2JeiIntegrationCompat`、若干兼容类和高级 CPU mixin 仍需在对应 26.1 参考模组存在时再核查。
- 当前分支新增 `ClientRecipeSyncCache`，可能是 26.1.2 迁移后的 JEI 同步方案，要确认显示等价。
- 缺少可选模组时数据包条件不能报错；安装对应模组时配方应出现。

## 11 研究笔记、神秘元件、无限元件与彩蛋

main 需要确认的能力：

- 研究笔记首次打开生成目标物和九项有序材料清单。
- 研究仪式依赖大气电离仪上方反应场、雷暴凝核和极高压闪电闭合。
- 材料正确但顺序错误时湮灭并可能生成焦黑仪式残片；笔记保留并标记状态。
- 铁砧加催化物可预锁目标。
- 神秘元件单次解析成不同稀有结果，解析后壳体归零。
- Fumo 方块、渲染、掉落、Pigmee 礼物和彩蛋 overlay/packet 行为保留。

main 关键路径：

- `src/main/java/com/moakiee/ae2lt/logic/research/**`
- `src/main/java/com/moakiee/ae2lt/item/ResearchNoteItem.java`
- `src/main/java/com/moakiee/ae2lt/item/FixedInfiniteCellItem.java`
- `src/main/java/com/moakiee/ae2lt/event/PigmeeFumoGiftHandler.java`
- `src/main/java/com/moakiee/ae2lt/blockentity/FumoBlockEntity.java`
- `src/main/java/com/moakiee/ae2lt/client/EasterEgg*.java`
- `src/main/java/com/moakiee/ae2lt/network/EasterEggPacket.java`

26.1.2 核查点：

- 研究笔记和神秘元件高度依赖数据组件/NBT/保存状态，版本迁移后必须做重启验证。
- 仪式材料池涉及大量外部模组物品，缺模组时候选池必须仍然有效。
- 客户端书本内容、tooltip、完成标记和语言键要检查。

## 12 客户端渲染、GUI 与资源迁移

main 需要确认的能力：

- 所有机器 screen 可打开，进度条、能量条、流体槽、输出配置按钮、模式按钮、状态文本显示正确。
- BE renderer：水晶催化器、闪电模拟室、闪电装配室、Fumo、无线连接/线缆渲染等。
- 过载线缆贴图和 cable bus render state 正确，透明色和 16 色均显示。
- AE2 screen JSON 与 Java screen 坐标一致。
- 语言键覆盖所有 tooltip、JEI 文本、GUI 状态、频率界面、研究书页。

main 关键路径：

- `src/main/java/com/moakiee/ae2lt/client/**`
- `src/main/java/com/moakiee/ae2lt/mixin/client/**`
- `src/main/resources/assets/ae2/screens/**`
- `src/main/resources/assets/ae2lt/textures/**`
- `src/main/resources/assets/ae2lt/models/**`
- `src/main/resources/assets/ae2lt/items/**`，仅当前分支存在，需作为资源格式迁移点核查

26.1.2 核查点：

- 当前分支新增多个 `*RenderState` 类，属于 26.1.2 客户端渲染 API 迁移线索。
- main 的 `models/item` 与当前分支的 `items` 可能是版本格式差异；需要实际进游戏或资源加载日志验证。
- GUI sprite 路径迁移后，slot 背景、按钮图标、流体槽图标不能缺图。

## 第一轮文件差异线索

以下只是文件级初筛，不等同于最终功能结论。

### 当前分支相对 main 疑似缺失或需找替代的文件

P0：

- `src/main/java/com/moakiee/ae2lt/block/OverloadedPowerSupplyBlock.java`
- `src/main/java/com/moakiee/ae2lt/blockentity/OverloadedPowerSupplyBlockEntity.java`
- `src/main/java/com/moakiee/ae2lt/logic/OverloadedPowerSupplyLogic.java`
- `src/main/java/com/moakiee/ae2lt/menu/OverloadedPowerSupplyMenu.java`
- `src/main/java/com/moakiee/ae2lt/client/OverloadedPowerSupplyScreen.java`
- `src/main/java/com/moakiee/ae2lt/logic/energy/AppFluxAccess.java`
- `src/main/resources/assets/ae2/screens/overloaded_power_supply.json`
- `src/main/resources/assets/ae2lt/blockstates/overloaded_power_supply.json`
- `src/main/resources/assets/ae2lt/models/item/overloaded_power_supply.json`
- `src/main/resources/data/ae2lt/recipe/lightning_assembly/overloaded_power_supply.json`
- `src/main/resources/data/ae2lt/loot_table/blocks/overloaded_power_supply.json`

P1：

- `src/main/java/com/moakiee/ae2lt/integration/jei/compat/ae2jeiintegration/AE2JeiIntegrationCompat.java`
- `src/main/java/com/moakiee/ae2lt/logic/AdvancedAECompat.java`
- `src/main/java/com/moakiee/ae2lt/logic/AdvancedBlockingCompat.java`
- `src/main/java/com/moakiee/ae2lt/logic/SmartDoublingCompat.java`
- `src/main/java/com/moakiee/ae2lt/mixin/AdvCraftingCpuAccessor.java`
- `src/main/java/com/moakiee/ae2lt/mixin/AdvCraftingCpuLogicMixin.java`
- `src/main/java/com/moakiee/ae2lt/mixin/ECOCraftingCpuAccessor.java`
- `src/main/java/com/moakiee/ae2lt/mixin/ECOCraftingCpuLogicMixin.java`
- `src/main/java/com/moakiee/ae2lt/util/MixinReflectionSupport.java`

已处理：

- `src/main/java/com/moakiee/ae2lt/item/OverloadedWirelessConnectorItem.java`（恢复，原生供应器/接口范围）
- `src/main/java/com/moakiee/ae2lt/client/WirelessConnectorRenderer.java`（恢复，使用 26.1 渲染事件/API）
- `src/main/java/com/moakiee/ae2lt/network/WirelessConnectorUsePacket.java`（恢复，使用 26.1 网络 payload 注册）
- `src/main/java/com/moakiee/ae2lt/integration/jade/LightningCollectorJadeProvider.java`（恢复，使用 Jade 26.1 provider API）

资源/数据包需复核：

- `src/main/resources/data/ae2lt/recipe/assembler/overload_processor.json`
- `src/main/resources/data/ae2lt/recipe/cutter/unoverloaded_circuit_board.json`
- `src/main/resources/data/ae2lt/recipe/lightning_assembly/overloaded_wireless_connect_tool.json`（2026-05-09 已恢复）
- `src/main/resources/data/ae2lt/recipe/overload_processing/aae_*.json`
- `src/main/resources/data/ae2lt/recipe/overload_processing/appflux_*.json`
- `src/main/resources/data/ae2lt/recipe/overload_processing/eae_*.json`
- `src/main/resources/data/ae2lt/recipe/overload_processing/mega_*.json`
- `src/main/resources/data/ae2lt/recipe/overload_processing/neoeco_*.json`
- `src/main/resources/data/ae2lt/recipe/crystal_catalyzer/*energized*.json`
- `src/main/resources/data/ae2lt/recipe/crystal_catalyzer/*entro*.json`
- `src/main/resources/META-INF/accesstransformer.cfg`

### 当前分支新增、可能属于 26.1.2 迁移替代的文件

- `src/main/java/com/moakiee/ae2lt/client/*RenderState.java`
- `src/main/java/com/moakiee/ae2lt/integration/jei/ClientRecipeSyncCache.java`
- `src/main/java/com/moakiee/ae2lt/machine/common/AutomationItemResourceHandler.java`
- `src/main/java/com/moakiee/ae2lt/machine/common/MachineEnergyStorage.java`
- `src/main/java/com/moakiee/ae2lt/machine/overloadfactory/recipe/OverloadProcessingFluidStackTemplate.java`
- `src/main/java/com/moakiee/ae2lt/menu/IconAppEngSlot.java`
- `src/main/java/com/moakiee/ae2lt/mixin/client/CableBusModelMixin.java`
- `src/main/java/com/moakiee/ae2lt/util/SavedDataCodecs.java`
- `src/main/resources/assets/ae2lt/items/**`
- `src/main/resources/assets/ae2lt/textures/gui/sprites/block/slot/**`

## 建议执行顺序

1. 先做 P0 入口核查：编译、启动、注册、能力、菜单、配方序列化器、AE2 key/storage。
2. 再核查 03/04/06/07，因为这些是主玩法和自动合成闭环。
3. 单独核查 09 AppFlux 供电仪：当前文件级初筛最像真实缺口。
4. 然后核查 08 无线频率网络，重点是权限/持久化/跨维度/频道统计。
5. 最后核查 10/11/12 的兼容、彩蛋、客户端资源和指南完整性。

## 后续文档拆分建议

如果逐板块核查内容变长，建议在 `else/plan` 下继续拆分：

- `01-基础注册与资源核查.md`
- `02-闪电存储与能力核查.md`
- `03-闪电采集转化天气核查.md`
- `04-核心配方机器核查.md`
- `05-过载材料水晶成长核查.md`
- `06-过载网络频道核查.md`
- `07-过载样板自动合成核查.md`
- `08-无线频率网络核查.md`
- `09-AppFlux过载供电仪核查.md`
- `10-兼容与客户端资源核查.md`
