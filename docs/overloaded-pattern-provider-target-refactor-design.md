# 过载样板供应器 Target 重构设计

## 1. 目标

重构 `OverloadedPatternProviderLogic`，在不改变现有发配、返回、阻挡、能耗和存档语义的前提下：

- 让普通目标与无线连接使用同一套单目标执行逻辑；
- 通过继承和对象所有权消除 `WirelessConnection -> TargetAddress -> TargetKey -> ProviderTarget` 一类热路径转换；
- 将目标执行与调度策略分离；
- 控制拆分类数量，只抽取实际复用超过两处或本身足够复杂的算法；
- 本轮不单独重构过载 ME 接口和过载供电仪。

## 2. Target 类型层次

采用三层继承：

```text
TargetAddress
    └─ ProviderTarget
           └─ WirelessConnection implements WirelessConnectionRef
```

### 2.1 `TargetAddress`

`TargetAddress` 只定义不可变的物理地址和地址比较语义：

```java
abstract class TargetAddress {
    private final ResourceKey<Level> dimension;
    private final BlockPos pos;
    private final Direction boundFace;

    protected TargetAddress(
            ResourceKey<Level> dimension,
            BlockPos pos,
            Direction boundFace) {
        this.dimension = dimension;
        this.pos = pos.immutable();
        this.boundFace = boundFace;
    }

    public final ResourceKey<Level> dimension();
    public final BlockPos pos();
    public final Direction boundFace();

    public final boolean sameTarget(
            ResourceKey<Level> otherDimension,
            BlockPos otherPos);

    @Override
    public final boolean equals(Object other);

    @Override
    public final int hashCode();
}
```

比较语义固定如下：

- `equals/hashCode` 比较 `dimension + pos + boundFace`，表示具体机器输入面；
- `sameTarget` 只比较 `dimension + pos`，继续用于“一台机器只能有一条无线连接”的替换和去重规则；
- 子类不得把别名、调度状态或缓存加入地址身份。

### 2.2 `ProviderTarget`

`ProviderTarget extends TargetAddress`，增加供应器单目标执行能力和目标实际拥有的运行态：

```java
class ProviderTarget extends TargetAddress {
    private final ProviderTargetRuntime runtime;

    public final PushResult pushPattern(...);
    public final ReturnResult returnOutputs(...);
    public final OverflowResult flushOverflow(...);
}
```

`ProviderTargetRuntime` 保存：

- 当前方块实体身份；
- `MachineAdapter` 缓存；
- AE2 `PatternProviderTarget` 缓存；
- 最后一次成功发配的样板；
- 当前目标拥有的 overflow。

这些数据属于物理目标，不属于普通或无线调度器。

### 2.3 `WirelessConnection`

将供应器当前的 `WirelessConnection` record 改为不可变 final class：

```java
final class WirelessConnection
        extends ProviderTarget
        implements WirelessConnectionRef {

    @Override
    public CompoundTag toTag();

    public static WirelessConnection fromTag(...);
}
```

`WirelessConnectionRef` 暂时继续声明：

```java
ResourceKey<Level> dimension();
BlockPos pos();
Direction boundFace();
default boolean sameTarget(...);
CompoundTag toTag();
```

继承自 `TargetAddress` 的访问器和 `sameTarget` 会满足并覆盖接口契约。暂不删除 Thunderbolt 接口中的声明，以避免破坏现有实现和二进制兼容。

## 3. 对象所有权与生命周期

### 3.1 普通目标

- `ProviderNormalDispatch` 持有并复用普通 `ProviderTarget`；
- 相邻拓扑不变时不重新创建 target；
- 每个具体 `dimension + pos + face` 对应一个普通 target 对象。

### 3.2 无线目标

- 方块实体的无线连接列表持有正式 `WirelessConnection`；
- `ProviderWirelessDispatch` 直接调度这些对象，不创建替代地址或运行时 target；
- `WirelessConnection` 同时承担无线配置、NBT 身份和供应器单目标执行。

### 3.3 Overflow 所有权

- overflow 直接属于产生它的 `ProviderTarget`；
- 无线连接被删除但仍有 overflow 时，由 overflow 队列继续强引用原 `WirelessConnection`；
- 该对象不再参与新发配，但仍可执行 `flushOverflow`；
- overflow 清空且连接已删除后才释放对象；
- 普通模式切换到无线模式时，普通 pending overflow 仍由原普通 target 持有；反向切换同理。

### 3.4 方块实体替换

目标位置的方块实体发生替换时：

- 保留 `ProviderTarget` 对象和它拥有的 overflow；
- 清除旧的方块实体引用、adapter、capability 和最后成功样板；
- 下一次操作重新解析目标能力。

### 3.5 NBT 恢复

读取顺序固定为：

1. 恢复无线连接列表并创建正式 `WirelessConnection`；
2. 恢复 overflow；
3. 按完整 `dimension + pos + face` 查找已存在的连接对象；
4. 找到时直接将 overflow 挂到该对象；
5. 找不到时创建仅由 overflow 持有的 orphan `WirelessConnection`；
6. 后续重新添加同地址连接时复用 orphan 对象，避免产生两份 `ProviderTargetRuntime`。

该匹配只发生在加载、连接编辑和拓扑变化时，不进入 push 热路径。

## 4. 调度边界

`ProviderTarget` 负责执行并汇报事实：

- `pushPattern`；
- `returnOutputs`；
- `flushOverflow`；
- 实际接管 copies 数；
- 是否完整插入；
- 是否产生或推进 overflow；
- 目标是否失效或暂时拒绝。

`ProviderTarget` 不决定：

- cooldown；
- probe；
- 按样板 penalty；
- fairness；
- due tick；
- 下一次重试时间。

`ProviderNormalDispatch` 管理：

- 普通相邻目标发现和轮转；
- copies 分配；
- 普通公平计数；
- 按 `(ProviderTarget, IPatternDetails)` 的 penalty；
- 普通 return 与 overflow 重试时间。

`ProviderWirelessDispatch` 管理：

- 活跃无线连接；
- ready 队列；
- 目标 cooldown 与提前 probe；
- 按 `(WirelessConnection, IPatternDetails)` 的 penalty；
- 公平分配；
- 无线 return 与 overflow 重试时间；
- 无线时间轮。

两种 Dispatch 最终都直接调用 `ProviderTarget`：

```java
dispatch(ProviderTarget target, IPatternDetails pattern, ...);
```

无线调用无需转换：

```java
dispatch(wirelessConnection, pattern, ...);
```

## 5. 供应器整体结构

主体收敛为：

- `OverloadedPatternProviderLogic`
  - AE2 外观和生命周期；
  - 样板库存与可用样板目录；
  - return inventory；
  - 全局锁定和主产物解锁规则；
  - 模式、升级卡、能耗和 NBT 入口；
  - AE2 Accessor 调用；
  - `WirelessEnergyDistributor`。
- `TargetAddress`
- `ProviderTarget`
- `ProviderNormalDispatch`
- `ProviderWirelessDispatch`

不增加含义不明确的 `ProviderStateStorage`，也不增加 `ProviderTargets` registry 或 Route 包装层。

## 6. 算法类控制

只有满足“实际复用超过两处”或“实现本身较大且需要独立测试”时才抽成算法类。

保留：

- `DueTaskQueue`：penalty、return、overflow 等多处使用；
- `DispatchFairnessScheduler`：实现和状态足够复杂；
- `WirelessEnergyDistributor`：供应器、ME 接口、供电仪三处共用。

折回实际所有者，不单独成类：

- `ReadyDispatchQueue`；
- `PatternDispatchPenaltyTracker`；
- `AdaptiveRetryPolicy`；
- `ProviderDispatchPolicy`；
- `AutoReturnScheduler`；
- `AdaptiveBatchRamp`；
- 泛型 `TimingWheel`。

批量 ramp 由 `ProviderTarget.pushPattern` 内部统一实现；普通与无线 Dispatch 只决定为当前目标分配多少 copies。

无线时间轮保留为 `ProviderWirelessDispatch` 内部实现。过载接口 I/O 时间轮与能量 int-index wheel 的数据结构和语义不同，本轮不强行统一。

## 7. 过载接口与过载供电仪

本轮不单独重构二者：

- 过载 ME 接口继续维护自己的普通/无线存储 I/O 状态；
- 过载供电仪继续使用现有能量目标、ticket 轮转和普通/过载模式；
- 三者继续共用 `WirelessEnergyDistributor`；
- 二者的无线连接暂不继承供应器专用的 `ProviderTarget`。

以后如需统一，可在不改变本方案的情况下扩展为：

```text
TargetAddress
    ├─ ProviderTarget
    │      └─ ProviderWirelessConnection
    ├─ InterfaceTarget
    │      └─ InterfaceWirelessConnection
    └─ PowerTarget
           └─ PowerWirelessConnection
```

## 8. 兼容性要求

- 保持现有无线连接 NBT 字段不变；
- 保持本地与无线 overflow NBT 不变；
- 保持旧 `sendList` 恢复语义；
- 保持 return inventory 和解锁规则；
- 保持无线连接以 `dimension + pos` 去重、以 face 更新的行为；
- 保持 `WirelessConnectionRef` 公共接口兼容；
- 不改变 `WirelessEnergyDistributor` 的批量预取与分配语义。

## 9. 测试与验收

### Target

- `TargetAddress` 的访问器、不可变 `BlockPos`、`equals/hashCode` 和 `sameTarget`；
- 父类与 `WirelessConnection` 在相同完整地址下 equality 一致；
- face 不同时 `equals` 为 false，但 `sameTarget` 为 true；
- 方块实体替换后缓存失效而 overflow 保留。

### 发配与返回

- 普通与无线均调用同一套单份 push；
- batch ramp 只有一份实现；
- 普通与无线均调用同一套 `returnOutputs`；
- 部分插入、sendList 阻塞和 overflow 恢复；
- 普通阻挡、同样板继续发配和红石阻挡。

### 所有权

- 删除带 overflow 的无线连接不会丢物；
- overflow 清空后 orphan target 可以释放；
- NBT 恢复不会为同一无线连接创建两份 runtime；
- 重新添加 orphan 地址时复用原对象；
- 普通/无线模式切换不会错误丢弃另一模式的 pending overflow。

### 调度与性能

- cooldown、probe、penalty 不进入 target 执行代码；
- 同一 target 的不同样板 penalty 相互隔离；
- 无线空闲时不逐 tick 扫描全部连接；
- push 热路径不创建地址转换对象、不查询 target registry；
- 批量 ramp 保持 `O(log copies)`。

### 附带回归

- 过载 ME 接口普通/无线存储 I/O；
- 过载供电仪普通/过载能量传输；
- 三个设备的 `WirelessEnergyDistributor` 行为。

## 10. 实施顺序

1. 将当前未完成 WIP 保存到本地备份提交；
2. 从执行重构时的最新 `dev/1.2` 干净实现重新开始；
3. 引入 `TargetAddress -> ProviderTarget -> WirelessConnection` 层次；
4. 将普通与无线单目标执行迁移到 `ProviderTarget`；
5. 实现对象所有权、orphan overflow 和 NBT 恢复；
6. 分离普通与无线 Dispatch；
7. 合并批量 ramp、return 和 overflow 执行路径；
8. 删除此前产生的过细辅助类；
9. 运行全部单元测试、集成测试和游戏启动回归。
