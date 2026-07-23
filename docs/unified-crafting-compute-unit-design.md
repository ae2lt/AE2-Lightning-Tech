# 天枢超算阵列与天枢物质扭曲矩阵统一计算单元设计

## 1. 文档目的

本文定义天枢超算阵列（AE2 合成 CPU）与天枢物质扭曲矩阵共用的计算单元模型、主核心增幅规则、数值目标、结构限制和执行层预算语义。

本文同时作为当前实现契约和后续调整依据。正文中的公式、结构限制、执行预算与状态字段已在当前代码中落地；后续修改实现时必须同步更新本文、玩家指南和验收测试。

## 2. 设计目标

统一后的计算系统必须满足：

1. 天枢和矩阵使用同一套单元聚合、主核心等级和增幅模型。
2. 同一种外围单元只提供固定的原始贡献，不能根据主核心类型自行切换数值。
3. 主核心负责将原始贡献转换为最终容量、CPU 成功发配预算、每任务总发配量和矩阵逻辑吞吐。
4. 成功发配预算始终只接受一维增幅；过载主核心的二维能力只作用于容量和总发配量。
5. 增幅单元在量子和过载主核心下都允许最多安装 15 个。
6. 发配单元没有数量硬限制，但最终发配预算必须有主核心上限和全局服务器保护上限。
7. 容量、发配和增幅共同占用有限核心槽，形成容量、并行和批量吞吐之间的取舍。
8. 矩阵只计算温度修正后的 `operationsPerTick`，不维护玩家可配置的 `D`。
9. 所有矩阵共享固定的 provider 调用熔断；多维能力也不能绕过该服务器保护上限。

目标性能区间如下：

| 主核心 | CPU 发配预算 | 含 batch 的目标吞吐 | CPU 推荐容量区间 |
|---|---:|---:|---:|
| 基准 | 128–512/t | 约 500–1,000/t | 约 1–64 MiB |
| 量子 | 约 800–3,000/t | 约 3,000–10,000/t | 约 256 MiB–8 GiB |
| 过载 | 约 6,000–16,384/t | 约 1–4 Mi/t | 不低于 200 GiB |
| 多维 | CPU 发配预算不超过 16,384/t | 逻辑无限 | 无限 |

这里的吞吐表示规划中样板 copies 的理论执行上限，不表示加工设备能够在同一 tick 完成对应配方。实际吞吐仍受样板供应器、加工设备、输入库存、能源、输出空间和第三方设备能力限制。

## 3. 单元种类

### 3.1 主核心

统一计算器定义四种主核心等级：

1. 基准主核心。
2. 量子主核心。
3. 过载主核心。
4. 多维主核心。

天枢与矩阵共用等级定义和计算配置。天枢将特殊无限等级注册为“多维主核心”，矩阵当前将同一 `MULTIDIMENSIONAL` envelope 暴露为“创造主核心”；两者在计算层都是第四档特殊无限模式。主核心方块是否物理共用由注册和美术方案决定，但不能再维护两套互不相干的数值公式。

矩阵创造主核心只用于开发、测试或创造模式，不计入正常科技树；如果以后增加可合成的矩阵多维主核心，应直接复用同一等级配置，而不是新增第五套公式。

### 3.2 外围单元

参与统一算力公式的外围计算单元只保留五种逻辑类型：

| 单元 | 固定原始贡献 | 天枢 | 矩阵 |
|---|---|---:|---:|
| 发配/线程单元 | `dispatchUnits + 固定能力点` | 并行单元固定 `+1` | T1 线程单元 `+1`，T2 `+2` |
| 增幅单元 | `amplifierUnits + 1` | 可用 | 可用 |
| 容量单元 | `storageUnits + 1` | 可用 | 禁止 |
| 热控单元 | `coolingUnits + 固定能力点` | 禁止 | T1 `+1`，T2 `+2` |
| 空白单元 | 无 | 可用 | 可用 |

外围单元类不得包含类似 `if (mainCore == OVERLOAD)` 的数值分支。矩阵 T1/T2 线程与热控单元可以具有不同但固定的原始能力点；该能力不随主核心改变。扫描器只汇总原始能力点，所有等级换算集中在主核心配置和统一计算器中。

天枢另有两种不参与统一算力公式的功能存储单元：闭环样板仓和闭环种子存储器。它们仍然是外部物理存储，安装在外壳的 17 个冷却兼容位并替代对应位置的相变冷却单元，不占用核心舱的 26 个外围槽，也不计入 `P`、`S`、`N`，不提供派发、合成容量或增幅。主核心内置的是闭环分析与执行能力，不是这两类数据存储的容量。

不再需要以下独立计算单元：

- 矩阵 T1/T2 线程单元的独立等级公式；它们只保留固定 `1/2` 原始能力点差异。
- 矩阵 T1/T2 批量增幅单元；矩阵改为直接使用与天枢相同的天枢增幅单元。
- 天枢专用且与矩阵语义重复的另一套并行或增幅公式。
- 库存维持核心。
- 另行提供算力的闭环计算核心或矩阵闭环处理器。

库存维持、闭环分析与闭环执行继续作为主核心内置能力；天枢闭环样板仓和闭环种子存储器继续作为外部功能存储。端口继续只负责网络链接和请求转发。

## 4. 统一术语

设：

```text
P = 发配能力点总和；天枢并行单元与矩阵 T1 线程单元各为 1，矩阵 T2 线程单元为 2
S = 容量单元数量
N = 增幅单元数量
R = 1 + N
Gd = 主核心对发配单元的一维增幅
Gs = 主核心对容量单元的增幅
D = 物理 CPU 每 tick 共享的成功 provider 发配次数；矩阵不维护此状态
Gt = 主核心对每任务总发配量的一维或二维增幅
T = 每个虚拟 CPU 每 tick 最多成功发出的 pattern copies
η = 主机运行效率；天枢恒为 1，矩阵由温度决定
M = 矩阵温度修正后的 operationsPerTick
providerCallLimit = 矩阵每 tick 最多接受的 provider 调用次数
```

正常有限主核心至少需要一个发配单元，即 `P >= 1`。多维主核心使用特殊预算，不依赖外围发配单元。

## 5. 增幅模型

量子和过载主核心都允许安装最多 15 个增幅单元：

```text
0 <= N <= 15
1 <= R <= 16
```

设主核心载波宽度 `A = 2R`。发配单元始终只经过一维载波；容量在过载主核心下经过二维地址网络：

```text
基准：Gd = 1，Gs = 1
量子：Gd = A，Gs = A
过载：Gd = A，Gs = A²
多维：特殊无限预算
```

满增幅时：

```text
N = 15
R = 16

量子：Gd = 32，Gs = 32
过载：Gd = 32，Gs = 1024
```

`1024` 只用于过载容量地址网络。过载发配仍为一维 `Gd=32`，避免一个发配单元直接顶到 16,384，使发配单元失去配装意义。

基准主核心不使用增幅单元。多维主核心本身已经是特殊无限模式，也不需要增幅单元。两种主核心下放入增幅单元应当被结构校验拒绝，而不是静默浪费。

## 6. 发配预算

每点发配能力固定提供 128 点原始发配：

```text
rawDispatch = 128 × P
D = min(rawDispatch × Gd, tierDispatchCap)
```

主核心发配上限为：

| 主核心 | `tierDispatchCap` |
|---|---:|
| 基准 | 512 |
| 量子 | 3,072 |
| 过载 | 16,384 |
| 多维 | 16,384 CPU 发配操作保护 |

发配单元没有数量硬限制：

- 玩家可以继续安装发配单元。
- 超过当前主核心发配上限后不再提高 `D`。
- 结构不能因为发配单元过多而失效。
- 控制器界面必须显示原始发配、有效发配和“已达到发配上限”。

这个规则形成增幅与发配单元之间的替代关系：

- 增幅较少时，需要更多发配单元达到目标 `D`。
- 增幅较高时，一个或少量发配单元即可达到上限。
- 增幅单元本身占用核心槽，因此提高增幅仍然具有结构成本。

`D` 的严格语义是同一物理 CPU 每 tick 允许完成的成功 provider 发配次数。普通样板成功一次消费 `1 D`；一次 batch 无论接受多少 copies，只要接受量大于零也只消费 `1 D`。输入不足、能源不足、provider 返回全部 leftover、返回 `false` 或抛异常都不消费玩家的 `D`。

天枢把一个物理 CPU 拆成多个时间轮虚拟 CPU 时，`D` 由物理 CPU 共享；`T` 则为每个虚拟 CPU 各自拥有的一份每 tick 总发配量。调度器只能从一份共享 `remainingD` 扣除成功发配，但必须为每个虚拟 CPU 单独维护 `remainingT`，不能把 `T` 再做全局争抢。

失败调用的服务端成本由调度器去重承担，不能转嫁到玩家的成功额度。每个物理 CPU 每 tick 对相同 `(canonical provider pattern identity, provider identity)` 最多允许失败一次；包装样板先通过 provider lookup delegate 展平，避免同一底层样板借不同包装重复失败。第一次实际发配失败后，该组合从本 tick 候选集中移除。输入、能源或种子在调用 provider 前不足不属于 provider 失败。

## 7. CPU 容量

主核心提供最低工作容量：

| 主核心 | 内置容量 |
|---|---:|
| 基准 | 1 MiB |
| 量子 | 256 MiB |
| 过载 | 64 GiB |
| 多维 | 无限 |

每个容量单元固定提供 64 MiB 原始容量，并使用容量增幅 `Gs`：

```text
rawExternalStorage = 64 MiB × S
C = coreInternalStorage + rawExternalStorage × Gs
```

所有有限主核心都不设置容量硬上限。容量只受核心舱物理槽位、增幅数量和 `long` 饱和保护限制。平衡表中的容量是推荐配置区间，不是结构能够达到的绝对最大值。

过载 CPU 的平衡验收仍要求目标配置能够提供至少 200 GiB。是否允许低于 200 GiB 的过载结构形成由最终实现决定；无论结构是否形成，低容量配置都不能被宣传为达到过载设计目标。

满增幅示例：

```text
量子：
Gs = 32
每个容量单元 = 64 MiB × 32 = 2 GiB
256 MiB 内置 + 4 × 2 GiB = 8.25 GiB

过载：
Gs = 1024
每个容量单元 = 64 MiB × 1024 = 64 GiB
64 GiB 内置 + 3 × 64 GiB = 256 GiB
```

基准主核心采用同一容量单元后会形成较大的初始档位跳跃：

```text
没有容量单元：1 MiB
一个容量单元：65 MiB
```

继续增加容量单元会继续线性增加容量，不会在 64 MiB 停止。一个发配单元与 25 个容量单元组成的极端基准 CPU 可以达到约 1.56 GiB，但其发配预算只有 128/t，不能替代高阶主核心。

不增加只提供 1 MiB 的 T1 容量单元。该单元在满增幅量子主核心下也只提供 32 MiB，在满增幅过载主核心下只提供 1 GiB，无法承担高阶 CPU 的容量需求，最终仍需保留 64 MiB 容量单元。为了基准档的细粒度容量额外增加一种几乎只在低阶使用的外围单元，会破坏精简后的五种外围单元模型。

容量没有 TPS 风险，因此不需要像发配预算一样设置等级硬上限。实现只需要使用饱和加法和饱和乘法，溢出时固定为 `Long.MAX_VALUE`。

## 8. 每任务总发配量

执行层不再暴露单次 batch 宽度 `q`，也不再使用平方根操作计费。增幅网络只负责从 `D` 计算每个虚拟 CPU 独占的每 tick 总发配量 `T`：

```text
基准：Gt = 2
量子：Gt = R
过载：Gt = R²
多维：Gt = unbounded
```

量子主核心将增幅通道组成一维总线；过载主核心将相同通道组成二维总发配网络。`Gt` 只是计算 `T` 的内部系数，不限制单次 batch；单次 batch 可以使用该虚拟 CPU 当前剩余的全部 `T`，仍受任务剩余量、provider capacity、输入和能源限制。

主核心具有每个虚拟 CPU 的逻辑 copies 上限：

| 主核心 | `tierCopyCap` |
|---|---:|
| 基准 | 1,024 |
| 量子 | 10,240 |
| 过载 | 4,194,304 |
| 多维 | 逻辑无限 |

统一吞吐公式：

```text
effectiveDispatch = floor(D × η)
T = min(effectiveDispatch × Gt, tierCopyCap)
```

该公式只用于 CPU 执行器。矩阵不持有可消费的 `D`，而是直接生成 `operationsPerTick`。

## 9. 天枢物质扭曲矩阵温度层

矩阵扫描同样的 `P`、`N` 和主核心等级，但运行时只维护逻辑 copies 预算，不维护 CPU 的 `D`。量子和过载矩阵在纯计算阶段复用 `Gd` 和 `Gt`；稳态矩阵使用独立的线程单元算力表，以体现 T2 单元的等级收益：

```text
stableBaseOperations = min(1024 × T1数量 + 3584 × T2数量, 4096)
quantumBaseOperations = min(128 × P × Gd × Gt, 122880)
overloadBaseOperations = min(128 × P × Gd × Gt, 4194304)
M = floor(modeBaseOperations × thermalEfficiency)
```

其中 `tierCopyCap` 仍是 CPU 执行器的上限。矩阵专属上限不得写回 `ComputeTier`，否则会同时改变天枢 CPU 的复制预算。

热控单元始终只提供固定能力点：T1 为 1，T2 为 2；距离衰减和热量公式属于矩阵主机，不属于热控单元自身。

建议保留以下热设计原则：

- 基准和量子矩阵保持较低温度时效率较高。
- 过载矩阵在约 50% 温度附近达到最高效率。
- 矩阵实际接受的逻辑 operations 产生热量；batch 只是降低 provider 调用次数，不能重复计算同一份热量。
- 更多冷却单元使温度更稳定，但会延长过载矩阵预热时间。
- 更多发配和增幅单元会占用冷却或空白槽，形成吞吐与热稳定之间的取舍。
- 多维矩阵忽略温度，但实际 provider 调用仍受固定熔断保护。

温度曲线的具体常量可以独立平衡，但不能重新改变 CPU 的 `Gt`、`tierCopyCap` 和逻辑 operation 语义。矩阵专属上限属于矩阵运行预算，不替代 CPU 等级上限。

### 9.1 Provider 调用熔断

矩阵的百万级 `M` 不能直接视为允许百万次 Java provider 调用。第三方 CPU 可能具有极高并行但不支持 batch，此时每份样板都会调用一次 `pushPattern`。矩阵必须在共享 cluster 中额外维护：

```text
MAX_PROVIDER_CALLS_PER_TICK = 16,384
remainingOperations = M
remainingProviderCalls = MAX_PROVIDER_CALLS_PER_TICK
```

矩阵的 provider call 熔断不是玩家的 `D`：每次真正进入矩阵 provider 调用，无论最终接受量是否为零，都消费一个内部 provider call；只有成功接受的 copies 才消费 operations。一次 `pushBatch` 无论接受多少 copies，只发生一个 provider call，并按实际接受 copies 消费 operations。`isBusy` 在任一预算耗尽时返回 true，`getBatchCapacity` 在 provider call 预算耗尽时返回 0。

该限制具有以下约束：

- 它是所有矩阵等级共用的服务器安全保险丝，不是玩家算力属性。
- 它不受主核心、发配单元、增幅单元、温度或第三方 API 放大。
- 它必须存放在共享 `MatrixCraftingCluster`；端口、控制器适配层或第三方接口包装都不能各自获得一份预算。当前结构只允许一个物理端口。
- 不支持 batch 的 CPU 因而最多从同一矩阵接受 16,384 份样板/t；要使用百万级矩阵吞吐必须使用 batch。
- 多维或 unbounded 模式只能绕过逻辑 copies 上限，不能绕过 provider 调用熔断。

## 10. 单元限制

### 10.1 通用限制

- 每台结构必须恰好有一个合法主核心。
- 量子和过载主核心都允许最多 15 个增幅单元。
- 第 16 个增幅单元使配置无效，不能只忽略多余部分。
- 发配单元至少一个，没有数量硬限制；最终 `D` 按主核心上限截断。
- 空白单元没有数量限制。
- 单元放入不支持的主机时配置无效，例如矩阵使用容量单元、天枢使用冷却单元。
- 基准和多维主核心不接受增幅单元。
- 多维主核心不需要容量或发配单元；这些无意义单元应当被拒绝，外围位置使用空白单元填充。

### 10.2 天枢

- 主核心占核心舱中央位置。
- 其余 26 个核心舱位置由发配、增幅、容量和空白单元填充。
- 外壳的 17 个冷却兼容位可安装相变冷却单元、闭环样板仓或闭环种子存储器；闭环存储不计入核心舱单元数量。
- 容量单元没有数量硬限制，最终容量只受物理槽位和数值饱和保护限制。
- 高增幅减少达到发配和容量目标所需的专用单元数量；低增幅配置需要更多发配或容量单元。

库存维持、闭环分析、闭环执行和无载体运行状态本身不占用外围核心位置。闭环样板与种子的外部物理存储占用冷却兼容位，数量分别决定闭环样板容量与可用种子存储。

### 10.3 矩阵

- 主核心占矩阵核心舱中央位置。
- 其余 80 个位置由发配、增幅、冷却和空白单元填充。
- 冷却单元没有数量硬限制；过多冷却造成预热困难本身就是运行代价。
- 矩阵不能安装容量单元。

## 11. 配置示例

### 11.1 基准 CPU

```text
P = 2
N = 0
Gd = 1

D = 128 × 2 = 256
Gt = 2
T = 512/t
```

增加到四个发配单元后：

```text
D = 512
T = min(512 × 2, 1024) = 1024/t
```

### 11.2 满增幅量子 CPU

```text
N = 15
R = 16
Gd = 32
Gs = 32
P = 1
S = 4

D = min(128 × 1 × 32, 3072) = 3072
C = 256 MiB + 4 × 64 MiB × 32 = 8.25 GiB
Gt = 16
T = min(3072 × 16, 10240) = 10240/t
```

### 11.3 满增幅过载 CPU

```text
N = 15
R = 16
Gd = 32
Gs = 1024
P = 4
S = 3

D = min(128 × 4 × 32, 16384) = 16384
C = 64 GiB + 3 × 64 GiB = 256 GiB
Gt = 256
T = 16384 × 256 = 4,194,304/t
```

外围槽占用：

```text
15 增幅 + 4 发配 + 3 容量 = 22 / 26
```

剩余 4 个位置可使用空白单元，也可以继续增加容量；继续增加发配单元不会突破发配硬上限。

### 11.4 较低增幅过载 CPU

```text
N = 7
R = 8
Gd = 16
Gs = 256
P = 8
S = 9

D = min(128 × 8 × 16, 16384) = 16384
C = 64 GiB + 9 × 16 GiB = 208 GiB
Gt = 64
T = 16384 × 64 = 1,048,576/t
```

该配置使用较多发配和容量单元换取较少增幅，总计占用 `7+8+9=24` 个外围槽，仍然达到过载 CPU 的容量和最低吞吐目标。

## 12. 统一代码契约

扫描层只生成原始单元统计：

```java
public record ComputingUnitTotals(
        int dispatchUnits,
        int amplifierUnits,
        int storageUnits,
        int coolingUnits) {
}
```

主核心等级只描述能力与限制。当前实现使用 `ComputeTier` 枚举承载这些只读配置，而不是额外维护一份重复的 profile record：

```java
public enum ComputeTier {
    BASELINE(...),
    QUANTUM(...),
    OVERLOAD(...),
    MULTIDIMENSIONAL(...);

    // maxAmplifierUnits, dispatchCap, internalStorage,
    // copyCap, multidimensional
}
```

统一计算器生成执行层只读预算：

```java
public record CraftingComputeEnvelope(
        long storageBytes,
        int successfulDispatchesPerTick,
        long maxCopiesPerTick,
        boolean unboundedBatch,
        boolean dispatchCapped) {
}
```

天枢 CPU 用 `CraftingComputeEnvelope` 配置自己的时间轮池，并通过 Thunderbolt 的 `ExtendedCraftingCpuCluster` / `ExtendedCraftingCpuClusterProvider` 公共接口注册到 AE2。第三方模组注册自己的 CPU 时实现这组公共生命周期接口即可；如需复用本数值模型，可以自行构造相同预算对象，但不依赖天枢控制器或天枢方块实体。

矩阵只使用不可变的逻辑吞吐快照：

```java
public record MatrixComputeEnvelope(
        long operationsPerTick,
        int maxProviderCallsPerTick,
        double thermalEfficiency,
        boolean unboundedOperations) {
}
```

矩阵的运行时 cluster 从该快照初始化两份独立计数器：`remainingOperations` 和 `remainingProviderCalls`。两种 envelope 可以由同一个统一计算器生成，但执行层不得把矩阵的 provider call 熔断重新解释为 CPU 的 `D`。

## 13. 执行器与调度器要求

物理 CPU 每 tick 初始化一份共享成功额度；每个虚拟 CPU 初始化自己的总发配量：

```text
sharedRemainingD = D
virtualCpuRemainingT[cpu] = T
```

普通样板成功发配一份后扣 `1 D + 1 T`。batch 成功接受 `acceptedCopies > 0` 后扣 `1 D + acceptedCopies T`。全部拒绝、抛异常、输入不足和能源不足均不扣 `D/T`。

为了避免大量已失败组合造成空转，调度器不得在每次成功发配前重新线性扫描全部虚拟 CPU 或全部失败 provider：

1. tick 首轮按轮换顺序对每个虚拟 CPU 只给予一次成功发配额度用于发现可工作任务。
2. 只有本轮实际成功的虚拟 CPU 才进入本 tick 的 productive queue。
3. 后续 `D` 通过小时间片在 productive queue 中轮转；一次时间片没有任何成功的 CPU 立即从本 tick 队列移除。
4. 样板的 provider 候选在物理 CPU 范围按 tick 缓存，最近成功 provider 优先。
5. `(canonical provider pattern, provider)` 第一次失败后从候选表移除；后续检查必须是 O(1) 跳过，不能每轮反复扫描失败前缀。
6. productive queue 为空或完整一轮没有成功时立即结束本 tick，即使 `D` 尚有余额也不能空转。

不支持 batch、需要动态过载输出登记或需要逐份种子追踪的样板继续逐份运行，每次成功消费 `1 D + 1 T`。模糊和过载样板按成功发配的 pattern copies 计量，不按输出物品数量计量。

只有 CPU envelope 自身声明 `unboundedBatch` 时才可以把该虚拟 CPU 的 `T` 设为无限；普通有限 CPU 上的 provider 不能绕过 `T`。provider 的 `BatchDispatchMode.UNBOUNDED` 只兼容旧执行器的单次批量计费，并使该 provider 优先接收当前 `T` 范围内的 batch；每次成功 provider 调用仍消费一份共享 `D`。任务探测次数和异常保护可以保留为不可见的内部安全熔断，但不能扣除或伪装成玩家的 `D`。

## 14. 状态显示

天枢控制器界面至少显示：

- 主核心等级。
- 发配、增幅、容量、闭环样板仓和闭环种子存储器数量。
- `R`、`Gd` 和 `Gs`。
- 原始发配与有效 `D`。
- 是否达到发配硬上限。
- 内置容量、外部容量和最终容量。
- 每个虚拟 CPU 的每 tick 总 copies 上限 `T`。
- 无效配置原因，例如增幅单元超过 15、单元类型不适用于当前结构。

矩阵控制器界面至少显示：

- 主核心等级，以及线程能力点、增幅单元数量和热控能力点。
- `R`、`Gd` 和 `Gt`，以及增幅前的原始发配。
- 当前温度效率、`operationsPerTick` 和剩余 provider 调用熔断预算。
- 无效配置原因。

CPU 界面中的“发配预算”必须指 `D`，不能显示 `T` 后仍称为发配。矩阵界面不显示或维护 CPU 的 `D`，也不再显示旧的 `q`、`baseBatch`、`batchSize` 或由其反推的 dispatches；只显示逻辑 operations 吞吐和 provider 调用保护值。

## 15. 验收用例

1. 天枢并行单元与矩阵 T1 线程单元在扫描层固定贡献一个 `dispatchUnits`，矩阵 T2 线程单元固定贡献两个；任何单元都不读取主核心类型。
2. 同一个容量单元在扫描层始终只贡献一个 `storageUnits`，不读取主核心类型。
3. 量子和过载都可以安装 15 个增幅单元，第 16 个使配置无效。
4. 发配单元超过达到上限所需数量时结构仍有效，`D` 保持在当前主核心上限并显示 capped 状态。
5. 基准 CPU 使用两个发配单元时得到 `D=256`、`T=512/t`。
6. 基准 CPU 增加第二个容量单元后容量继续增长，不存在 64 MiB 硬上限。
7. 满增幅量子 CPU 使用一个发配单元时 `D=3072`，不会超过量子发配上限。
8. 满增幅量子 CPU 使用四个容量单元时容量为 `8.25 GiB`，继续增加容量单元仍会增长。
9. 满增幅量子 CPU 的总吞吐不会超过 `10240/t`。
10. 满增幅过载 CPU 使用四个发配单元、三个容量单元时得到 `D=16384`、容量 `256 GiB`。
11. 满增幅过载 CPU 的每任务总吞吐为 `4194304/t`，不会因 `Gs=1024` 将发配增幅错误提高到 1024。
12. `N=7`、八个发配单元、九个容量单元的过载 CPU 得到 `208 GiB` 和 `1048576/t`。
13. 容量计算发生乘法或加法溢出时饱和为 `Long.MAX_VALUE`，不能回绕为负数。
14. 矩阵与天枢在相同主核心、发配数量和增幅数量下复用相同的 `Gd`、`Gt` 与 `tierCopyCap`，但矩阵只生成 `operationsPerTick`，不持有 `D`。
15. 矩阵只通过温度效率降低 `operationsPerTick`，不在运行时维护 CPU 发配操作预算。
16. 矩阵放入容量单元或天枢放入冷却单元时配置无效。
17. 多维主核心具有无限逻辑容量和 batch 能力，但矩阵实际 provider 调用仍不会超过 16,384/t。
18. 非 batch、模糊、过载动态输出和闭环种子跟踪路径均不能绕过 CPU 发配硬上限或矩阵 provider 调用熔断。
19. 矩阵拥有百万级 `operationsPerTick` 时，普通 `pushPattern` 在第 16,385 次调用前已返回 busy，剩余 operations 留到后续 tick。
20. 同一矩阵通过端口、控制器适配层或第三方接口包装访问时始终共享一份 provider 调用预算；一次成功 `pushBatch` 只消费一个 provider call，并按 accepted copies 消费 operations。
21. 同一物理天枢的所有虚拟 CPU 共享一份 `D`，每个虚拟 CPU 独占一份 `T`。
22. provider 对同一 canonical provider pattern 首次失败后，本 tick 内不会被任何虚拟 CPU 再次调用，失败不消费 `D/T`。
23. 99% 虚拟 CPU 或 provider 已被淘汰时，剩余成功额度直接由 productive queue 使用，不会在每次成功之间重新扫描失败项。
24. 矩阵 T1/T2 热控单元分别贡献一个和两个原始冷却能力点，再统一应用距离衰减。
25. 矩阵不再注册独立增幅 T1/T2 方块，天枢与矩阵都使用同一个天枢增幅单元，每个固定贡献一个 `amplifierUnits`。

## 16. 实施边界

实现时应同步替换：

- 天枢核心扫描和 `CpuInternalCoreCalculator` 的旧存储/并行倍率。
- 矩阵 `MatrixCraftingUnit`、`MatrixCraftingProfile` 和 `MatrixCraftingMath` 中独立的线程、增幅和吞吐公式。
- CPU/矩阵向执行器传递预算的接口。
- 天枢调用 `BatchExecutor` 时不再使用平方根计费或 `maxBatchOps` 作为 CPU 预算；改为成功调用计数与每虚拟 CPU copies 上限。Thunderbolt 面向其他调用方保留的旧 accounting mode 与兼容参数不属于天枢算力模型，不能反向影响 `D/T`。
- 控制器菜单、状态文本和 AE Guide 中的旧单元数值。

本设计不要求改变：

- 闭环样板分析和执行成员语义。
- 模糊样板与过载样板的动态输入输出处理。
- 种子账本和批量共享输入结算。
- 控制器 UUID `SavedData` 持久化。
- 端口仅作链接的职责。

## 17. 注册 ID 与迁移

注册 ID 只在旧名称已经违背最终结构语义时调整。仍准确描述对象的 ID 保持不变，避免无意义地破坏存档、KubeJS 脚本和整合包配方。例如矩阵的外壳、控制器、端口、主核心、样板仓及其方块实体/菜单继续使用 `matter_warping_matrix_*`；天枢的空白、存储和并行单元继续使用 `*_supercomputing_unit`。

需要迁移的方块和对应物品通过 NeoForge registry alias 定向到以下正式 ID，不重复注册兼容方块：

| 旧 ID | 正式 ID |
|---|---|
| `baseline_supercomputing_unit` | `tianshu_baseline_main_core` |
| `quantum_supercomputing_unit` | `tianshu_quantum_main_core` |
| `overload_supercomputing_unit` | `tianshu_overload_main_core` |
| `multidimensional_supercomputing_unit` | `tianshu_multidimensional_main_core` |
| `amplifier_supercomputing_unit` | `tianshu_amplifier_unit` |
| `matter_warping_matrix_blank_sub_core` | `matter_warping_matrix_blank_unit` |
| `matter_warping_matrix_thread_sub_core_t1/t2` | `matter_warping_matrix_thread_unit_t1/t2` |
| `matter_warping_matrix_cooling_sub_core_t1/t2` | `matter_warping_matrix_thermal_control_unit_t1/t2` |
| `matter_warping_matrix_multiplier_sub_core_t1/t2` | `tianshu_amplifier_unit` |

旧矩阵增幅 T1/T2 合并为同一个共享增幅单元是有损的等级收敛，但必须保留方块和物品本体，不能在加载旧世界时变为空气。未列出的既有 ID 不得仅为统一前缀再次改名。
