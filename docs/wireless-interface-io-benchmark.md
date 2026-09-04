# 过载接口无线快速 I/O 压力基准与通过标准

## 0. 给调度优化 AI 的使用入口

这一节是实际操作手册。把本文件交给后续负责优化的 AI 时，应要求它先完整阅读第 0、7、8、9、12 节，再开始改代码。其余章节是场景搭建、指标定义和故障定位的详细依据。

### 0.1 先分清哪些步骤已经自动化

| 能力 | 是否自动 | 使用方式 |
|---|---:|---|
| 编译和普通单元测试 | 是 | `compileJava`、`compileTestJava`、`test` |
| 25 个传输语义/故障场景 + 178 个调度压力场景 | 是 | `wirelessInterfaceIoModel` |
| 强制执行模型门槛 | 是 | `wirelessInterfaceIoModelAcceptance` |
| 快速迭代子集 | 是 | `wirelessInterfaceIoModelQuick` |
| 20,000 tick 长稳矩阵 | 是 | `wirelessInterfaceIoModelEndurance` |
| 保存/比较模型基线与候选 | 是 | `recordWirelessIoModelBaseline`、`wirelessInterfaceIoOptimizationCheck` |
| 自动生成 1024 目标、AE 网络、能量和存储 | 是 | `runWirelessIoGameTestServer` |
| 自生成压力/控制组真实服务端计时 | 是 | GameTest 夹具 + tick 探针；无需存档或玩家操作 |
| 256 目标冷热/脉冲/突发鲁棒性集成验收 | 是 | `runGameTestServer`；当前调度预期失败，优化候选必须通过 |
| 五轮独立 JVM 压力/控制运行 | 是 | `scripts/run-wireless-io-gametest-benchmark.ps1` |
| 基线/候选五轮统计比较 | 是 | `scripts/compare-wireless-io-gametest-benchmarks.ps1` |
| 手工真实整合包世界 | 可选 | 仅用于校准外部机器/复杂存储，与自动基准不是同一结果集 |
| 比较一对真实 JSON 是否达标 | 是 | `checkWirelessIoBenchmark` |
| 汇总并比较基线/候选各五对真实运行 | 是 | `checkWirelessIoBenchmarkRegression` |

特别注意：对旧的 `runWirelessIoBenchmarkServer`，`-Pae2ltBenchmarkScenario=...` **只是报告标签**；对新的 `runWirelessIoGameTestServer`，负载由 `WirelessInterfaceGameTests` 自动生成，场景标签仍不改变固定负载参数。不得把两种入口的 JSON 混在同一次比较中。

当前正式策略：确定性模型负责宽场景和严格语义；自生成 GameTest 专用服负责真实 `ServerLevel`、AE 网格、capability、时间轮和 JVM 性能比较；手工世界只作外部有效性校准。GameTest 的“测试总耗时”不参与判断，MSPT/TPS 只读取独立 tick 探针产生的 JSON/CSV，边界见第 2.4 节。

### 0.1.1 当前实现状态

截至本测试扩展所在工作树（生产调度仍为 `95a16d3d`），当前状态如下：

- 已完成 FAST 无线 I/O 调度优化：空闲连接有界重试、慢生产者相位错峰、冷启动恢复和导出拒绝退避；对应模型已同步更新。
- 编译、普通测试和完整报告型模型可以运行；完整模型现为 `25` 个语义场景和 `178` 个调度压力场景。
- 原来的 99 个压力场景仍通过；新增严格矩阵当前有 `34` 个场景未达到建议门槛，集中在 9/10/11/19 tick 边界、1-tick 脉冲、4-tick 突发、10/20 tick 抖动、20→1→20 速率切换和目标反复短断。
- 因此当前生产调度**不再满足扩展后的 `wirelessInterfaceIoModelAcceptance`**。这是压力测试新增发现，不应通过放宽门槛消除。
- 自生成 GameTest 已实际启动成功：1024 个桶目标、每目标 27 种物品、创造能源和无限 ME 存储均由代码创建。同一次 300-tick 冒烟对照中，控制组 mean/P99 为 `0.682/3.111 ms`，压力组 mean/P95/P99 为 `27.714/34.843/53.167 ms`，压力组无线 I/O P99 为 `42.107 ms`、`>50 ms` 比例为 `1.667%`。控制校正后的 mean/P99 增量约为 `27.032/50.056 ms`；这轮明显超过第 8 节严格预算，但样本不足，只能作为当前机器上的单轮问题证据。
- 普通 `runGameTestServer` 的 256 目标 transition 用例已真实复现恢复期堵槽：热→空闲→单 tick 脉冲→4 tick 突发→周期→再热时间线中的生产受阻率为 `16.364%`，严格上限为 `0.1%`。测试以正常 GameTest failure 返回，夹具没有崩溃。
- 上述单轮不是最终回归结论。正式结论仍需在固定 CPU/电源状态下运行基线和候选各五对独立 JVM，并使用控制组校正。

### 0.2 第一次接手时建立不可覆盖的基线

先确认基准分支能够编译，并记录原始提交和工作树状态：

```powershell
$baselineCommit = git rev-parse HEAD
git status --short
.\gradlew.bat compileJava compileTestJava
.\gradlew.bat test
.\gradlew.bat recordWirelessIoModelBaseline --rerun-tasks
```

这一步的有效结果应满足：

- 所有命令均成功；普通 `test` 不包含压力标签，压力模型由独立任务执行。
- `wirelessInterfaceIoModel` 退出码为 0，报告中没有 `correctness failure`。
- 当前调度允许出现第 12 节列出的已知建议门槛失败；这不是测试环境损坏，但候选不能把这些失败当作通过。
- 记录 `git rev-parse HEAD` 的值。若工作树非空，必须保存 diff；不得把未知本地修改当作优化效果。

上面的任务把三个模型报告直接保存到不会被 `clean` 删除的固定基线目录：

```text
benchmark-results/wireless-io-model-baseline-v2/model-summary.md
benchmark-results/wireless-io-model-baseline-v2/model-metrics.csv
benchmark-results/wireless-io-model-baseline-v2/scheduling-pressure.csv
```

`v2` 是 CSV schema，不是生产版本。测试场景或字段发生变化时必须提升 schema 并重新记录基线；比较器会拒绝跨 schema 比较。

推荐保存结构：

```text
<benchmark-results>/
├─ baseline-<commit>/
│  ├─ model-summary.md
│  ├─ model-metrics.csv
│  ├─ scheduling-pressure.csv
│  └─ live/<scenario>/<run-1..5>/
└─ candidate-<commit>/
   ├─ model-summary.md
   ├─ model-metrics.csv
   ├─ scheduling-pressure.csv
   └─ gametest-live/control-run1..5 + stress-run1..5
```

`benchmark-results/` 已被 Git 忽略。不要把基线放进 `build/`，因为 `clean` 会删除它；也不要提交大型实时 CSV。旧版只包含两个文件的基线不能与新版比较，必须在本测试提交上重新生成包含 `scheduling-pressure.csv` 的基线。

### 0.3 每次调度改动后的快速循环

优化 AI 每完成一个可独立解释的小改动，就按以下顺序执行：

```powershell
.\gradlew.bat compileJava compileTestJava
.\gradlew.bat test
.\gradlew.bat wirelessInterfaceIoModelQuick --rerun-tasks
```

然后检查：

1. `model-summary.md` 中的正确性不变量必须全部通过。
2. 阅读 quick 报告；若改动进入候选，使用一条命令记录候选、比较固定 v2 基线并强制执行全部门槛：

```powershell
.\gradlew.bat wirelessInterfaceIoOptimizationCheck --rerun-tasks
```

候选固定写入 `benchmark-results/wireless-io-model-candidate-v2/`。如需并存多个候选，仍可显式传入 `ae2ltBenchmarkModelReportDir` 和两个比较目录。

3. 比较器同时读取两份 CSV，自动拒绝吞吐、受阻率、最长受阻、批次 P99 延迟、**有需求后的最长等待**、输出满槽占比、积压 item-tick、总/空闲调度访问和 mean/P99 工作量回归。`max_service_gap` 只保留诊断，不再把“空闲轮询变少”错误判成回归。比较报告位于 `build/reports/wireless-interface-io-comparison/model-comparison.md`。
4. 若报告仍有建议门槛失败，必须说明具体场景和指标，不能只汇报 Gradle 任务成功。
5. 当报告中的建议门槛全部达到后，执行：

```powershell
.\gradlew.bat wirelessInterfaceIoModelAcceptance --rerun-tasks
```

`wirelessInterfaceIoModelAcceptance` 成功是进入正式实时验收的必要条件，不是最终性能结论。模型中的 `mean_work`、`p99_work` 是可解释的工作量代理，不是毫秒，也不能换算为 TPS。

为了保持基准可比性，调度优化期间遵循以下修改边界：

- 可以修改生产调度、时间轮、批处理、缓存、退避和刷新实现。
- 可以增加默认关闭的诊断字段，但正式测试必须关闭无关日志、断点和分析器。
- 不得删除场景、缩短窗口、降低门槛、放宽守恒式或把失败改成忽略。
- 不得通过延迟工作到采样结束后、无限扩大缓冲、丢弃物品、复制物品、减少理论负载或关闭连接来获得通过结果。
- 若确实发现测试本身有错误，测试修复和调度优化应拆成不同提交，并在报告中重跑修复前后的基线。

### 0.4 无需手工世界的服务端性能测试

#### 0.4.1 自生成 GameTest 压力/控制组（默认正式路径）

这一入口不读取存档，也不要求进入游戏。测试代码自动放置并连接：

- 1 个 `WIRELESS + FAST + AUTO import` 过载接口。
- 1 个创造能源单元和 1 个装有无限存储元件的 ME 驱动器。
- 1024 个真实桶方块实体，均在连接范围和强制加载结构内。
- 每个目标 27 个不同 item key、每 key 64 个物品；压力组在正式窗口持续原子补满，控制组保持相同方块、连接和空闲轮询但不生产。

一次快速冒烟（300 样本）可分别运行：

```powershell
.\gradlew.bat runWirelessIoGameTestServer `
  '-Pae2ltBenchmarkScenario=gametest-control-1024x27-smoke' `
  '-Pae2ltBenchmarkControl=true' `
  '-Pae2ltBenchmarkWarmupTicks=40' `
  '-Pae2ltBenchmarkSampleTicks=300'

.\gradlew.bat runWirelessIoGameTestServer `
  '-Pae2ltBenchmarkScenario=gametest-stress-1024x27-smoke' `
  '-Pae2ltBenchmarkWarmupTicks=40' `
  '-Pae2ltBenchmarkSampleTicks=300'
```

正式结果至少 1,200 个样本，不要把 smoke JSON 交给正式验收器。自动运行五对 `C1 → S1 → ... → C5 → S5`：

```powershell
.\scripts\run-wireless-io-gametest-benchmark.ps1 `
  -Runs 5 -WarmupTicks 200 -SampleTicks 1200 `
  -OutputDirectory benchmark-results\wireless-io-live-baseline
```

在另一个提交上用不同目录再跑一次，然后比较：

```powershell
.\scripts\compare-wireless-io-gametest-benchmarks.ps1 `
  -BaselineDirectory benchmark-results\wireless-io-live-baseline `
  -CandidateDirectory benchmark-results\wireless-io-live-candidate
```

脚本的每一轮都会启动新的 GameTestServer JVM，保存 JSON 和逐 tick CSV。压力测试中逐 tick 创建/补充桶内物品的成本被计入压力组；这有意模拟机器生产成本。基线和候选使用相同夹具，控制校正与相对回归用于判断调度改动，不能拿这组绝对数值宣称任意整合包都能达到相同 TPS。

单轮 `checkWirelessIoBenchmark` 仍可用于快速检查：

```powershell
.\gradlew.bat checkWirelessIoBenchmark `
  '-Pae2ltBenchmarkStressReport=C:\absolute\stress-run1.json' `
  '-Pae2ltBenchmarkControlReport=C:\absolute\control-run1.json' `
  '-Pae2ltBenchmarkMaxIoP99Ms=12'
```

#### 0.4.2 可选的手工世界校准

真实 TPS/MSPT 比较使用同一份世界快照、同一拓扑和同一 JVM 参数。控制组保留过载接口、连接、区块加载和无线快速 AUTO，只关闭机器生产/消费；这样仍有无线 I/O 调用，探针可以开始采样。

先恢复世界快照并启动控制组：

```powershell
.\gradlew.bat runWirelessIoBenchmarkServer `
  '-Pae2ltBenchmarkScenario=control-import-1024x32-continuous-run1' `
  '-Pae2ltBenchmarkCommit=<git-commit>' `
  '-Pae2ltBenchmarkWarmupTicks=200' `
  '-Pae2ltBenchmarkSampleTicks=1200'
```

看到日志 `Wireless I/O benchmark complete` 后，确认生成的 JSON 中：

```text
partial = false
samples >= 1200
interfaceCalls > 0
fastInterfaceCalls = interfaceCalls
```

保存 JSON 和对应 `-ticks.csv`，正常停止服务器。再次恢复**同一份**世界快照，启用机器负载，然后启动压力组：

```powershell
.\gradlew.bat runWirelessIoBenchmarkServer `
  '-Pae2ltBenchmarkScenario=import-1024x32-continuous-run1' `
  '-Pae2ltBenchmarkCommit=<git-commit>' `
  '-Pae2ltBenchmarkWarmupTicks=200' `
  '-Pae2ltBenchmarkSampleTicks=1200'
```

报告默认在：

```text
run-wireless-io-benchmark/benchmark-reports/wireless-interface-io/
```

如果停止服务器时只得到 `partial: true`，或样本数小于 1,200，该轮只能诊断，不能进入正式验收。如果一直没有报告，优先检查接口是否确实为 `WIRELESS + FAST + AUTO`、区块是否加载，以及是否发生过一次 `tickGridItemIo` 调用；不要仅修改场景标签重试。

用验收器比较这一对报告：

```powershell
.\gradlew.bat checkWirelessIoBenchmark `
  '-Pae2ltBenchmarkStressReport=C:\absolute\path\stress.json' `
  '-Pae2ltBenchmarkControlReport=C:\absolute\path\control.json' `
  '-Pae2ltBenchmarkMaxIoP99Ms=12'
```

正式比较必须为控制组和压力组各启动一个新 JVM，不能在同一服务器进程中先空载再加压。建议按 `C1 → S1 → C2 → S2 … → C5 → S5` 交错执行，减少温度和后台负载随时间漂移带来的偏差。每一对都先运行验收器，最终再按第 10.5 节比较五轮中位数。

收齐基线和候选各五对报告后，使用分号分隔同组的五个 JSON；四组中的第 N 个路径必须来自第 N 轮配对运行：

```powershell
.\gradlew.bat checkWirelessIoBenchmarkRegression `
  '-Pae2ltBenchmarkBaselineStressReports=C:\b\s1.json;C:\b\s2.json;C:\b\s3.json;C:\b\s4.json;C:\b\s5.json' `
  '-Pae2ltBenchmarkBaselineControlReports=C:\b\c1.json;C:\b\c2.json;C:\b\c3.json;C:\b\c4.json;C:\b\c5.json' `
  '-Pae2ltBenchmarkCandidateStressReports=C:\n\s1.json;C:\n\s2.json;C:\n\s3.json;C:\n\s4.json;C:\n\s5.json' `
  '-Pae2ltBenchmarkCandidateControlReports=C:\n\c1.json;C:\n\c2.json;C:\n\c3.json;C:\n\c4.json;C:\n\c5.json'
```

该任务检查每份报告是否完整且为纯 FAST，计算基线/候选五轮中位数和逐轮控制组校正值，自动拒绝绝对 MSPT/TPS 超标，以及平均 MSPT、P99、超 50 ms tick、无线 I/O P99、GC 和堆峰值回归。输出为：

```text
build/reports/wireless-interface-io-live-comparison/live-comparison.md
```

`Optimization status: MEASURABLE_IMPROVEMENT` 表示至少一个 mean/P99/控制校正/wireless I/O 指标改善超过容差；`NO_MEASURABLE_IMPROVEMENT` 即使没有回归也不能宣称“提高了速度”。最终目标不是只把门槛跑绿，而是在所有正确性、吞吐和延迟约束通过后，继续降低控制校正后的 mean/P99 MSPT 和 wireless I/O P99。

### 0.5 优化期间与发布前分别跑多少场景

日常迭代不需要每改一行就手工跑完整真实矩阵：

| 阶段 | 必跑内容 | 用途 |
|---|---|---|
| 每个小改动 | `test` + `wirelessInterfaceIoModelQuick` | 快速发现核心守恒、冷启动堵槽和工作放大回归 |
| 每个候选提交 | `wirelessInterfaceIoOptimizationCheck` | 执行全部 203 个模型场景并和固定 v2 基线比较 |
| 调整冷却/退避/唤醒的候选 | `runGameTestServer` | 强制真实方块实体的冷热、脉冲、突发和恢复堵槽 ≤0.1% |
| 有希望的候选 | 模型验收 + 自生成 GameTest 300-tick 压力/控制冒烟 | 淘汰真实 MSPT 明显恶化的方案 |
| 最终候选 | endurance + 自生成 GameTest 各 5 对、每轮 1,200 样本 | 检查长期状态并作最终通过结论 |
| 任何缓存/连接状态改动 | 额外强制 R-CACHE、R-FILTER、断网、断目标、重启恢复 | 防止只优化稳定热路径 |

最终候选不能只选择对自己有利的场景。自生成 GameTest 是最低真实服务端要求；外部模组机器 capability 若无法自动搭建，标记为“未校准”，不能写成已覆盖该模组，但不妨碍对固定桶 capability 的基线/候选回归作结论。

### 0.6 怎样从结果决定下一步

| 结果 | 结论 | 下一步 |
|---|---|---|
| 守恒/容量断言失败 | 功能错误，任何 TPS 都无效 | 先修复丢失、复制、越界或记账问题 |
| 堵槽或公平性失败，TPS 通过 | 调度为了性能牺牲服务质量 | 查最差连接间隔、时间轮饥饿和缓冲积压 |
| 模型工作量失败，真实 TPS 通过 | 仍存在放大风险 | 保留结果，扩大机器数/物品种类后复测 |
| 模型通过，真实 P99/TPS 失败 | 实际世界热点不在代理计数中 | 用 tick CSV 与 profiler 定位 capability、存储或分配热点 |
| 绝对门槛通过但比基线退化 | 候选不是性能改进 | 按第 9 节回归标准拒绝或说明明确取舍 |
| 模型、实时绝对门槛和相对基线都通过 | 候选可接受 | 保存产物、提交代码并填写第 13 节记录 |

判断顺序固定为：正确性和守恒 → 堵槽/吞吐/公平性 → P99 与超 50 ms tick → 平均 MSPT/TPS → 操作数和内存。不能用较好的平均 MSPT 掩盖严重尾延迟，也不能用较低无线 I/O 耗时掩盖机器输出槽持续堵塞。

### 0.7 可以直接交给另一个 AI 的任务说明

可将下面这段连同本文件一起交给优化 AI：

```text
请先完整阅读 docs/wireless-interface-io-benchmark.md，尤其是第 0、7、8、9、12 节。
你的任务是优化 WIRELESS + FAST 下自动回收和自动发配的调度，目标是降低游戏服务端
MSPT/P99、避免 TPS 下降，同时保持吞吐、公平性、恢复能力和物品守恒。

先在未修改代码上执行第 0.2 节，保存提交号、工作树状态和模型报告作为基线。
当前基线的 wirelessInterfaceIoModel 应成功，但第 12 节列出的建议门槛会失败；
不要把它们当成测试设施故障，也不要修改测试门槛来消除失败。

每个候选改动后执行第 0.3 节。任何 correctness、容量、堵槽或公平性回归都应先停止性能比较。
完整候选必须通过 checkWirelessIoModelRegression；最终候选还要运行 wirelessInterfaceIoModelEndurance。
模型指标不是 MSPT；候选模型达标后，按第 0.4、0.5 节运行自生成 GameTest 的控制组和压力组。
GameTest 会自动生成固定负载；ae2ltBenchmarkScenario 只负责报告命名，不会改变负载规模。

不要删除或弱化基准，不要改变负载规模，不要把工作延迟到采样窗口以后，不要用无限缓冲、
丢弃、复制或关闭连接换取 TPS。若必须修测试，单独提交测试修复并重建基线。

最终请报告：候选提交、改动摘要、所有执行命令、模型报告中每个失败项、五轮实时 JSON/CSV
路径、控制/压力中位数、最差一轮、第 7～9 节每项是否通过，以及任何未执行场景。
不要只报告 BUILD SUCCESSFUL。
```

一个优化任务只有在以下材料齐全时才算完成：基线提交与候选提交、无正确性失败的模型报告、最终候选的模型验收结果、真实控制/压力原始 JSON 和 CSV、五轮汇总、未测试项清单，以及依据第 7～9 节得出的明确通过/不通过结论。

## 1. 目标与范围

本基准只评估过载接口在 `WIRELESS + FAST` 下的物品自动回收（`ImportMode.AUTO`）和自动发配（`ExportMode.AUTO`）。最终性能结论以真实 NeoForge 服务端的主线程 MSPT/TPS 为准；确定性模型只负责验证堵槽、吞吐、公平性、守恒和调度边界，不能替代真实服务器计时。

基准不规定实现算法。后续可以改变时间轮、退避、批量、缓存或缓冲区刷新方式，但不得用少扫描换取机器堵槽，也不得用吞物、复制、无限积压或延迟到测试窗口之后来换取表面 TPS。

生产调度本身未为本基准改变。唯一生产侧改动是：

- 把现有的下一次 I/O 调度决策暴露给测试，使模型调用真实冷却/探测状态机。
- 增加默认关闭的计时探针。未启用时调用点只有一个可预测布尔分支；启用后记录完整服务器 tick 和无线接口 I/O 的墙钟耗时。

### 1.1 非目标

本轮不做以下事情：

- 不修改快速模式的冷却常量、时间轮大小、缓冲刷新间隔或过滤逻辑。
- 不依据当前模型失败直接选择优化方案；模型只指出需要真实 MSPT 验证的压力形态。
- 不使用客户端 FPS、单次 `System.nanoTime()` 或一次 Spark 采样代替重复的服务端 tick 测量。
- 不把测试 JVM 中模拟器自身的运行时间作为游戏 TPS。
- 不要求不可持续负载保持零积压；下游容量永久低于上游产量时，只检查有界性、守恒和恢复能力。

### 1.2 术语

| 术语 | 定义 |
|---|---|
| 理论产出 | 若输入、输出槽和调度都不阻塞，机器在给定窗口内应产生的总量 |
| 实际产出 | 机器确实写入输出槽的总量；因输出槽满而跳过的不计入 |
| 已回收 | 已从远端机器输出槽取得所有权的总量，位置可能仍在接口 import buffer |
| 已入网 | 已从 import buffer 成功插入 ME 存储的总量 |
| 理论消费 | 若发配及时，机器在给定窗口内应消费的总输入量 |
| 实际消费 | 机器实际取得并消费的输入量 |
| 堵槽事件 | 一次计划生产因缺少足够输出槽而完全或部分无法发生 |
| 目标访问 | 调度器选择一个连接并开始解析目标/wrapper；即使随后无搬运也计一次 |
| 物理操作 | 对远端或 ME 存储执行一次真实查询、模拟或修改；模型中按种类分别统计 |
| 工作 MSPT | `ServerTickEvent.Pre` 到最低优先级 `Post` 之间的服务端 tick 工作耗时，不含为维持 20 TPS 主动睡眠 |
| 容量 TPS | `min(20, 1000 / meanWorkMspt)`，表示当前工作量下服务器的理论持续 tick 能力 |

### 1.3 当前调度中需要由基准验证的行为

以下内容是测试设计依据，不是优化结论：

- 无线 I/O 条目按“连接 × AEKeyType × IMPORT/EXPORT”进入 128 槽时间轮。
- FAST 成功后的最短冷却为 1 tick；热态连续成功后，失败冷却可能受最近成功间隔限制。
- 无精确过滤时，回收通过 `getAvailableStacks` 枚举远端全部可用 key；缓存最多保留 256 个 key，截断缓存 TTL 为 5 tick。
- 回收先写入持久 import buffer，默认每 5 tick 尝试一次 ME 插入；同一 AEKeyType 完全拒收后会形成 20 tick 类型锁。
- 发配每个目标最多检查 36 个配置项，并为拒绝的 key 维护退避状态。
- 连接有效性和 wrapper 有独立刷新周期，故障恢复不能只测理想稳定拓扑。

测试必须能区分“为了吞吐而必需的每 tick 工作”和“机器已经空闲/满仓后仍在发生的无效工作”。

## 2. 测试架构与职责边界

### 2.1 确定性负载模型

运行：

```powershell
.\gradlew.bat wirelessInterfaceIoModel
```

该入口不会因为当前调度尚未达到建议门槛而失败，但所有所有权守恒、槽位容量和边界断言必须通过。输出位于：

```text
build/reports/wireless-interface-io/model-summary.md
build/reports/wireless-interface-io/model-metrics.csv
build/reports/wireless-interface-io/scheduling-pressure.csv
```

优化后强制执行模型门槛：

```powershell
.\gradlew.bat wirelessInterfaceIoModelAcceptance
```

模型使用生产代码中的 `CooldownTracker`、探测状态和 `nextIoSchedule`，并以可复现的机器负载代替世界/capability。它记录扫描、抽取、缓冲刷新和发配调用数，用于解释 MSPT 变化，不把这些计数换算成真实 TPS。

完整入口当前自动覆盖 203 个场景：25 个传输语义、缓存、故障和恢复场景，加上 178 个调度压力场景。除原矩阵外，新增了阈值两侧周期、短脉冲、短突发、确定性抖动、冷热速率切换、31/32/33 成功 streak、反复目标断开、调度器重建、32/64 接口拓扑和千/万级堆叠精确边界。测试会硬性验证这些维度没有被后续优化删除。

三个运行档位：

```powershell
# 日常小改动；输出到 build/reports/wireless-interface-io-quick/
.\gradlew.bat wirelessInterfaceIoModelQuick

# 完整 203 场景；输出到 build/reports/wireless-interface-io/
.\gradlew.bat wirelessInterfaceIoModel

# 完整矩阵再加回收、发配、双向各 20,000 tick；输出到 endurance 目录
.\gradlew.bat wirelessInterfaceIoModelEndurance
```

每个场景都执行以下硬性校验：

```text
producedOutput = recoveredOutput + finalMachineOutput
recoveredOutput = importedToNetwork + finalImportBuffer
dispatchedInput = consumedInput + finalMachineInput
0 <= occupiedOutputSlots <= configuredOutputSlots
unique scenario IDs and required matrix dimensions are present
```

`scheduling-pressure.csv` 另外记录理论/实际物品数、生产或消费受阻次数和短缺量、受阻机会比例、最长连续受阻、每批输出等待回收的 P50/P95/P99/max、空闲服务间隔、有需求后的最长等待、输出非空/满槽占比、积压 item-tick、每 tick 工作分布和空闲访问量。这些字段是后续调度优化的主要对照数据。

基线模式只要上述正确性不变量和矩阵完整性通过就返回成功；建议门槛写入报告但不令任务失败。`wirelessInterfaceIoModelAcceptance` 才强制执行建议门槛，适合优化完成后或 CI 专项任务使用。长期候选使用 `wirelessInterfaceIoModelEnduranceAcceptance`。

### 2.2 自生成真实服务端 MSPT/TPS 基准

默认入口直接启动 GameTestServer 并创建固定压力夹具，不需要世界存档：

```powershell
.\gradlew.bat runWirelessIoGameTestServer `
  '-Pae2ltBenchmarkScenario=gametest-stress-1024x27' `
  '-Pae2ltBenchmarkCommit=<git-commit>' `
  '-Pae2ltBenchmarkWarmupTicks=200' `
  '-Pae2ltBenchmarkSampleTicks=1200'
```

探针在第一次无线接口 I/O 调用后开始预热，默认预热 200 tick，再采样 1,200 tick（60 秒）。GameTest 把夹具创建放在采样前，并在采样完成后再执行最终守恒/堵槽断言。完成后生成：

```text
run-wireless-io-gametest/benchmark-reports/wireless-interface-io/<time>-<scenario>.json
run-wireless-io-gametest/benchmark-reports/wireless-interface-io/<time>-<scenario>-ticks.csv
```

JSON 包含主线程 tick 的 mean/P50/P95/P99/max MSPT、无线接口 I/O 的对应耗时、超过 50/100 ms 的 tick、容量 TPS、接口调用数、配置连接访问量、GC 次数/时间和堆内存峰值。CSV 保留每个 tick 的原始样本，不能只看平均值。旧的 `runWirelessIoBenchmarkServer` 仍可用于可选手工世界校准，但不是默认流程。

`max` 容易被一次 JIT、存盘或 GC 放大，只作诊断；P95、P99 和超 50 ms 比例参与正式验收。

计时范围：

```text
ServerTickEvent.Pre (HIGHEST)
  ├─ 服务端常规 tick 工作
  ├─ 世界、方块实体、AE2 网格 tick
  │    └─ OverloadedInterface.tickGridItemIo
  │         └─ 仅无线路径累计 wirelessIoNanos
  ├─ 其他模组 tick 监听器
  └─ ServerTickEvent.Post (LOWEST) -> tickNanos
```

`wirelessIoNanos` 是同一服务器 tick 内所有无线过载接口 I/O 调用耗时之和；它不是每个接口的平均值。报告同时记录 `interfaceCalls` 和 `configuredConnectionVisits`，便于按接口数、连接数归一化。

### 2.3 报告验收器

真实报告生成后，使用同一任务比较压力组和控制组：

```powershell
.\gradlew.bat checkWirelessIoBenchmark `
  '-Pae2ltBenchmarkStressReport=C:\absolute\path\stress.json' `
  '-Pae2ltBenchmarkControlReport=C:\absolute\path\control.json' `
  '-Pae2ltBenchmarkMaxIoP99Ms=12'
```

Windows 下必须把每个 `-Pname=value` 作为一个完整参数加引号，否则 `.bat`/PowerShell 组合可能把值误解析成 Gradle 任务名。

验收器检查完整样本数、FAST 模式纯度、绝对 MSPT、相对控制组增量、容量 TPS、GC 暂停比例和无线 I/O P99。它不读取模型 CSV，因此吞吐/堵槽和 TPS 两类结果都必须单独通过。

### 2.4 GameTest 能替代什么，不能替代什么

本项目依据 [NeoForge 1.21.1 官方 GameTest 文档](https://docs.neoforged.net/docs/1.21.1/misc/gametest/) 实现了开发态夹具。GameTest 可以在真实游戏 tick 中放置结构、按 tick 执行动作、断言结果，并由 `GameTestServer` 在必需测试失败时返回非零退出码，因此它可以替代：

- 人工创建 AE 网络、供电、存储、过载接口和 1024 个目标。
- 人工逐个连接、装填物品、启停负载和观察输出槽。
- 手工等待冷启动、短脉冲和恢复时间线。
- 对真实 `ServerLevel`、方块实体、item capability、AE 网格 tick 和时间轮协同的集成正确性检查。

它不能替代：

- 任意玩家整合包世界的绝对性能代表性；桶 capability 不等于所有模组机器。
- 多玩家、区块生成、实体 AI、存档和其他模组同时繁忙时的总服表现。
- 单次运行的可靠纳秒微基准；JIT、CPU 睿频、GC 和后台进程仍会造成噪声。
- GameTest 方法墙钟总耗时作为 MSPT。结构创建、测试报告和服务器启动时间不进入正式指标。

仓库中的 `WirelessInterfaceGameTests` 位于 `src/jdb`，只进入开发运行，不进入发布 jar。空结构由可审查的 Base64 资源在 `processJdbResources` 时生成 NBT。性能入口只运行持续 1024×27 回收压力；另一个 transition 测试在普通 `runGameTestServer` 中检查冷/热变化、单 tick 脉冲、四 tick 突发、周期恢复和再次满载，在性能运行中自动跳过，防止两个负载重叠。性能入口通过不代表 transition 鲁棒性通过，反之亦然。

性能计时仍由 `WirelessIoPerformanceProbe` 独立完成。它在 GameTestServer 的真实主线程上采集每 tick 总耗时和过载接口 I/O 包围耗时；压力和控制都用相同结构、相同 1024 连接和相同轮询，仅压力组生产物品。正式比较使用五对新 JVM 的中位数与逐对控制校正。这个组合可称为“自包含真实服务端回归基准”，不能称为“某个具体整合包真实服实测”。

## 3. 统一运行条件

每次正式比较必须满足：

- 相同提交以外的模组列表、配置、世界、Java 版本、JVM 参数、CPU 电源策略和后台负载一致。
- 使用专用服务端；不要用集成服务端或同时渲染客户端画面作为正式结果。
- 自生成 GameTest 使用每轮全新的固定 seed 测试级别；测试结构会强制加载覆盖的区块。手工校准才需要恢复世界快照。
- 压力采样期间不执行数据包重载、存档备份、Spark/JFR 分析或其他人工命令。
- ME 网络有足够能量和可写存储；除“ME 满/恢复”场景外，ME 存储不是瓶颈。
- 每组先跑一次不计分的 JIT 预热，再重启 JVM 独立跑 5 次。正式值取 5 次的中位数；最差一次保留作诊断。
- 每次压力场景必须配一个同世界控制场景：机器、区块和 ME 网络仍加载，但不产生/消费物品。接口保持无线快速 AUTO，使计时探针仍能开始采样。
- 报告中 `fastInterfaceCalls == interfaceCalls`，否则该轮不是纯快速模式结果。
- 任何一次出现物品丢失、复制、负数库存、持久缓冲无法在重启后恢复，整组直接失败。

### 3.1 必须记录的机器与 JVM 信息

每组五次运行必须共用并记录：

- CPU 型号、物理核心数、是否固定性能模式、是否有温度降频。
- 总内存、分配给 JVM 的 `-Xms/-Xmx`、GC 类型和全部 JVM 参数。
- 操作系统版本、Java vendor/version、NeoForge/AE2/本模组版本。
- Git commit、工作树是否有未提交修改、模组清单哈希、配置目录哈希。
- 世界快照哈希、视距/模拟距离、强加载区块数、在线玩家数。
- 测试开始前后的可用磁盘空间；正式窗口不得与自动备份或杀毒全盘扫描重叠。

探针 JSON 自动写入 Java/JVM、OS、CPU 逻辑处理器、最大堆和提交标签。其余信息应放进本文件第 11 节的运行记录模板。

### 3.2 世界与 ME 网络拓扑

正式压力世界建议使用固定布局：

```text
一个独立维度或平坦测试区
└─ 一个稳定供电的 ME 网络
   ├─ 过载控制器/足够频道
   ├─ 足够大的可写存储（故障场景除外）
   ├─ 一台被测过载接口：WIRELESS + FAST
   └─ N 个强加载远端目标
      ├─ 固定方向 capability
      ├─ 固定输出槽和输入容量
      └─ 独立生产/消费/堵塞计数器
```

同一组对照/压力运行必须复用世界快照，不能在压力组额外生成区块或改变红石网络。目标坐标和绑定面保持不变；若测试 1024 个连接，必须确认接口实际保存了 1024 个有效连接，而不是只摆放了 1024 台机器。

### 3.3 控制组定义

控制组不能简单地移除全部机器或关闭接口，因为那会同时移除方块实体、区块和 AE2 网格的基础成本。正确控制组是：

- 世界、机器、绑定、强加载、接口 FAST/AUTO 状态与压力组一致。
- 机器的生产和消费脚本暂停，远端 capability 仍存在。
- ME 能量和存储状态与压力组起点一致。
- 保持至少一次无线接口 I/O 调用，从而触发探针预热和采样。

控制组用于扣除“同一世界本来就有的 tick 成本”，但绝对 50 ms 门槛仍以压力组本身判断。

## 4. 负载发生器契约

真实服务器场景可以使用专用测试机器、脚本机器或固定模组机器，但负载发生器必须满足本节契约，否则结果不可比较。

### 4.1 key 与槽位

- “K 个不同物品”指 K 个不同 `AEKey`，不是同一 key 的 K 个数量。
- 数据组件差异必须能经过保存/重载后保持稳定，不能每次查询临时随机生成。
- 高基数场景使用固定种子预生成 key 集合；调度访问顺序不得推进随机数。
- 每个输出 key 默认数量为 1；若改变数量，必须在场景 ID 和报告中注明。
- 动态 key 场景每个生产 tick 使用新 key，用于真正占用新槽；固定 key 场景允许堆叠，用于过滤和容量测试。

### 4.2 机器 tick 顺序

统一采用保守顺序：

```text
1. 机器尝试消费输入/生产输出
2. 记录理论机会、实际完成和堵槽
3. 过载接口执行回收/发配
4. 记录 tick 结束时输入、输出和接口缓冲状态
```

该顺序允许输出在接口本 tick 扫描前先占槽，能暴露“一 tick 晚到就堵塞”的情况。若真实模组方块实体的 tick 顺序不同，必须在结果中注明，并另外跑一次最不利顺序。

### 4.3 每台机器必须暴露的计数器

至少记录：

- `theoreticalProduction`、`actualProduction`、`blockedProductionEvents`。
- `theoreticalConsumption`、`actualConsumption`、`underfilledProcessEvents`。
- tick 末输出槽数/数量、输入数量。
- 首次堵塞 tick、最长连续堵塞、恢复后的首次成功 tick。
- 每个目标在每个 100-tick 窗口中的完成率。

全部机器还要汇总 ME 入网量、接口 import buffer、发配总量。测试结束时执行第 2.1 节的三条所有权等式。

### 4.4 随机和复现

若增加随机生产间隔或随机 key 数，统一使用：

```text
baseSeed = 20260904
machineSeed = baseSeed + machineIndex
```

每台机器维护独立随机状态，只有机器自身计划事件推进随机数。失败报告必须包含 seed、机器编号、tick 和窗口范围。

## 5. 强制场景矩阵

自动调度压力矩阵不是所有维度的笛卡尔积；那会产生大量重复且难以日常执行的组合。它使用边界值与成对组合覆盖，保证每个关键维度的两端、临界点和高风险交互至少出现一次。完整任务启动时会验证下列维度仍然存在，少一个就直接测试失败：

| 维度 | 自动覆盖值 |
|---|---|
| 总连接数 | `0/1/64/256/1023/1024` |
| 过载接口数 | `1/2/4/16/32/64`，总连接数保持 1024 |
| 回收/发配方向 | 仅回收、仅发配、双向同时运行 |
| 动态输出 key 数 | `0/1/8/31/32/35/36/255/256/257` |
| 输出槽 | `0/1/31/32/33/63/64/65` |
| 单槽堆叠上限 | `64/999/1000/1001/1024/9999/10000/10001/65536` |
| 每 tick 每 key 产出/消费数量 | `1/64/999/1000/1001/1024/9999/10000/10001`，总量使用 `long` |
| 输出行为 | 每轮全新 key、固定可堆叠 key；原子生产、允许部分生产 |
| 机器与接口 tick 顺序 | 机器先执行、接口先执行 |
| 生产相位 | 全同步、按机器编号均匀错开、固定哈希错开 |
| 生产周期 | `1/4/5/6/9/10/11/19/20/21/39/40/41` tick |
| 负载形态 | 持续、周期、冷启动、热恢复、反复启停、1-tick 脉冲、4-tick 突发、10/20 tick 抖动、20→1→20 切换、31/32/33 streak、目标短断/长断、调度重建、零工作 |
| 发配配置 | `0/1/35/36` key；其中活跃需求为 `0/1/全部` |
| 目标输入容量 | 每 key `1/2/64` |
| 长稳态 | 回收、发配、双向各 20,000 tick |

此外，原有 25 个语义场景覆盖 ME 完全拒收/半速接收、远端不可达、ME 缺货、缓存 `255/256/257`、精确过滤、稀疏发配、持久 import buffer、故障恢复和 5,000 tick 双向守恒。两组场景合在一起才是完整模型基准。

### 5.1 无线自动回收

| 场景 | 连接数 | 每台机器产出 | 输出槽 | 目的 |
|---|---:|---:|---:|---|
| R1 | 1 | 每 tick 32 个不同物品 | 64 | 单目标延迟基线 |
| R64 | 64 | 每 tick 32 个不同物品 | 64 | 小型产线 |
| R256 | 256 | 每 tick 32 个不同物品 | 64 | 常见高压 |
| R1024 | 1024 | 每 tick 32 个不同物品 | 64 | 最大连接数持续压力 |
| R-K255/256/257 | 1024 | 每 tick 255、256、257 个不同 key | 至少两 tick 容量 | 缓存截断边界 |
| R-MIX | 1024 | 各三分之一每 1/5/20 tick 产出 8 个 key | 64 | 快慢机器公平性 |
| R-BURST | 1024 | 每 20 tick 同步突发 32 个 key | 64 | 时间轮同步峰值 |
| R-IDLE | 1024 | 40 tick 热产出、120 tick 空闲、再热产出 | 64 | 空闲退避与唤醒延迟 |
| R-COLD-SLOT | 1024 | 前 160 tick 无输出，随后每 tick 32 个新 key | 32/33/63/64/65 | 冷退避后的堵槽复现 |
| R-SLOT | 256/1024 | 每 tick 32 个新 key | 0/1/31/32/33/63/64/65 | 原子/部分生产容量边界 |
| R-ORDER-PHASE | 1024 | 每 1/5/20 tick 32 个新 key | 64 | 两种 tick 顺序和三种机器相位 |
| R-MULTI | 1024 总连接 | 1/2/4/16/32/64 台接口分担 | 64 | 多接口共享服务器和 ME 网络 |
| R-LARGE-STACK | 1024 | 36 个固定 key，每 key 每 tick 64/1024/10000 | 单槽上限 64/1024/10000/65536 | 大堆叠数量、批量回收和 `long` 计数 |
| R-THRESHOLD | 1024 | 周期位于 4/5/6、9/10/11、19/20/21、39/40/41 | 64 | 攻击冷却、慢生产者、缓存 TTL 和退避阈值两侧 |
| R-PULSE | 1024 | 每 40 tick 仅生产 1 tick，或连续生产 4 tick | 64 | 空闲调度是否错过短暂输出并形成长积压 |
| R-JITTER | 1024 | 间隔在 9/10/11 或 19/20/21 间确定性抖动 | 64 | 防止只对固定周期学习成功 |
| R-RATE-SWITCH | 1024 | 20 tick 慢速→每 tick 热速→20 tick 慢速 | 64 | 旧 pacing 是否拖住突然升速的机器 |
| R-STREAK | 1024 | 连续成功 31/32/33 tick 后空闲并恢复 | 64 | 精确覆盖 idle streak 分支边界 |
| R-FLAP/REBUILD | 1024 | 目标单 tick 反复不可达、40 tick 中断、每 40 tick 重建调度 | 64 | 重连、generation、时间轮和缓存复建 |
| R-STACK-EDGE | 1024 | 每 key 999/1000/1001/9999/10000/10001 | 同值 | 千/万级堆叠精确边界 |
| R-FILTER | 1024 | 36 个固定 key | 36×64 | 无过滤、精确、模糊、反向过滤 |

“不同物品”必须在 AEKey 层面不同；可以是不同物品 ID，也可以是合法且可持久化的不同数据组件。不能用同一 key 的数量增长代替高基数压力。

常规吞吐场景以输出槽两 tick 容量为正常起点；R-SLOT 和 R-COLD-SLOT 则有意测试不足一批、恰好一批、一批以上不足两批、恰好两批和两批以上的边界。R-K 的“至少两 tick 容量”具体为 `2 × K` 个槽；如果测试机器不支持这么多真实槽，必须使用能等价暴露 K 个 AEKey 的测试 capability，不能缩减 K。

时间线：

- R1/R64/R256/R1024：tick 0 开始持续生产，200 tick 预热后统计 1,200 tick。
- R-K255/256/257：分别独立运行，禁止在同一 JVM 中连续改 K 而复用缓存历史。
- R-MIX：机器编号 `% 3` 决定 1/5/20 tick 周期；每个目标的完成率按自身理论量归一化。
- R-BURST：所有目标同相位在 `tick % 20 == 0` 生产，专门观察同步 P99 尖峰；另跑目标相位均匀分散的诊断组，但分散组不能替代同相位正式结果。
- R-IDLE：前 40 tick 每 tick 生产，随后 120 tick 完全停止，再恢复持续生产。分别统计空闲访问比例和恢复延迟。
- R-COLD-SLOT：接口先空转至冷退避稳定，再突然让全部机器每 tick 生产。该场景直接检查“为了减少空闲扫描而退避后，机器输出槽是否在重新唤醒前堵住”。
- R-SLOT：原子生产若连一批输出都容不下，应由 `EXPECT_BACKPRESSURE` 自检确认发生堵塞；能容下一批及以上的场景属于可持续场景，不允许以容量不足解释调度延迟。
- R-ORDER-PHASE：机器先执行是正式保守结果；接口先执行是诊断对照。周期 5/20 的同步与错相结果用于判断 P99 峰值究竟来自总工作量还是同 tick 聚集。
- R-MULTI：总连接和负载不变，仅改变接口分组，分离每接口固定成本与每连接成本。
- R-LARGE-STACK：固定 key 可堆叠，不用增加 key 数来伪造大数量；分别跑持续热态和空转 160 tick 后冷启动。报告按物品数量统计吞吐、短缺和回收延迟，不能只按“处理了一个 key”计成功。
- R-PULSE/R-JITTER/R-RATE-SWITCH：除了 P99 批次延迟，还强制检查 `max_demand_wait`、输出非空/满槽比例和 `backlog_item_ticks`。空闲期间较大的 `max_service_gap` 本身不是失败；有物品后仍长时间不服务才是失败。
- R-FLAP/REBUILD：目标不可用 tick 不可能完成物理传输，但恢复后的可行窗口仍必须重新达到吞吐门槛；调度重建不得丢弃输出、重复发配或让旧 generation 继续执行。
- R-FILTER：四次独立运行无过滤、精确、模糊、反向过滤；允许输出集合保持一致，避免过滤语义改变负载总量。

### 5.2 无线自动发配

| 场景 | 连接数 | 配置 | 消费 | 目的 |
|---|---:|---:|---:|---|
| D1 | 1024 | 36 个 key，每 key 容量 64 | 每 tick 每 key 1 | 持续满速发配 |
| D-SPARSE | 1024 | 36 个 key | 只有 1 个 key 每 tick 消费 | 逐 key 拒绝退避成本 |
| D-CAP1 | 1024 | 36 个 key，每 key 容量 1 | 每 tick 每 key 1 | 最小容量与每 tick 唤醒 |
| D-IDLE | 1024 | 36 个 key | 40 tick 消费、120 tick 停止、再消费 | 满机器空闲扫描与恢复 |
| D-COLD | 1024 | 36 个 key，容量 1/2/64 | 前 160 tick 无需求，随后每 tick 消费 | 冷退避后的欠料复现 |
| D-LARGE-STACK | 1024 | 36 个 key，容量 64/1024/10000/65536 | 每 tick 每 key 消费 64/64/1024/10000 | 大堆叠批量发配和数量正确性 |
| D-MISSING | 256 | 36 个 key | ME 缺货 80 tick 后恢复 | 源端缺货退避 |
| D-STREAK | 1024 | 36 个 key | 连续成功 31/32/33 tick 后停止并恢复 | 发配成功 streak 与拒绝退避边界 |
| D-FLAP/OUTAGE | 1024 | 36 个 key | 目标反复单 tick 不可达或持续 40 tick 不可达 | 失效 capability、重连和恢复延迟 |
| D-REBUILD | 1024 | 36 个 key | 每 40 tick 重建调度状态 | generation、旧条目失效和重复发配 |
| D-STACK-EDGE | 1024 | 36 个 key，容量 999/1000/1001/9999/10000/10001 | 同值批量消费 | 千/万级输入堆叠精确边界 |

还必须分别覆盖配置数量 `0/1/35/36`、普通数量/无限数量、目标完整接收/部分接收/零接收，以及某些 key 永久不被目标接受。

时间线：

- D1：目标初始为空；每 tick 先从全部 36 个 key 各消费 1，再允许接口发配。
- D-SPARSE：36 个 key 均可装入，但只有第 0 个 key 持续消费；其余 35 个保持满仓，观察逐 key 拒绝缓存是否仍造成整目标热轮询。
- D-CAP1：每个 key 只有 1 容量，任何晚一个 tick 的补货都会直接表现为机器欠料。
- D-IDLE：前 40 tick 消费，120 tick 完全停止，之后恢复；空闲期间目标保持满仓。
- D-COLD：接口在空目标上先退避，tick 160 同时启动所有机器；容量 1 和 2 会把调度唤醒延迟直接表现为欠料，容量 64 用作充足缓冲对照。
- D-LARGE-STACK：每个 key 的需求量与容量独立变化，防止实现只在小数量或容量等于消费量时正确；理论消费、实际消费、发配量和最终库存都必须使用 `long` 守恒。
- D-MISSING：机器持续消费，ME 在 `[80,160)` 不提供配置 key，tick 160 恢复；记录首次重新发配和机器恢复满速的两个延迟。
- D-STREAK/D-FLAP/D-REBUILD：与回收方向使用同一组阈值和故障时间线；恢复后的需求等待不得被故障窗口本身掩盖，重建前的旧调度条目不得继续执行。

配置边界 `0/1/35/36` 已进入确定性模型。当前自生成性能 GameTest 不包含 AUTO export；若调度实现后续改动发配路径，应先要求模型四个边界全部通过，再按实际风险增加独立发配 GameTest 或外部世界校准，不能用 import 性能外推 export。

### 5.3 双向与故障边界

- B1：1024 台机器每 tick 消耗 1 个输入并产生 32 个不同输出，同时开启回收和发配。
- B-LONG：至少 256 台机器连续运行 5,000 tick，验证长期节奏不漂移、缓存和缓冲不持续增长。
- ME 存储从 tick 80 到 160 完全拒绝写入，然后恢复；检查持久 import buffer、类型锁和解锁后的排空。
- 目标 capability/区块从 tick 80 到 160 不可用，然后恢复；机器在可行时继续产出，覆盖掉线重连。
- 连接数 `0/1/1023/1024/1025`；第 1025 个必须被拒绝且不影响已有连接。
- 时间轮跨 127→128、接近 `Long.MAX_VALUE` 的冷却和 `nextIoSchedule` 饱和、区块卸载/重载、接口移除/重放、模式切换、过滤器热修改。加法溢出必须饱和到 `Long.MAX_VALUE`，不得回绕成负数或立即到期。
- 只有 item、只有 fluid、item+fluid、带 Applied Flux FE key；FE 不得重复进入物品 I/O 路径。
- ME 只有部分 key 可写、能量在 SIMULATE 与 MODULATE 之间耗尽、远端部分插入；全部执行所有权守恒。

故障注入必须发生在正式时间线上，不能通过停止服务器、卸载整个世界或修改配置后重启来代替。标准故障窗口为 `[80,160)`，恢复后至少继续运行 200 tick。

### 5.4 自动模型与真实服务器的覆盖对应

| 类别 | 确定性模型 | 真实服务器正式要求 |
|---|---|---|
| 0/1/64/256/1023/1024 持续回收 | 自动 | R1、R64、R256、R1024；0/1023 为模型边界 |
| 0/1/31/32/33/63/64/65 输出槽 | 自动 | 32/64 及当前真实机器实际槽数强制 |
| 冷启动、热重启、反复启停 | 自动 | GameTest 自动覆盖持续热态和 import 短脉冲；完整动态矩阵由模型强制 |
| 两种 tick 顺序、三种生产相位 | 自动 | 真实顺序 + 机器先执行的最不利顺序 |
| 1/2/4/16/32/64 台接口共享 1024 连接 | 自动 | GameTest 固定 1×1024；多接口性能按候选风险追加 |
| 单槽 64/999/1000/1001/1024/9999/10000/10001/65536 | 自动 | GameTest 固定原版 64；大堆叠语义由模型强制 |
| 255/256/257 key | 自动 | GameTest 固定 27 key；高基数性能按候选风险追加 |
| 混合周期、阈值两侧、脉冲、抖动、速率切换 | 自动 | GameTest import 短脉冲；其余由模型强制 |
| 31/32/33 streak、目标 flap/outage、调度重建 | 自动 | 模型强制；GameTest 验证稳定真实 capability 路径 |
| 精确过滤 | 自动 | 模型强制 |
| 模糊/反向过滤 | 规范要求 | 外部校准，当前自动夹具不宣称覆盖 |
| ME 0%/50% 接收、目标不可达 | 自动 | 模型强制；第三方 capability 故障行为未校准 |
| 0/1/35/36 发配配置和持续/稀疏/容量 1/恢复 | 自动 | 模型强制；当前性能 GameTest 只测 AUTO import |
| 1024 双向、5000/20000 tick 长稳态 | 自动 | 模型强制；GameTest 负责固定 import 真服计时 |
| item/fluid/FE、部分插入、卸载重载 | 规范/现有单元测试互补 | 自动固定桶夹具不覆盖，发布前按实际整合包校准 |

模型“自动”只表示调度行为和守恒自动执行，不表示真实 MSPT 已经测量。GameTest“自动”只表示原版桶 item capability 和本仓库 AE 网络的真实服务端路径；不能外推到任意第三方机器、fluid 或 FE capability。

## 6. 指标定义与计算

### 6.1 滑动吞吐

对每个正式 100-tick 半开窗口 `[start,start+100)`：

```text
productionRatio = actualProduction / theoreticalProduction
recoveryRatio   = recoveredFromMachines / actualProduction
dispatchRatio   = actualConsumption / theoreticalConsumption
combinedRatio   = completedProcessEvents / theoreticalProcessEvents
```

窗口从预热结束 tick 开始逐 tick 滑动，不能只按不重叠的 100 tick 分桶。分母为 0 的窗口跳过吞吐判断，但仍参与空闲访问和 MSPT 统计。

### 6.2 公平性

每台机器计算同一窗口内的归一化完成率：

```text
targetRatio[i] = actual[i] / theoretical[i]
fairnessSpread = max(targetRatio) - min(targetRatio)
```

理论量为 0 的目标不参与该窗口公平性。混合周期场景禁止直接比较原始数量，否则 1-tick 机器必然高于 20-tick 机器。

### 6.3 堵槽与恢复

```text
blockedRatio = blockedProductionEvents / theoreticalProductionEvents
pressureEventRatio = (blockedProductionEvents + underfilledProcessEvents) / scheduledOpportunities
pressureShortfall = sum(theoreticalItems - completedItems for pressured opportunities)
batchLatency = extractionTick - successfulProductionTick
restartLatency = firstSuccessfulTransferTick - workloadResumeTick
drainLatency = firstSteadyOutputTick - dependencyRecoveryTick
```

模型按物品数量加权统计 `batchLatency` 的 P50/P95/P99/max，并分别保留 `pressureEvents` 和 `pressureShortfall`。因此一次只少 1 个物品的部分生产，不会和一次整批 32 个物品完全失败混为同样严重。

“稳态输出”定义为所有目标输出槽占用回到故障前 P95 水位以内，并连续保持至少 20 tick；仅偶然清空一 tick 不算排空。

### 6.4 MSPT/TPS

五次独立 JVM 运行分别计算，然后对五个结果取中位数：

```text
meanMspt       = mean(tickNanos) / 1e6
p95Mspt        = percentile(tickNanos, 0.95) / 1e6
p99Mspt        = percentile(tickNanos, 0.99) / 1e6
over50Ratio    = count(tickNanos > 50ms) / sampleTicks
capacityTps    = min(20, 1000 / meanMspt)
headroomTps    = 1000 / meanMspt
meanDelta      = stress.meanMspt - control.meanMspt
p99Delta       = stress.p99Mspt - control.p99Mspt
wirelessShare  = wirelessIoNanos / tickNanos
```

报告中的 tick `max`、单次最慢接口和 GC 恰好重叠的 tick 用于定位，不作为单独否决条件。

## 7. 吞吐和堵槽通过标准

从预热结束后的每一个滑动 100-tick 窗口检查，而不是只看全程平均：

- R1/R64/R256/R1024/R-K：理论产出的至少 99.5% 被实际产出，至少 99% 已离开机器输出槽。
- R-MIX/R-BURST：理论产出的至少 99% 被实际产出；任一目标的归一化完成率不得低于 95%。
- D1/D-CAP1：理论消费量的至少 99% 被满足；D-SPARSE 至少 99%。
- B1/B-LONG：理论加工机会的至少 95% 完成。
- 在 ME 可写、目标可达的持续场景中，阻塞生产机会不超过 0.1%，任一机器连续堵槽不得超过 2 tick。
- 自动模型的可持续场景使用更严格的确定性门槛：受阻机会必须为 0；真实服务器允许的 0.1% 只用于吸收负载发生器和区块生命周期的非调度噪声。
- R-SLOT 在槽位少于一整批时必须确实观察到 backpressure，借此验证负载发生器不会把失败生产误报为成功；这类 `EXPECT_BACKPRESSURE` 自检不参与可持续吞吐结论。
- 持续热态的回收批次 P99 等待不超过 2 tick；冷态首次启动、热恢复和反复启停不超过 5 tick。
- R-COLD-SLOT 中能容纳至少一整批的槽位不得发生生产受阻；D-COLD 容量 1/2 不得发生欠料。容量充足的对照组不能掩盖小容量结果。
- R-IDLE/D-IDLE 恢复工作后，P99 首次成功回收/发配延迟不超过 5 tick。
- ME 或目标恢复后，所有机器输出槽在 25 tick 内回到稳态水位；发配恢复不超过 40 tick。
- 同构目标的 100-tick 完成率最大值与最小值之差不超过 5 个百分点；不得存在长期零服务目标。
- 持续可写场景中 import buffer 的 key 数不能随运行时间单调增长；5,000 tick 结束时增长斜率必须为 0。

不可持续场景（例如机器产出永久高于 ME 接收能力）不要求零堵槽，但仍要求所有权守恒、内存有界、服务器不崩溃，并在下游恢复后按上述时限排空。

## 8. MSPT/TPS 正式通过标准

每个强制压力场景的 5 次运行取中位数，必须同时满足。这里保留明显高于“刚好 20 TPS”的余量，不能把 49.x ms 的平均 tick 当成优秀结果：

- 主线程平均 MSPT ≤ 25 ms，相当于未封顶处理能力至少 40 TPS，给其他世界工作保留约一半 tick 预算。
- 主线程 P95 MSPT ≤ 35 ms。
- 主线程 P99 MSPT ≤ 45 ms，尾部仍保留至少 5 ms 安全余量。
- 超过 50 ms 的 tick ≤ 0.1%；1,200 样本中至多允许 1 个，2 个即失败。
- 按 tick 工作耗时折算的容量 TPS ≥ 19.8。
- 相对同夹具控制场景：平均 MSPT 增量 ≤ 10 ms，P99 MSPT 增量 ≤ 15 ms。
- R64 的无线接口 I/O P99 ≤ 1.5 ms；R256 ≤ 4 ms；R1024/D1/B1 ≤ 12 ms。R-K257 极端高基数允许 18 ms，但仍必须满足整服 P99 ≤ 45 ms。
- GC 总暂停时间不超过采样墙钟时间的 2%；不能依赖每轮 Full GC 才维持缓冲区大小。

检查一对真实报告：

```powershell
.\gradlew.bat checkWirelessIoBenchmark `
  '-Pae2ltBenchmarkStressReport=C:\path\stress.json' `
  '-Pae2ltBenchmarkControlReport=C:\path\control.json' `
  '-Pae2ltBenchmarkMaxIoP99Ms=12'
```

该任务检查 MSPT/TPS 和控制组增量。吞吐、堵槽、公平性和所有权门槛由负载发生器及确定性模型报告共同检查；两部分都通过才算总通过。

### 8.1 分规模无线 I/O 预算

| 场景规模 | wireless I/O P99 上限 | 说明 |
|---|---:|---|
| 1 目标 | 0.25 ms | 用于发现固定开销异常 |
| 64 目标 | 1.5 ms | 小型产线预算 |
| 256 目标 | 4 ms | 常见高压预算 |
| 1024 目标 | 12 ms | 最大连接持续/双向预算 |
| 1024×257 key | 18 ms | 极端高基数特例，整服 P99 仍须 ≤45 ms |

如果一台服务器上存在多台过载接口，每台都达到 1024 连接，12 ms 不是“每台接口都可用 12 ms”的许可；报告中的 `wirelessIoNanos` 是全部接口之和，仍按整服总预算判断。

### 8.2 通过矩阵

总结果只有四个状态：

| 正确性/吞吐 | MSPT/TPS | 结论 |
|---|---|---|
| 通过 | 通过 | PASS |
| 失败 | 通过 | FAIL：性能来自少做必要工作 |
| 通过 | 失败 | FAIL：功能正确但服务器不可承受 |
| 失败 | 失败 | FAIL |

## 9. 回归判定

在绝对门槛之外，每次调度优化还必须与修改前基线比较：

- 吞吐或恢复延迟任何一项变差，性能数字再好也不接受。
- 5 次运行的中位平均 MSPT 不得回退超过 3%（且超过 0.20 ms 才视为可测回退）。
- 中位 P99 MSPT 不得回退超过 5%（且超过 0.50 ms 才视为可测回退）。
- 超 50 ms tick 数不得增加超过 1 个，候选中位比例仍须不高于 0.1%。
- 无线 I/O P99 不得回退超过 5%（且超过 0.05 ms 才视为可测回退）；GC 时间或堆峰值不得回退超过 10%。
- 报告必须保存提交 ID、场景 ID、世界快照哈希、Java/JVM 参数和五个原始 JSON；禁止只保留人工整理后的平均值。

优化优先级是：守恒与不堵槽 → P99/超时 tick → 平均 MSPT → 操作数/内存。这样可以避免为了降低平均扫描量，引入偶发 100+ ms 尖峰或机器长时间饿死。

## 10. 标准执行流程

### 10.1 提交前快速检查

```powershell
.\gradlew.bat compileJava compileTestJava
.\gradlew.bat test
.\gradlew.bat wirelessInterfaceIoModelQuick
```

预期：编译和默认测试成功；quick 模型任务成功；`build/reports/wireless-interface-io-quick/model-summary.md` 可以包含建议门槛失败，但不得包含 correctness failure。

### 10.2 优化完成后的模型门槛

```powershell
.\gradlew.bat wirelessInterfaceIoModelAcceptance
.\gradlew.bat wirelessInterfaceIoModelEnduranceAcceptance
.\gradlew.bat runGameTestServer
```

该任务失败时先阅读：

```text
build/reports/wireless-interface-io/model-summary.md
build/reports/wireless-interface-io/model-metrics.csv
build/reports/wireless-interface-io/scheduling-pressure.csv
build/reports/tests/wirelessInterfaceIoModelAcceptance/index.html
build/reports/wireless-interface-io-endurance/
```

完整模型验收先执行；通过后再执行长稳验收和真实 transition GameTest。禁止通过删除场景、缩短测试窗口、调低机器数或放宽门槛来让任务变绿；若规范确需调整，必须在提交中说明负载假设为何不成立。

### 10.3 自生成真实基准准备

1. 固定 CPU、电源模式、Java 版本和 JVM 参数，关闭会抢占 CPU 的后台任务。
2. 记录 `git status --short` 和 `git rev-parse HEAD`；脏工作树必须连同 diff 保存。
3. 先运行 300 样本的控制/压力 smoke，确认 GameTest 夹具、AE 网络、无限存储和报告探针可用。
4. 在基线提交运行五对正式测试，保存到独立且不会被 `clean` 删除的目录。
5. 在候选提交使用完全相同的参数再运行五对测试。
6. 使用比较脚本检查候选的绝对门槛、逐对控制校正和相对回归。
7. 保存两个目录中的 manifest、10 个 JSON 和 10 个逐 tick CSV；任何缺失、`partial=true` 或样本少于 1,200 都作废。

脚本按 `C1 → S1 → C2 → S2 … → C5 → S5` 运行，并且每一轮都启动新 GameTestServer JVM。不要在同一 JVM 内切换控制/压力，也不要把旧手工世界报告混入 GameTest 结果。

### 10.4 正式运行与比较

基线提交：

```powershell
.\scripts\run-wireless-io-gametest-benchmark.ps1 `
  -Runs 5 -WarmupTicks 200 -SampleTicks 1200 `
  -OutputDirectory benchmark-results\wireless-io-live-baseline
```

候选提交：

```powershell
.\scripts\run-wireless-io-gametest-benchmark.ps1 `
  -Runs 5 -WarmupTicks 200 -SampleTicks 1200 `
  -OutputDirectory benchmark-results\wireless-io-live-candidate
```

比较：

```powershell
.\scripts\compare-wireless-io-gametest-benchmarks.ps1 `
  -BaselineDirectory benchmark-results\wireless-io-live-baseline `
  -CandidateDirectory benchmark-results\wireless-io-live-candidate
```

服务端日志出现 `Wireless I/O benchmark complete` 后才表示该轮采样写盘。GameTest 自身还会继续到最终守恒/堵槽断言；因此必须等 Gradle 进程以 0 退出，不能只看见 JSON 就强制结束。

### 10.5 五次结果汇总

比较脚本调用 `checkWirelessIoBenchmarkRegression`，自动计算下列中位数并检查绝对门槛和回归；若需诊断单轮，可另用 `checkWirelessIoBenchmark`。原始 JSON/CSV 仍必须保留：

- mean/P95/P99 MSPT 中位数。
- >50 ms tick 比例中位数和最差值。
- wireless I/O P99 中位数。
- GC 时间中位数和堆峰值最差值。
- 吞吐、堵槽、恢复延迟的最差窗口，而不是五轮平均。

## 11. 报告字段说明

### 11.1 JSON 摘要

| 字段 | 含义 |
|---|---|
| `schema` | 报告格式版本 |
| `scenario` | 命令行传入的场景名 |
| `commit` | 命令行传入的 Git 标识 |
| `partial` | 是否在达到目标样本数前停止 |
| `samples` / `warmupTicks` | 正式样本数和实际预热 tick |
| `tickMs` | 整服工作 tick 的 mean/P50/P95/P99/max |
| `wirelessIoMs` | 每 tick 所有无线接口 I/O 总耗时分布 |
| `capacityTps` | 按平均工作 MSPT 折算的持续 TPS 能力 |
| `ticksOver50MsRatio` | 超过 50 ms 的 tick 比例 |
| `interfaceCalls` | 样本窗口内无线接口 I/O 调用总数 |
| `fastInterfaceCalls` | 其中 FAST 模式调用数 |
| `configuredConnectionVisits` | 每次接口调用时配置连接数之和；不是已解析成功数 |
| `gcCollections` / `gcMillis` | 样本期间 GC 次数与耗时 |
| `peakUsedHeapBytes` | 从负载首次出现到报告完成的堆使用峰值 |

### 11.2 tick CSV

每行对应一个正式样本：

```text
sample,tick_nanos,wireless_io_nanos,interface_calls,fast_interface_calls,configured_connections
```

用途包括：定位 5-tick import buffer 刷新峰、20-tick 突发相位、连接刷新周期和 GC 重叠。分析时不得删除慢 tick；可以另做“排除 GC”诊断图，但正式 P99 使用原始全集。

### 11.3 模型 CSV

`model-metrics.csv` 记录 25 个传输语义/故障场景的吞吐、堵槽、恢复、扫描、缓冲和发配计数。

`scheduling-pressure.csv` 记录 178 个调度压力场景及其全部输入维度，并包含：

- `min_window`、`min_machine`：最差滑动窗口和最差机器吞吐。
- `pressure_events`、`pressure_shortfall`、`pressure_ratio`、`max_pressure_streak`。
- `latency_p50/p95/p99/max`：成功生产的物品等待回收 tick 数。
- `max_service_gap`：同一连接两次调度访问的最大间隔，只用于诊断空闲轮询。
- `max_demand_wait`：目标已经存在可搬运物品或输入需求后，最长等待服务 tick 数；这是延迟验收与回归字段。
- `mean_work/p99_work/max_work/p99_mean_ratio`：模型工作量分布。
- `scheduler_visits/productive_visits/idle_visits/idle_visit_ratio` 和输出槽峰值占用。
- `output_nonempty_ratio`、`output_full_ratio`、`backlog_item_ticks`：输出非空/满槽时间占比和按数量加权的积压暴露。
- `output_amount_per_key`、`output_stack_capacity`、`input_capacity`、`consumption_per_key`，用于确认大堆叠场景没有退化成小数量测试。

两份 CSV 中的 `elapsed_nanos` 都只是测试程序自身诊断字段，不稳定、不参与 MSPT/TPS 验收，也不进入基线回归比较。

### 11.4 模型回归比较报告

`checkWirelessIoModelRegression` 要求基线和候选具有完全相同的场景集合。它生成：

```text
build/reports/wireless-interface-io-comparison/model-comparison.md
```

该任务对吞吐、受阻、最长连续受阻、恢复/排空延迟、批次 P99、`max_demand_wait`、输出满槽比例、积压 item-tick、总/空闲访问和工作量分别比较；功能指标不允许恶化，mean/P99 工作量默认允许最多 10% 浮动，以便候选用少量平均工作换取明显更好的尾延迟。空闲期间的 `max_service_gap` 不作回归门槛，否则减少无效轮询反而会被误判。它不比较 `elapsed_nanos`，也不能代替真实 MSPT。

## 12. 当前调度的已知结果

本测试提交不再修改生产调度；以下结果来自生产提交 `95a16d3d` 加本测试矩阵，后续优化应把它作为“待改善候选”，不是永久黄金基线：

- 25 个传输语义/故障场景全部通过正确性和建议门槛；178 个调度压力场景全部通过守恒、容量和矩阵完整性。
- 178 个压力场景中有 34 个未达到严格调度门槛：7 个周期 9/10/11/19 边界、6 个单 tick 脉冲、5 个四 tick 突发、8 个 10/20 tick 抖动、6 个 20→1→20 速率切换，以及 2 个目标反复短断场景。
- 9/10/11 tick 边界可出现约 7.7% 的受阻机会和最长 2 tick 连续受阻；速率切换可出现约 3.7%～4.5% 的受阻机会及最长 14～17 tick 连续受阻。短脉冲虽未必立即堵槽，但 `max_demand_wait` 达到 15～20 tick，说明输出出现后可能长时间不被服务。
- 目标 flap 的失败属于故障恢复鲁棒性门槛，不应和稳定可达时的吞吐混为一谈；它证明反复断连后当前调度仍可能形成额外等待。
- 因此 `wirelessInterfaceIoModel` 应以 0 退出并报告 34 个 acceptance failure；`wirelessInterfaceIoModelAcceptance` 应失败。前者表示测试设施和硬性语义正常，后者表示当前调度尚未达到优化目标。
- 一对 300 样本 GameTest 冒烟中，控制组 mean/P99 为 `0.682/3.111 ms`，压力组 mean/P95/P99 为 `27.714/34.843/53.167 ms`，无线 I/O P99 为 `42.107 ms`，超过 50 ms 的 tick 为 `1.667%`。这同时违反 mean、P99、超时比例和无线 I/O P99 的严格预算。
- 冒烟轮样本不足且只来自一台机器，不能替代五轮正式结论；但控制组远低于压力组，足以说明这里确实存在需要优化的负载延迟/TPS 风险，而不是 GameTest 空框架本身就消耗了 50 ms。
- 普通 GameTest 的 256 目标 transition 场景当前以 `16.364% > 0.1%` 的生产受阻率失败；失败时 256 个连接仍有效、机器输出最终为 0、import buffer 为 0，表明夹具能够排空，但冷热/脉冲/突发切换期间服务不够及时。它是最初“输出槽容易卡住”问题的真实方块实体复现，不只是模型推断。

后续 AI 不应把当前失败数写死为目标，也不应通过放宽阈值获得通过。正确目标是：全部硬性语义继续通过、34 个调度失败归零，并由五对 GameTest 证明第 8、9 节均通过且相对基线有可测改善。

### 12.1 2026-09-04 候选调度修复记录

本轮候选针对目标解析/包装器暂时不可用时仍沿用普通空 I/O 退避的问题进行修复：

- `CooldownTracker` 单独记录连续目标失效次数，并设置一次性快速恢复标记；目标恢复路径前 4 次在下一 tick 重试，持续失效后退回对齐的 5 tick 有界轮询。
- 目标失效会清理连接级慢生产者相位，避免短暂断连后继承旧的慢相位；普通空 I/O、导出拒收和传输语义仍沿用原有退避规则。
- 生产代码中的 target level / wrapper 两个不可用分支，以及确定性压力模型和 I/O stress model，均使用同一目标失效标记，避免验收模型与实际调度分叉。
- 未修改任何吞吐阈值、压力负载、槽位容量、所有权守恒规则或连接关闭策略。

验证结果：`compileJava compileTestJava` 通过，完整 `wirelessInterfaceIoModel` 的 25 个语义场景和 178 个压力场景均通过守恒、容量与矩阵完整性检查。严格压力门槛由基线的 34 个失败降为 32 个：2 个目标反复短断场景已通过；剩余失败为 7 个周期边界、6 个单 tick 脉冲、5 个四 tick 突发、6 个 10 tick 抖动、2 个 20 tick 抖动和 6 个速率切换场景。由于严格门槛仍未归零，`wirelessInterfaceIoModelAcceptance` 目前仍应失败；本候选不应被记录为最终验收通过。

## 13. 结果记录模板

```text
Date:
Commit:
Dirty worktree: yes/no
Scenario:
Model suite: quick/full/endurance
Baseline model directory:
Candidate model directory:
Model regression comparison: pass/fail
Remaining model acceptance failures:
Benchmark fixture: gametest-import-1024x27
GameTest source commit:
External world snapshot hash (optional):
Mods/config hash:
CPU / power mode:
RAM:
Java / JVM args:
View / simulation distance:
Loaded chunks / players:

Control reports (5):
Stress reports (5):

Median control mean/p99 MSPT:
Median stress mean/p95/p99 MSPT:
Median mean/p99 delta:
Median wireless I/O p99:
Worst >50 ms ratio:
Median GC ms / worst heap peak:

Minimum sliding throughput:
Minimum target throughput:
Blocked event ratio / maximum streak:
Pressure shortfall / batch latency P50/P95/P99/max:
Maximum demand wait / diagnostic service gap:
Output nonempty/full ratio / backlog item-ticks:
Restart / drain latency:
Ownership invariant: pass/fail

Final verdict: PASS/FAIL
Failure window and first divergent tick:
Notes:
```

## 14. 失败定位顺序

1. 先看所有权与槽位不变量；失败时停止解释性能数据。
2. 再看滑动吞吐、堵槽和恢复延迟，确认调度确实完成必要工作。
3. 对比压力/控制 mean、P95、P99 和 >50 ms tick。
4. 用 tick CSV 对齐 5/20/128 tick 周期，判断是否为缓冲刷新、突发或时间轮同步。
5. 检查 `wirelessIoNanos / tickNanos`。占比高时继续分析接口；占比低时说明主要瓶颈在机器、ME 网络或其他模组。
6. 检查 GC 时间和堆峰值；若慢 tick 与 GC 重叠，再分析 key/缓冲分配。
7. 最后才用模型中的扫描、访问和插入计数解释是哪类工作放大。

若需要 Spark/JFR，应在完全相同的失败场景上另跑诊断轮；采样器带来的额外开销不纳入正式五轮数据。

## 15. 已知限制

- 服务端事件计时覆盖绝大多数 tick 工作，但同优先级且在探针 `Post` 之后注册的极少量监听器可能不在范围内；压力/控制使用相同模组顺序可抵消该偏差。
- `configuredConnectionVisits` 是接口调用时连接列表长度，不等于本 tick 实际走到 capability 的目标数；精确热点仍需结合模型或 profiler。
- 自动模型不构造完整 Minecraft 世界，不覆盖 capability 实现自身的第三方性能差异。
- 自生成 GameTest 已把结构创建放在采样前，并让探针只记录预热后的 tick；但逐 tick 物品生产本身属于压力场景总成本。控制组校正能扣除固定世界成本，不能完全分离“机器生产对象分配”和“无线调度”两者。
- 性能 GameTest 当前固定为一个 AUTO import 接口、1024 个原版桶和 27 个 item key；自动发配、双向、多接口和高基数的调度正确性由模型覆盖，但没有同等真实 GameTest MSPT 场景，不能宣称其绝对性能已经实测。
- 模糊/反向过滤、fluid、Applied Flux FE、第三方机器 capability、部分插入和真实区块卸载属于真实服务器/现有专项测试互补项，不应从 item-only 固定桶模型外推。
- 绝对 MSPT 门槛以本项目目标服务器为准；更慢硬件仍必须记录控制组，但不能只凭相对增量忽略整服已低于 20 TPS。
