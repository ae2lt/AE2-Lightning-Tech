# 过载样板供应器自适应批量发配压力与收敛模型

## 1. 目的

本文定义过载样板供应器无线均分路径的性能验收模型，只规定可观察目标，不指定具体调度算法。

自适应批量必须在一次常规合成任务仍有实际意义的时间内完成学习。为了减少目标访问而长期牺牲机器吞吐，或者在任务接近结束后才收敛，均视为失败。实现细节与本文冲突时，以本文的吞吐和收敛验收结果为准。

## 2. 统一拓扑与运行条件

基准拓扑固定为：

```text
无线目标数 N              = 512
固定模型单次处理量 Q      = 512 copies
固定模型输入容量 C        = 512 或 2048 copies
固定模型处理周期 P        = 1 或 5 tick
低吞吐固定模型容量 C      = 576 copies
低吞吐固定模型处理周期 P  = 20 tick
低吞吐固定模型处理量 Q    = 9 copies
随机模型输入容量 C        = 2048 copies
随机模型处理间隔          = 2..5 tick
随机模型单次处理量        = 512..1024 copies
无线调度模式              = EVEN_DISTRIBUTION
无线速度模式              = NORMAL
自适应批量                = 开启
CPU 待发请求              = 持续充足
```

其他条件：

- 512 个目标均存活、已加载、可接受同一个 canonical pattern。
- 不启用阻挡、红石锁、主产物锁或第三方动态定向样板。
- 能源和 CPU copies 预算充足，不构成全局中止条件。
- 输出回收不会背压输入，测试过程中不存在旧 `sendList` 或无线 overflow。
- 每个服务器 tick 对该持续请求执行一次无线批量调度。
- 为使测试可复现，每 tick 先执行机器处理，再执行供应器发配。

固定模型中的单目标在处理 tick 上执行：

```text
processed = min(Q, queuedCopies)
queuedCopies -= processed
```

库存不足当次处理上限时仍处理已有 copies，只是不能吃满理论吞吐；不要求积累到处理上限后才能启动。

测试目标的单次物理批次采用完整接收语义：

```text
chunk <= C - queuedCopies  -> 完整接收
chunk >  C - queuedCopies  -> 零接收
```

该基准不制造部分插入和 overflow；部分所有权转移仍由独立正确性测试覆盖。

## 3. 六组强制压力模型

旧基线以 100 tick 为一个冷启动观测段；正式验收按第 5 节运行“启动期加至少一个完整 100-tick 窗口”的收敛测试和 5,000-tick 长稳态测试。所有模型的初始目标库存和自适应运行时状态为空。

| 模型 | 单目标容量 `C` | 处理间隔 | 单次处理上限 | 100 tick 理论总吞吐 |
|---|---:|---:|---:|---:|
| A | 512 | 1 tick | 512 | 26,214,400 copies |
| B | 2048 | 1 tick | 512 | 26,214,400 copies |
| C | 512 | 5 tick | 512 | 5,242,880 copies |
| D | 2048 | 5 tick | 512 | 5,242,880 copies |
| E | 576 | 20 tick | 9 | 23,040 copies |
| R | 2048 | 每次随机 2..5 tick | 每次随机 512..1024 | 按固定随机事件流求和 |

固定模型的理论总吞吐按下式计算：

```text
processingOpportunities = 100 / P
theoreticalCopies = N * Q * processingOpportunities
throughputRatio = processedCopies / theoreticalCopies
```

随机模型 R 必须使用可复现、与调度访问顺序无关的事件流：

```text
baseSeed = 20260801L
每个目标独占 new SplittableRandom(baseSeed + targetIndex)
初始化调用顺序：firstDelay = nextInt(2, 6)，currentLimit = nextInt(512, 1025)
以 tick 0 为初始化点，firstProcessingTick = firstDelay
每次处理使用当前上限，完成后：
    下一次处理间隔 = nextInt(2, 6)
    下一次处理上限 = nextInt(512, 1025)
```

每个目标的随机数状态只能由该目标自己的处理事件推进，不能由调度器是否访问、发配是否成功或目标遍历顺序推进。测试先生成完整的计划处理事件流，再以各事件的处理上限之和作为 `theoreticalCopies`；不得用均值估算理论吞吐，也不得用系统时间作为随机种子。

在单次物理 push 最多补满整个输入容量、没有拒收且忽略冷启动的理想条件下，100 tick 的物理 push 数下界约为：

| 模型 | 理想物理 push 数下界 |
|---|---:|
| A | 51,200 |
| B | 12,800 |
| C | 10,240 |
| D | 2,560 |
| E | 40（纯容量下界；正式发配指标另应用每目标 100 tick 一次的存活保底） |
| R | `sum(theoreticalCopiesPerTarget / 2048)`（实数除法），由固定事件流计算 |

因此不能为所有工作负载设置同一个“push 数必须低于全扫描某固定比例”的指标。模型 A 若要吃满吞吐，本来就需要接近每 tick 向全部目标各 push 一次；发配优化必须服从吞吐，而不能反过来限制必要做工。

## 4. 强制验收指标

### 4.1 在十倍处理时间内收敛并持续保持吞吐门槛

启动学习期按处理时间的十倍计算：

```text
fixedStartupTicks = 10 * P
randomStartupTicks = 10 * maxRandomPeriod = 10 * 5 = 50
```

| 模型 | 启动学习期 | 开始验收的 tick |
|---|---:|---:|
| A、B：`P=1` | 10 tick | 10 |
| C、D：`P=5` | 50 tick | 50 |
| E：`P=20` | 200 tick | 200 |
| R：`P=2..5` | 50 tick | 50 |

学习期是唯一允许的收敛时间，不对其吞吐设通过门槛。从各模型的 `startupTicks` 机器处理阶段开始，固定模型 A–E 的吞吐必须持续保持在理论上限的 95% 以上，随机模型 R 必须持续保持在其已生成理论处理量的 80% 以上。

验收统一使用 100-tick 滑动窗口。从 `startTick=startupTicks` 开始，对每一个半开窗口 `[startTick, startTick+100)`，窗口总吞吐不得低于对应门槛。单处理 tick 和 20-tick 窗口只保留为诊断数据，不作为通过条件，避免短周期相位和随机处理量造成过大的统计波动。

固定模型 A–D 单个处理点的理论上限为：

```text
N * Q = 512 * 512 = 262,144 copies
minimumProcessedPerProcessingTick = ceil(262,144 * 0.95) = 249,037 copies
```

模型 E 单个处理点的理论上限为 `512 * 9 = 4,608 copies`，95% 最低通过量为 `4,378 copies`。

100-tick 滑动窗口的断言值为：

| 模型 | 窗口内处理机会 | 100 tick 理论吞吐 | 95% 最低通过量 |
|---|---:|---:|---:|
| A、B：`P=1` | 100 | 26,214,400 | 24,903,680 |
| C、D：`P=5` | 20 | 5,242,880 | 4,980,736 |
| E：`P=20` | 5 | 23,040 | 21,888 |

随机模型 R 不使用固定平均值代替理论处理量。对每个 100-tick 窗口执行：

```text
theoreticalR = 固定随机事件流在该范围内生成的处理上限之和
actualR * 100 >= theoreticalR * 80
```

短暂达到门槛后再次跌落不算通过。参数化收敛测试至少运行 `max(200, startupTicks + 100)` tick；长稳态测试运行 5,000 tick，并对各模型启动期之后的所有 100-tick 滑动窗口执行相同断言。超过对应 `startupTicks` 才逐渐收敛，即使最终达到 100%，仍判定不合格。

### 4.2 固定模型发配指标不得超过 400%

`physicalPushes` 统计实际进入一次 chunk push 的次数，而不是外层调度器选择目标的次数。完整接收、部分接收和零接收均计一次，`SIMULATE` 插入即告失败或过大批次探测失败也计一次；一次 chunk push 内部的 `SIMULATE -> MODULATE` 仍合计一次，不按 capability 内部调用拆分。同一次目标选择内依次 push `1, 1, 2, 4` 必须计为四次。尚未调用目标插入就因阻挡、CPU 输入或全局能源不足而中止，不计物理 push。

固定模型 A–E 以理论吞吐需求和目标容量计算理想最少发配数。对于 100 tick 内容量需求不足一次完整 push 的模型 E，另应用每目标至少一次调度机会的存活保底：

```text
rawIdealPushesPerTarget100 = (100 / P) * Q / C
effectiveIdealPushesPerTarget100 = max(1, rawIdealPushesPerTarget100)
effectiveIdealPushesTotal100 = N * effectiveIdealPushesPerTarget100
dispatchMetric = actualPhysicalPushes100 / effectiveIdealPushesTotal100 * 100%
```

从各固定模型的 `startupTicks` 开始，每一个 100-tick 滑动窗口必须同时满足：

```text
dispatchMetric <= 400%
actualPhysicalPushes100 <= 4 * effectiveIdealPushesTotal100
每个目标的 physicalPushes100 <= 4 * effectiveIdealPushesPerTarget100
```

五组固定模型的具体上限如下：

| 模型 | 理想每目标 push/100t | 理想总 push/100t | 400% 每目标上限 | 400% 总 push 上限 |
|---|---:|---:|---:|---:|
| A：512 / 1 tick | 100 | 51,200 | 400 | 204,800 |
| B：2048 / 1 tick | 25 | 12,800 | 100 | 51,200 |
| C：512 / 5 tick | 20 | 10,240 | 80 | 40,960 |
| D：2048 / 5 tick | 5 | 2,560 | 20 | 10,240 |
| E：576 / 20 tick、每次 9 | 1（原始容量下界为 0.078125） | 512 | 4 | 2,048 |

模型 D 中，每个目标每 5 tick 最多处理 512，容量 2048 正好覆盖四次处理，因此理想状态是每 20 tick push 一次 2048。100 tick 内理想为每目标五次；400% 上限允许每目标最多二十次、全部目标最多 10,240 次，等价于平均每 5 tick push 一次。

模型 E 专门验证低吞吐保底。单目标 100 tick 只消耗 45 copies，纯容量下界仅为 `45 / 576 = 0.078125` 次 push，不能直接作为整数窗口中的存活调度基线。因此按每目标每 100 tick 至少一次机会计为 100%；400% 表示任意滑动 100-tick 窗口内每目标最多四次物理 push、全部 512 个目标最多 2,048 次。该保底只改变发配指标的分母，不降低 95% 吞吐要求，也不要求目标在没有待发请求时产生空 push。

400% 是上限而不是目标值。低于 100% 只有在吞吐断言同时通过时才代表减少了 push；若吞吐不足，低发配指标只说明目标没有得到足够补货，不能算性能优势。

### 4.3 随机模型发配指标不得超过 800%

随机模型 R 使用同一个固定随机事件流生成理论处理需求，并按 2048 copies 的目标容量换算理想最少发配数：

```text
idealPushesR = theoreticalCopiesR / 2048
dispatchMetricR = actualPhysicalPushesR / idealPushesR * 100%
dispatchMetricR <= 800%
```

从随机模型的 tick 50 开始，每一个 100-tick 滑动窗口都必须对总 push 数和每个目标分别验收。测试使用整数交叉乘法，避免浮点和取整放宽上限：

```text
actualPhysicalPushesR * 2048 * 100 <= theoreticalCopiesR * 800
physicalPushesR[i] * 2048 * 100 <= theoreticalCopiesR[i] * 800
```

完整接收、部分接收、零接收和过大探测仍分别按一次物理 push 计算。800% 只用于容纳随机处理间隔、随机处理量和目标相位差带来的额外补货，不允许降低 80% 吞吐门槛；低吞吐与低 push 同时出现仍判定失败。

只用于理解量级时，随机分布的均值为 3.5 tick 和 768 copies，因此每目标每 100 tick 的期望理论需求约为 21,942.86 copies，理想 push 约为 10.71 次，800% 约为 85.71 次。该均值不得用于断言；实际 push 上限始终按当前固定事件流窗口的理论需求计算。

### 4.4 公平性与失败稳定性

同构、持续可用目标从各自启动期结束起不得出现长期零接收目标。固定模型 A–E 在每个 100-tick 窗口内应满足：

```text
standard models: minAcceptedPerTarget > 0
standard models: maxAcceptedPerTarget <= 2 * minAcceptedPerTarget
model E: minPhysicalPushesPerTarget > 0
model E: maxPhysicalPushesPerTarget <= 2 * minPhysicalPushesPerTarget
```

随机模型 R 的目标理论负载本身不同，不直接比较原始 accepted copies；改为比较每个目标的 `acceptedCopies / theoreticalCopies`，并保证每个有理论处理需求的目标都得到实际补货。随机负载差异不得被误报为调度不公平。

超过安全批次上界的探索失败必须与“目标不可用、机器拒绝当前样板、overflow 未排空”等真实失败分开统计。稳态不得形成“安全批次成功一次、下一次翻倍失败、进入多 tick 冷却”的固定振荡。

## 5. 参数化测试用例

至少提供以下六个参数化用例；它们共享同一模拟器和断言，固定模型改变 `C`、`P` 与 `Q`，随机模型改用固定事件流：

```text
adaptiveBatch_capacity512_period1_targets512_maxProcess512
adaptiveBatch_capacity2048_period1_targets512_maxProcess512
adaptiveBatch_capacity512_period5_targets512_maxProcess512
adaptiveBatch_capacity2048_period5_targets512_maxProcess512
adaptiveBatch_capacity576_period20_targets512_maxProcess9
adaptiveBatch_random_capacity2048_period2to5_targets512_maxProcess512to1024
```

每个参数组合运行收敛测试与长稳态测试：

- A–D、R 使用 `convergesAndStaysWithinLimitsFor200Ticks`；E 的启动期本身为 200 tick，因此使用至少 300 tick 的对应收敛用例，保证启动期后存在完整的 100-tick 正式窗口。
- `doesNotRegressAcross5000Ticks`：在长时间滑动窗口中重复相同验收，禁止后期节奏漂移。

随机用例固定使用 `baseSeed=20260801L`，并在失败消息中输出 seed、目标编号、tick/窗口范围、理论处理量、实际处理量和物理 push 数，保证失败可复现。不得只断言全程平均值。

测试骨架应保持以下语义：

```java
var result = simulate(
        targets = 512,
        capacity = C,
        processingSchedule = model.processingSchedule(),
        pendingCopies = Long.MAX_VALUE,
        ticks = testTicks);

int throughputPercent = model.isRandom() ? 80 : 95;
int dispatchPercent = model.isRandom() ? 800 : 400;

for (long start = startupTicks; start + 100 <= testTicks; start++) {
    long theoretical = result.theoreticalBetween(start, start + 100);
    long effectiveIdealNumerator = Math.max(
            theoretical,
            targets * C);
    assertThat(result.processedBetween(start, start + 100) * 100)
            .isAtLeast(theoretical * throughputPercent);
    assertThat(result.physicalPushesBetween(start, start + 100) * C * 100)
            .isAtMost(effectiveIdealNumerator * dispatchPercent);
    for (var target : result.targets()) {
        long targetTheoretical = result.theoreticalBetween(target, start, start + 100);
        long targetIdealNumerator = Math.max(targetTheoretical, C);
        assertThat(result.physicalPushesBetween(target, start, start + 100) * C * 100)
                .isAtMost(targetIdealNumerator * dispatchPercent);
    }
}
```

模拟器还必须断言所有 accepted copies 都由目标或供应器 overflow 持有，CPU leftover 与 ownership 转移守恒；不能用吞物或重复结算换取表面吞吐。

## 6. 必须记录的指标

每组测试至少输出：

- `processedCopies`：窗口内实际完成的 copies。
- `theoreticalCopies`：窗口理论上限。
- `throughputRatio`：主要考核指标。
- `idleCopies`：处理机会中未吃满的 copies。
- `underfilledRuns`：实际处理量小于该次计划处理上限的单目标处理次数。
- `physicalPushes`：实际调用目标物品/流体插入的次数；一次外层目标选择可以贡献多次。
- `dispatchMetric`：`physicalPushes / idealPushes * 100%`，只能在同一窗口的吞吐达标后解释。
- `failedVisits`：访问后零 ownership 转移的次数。
- `maxPhysicalPushesPerTick`：单 tick 物理 push 峰值。
- `fullScanTicks`：单 tick 恰好访问全部 512 个目标的次数。
- `minAcceptedPerTarget` 与 `maxAcceptedPerTarget`：100 tick 内同构活跃目标的接收量范围。
- `minimumProcessingTickThroughputAfterStartup`：启动期后最差单处理点吞吐率，仅作诊断。
- `minimumSlidingThroughput20AfterStartup`：启动期后最差 20-tick 滑动窗口吞吐率，仅作诊断。
- `minimumSlidingThroughput100AfterStartup`：启动期后最差 100-tick 滑动窗口吞吐率，作为验收指标。
- `maximumDispatchMetric100AfterStartup`：启动期后最差 100-tick 滑动窗口发配指标。
- `maximumTargetPushes100AfterStartup`：任一目标在任一 100-tick 滑动窗口内的最大物理 push 数。
- `randomSeed`：随机模型使用的固定 seed。
- `scheduledProcessingEvents`：随机模型各 tick 的计划处理次数和理论处理量，供失败复现。

吞吐通过后再比较发配成本。不得以更少的 `physicalPushes`、更低的峰值或更漂亮的平均值，掩盖实际机器空转。

## 7. 当前分支基线快照

以下结果用于记录 2026-08-01 的比较现场，不属于永久规范：

- `feature/adaptive-batch-dispatch`：`4003e08a34a49c1b05d91c4f32c8e59d42d273e1`
- `feat/adaptive-batch-dispatch`：`7be3e5cdcaede3db4a509658de25362a292fed99`

旧压力程序只覆盖固定模型 A–D。下表依次为“前 100 tick 平均吞吐 / `90..99` 吞吐”，仅作诊断，不能替代各模型启动期后逐处理点和逐滑动窗口的新验收：

| 模型 | `feature/adaptive-batch-dispatch` | `feat/adaptive-batch-dispatch` |
|---|---:|---:|
| A：512 / 1 tick | 13.92% / 12.73% | 12.00% / 10.00% |
| B：2048 / 1 tick | 39.97% / 40.94% | 38.00% / 40.00% |
| C：512 / 5 tick | 53.56% / 55.27% | 55.00% / 50.00% |
| D：2048 / 5 tick | 85.24% / 99.01% | 90.00% / 100.00% |

前 100 tick 的“目标访问 / 零接收 / 单 tick 访问峰值 / 全量扫描 tick”如下：

| 模型 | `feature/adaptive-batch-dispatch` | `feat/adaptive-batch-dispatch` |
|---|---:|---:|
| A | 15,140 / 3,348 / 512 / 7 | 15,872 / 5,120 / 512 / 31 |
| B | 14,407 / 2,800 / 512 / 7 | 15,360 / 4,608 / 512 / 30 |
| C | 14,459 / 3,157 / 512 / 7 | 15,872 / 5,120 / 512 / 31 |
| D | 14,303 / 3,065 / 512 / 7 | 15,872 / 5,120 / 512 / 31 |

`feat/...` 在四组模型中都保留明显的同步峰值；A、C、D 的前 100 tick 有 5,120 次零接收，占 15,872 次访问的约 32.3%。

长稳态 `4900..4999` 的“吞吐 / 发配指标”为：

| 模型 | `feature/adaptive-batch-dispatch` | `feat/adaptive-batch-dispatch` |
|---|---:|---:|
| A | 2.60% / 4.00% | 11.00% / 22.00% |
| B | 12.74% / 20.98% | 45.00% / 88.00% |
| C | 15.32% / 24.82% | 55.00% / 110.00% |
| D | 88.81% / 282.66% | 100.00% / 440.00% |

旧快照只记录了一个固定的 100-tick 汇总，不能证明每一个滑动 100-tick 窗口都通过，但已经足以证明两个实现均未通过固定四模型验收：A–C 吞吐低于 95%；`feature/...` 的 D 低于 95% 吞吐；`feat/...` 的 D 虽达到 100% 吞吐，发配指标却达到 440%，超过新的 400% 上限。低吞吐模型中的低发配指标不算优点。

随机模型 R 尚未包含在这份旧快照中，后续基线必须使用本文固定 seed 补测，不能从 A–D 推断其结果。

- `feature/...` 能削减同步全扫描峰值，但会错误拉长补货间隔，并在部分模型中随时间继续退化。
- `feat/...` 的同构目标分配较整齐，但存在同步全扫描和持续的成功/超量拒绝振荡，访问失败率过高。
- `feat/...` 当时的完整 `check` 还因 `ProviderTarget.pushPattern` 签名与测试未同步而产生 44 个测试编译错误；压力主程序能够运行不代表该分支满足合并条件。

后续实现更新基线时必须同时记录提交 ID、运行模式、六个模型的完整指标和最差滑动窗口，不能只替换吞吐百分比。

## 8. 实现刷新记录

### 8.1 保留已证明批次并按实测间隔补货

运行 `adaptiveBatchStress` 的 200-tick 收敛用例；下表记录“全程吞吐 / 最差正式 100-tick 吞吐 / 最大 100-tick 发配指标”。该实现刷新了固定模型 A–D 的吞吐历史最好值，因此即使随机模型尚未达标也必须独立保留提交：

| 模型 | 全程吞吐 | 最差 100-tick 吞吐 | 最大 100-tick 发配指标 | 结论 |
|---|---:|---:|---:|---|
| A | 94.50% | 98.50% | 100.00% | 通过正式窗口 |
| B | 95.00% | 99.50% | 216.00% | 通过正式窗口 |
| C | 95.00% | 100.00% | 100.00% | 通过正式窗口 |
| D | 95.00% | 100.00% | 200.00% | 通过正式窗口 |
| R | 70.12% | 67.78% | 140.01% | 吞吐未达到 80% |

这里的单 tick 最差值和 20-tick 最差值仍由测试输出，但只用于定位相位抖动；只有 100-tick 滑动窗口参与通过判定。

### 8.2 可变容量下逐级回退并重新增长

在已证明批次也暂时放不下时逐级减半，使随机机器可以利用不足一个历史大批次的空闲容量。R 的最差 100-tick 吞吐由 67.78% 刷新到 100%，但每 tick 重试导致固定模型 A 发生成功/拒绝交替，且 C、D、R 的发配指标超限：

| 模型 | 全程吞吐 | 最差 100-tick 吞吐 | 最大 100-tick 发配指标 | 结论 |
|---|---:|---:|---:|---|
| A | 48.00% | 50.00% | 100.00% | 吞吐回退 |
| B | 95.00% | 99.50% | 400.00% | 通过 |
| C | 95.00% | 100.00% | 500.00% | 发配超限 |
| D | 95.00% | 100.00% | 2,000.00% | 发配超限 |
| R | 96.13% | 100.00% | 933.52% | 吞吐刷新，发配超限 |

该记录证明动态回退能解决随机处理量下的欠供，但不能直接以“失败后下一 tick 重试”作为最终节奏；后续实现必须在保留 R 吞吐的同时恢复固定模型的覆盖间隔。

### 8.3 按真实恢复间隔调度并限制提前探测

提交 `218a700e` 引入按目标、按 canonical pattern 的补货间隔学习；提交 `eaec03e7` 限制提前一 tick 的探测频率。固定模型和随机模型的 200-tick 正式窗口全部通过：

| 模型 | 最差 100-tick 吞吐 | 最大 100-tick 发配指标 |
|---|---:|---:|
| A | 98.50% | 100.00% |
| B | 99.50% | 332.00% |
| C | 100.00% | 150.00% |
| D | 100.00% | 300.00% |
| R | 94.69% | 499.06% |

5,000-tick 长稳态中，全局吞吐和全局发配仍通过，但随机模型少数单目标在滑动 100-tick 窗口达到 913.04%，超过 800% 上限。失败窗口通常约有一次成功对应一次零接收；20-tick 波动不参与这一结论。

### 8.4 已证明批次拒绝后退到一半

提交 `70306262` 移除会破坏长稳态公平性的周期性强制增大，并把已证明批次的临时回退从四分之一改为二分之一。以 1024-copy 已证明批次为例，容量不足时下一次尝试 512，而不是 256；恢复后仍可返回已证明批次，不重新执行完整冷启动爬坡。

200-tick 收敛结果如下：

| 模型 | 全程吞吐 | 最差 100-tick 吞吐 | 最大 100-tick 发配指标 |
|---|---:|---:|---:|
| A | 94.50% | 98.50% | 100.00% |
| B | 95.00% | 99.50% | 324.00% |
| C | 95.00% | 100.00% | 150.00% |
| D | 95.00% | 100.00% | 300.00% |
| R | 92.09% | 95.07% | 440.77% |

5,000-tick 长稳态结果如下；当时已有的五个模型（A–D、R）的每个滑动 100-tick 窗口、单目标发配上限和公平性断言全部通过，尚不包含后来新增的 E：

| 模型 | 全程吞吐 | 最差 100-tick 吞吐 | 最大 100-tick 发配指标 |
|---|---:|---:|---:|
| A | 99.78% | 98.50% | 100.00% |
| B | 99.80% | 99.50% | 324.00% |
| C | 99.80% | 100.00% | 150.00% |
| D | 99.80% | 100.00% | 300.00% |
| R | 99.25% | 95.07% | 475.30% |

正式结果只采用滑动 100-tick 窗口；单 tick 和 20-tick 最差值继续输出，且只用于定位短周期相位抖动。

### 8.5 吞吐优先的提前探测与失败压力边界

在二分之一回退基础上，后续实现依次恢复探测失败后的下一轮提前探测、允许成功探测继续收敛，并调节真实拒绝后的失败压力。下表只列随机模型 R；吞吐为最差滑动 100-tick 吞吐，发配为最大全局滑动 100-tick 发配指标。凡刷新当时历史纪录的实现均已独立提交，包括之后被更优实现取代或因另一指标失败的检查点。

| 提交 | 200 tick 吞吐 / 发配 | 5,000 tick 吞吐 / 发配 | 结论 |
|---|---:|---:|---|
| `edbbb86a` | 95.13% / 444.22% | 95.13% / 477.94% | 通过；探测失败恢复后可再次提前探测 |
| `0eeca815` | 98.58% / 481.13% | 98.58% / 481.91% | 通过；成功提前探测可继续收敛 |
| `ad3f8121` | 98.94% / 481.88% | 98.94% / 481.88% | 通过；真实拒绝统一下一 tick 退半重试 |
| `1ab1028e` | 99.11% / 710.09% | 未运行 | 刷新吞吐，但 200 tick 单目标发配超过 800% |
| `3bf895d0` | 99.14% / 571.56% | 99.14% / 622.43% | 刷新吞吐，但长稳态单目标发配超过 800% |
| `b41eed1e` | 98.97% / 501.99% | 98.97% / 501.99% | 通过；失败压力达到 6 后进入 3-tick 保护 |
| `33dc637b` | 99.09% / 529.95% | 99.09% / 539.25% | 刷新吞吐，但长稳态单目标最坏 836.58% |
| `ba39101e` | 99.00% / 513.61% | 99.00% / 513.61% | 通过；失败压力达到 7 后进入 3-tick 保护 |
| `2cc7f35b` | 99.04% / 531.39% | 99.04% / 534.69% | 刷新吞吐，但长稳态单目标最坏 832.62% |
| `82193fde` | 99.03% / 514.67% | 99.03% / 514.67% | 通过；仅确认学习间隔缩短时额外减压 |
| `f021ac86` | 99.04% / 516.12% | 99.04% / 516.12% | 通过；确认缩短时将失败压力额外降低 3 |

最终策略不把偶然一次提前探测成功当作提速证据。只有连续两次提前探测成功、实际令学习间隔缩短时，才加速恢复失败压力；压力达到 7 后仍强制至少覆盖 3 tick。这样保留吞吐优先，同时给单目标 800% 上限留出长稳态余量。

### 8.6 最终长稳态结果

提交 `f021ac86` 的 5,000-tick 完整结果如下。最坏单目标发配使用同一个滑动 100-tick 窗口计算，不以全局平均掩盖局部热点：

| 模型 | 全程吞吐 | 最差 100-tick 吞吐 | 最大全局发配 | 最坏单目标发配 |
|---|---:|---:|---:|---:|
| A | 99.78% | 98.50% | 100.00% | 100.00% |
| B | 99.80% | 99.50% | 324.00% | 324.00% |
| C | 99.80% | 100.00% | 200.00% | 200.00% |
| D | 99.80% | 100.00% | 400.00% | 400.00% |
| R | 99.50% | 99.04% | 516.12% | 756.71% |

当时已有的五个模型（A–D、R）的吞吐、全局发配、单目标发配、100-tick 公平性和 ownership 守恒断言全部通过；该历史结果不代表新增模型 E 已通过。

## 9. 安全语义边界

本模型只验证批量大小学习、无线目标调度和吞吐收敛，不替代以下测试：

- 批次增长证明以“当前 tick、当前目标、当前 canonical pattern”为边界，不能跨 tick 继承。同一 tick 如果要从当前基准 `H` 上升，必须先依次完整接收 `H, H`，随后才可以按 `2H, 4H, 8H...` 继续倍增。因此合法序列是 `H, H, 2H, 4H...`，禁止 `H, 2H...` 和直接 `2H...`。
- 双重基准证明只在每个 tick 的增长开始处执行一次，不需要在同 tick 每翻一倍后再重复一次当前级。从 `2H` 成功继续上升到 `4H` 可以直接发送 `4H`，不要把序列写成 `H, H, 2H, 2H, 4H`。
- 历史只允许提供本 tick 的起始基准 `H`，可以直接用于不增长的普通补货，但不能与本 tick 的一次 `H` 合并成增长证明。即使上一 tick 已完整接收过 `H` 且没有产生 `sendList`/overflow，本 tick 要上升时仍必须实际执行 `H, H, 2H...`。
- 如果本 tick 的第二个 `H` 没有完整进入，则本 tick 的增长证明立即失效，第一份 `H` 不能与后续 tick 的任何发配拼成证明。该失败只终止当前 tick；下一 tick 仍可重新从一组全新的 `H, H, 2H...` 开始，不得把目标永久锁成单 `H` 补货。
- 只有某个物理批次的全部输入完整进入目标、没有留下 `sendList`/overflow 时，它才可以成为后续 tick 的新基准 `H`。零接收、部分接收或产生 overflow 都立即终止该目标本 tick 的增长；已经转移所有权的 partial 部分可以保留并结算，但不能提升 `H`，且余量排空前禁止向该目标发起新批次。
- 多输入样板在每个物理批次内保持原始输入顺序；上述 `H, H, 2H` 中的每一项都按一次完整 pattern copies 批次判断，不能按单个输入分别证明或增长。
- 模糊输入和过载动态输出的 ownership 结算。
- 部分插入后的 `sendList`/无线 overflow 排空。
- 阻挡、同一样板放行、主产物锁和红石锁。
- 能量不足、目标卸载和全局中止时已经转移 copies 的结算。
- canonical pattern 身份映射不得调用不可靠的第三方 `equals`。

任何压力优化都不得通过放宽这些正确性条件换取吞吐。
