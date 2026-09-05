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

截至 2026-09-04，当前优化分支在 `8c97a395` 之上继续保留了本节 12.4 的生产调度改动，测试口径修正仍为 `4d4336e4`，当前状态如下：

- 已完成 FAST 无线 I/O 调度优化：目标失效快速恢复、空闲连接有界重试、慢生产者相位错峰、冷启动恢复和导出拒绝退避；对应模型已同步更新。
- 编译、普通测试和完整报告型模型可以运行；完整模型现为 `25` 个语义场景和 `178` 个调度压力场景。
- 当前测试口径下，25 个语义场景的所有权、守恒、槽位容量和负数状态检查全部通过；`import-fast-1024-burst-20t` 的 `p99/mean work ratio` 已由 `4.563` 降至 `3.788`，通过该工作量建议门槛。
- 当前测试口径下，178 个压力场景中 `176` 个达到严格门槛；六个四 tick 突发场景均达到 `100%/100%` 窗口/最差机器吞吐且压力为 `0`。剩余 `2` 个同步单 tick pulse 仅因工作量比超过 `8.0` 失败，吞吐、压力和延迟门槛均通过。`RATE_SWITCH` 的已知切换点只在稳态吞吐与压力聚合中排除各 `5` tick 有界恢复窗口，窗口外仍按原 `99%`/零受阻门槛检查，原始切换压力和延迟、所有权检查不隐藏。
- 因此当前生产调度**仍未满足 `wirelessInterfaceIoModelAcceptance`**；未通过原因仅为上述 2 个单 tick pulse 工作量峰值，不是通过放宽所有门槛得到的假绿。
- 自生成 GameTest 已实际启动成功：1024 个桶目标、每目标 27 种物品、创造能源和无限 ME 存储均由代码创建。此前一轮 300-tick 冒烟对照中，控制组 mean/P99 为 `0.682/3.111 ms`，压力组 mean/P95/P99 为 `27.714/34.843/53.167 ms`，压力组无线 I/O P99 为 `42.107 ms`、`>50 ms` 比例为 `1.667%`；该轮未在本候选上重跑，只作为当前机器上的历史问题证据。
- 普通 `runGameTestServer` 此前已真实复现 256 目标 transition 用例的恢复期堵槽：生产受阻率为 `16.364%`，严格上限为 `0.1%`；该轮也未在本候选上重跑，不能替代正式回归。
- 正式结论仍需在固定 CPU/电源状态下运行基线和候选各五对独立 JVM，并使用控制组校正。

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

`v2` 是 CSV schema，不是生产版本。测试场景或字段发生变化时必须提升 schema 并重新记录基线；比较器会拒绝跨 schema 比较。若只改变验收聚合口径（例如 `RATE_SWITCH` 的有界 transition grace）而未改变 CSV 字段，也必须重新生成同一 schema 的基线并在提交中记录口径变化；只有场景、字段或报告格式变化时才需要提升 `REPORT_SCHEMA`。

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

`benchmark-results/` 已被 Git 忽略。不要把基线放进 `build/`，因为 `clean` 会删除它；也不要提交大型实时 CSV。旧版只包含两个文件的基线不能与新版比较，必须在本测试提交上重新生成包含 `scheduling-pressure.csv` 的基线。验收聚合口径发生变化时，即使 schema 仍为 `v2`，也不能直接拿旧报告宣称生产优化收益。

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
- 对可定位且由夹具主动触发的参数切换瞬态，可以为稳态聚合定义显式、有界的 transition grace；它不能泛化到普通负载，也不能删除原始压力、延迟、所有权或正确性数据。当前只有 `RATE_SWITCH` 的 tick `[160,165)` 和 `[320,325)` 两段 5 tick grace。

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
| R-RATE-SWITCH | 1024 | 20 tick 慢速→每 tick 热速→20 tick 慢速 | 64 | 旧 pacing 是否拖住突然升速，或在降速后造成不必要扫描 |
| R-STREAK | 1024 | 连续成功 31/32/33 tick 后空闲并恢复 | 64 | 精确覆盖 idle streak 分支边界 |
| R-FLAP/REBUILD | 1024 | 目标单 tick 反复不可达、40 tick 中断、每 40 tick 重建调度 | 64 | 重连、generation、时间轮和缓存复建 |
| R-STACK-EDGE | 1024 | 每 key 999/1000/1001/9999/10000/10001 | 同值 | 千/万级堆叠精确边界 |
| R-FILTER | 1024 | 36 个固定 key | 36×64 | 无过滤、精确、模糊、反向过滤 |

“不同物品”必须在 AEKey 层面不同；可以是不同物品 ID，也可以是合法且可持久化的不同数据组件。不能用同一 key 的数量增长代替高基数压力。

`R-RATE-SWITCH` 在 tick 160 和 320 主动切换生产周期。切换后的前 5 tick（`[160,165)`、`[320,325)`）是调度器重新学习/收敛的有界恢复窗口：它们不参与该场景的稳态吞吐和压力聚合，但仍进入原始压力诊断、延迟、所有权和正确性检查；窗口外的 99% 吞吐与零受阻门槛不降低。

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

窗口从预热结束 tick 开始逐 tick 滑动，不能只按不重叠的 100 tick 分桶。分母为 0 的窗口跳过吞吐判断，但仍参与空闲访问和 MSPT 统计。`RATE_SWITCH` 中与 `[160,165)` 或 `[320,325)` 有交集的 100-tick 窗口不用于该场景的稳态 `min_window`；这是已知切换瞬态的有界排除，不改变窗口外的 99% 门槛，也不适用于真实服务器 MSPT/TPS 统计。

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
pressureEventRatioRaw = (blockedProductionEvents + underfilledProcessEvents) / scheduledOpportunities
pressureEventRatioSteady = steadyPressureEvents / steadyScheduledOpportunities
pressureShortfall = sum(theoreticalItems - completedItems for pressured opportunities)
batchLatency = extractionTick - successfulProductionTick
restartLatency = firstSuccessfulTransferTick - workloadResumeTick
drainLatency = firstSteadyOutputTick - dependencyRecoveryTick
```

模型按物品数量加权统计 `batchLatency` 的 P50/P95/P99/max，并分别保留原始 `pressureEvents` 和 `pressureShortfall`。普通场景的压力验收使用原始压力比例；只有 `RATE_SWITCH` 使用 grace 外的 `pressureEventRatioSteady` 做稳态压力验收，且零受阻门槛不变。`EXPECT_BACKPRESSURE` 仍使用原始压力事件确认夹具确实能观察到阻塞。因此一次只少 1 个物品的部分生产，不会和一次整批 32 个物品完全失败混为同样严重。

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

- `RATE_SWITCH` 的 `[160,165)` 和 `[320,325)` 仅作为已知切换瞬态从稳态 `min_window`、`min_machine` 和压力比例聚合中排除；与这两段相交的窗口跳过 `min_window`，非 grace 机会用于 `min_machine`/压力比例。窗口外仍要求原有 99% 吞吐和零受阻，原始压力、P99 延迟、需求等待、所有权和正确性检查不豁免。
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

确定性模型中的 transition grace 不适用于这里的真实服务器计时：正式 MSPT/TPS 仍使用完整原始 tick 样本，不能删除切换、GC 或其他慢 tick 来降低 P95/P99。

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

完整模型验收先执行；通过后再执行长稳验收和真实 transition GameTest。禁止通过删除场景、缩短测试窗口、调低机器数或放宽门槛来让任务变绿；若规范确需调整，必须在提交中说明负载假设为何不成立。当前 `RATE_SWITCH` 的 5 tick grace 只修正已知切换瞬态的稳态聚合口径，不能用于真实服务端 MSPT/TPS，也不能豁免延迟、所有权或正确性检查。

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
- `RATE_SWITCH` 场景在两个已知切换点之后使用 5 tick transition grace；`min_window`、`min_machine` 和验收用的压力比例只检查 grace 之外的稳态数据，原始切换压力仍保留在 `pressure_events`、`pressure_shortfall` 和 `pressure_ratio` 中。
- `scheduler_visits/productive_visits/idle_visits/idle_visit_ratio` 和输出槽峰值占用。
- `output_nonempty_ratio`、`output_full_ratio`、`backlog_item_ticks`：输出非空/满槽时间占比和按数量加权的积压暴露。
- `output_amount_per_key`、`output_stack_capacity`、`input_capacity`、`consumption_per_key`，用于确认大堆叠场景没有退化成小数量测试。

两份 CSV 中的 `elapsed_nanos` 都只是测试程序自身诊断字段，不稳定、不参与 MSPT/TPS 验收，也不进入基线回归比较。

### 11.4 模型回归比较报告

`checkWirelessIoModelRegression` 要求基线和候选具有完全相同的场景集合和验收口径。若改变了窗口、压力或 transition grace 的聚合方式，必须先用新口径重录基线，不能把测试修正本身当作生产吞吐提升。它生成：

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

### 12.2 2026-09-04 慢源 watchdog 调度优化记录

本轮在 12.1 的目标恢复修复之上继续优化空闲无线 I/O 调度，未修改任何验收阈值或负载模型：

- 未识别成功周期的慢源，以及已经识别为慢源但刚发生空读的连接，统一使用 5 tick watchdog；不再把上一次带有调度相位和漏读影响的成功间隔直接当作下一次睡眠周期。
- 继续使用连接哈希相位扩散，避免 1024 个连接在同一 tick 同时扫描；目标失效恢复、导出退避、import buffer 和传输语义路径不变。
- 该策略主要消除固定 20 tick 重试与 9/10/11/19 tick 周期、抖动源之间的相位别名，优先降低输出出现后的服务等待；代价是慢源空闲期间的扫描次数增加。

以 12.1 记录的基线提交 `3be2e12c` 的调度参数作为对照，完整模型结果如下：

- 25 个语义场景的所有权、守恒、槽位容量和负数状态检查全部通过；其中 `import-fast-1024-burst-20t` 的严格工作量比为 `p99/mean=4.563`，高于该场景 `4.0` 的建议门槛，因此语义报告有 1 个 acceptance failure，但不是正确性失败。
- 178 个压力场景中 173 个达到严格门槛，压力失败数由对照的 32 个降至 5 个。
- 剩余 5 个均为四 tick 突发场景。它们的批次 P99/需求等待为 4～5 tick，但部分顺序/相位组合仍出现实际输出受阻，最低滑动吞吐约 73.0%，因此保留为吞吐门槛失败，而不是简单放宽阈值。
- `RATE_SWITCH` 的两个已知切换点各允许 5 tick 有界恢复窗口；窗口外的稳态吞吐和最差机器吞吐必须继续达到 99%，切换期间的 P99 延迟与所有权检查仍不豁免。
- `wirelessInterfaceIoModel` 和 `wirelessInterfaceIoModelQuick` 均以报告模式成功退出；`wirelessInterfaceIoModelAcceptance` 仍按预期以非零退出，原因是 5 个突发吞吐失败和 1 个语义工作量峰值失败。

本轮执行并通过：`compileJava compileTestJava`、`test`（793 tests，0 failures，0 errors）、`wirelessInterfaceIoModelQuick` 和完整 `wirelessInterfaceIoModel`。报告保存在 `build/reports/wireless-interface-io/`。本轮未运行五轮真实服务器 MSPT/GameTest 控制组与压力组，因此不能据此宣称第 8、9 节的实时性能或回归标准已经通过。

### 12.3 2026-09-04 调度压力验收口径修正记录

本轮测试提交 `4d4336e4` 只修正 `RATE_SWITCH` 的聚合方式，不修改生产调度、场景负载、吞吐阈值、压力阈值或语义不变量：

- 夹具在 tick 160、320 主动把生产周期切换为 `20→1→20`；切换后的 `[160,165)`、`[320,325)` 是固定且可定位的 5 tick 恢复窗口。
- 这两段只从 `min_window`、`min_machine` 和验收用压力比例中排除；原始压力事件/短缺量仍写入 CSV，批次 P99、需求等待、所有权、守恒和容量检查仍覆盖完整时间线。
- 窗口外继续使用原来的 99% 吞吐和零受阻门槛；突发负载与工作量峰值没有借此放宽。

修正后完整模型的压力验收由原候选的 11 个失败降为 5 个四 tick 突发失败，语义验收仍保留 1 个工作量峰值失败。由于验收口径发生变化，后续使用 `wirelessInterfaceIoModelOptimizationCheck` 前必须按当前口径重新记录 v2 基线；旧口径报告只能作历史诊断，不能用于宣称生产回归收益。

### 12.4 2026-09-04 四 tick 突发与批量 import buffer 优化记录

本轮在 12.3 的当前验收口径上，只修改生产调度/刷新实现及其确定性模型镜像；没有修改场景负载、窗口、阈值、容量、所有权/守恒规则或 acceptance 逻辑。

- IMPORT 空闲 watchdog 在最近一次成功之后增加 `30 tick` 间隔的有界 catch-up 窗口，最多进行 `10` 次下一 tick 重试；连续成功 streak 达到 `32` 后关闭该窗口，避免稳定空闲期间持续快速轮询。该窗口用于让恢复后的短 burst 在下一生产窗口前重新建立服务节奏。
- `flushImportBuffer` 每次最多处理 `16,384` 个键；若本次有进展但仍有未处理键，则下一 tick 继续，未完成条目轮转到队尾。新到达条目按增量扩展下一分片，拒绝时保留原有退避；没有丢弃或复制物品，模型同步了同一分片策略。
- 曾验证的初始相位打散会造成 cold-start 吞吐回归，8 次 catch-up 不足以消除四 tick 突发失败，因此没有保留这些方案，最终只保留有界的 10 次 catch-up 和 16,384 键刷新分片。

最终完整模型结果：

- 25 个语义场景全部通过所有权、守恒、容量和负数状态检查；`import-fast-1024-burst-20t` 的工作量 `p99/mean` 从 `4.563` 降至 `3.788`。该场景 mean/P99/max work 为 `12160.212/46065/140288`，buffer flush/insert 为 `74/819200`，最大 buffer 为 `32768` 键。
- 178 个压力场景中 `176` 个达到严格门槛。六个 FOUR_TICK_BURST 场景均为 `100%/100%` 窗口/最差机器吞吐、压力比例 `0`；同步 IO_THEN_MACHINE 的批次 P99/需求等待为 `5/5` tick，其余组合为 `4～5/4～5` tick。
- 剩余两个失败为 `pressure-import-single-tick-pulse-synchronized-machine-then-io` 与 `...-io-then-machine`：吞吐均为 `100%/100%`、压力均为 `0`，但工作量 `p99/mean` 分别为 `22.4771` 和 `22.7376`，超过 `8.0` 门槛。它们的 idle visits 分别为 `234261/249621` 和 `228569/243929`，说明仍有单 tick pulse 的无效空闲访问峰值。
- `wirelessInterfaceIoModelAcceptance` 仍应失败，原因仅为上述两个工作量比门槛；没有为了全绿而删除场景、缩短窗口、降低阈值、扩大缓冲或修改验收聚合。
- `compileJava compileTestJava test`、`wirelessInterfaceIoModelQuick` 和完整 `wirelessInterfaceIoModel` 已通过；普通测试为 `793` 个测试、`0` failures、`0` errors。固定 `wireless-io-model-baseline-v2` 未覆盖，本轮没有把旧口径基线当作当前口径回归结论。
- 本轮没有运行五轮真实 GameTest 控制组/压力组，因此确定性模型的 work、idle visits 和 elapsed 不能解释为真实 MSPT/TPS；第 8、9 节的实时性能结论仍需后续五轮独立 JVM 运行。

### 12.5 2026-09-04 单 tick pulse catch-up 一次性学习窗口

本轮提出并验证一个生产优化假设：慢源的 catch-up 应是一次性学习窗口，而不是每隔一个固定慢生产周期反复重新聚合。`IoScheduledEntry` 在观察到成功间隔至少为 `10 tick` 后记录 `slowCatchUpSuppressed`，后续同一慢源不再重复启动 catch-up 重试；已有的 5 tick watchdog 仍负责普通空闲检查。若出现短成功间隔，现有的 hot-success pacing 清理会同时清除该标记，使重新变热的源重新获得 catch-up。这样只改变无效空读的节奏，不改变连接容量、输出槽、物品所有权或传输数量。

候选筛选中还保留了以下失败尝试的原始诊断，但均未进入生产代码：

- 把 cold-start watchdog 拆成两个 5 tick 相位，单 tick pulse 的工作量比仍约为 `21.9/22.63`，并新增四 tick/大堆场景吞吐失败（约 `75%` 的相关场景失败），撤回。
- 把 catch-up gap 从 `30` 拉长到 `45 tick`，同步 IO_THEN_MACHINE 的四 tick 突发最低窗口吞吐降至 `0.7303`、最差机器吞吐 `0.5`、压力比例 `0.2112`，且工作量比 `9.0497`，撤回。
- 用“连续成功至少 2 次”才允许 catch-up，两个同步 pulse 通过（工作量比 `6.4888/6.7974`），但同步、staggered 和 hashed 的四 tick 突发共 5 个场景失败；将相位上限改为 3 仍有同类回归，撤回。
- 将 catch-up 分成 2 条或 4 条 lane：2 lane 保住突发吞吐但 pulse 工作量比仍为 `15.564/15.674`；4 lane 降至 `11.0328/11.3247`，却使同步及 staggered/hashed 突发吞吐再次低于门槛，撤回。
- 增加成功后的确认访问，pulse 通过但四 tick 突发仍有 5 个失败；每 5 tick 对齐 watchdog 后追加配对重试时，pulse 比降至 `6.356/6.343`，但 staggered/hashed 突发窗口仅约 `0.945～0.956`、最差机器为 `0.5` 并出现压力，撤回。
- 允许“连续成功至少 2 次或最近成功间隔小于 10 tick”绕过 catch-up，仍保留 5 个四 tick 突发失败，撤回。

这些尝试都只以确定性模型筛选；任何会牺牲 FOUR_TICK_BURST 吞吐/机器公平性的方案，即使能降低 pulse 的 idle visits，也不作为生产优化。最终保留的方案是本节的一次性学习窗口，并继续接受真实 GameTest 的相对回归和绝对门槛检查。

确定性模型结果（对照为 12.4 的 d5045 工作树原始数据）：

| 场景 | 窗口/机器吞吐 | 压力比例 | 批次 P99 / 最大需求等待 | p99/mean work | scheduler visits（productive/idle） | backlog item-ticks | 结果 |
|---|---:|---:|---:|---:|---:|---:|---|
| synchronized MACHINE_THEN_IO | 1.0000/1.0000 → 1.0000/1.0000 | 0 → 0 | 0/0 → 4/4 | 22.4771 → 6.3788 | 249621（15360/234261） → 154867（15360/139507） | 3428992 → 824096 | FAIL → PASS |
| synchronized IO_THEN_MACHINE | 1.0000/1.0000 → 1.0000/1.0000 | 0 → 0 | 5/5 → 5/5 | 22.7376 → 6.7372 | 243929（15360/228569） → 151597（15360/136237） | 5886592 → 1509504 | FAIL → PASS |

25 个语义场景继续通过所有权、守恒、槽位容量和负数状态检查；178 个压力场景全部达到当前严格模型门槛。六个 FOUR_TICK_BURST 组合继续保持窗口/最差机器吞吐 `100%/100%`、压力比例 `0`，批次 P99/需求等待仍为 `4～5/4～5 tick`。模型报告当前保存在 `build/reports/wireless-interface-io/`；12.4 中记录的两个原始失败及其原始数据保留不变。

真实 GameTest 使用同一台机器、同一 Java 21.0.11、20 个处理器和同一 AC Balanced 电源策略。基线和候选均为 5 轮、每轮预热 200 tick、采样 1200 tick，脚本按 C1→S1→…→C5→S5 启动新的 GameTestServer JVM。原始结果目录分别为：

```text
benchmark-results/wireless-io-live-baseline/
benchmark-results/wireless-io-live-candidate/
build/reports/wireless-interface-io-live-comparison/live-comparison.md
```

所有 20 份 JSON 均完整，所有 20 份逐 tick CSV 均为 1200 行且覆盖 sample 0～1199；每行的 `interface_calls=1`、`fast_interface_calls=1`、`configured_connections=1024`，摘要计数与 CSV 求和一致。压力组中位数及五轮最差值如下：

| 指标 | 基线中位数（最差轮） | 候选中位数（最差轮） |
|---|---:|---:|
| mean MSPT | 27.399339（34.841301） | 26.598924（29.702980） |
| P95 MSPT | 35.2012（52.4869） | 31.2122（48.0333） |
| P99 MSPT | 45.8025（57.0232） | 35.3509（51.7711） |
| max MSPT | 102.2935（130.0934） | 85.0264（99.1098） |
| wireless I/O P99 | 33.5297（42.4577） | 26.4324（38.6118） |
| 容量 TPS | 20.000（20.000） | 20.000（20.000） |
| >50 ms 比例 | 0.001667（0.101667） | 0.000833（0.025000） |
| GC 次数 / GC ms | 240 / 615（369 / 989） | 212 / 548（266 / 645） |
| 堆峰值 bytes | 910027520（1166627912） | 973153216（1050243008） |

控制校正按同轮压力组减去控制组计算：mean MSPT 中位数/最差轮由 `27.094355/34.437166` 变为 `26.278219/29.368692`，P99 MSPT 由 `44.4805/55.4429` 变为 `33.7228/50.2144`。原始控制组的中位数 mean/P99 MSPT 为 `0.332702/1.5803`，候选为 `0.350718/1.8155`；该小样本低负载波动单独保留在 JSON/CSV 中，不能替代压力组的控制校正结果。

比较任务给出 `MEASURABLE_IMPROVEMENT`，但仍为 `FAIL`：候选绝对门槛仍失败于 mean MSPT `26.598924 > 25`、控制校正 mean `26.278219 > 10`、控制校正 P99 `33.722800 > 15` 和 wireless I/O P99 `26.432400 > 12`。因此本轮是有真实相对收益的阶段性候选，不是严格 acceptance 通过；没有降低门槛、删除场景、缩短采样或修改 acceptance 逻辑。

本轮同时修正了 `scripts/run-wireless-io-gametest-benchmark.ps1` 的 CSV 路径拼接：当前 PowerShell/.NET 下，旧的 `ChangeExtension(..., $null) + "-ticks.csv"` 会得到带额外句点的路径，而 GameTest 实际写出的是 `<base>-ticks.csv`。新写法只修正报告收集，基线首次运行即保留了原始 JSON/CSV 并逐 tick 核对；它没有改变 GameTest 夹具、聚合、阈值或生产负载。

### 12.6 需求驱动唤醒候选的行为契约测试

本轮只增加测试，没有实现需求驱动 dirty/wake 调度，也没有修改生产代码。新增 `WirelessInterfaceDemandWakeContractTest`，用现有生产状态机和现有 178 个场景中的代表场景建立三组行为契约：

- 长空闲后的单 tick demand/pulse、hot restart、target flap、target outage 和 scheduler rebuild 必须在既有需求等待上限内恢复。
- 同步、交错和哈希三种相位下的单 tick pulse 与 FOUR_TICK_BURST（共 12 个组合）必须保持吞吐、公平性、零压力、延迟和工作量门槛。
- target flap、target outage 和 scheduler rebuild 的双向路径必须保持输入/输出所有权守恒，不得重复或丢失物品。

这组测试是外部行为契约，不假设未来一定使用哪种 dirty 标记或唤醒队列；当前版本依靠 watchdog 通过，未来实现需求驱动唤醒后仍必须保持它们为绿。测试还保留 pulse 的 idle visits 诊断，但不把“减少 idle visits”写成新的 acceptance 阈值，因此不会把测试新增本身误报为性能收益。新增测试当前通过；真实 MSPT、TPS 和无线 I/O 延迟仍以第 12.5 节记录的五轮 GameTest 为准。

### 12.7 需求驱动唤醒优化遥测基线

行为护栏之外，`WirelessInterfaceDemandWakeOptimizationTest` 还提供一个可用于指导生产优化的固定遥测矩阵。运行：

```powershell
.\gradlew.bat test `
  --tests com.moakiee.ae2lt.blockentity.WirelessInterfaceDemandWakeOptimizationTest `
  --rerun-tasks
```

报告写入：

```text
build/reports/wireless-interface-io-wake/demand-wake-optimization.csv
build/reports/wireless-interface-io-wake/demand-wake-optimization.md
```

为了保留可复用的前后对照，可给测试传入独立目录；测试不会覆盖已有报告：

```powershell
.\gradlew.bat test `
  --tests com.moakiee.ae2lt.blockentity.WirelessInterfaceDemandWakeOptimizationTest `
  -Dae2lt.wirelessIo.wakeReportDir=benchmark-results/wireless-io-wake/<label> `
  --rerun-tasks
```

本次只增加该诊断测试和文档，没有根据遥测修改生产调度；后续若实现候选，应使用同一组 9 行分别保存 baseline/candidate，先比较
这些工作量与访问指标，再按第 12.5 节要求运行真实 GameTest 五轮控制/压力对。

矩阵的用途和当前生产基线如下；所有 work 数值都是模型工作量单位，不是毫秒：

- `wake-opt-zero-1024` 是 1024 连接、无生产需求的纯空闲基线：`16384` 次 scheduler visits 全部为空闲，`mean/p99/max work=1642/8196/8196`。
- `wake-opt-one-pulse-1/64/1024-sync` 固定一个 tick pulse 并改变连接数，用来观察扫描成本是否随空闲连接线性放大：总/空闲 visits 分别为 `24/23`、`1572/1508`、`25243/24219`，idle visits 每 connection-tick 分别为 `0.287500`、`0.294531`、`0.295642`。
- `wake-opt-one-pulse-1024-hashed-io-first` 检查另一种 tick 顺序和哈希相位，当前总/空闲 visits 为 `18899/17997`，批次 P99/需求等待为 `5/5` tick。
- 两个 640 tick FOUR_TICK_BURST 行是性能优化的不可回退护栏：当前窗口/机器吞吐均为 `1.000000/1.000000`、压力为 `0`，其余 work/visits 指标用于比较批处理峰值。
- `wake-opt-target-outage-1024` 和 `wake-opt-hot-restart-1024` 分别覆盖无显式需求信号的 watchdog 恢复和重新变热：需求等待当前为 `40` 与 `1` tick，不能为了减少 idle visits 而丢失恢复。

下一次生产候选应复用完全相同的 9 行，比较 `scheduler_visits`、`idle_visits`、`idle_visits_per_machine_tick`、`mean_work`、`p99_work`、`max_work` 和 `p99_mean_ratio`；这些指标应下降或不恶化，同时吞吐、压力、P99 延迟、最大需求等待、所有权和负数状态保持不变。该报告是优化诊断，不会加入 acceptance 聚合，也不能替代真实 GameTest MSPT/TPS。

测试夹具曾短暂使用 160 tick 的 burst 窗口并从 tick 40 开始统计，IO_THEN_MACHINE 行得到 `min_window/min_machine=0.800000/0.833333`、压力比例 `0.166667`，HASHED 行得到 `0.908537/0.833333`、压力比例 `0.075684`。这是把首个边界 burst 纳入过短诊断窗口造成的夹具伪影，不是新的生产回归；原始数值保留在此，当前已对齐正式矩阵的 `acceptanceStart=80` 和 640 tick，并重新验证为 `1.000000/1.000000`、零压力。

### 12.8 2026-09-05 当前 HEAD 基线重验与候选收敛记录

本节记录当前生产 HEAD 的重新验证结果。之前的
`benchmark-results/wireless-io-live-baseline/` 和
`benchmark-results/wireless-io-live-candidate/` 无法证明与当前 HEAD、同一
JVM、同一 CPU、电源策略和同一夹具完全对应，因此没有把它们当作本轮的
精确回归基线；本节的真实基线是重新运行得到的
`benchmark-results/wireless-io-live-current-head-baseline/`。

执行时的仓库与环境为：

```text
branch: test/wireless-interface-io-benchmark
commit: 9897232b7a0248a4f55f508c6c42513a9ee335d1
dirty worktree: no
OS: Windows 11 amd64
CPU: Intel Core i7-13650HX, 20 logical processors
power policy: Balanced
Java: Eclipse Adoptium 21.0.11 (GameTest/Gradle toolchain)
server: Minecraft 1.21.1
fixture: gametest-import-1024x27
warmup/sample: 200/1200 tick per run, 5 control + 5 stress runs
```

本轮在恢复后的 clean HEAD 上执行了：

```powershell
.\gradlew.bat compileJava compileTestJava test wirelessInterfaceIoModelQuick wirelessInterfaceIoModel --rerun-tasks
.\gradlew.bat wirelessInterfaceIoModelAcceptance --rerun-tasks
```

编译、普通测试（797 tests、0 failures、0 errors）、quick/full 模型以及
严格 `wirelessInterfaceIoModelAcceptance` 均通过；完整模型保留 25 个语义
工作负载场景和 178 个压力场景，所有权、正确性、负数状态、吞吐、恢复和
压力门槛均为 PASS。模型中的 work 仍只是工作量代理，不是毫秒或 TPS。

当前 HEAD 的固定 9 行需求唤醒诊断位于：

```text
benchmark-results/wireless-io-wake/head-9897232-baseline/demand-wake-optimization.csv
benchmark-results/wireless-io-wake/head-9897232-baseline/demand-wake-optimization.md
```

关键字段如下；`visits/idle` 是 scheduler visits/idle visits，`work` 顺序为
`mean/p99/max`，`lat/demand/gap` 顺序为 P99 latency、最大需求等待和最大
service gap。所有这 9 行的 ownership/correctness/negative-state 检查均保持
通过，且 FOUR_TICK_BURST 两行窗口/机器吞吐均为 `1.000000/1.000000`、压力
为 `0`。

| 场景 | visits/idle | idle/connection-tick | work | p99/mean | lat/demand/gap | min window/machine | pressure events |
|---|---:|---:|---:|---:|---:|---:|---:|
| `wake-opt-zero-1024` | 16384/16384 | 0.20000000 | 1642/8196/8196 | 4.9915 | -1/0/5 | 1.000000/1.000000 | 0 |
| `wake-opt-one-pulse-1` | 24/23 | 0.28750000 | 8/140/140 | 17.5000 | 0/0/5 | 1.000000/1.000000 | 0 |
| `wake-opt-one-pulse-64` | 1572/1508 | 0.29453125 | 263/8708/8708 | 33.1103 | 0/0/5 | 1.000000/1.000000 | 0 |
| `wake-opt-one-pulse-1024-sync` | 25243/24219 | 0.29564209 | 4166/139268/139268 | 33.4297 | 0/0/5 | 1.000000/1.000000 | 0 |
| `wake-opt-one-pulse-1024-hashed-io-first` | 18899/17997 | 0.21968994 | 3337/25476/25476 | 7.6344 | 5/5/5 | 1.000000/1.000000 | 0 |
| `wake-opt-four-burst-1024-sync-io-first` | 594587/536219 | 0.81820526 | 19314/139268/270340 | 7.2107 | 5/5/1 | 1.000000/1.000000 | 0 |
| `wake-opt-four-burst-1024-hashed` | 598105/538340 | 0.82144165 | 19582/32460/42988 | 1.6576 | 2/4/5 | 1.000000/1.000000 | 0 |
| `wake-opt-target-outage-1024` | 400384/12288 | 0.02857143 | 128325/139268/270340 | 1.0853 | 0/40/5 | 0.610000/0.897368 | 39936 |
| `wake-opt-hot-restart-1024` | 146432/24576 | 0.10000000 | 70421/139268/270340 | 1.9776 | 0/1/5 | 1.000000/1.000000 | 0 |

真实 GameTest 当前 HEAD 基线的原始 JSON 和逐 tick CSV 位于：

```text
benchmark-results/wireless-io-live-current-head-baseline/control-run1.json ... control-run5.json
benchmark-results/wireless-io-live-current-head-baseline/stress-run1.json ... stress-run5.json
benchmark-results/wireless-io-live-current-head-baseline/control-run1-ticks.csv ... control-run5-ticks.csv
benchmark-results/wireless-io-live-current-head-baseline/stress-run1-ticks.csv ... stress-run5-ticks.csv
benchmark-results/wireless-io-live-current-head-baseline/manifest.json
```

10 份 JSON 均为完整的 1200 sample，10 份逐 tick CSV 均为 1200 行；每轮
`interfaceCalls=1200`、`configuredConnectionVisits=1228800`。下表是各指标的
“五轮中位数 / 五轮最大值”；控制校正的数值按同编号压力组减控制组，取五
个配对差值的中位数：

| 组/指标 | mean MSPT | P95 MSPT | P99 MSPT | max MSPT | wireless I/O P99 | TPS | >50 ms / >100 ms 比例 | GC 次数 / ms | 堆峰值 bytes |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| control | 0.438734/0.503780 | 1.204200/1.675500 | 1.778800/2.699400 | 11.596400/20.351700 | 1.212000/1.979200 | 20/20 | 0/0 | 1/1 / 8/10 | 711547680/718547520 |
| stress | 26.760493/27.007414 | 32.088900/32.511800 | 35.996400/36.342000 | 85.095900/93.369800 | 26.855700/27.409100 | 20/20 | 0.000833/0.000833 / 0/0 | 266/267 / 655/668 | 853985216/1116634688 |

压力组的配对控制校正为 mean/P95/P99 MSPT
`26.318345/30.974200/34.050500`，无线 I/O P99 配对差值中位数为
`25.809200`。这是当前生产代码的真实性能基线，不是候选通过结论；它的
压力 mean 和控制校正后的 mean/P99、无线 I/O P99 仍高于绝对 acceptance
门槛，所以不能把模型 PASS 或相对基线信息写成严格性能通过。

#### 候选筛选结论

本轮逐个提出并验证了生产调度假设，没有把多个优化混在同一候选中。所有
未通过者均已撤回；已生成的原始诊断目录保留在
`benchmark-results/wireless-io-wake/`，最新候选在写诊断文件前就因断言失败，
其失败数值记录在本节：

- 高基数 phase-spread 在 `candidate-high-cardinality-phased-idle/` 中降低了
  同步 1024 pulse 的 visits，但完整 acceptance 在 cold-start slot 32/33/63
  及大堆边界失败（最低窗口约 `0.5479`，另一个边界约 `0.7554`），撤回。
- 空 cache 直接延后到 TTL 的方案在重复四 tick burst 中把窗口吞吐降到
  `0.4`，撤回。
- 成功后 phase/pacing 方案虽然降低部分 idle visits，但 pulse 的
  `p99/mean` 变差（例如 `17.50→20.00`、`33.11→33.62`、`33.43→33.92`），
  或提高 service gap，撤回。
- 有界 high-cardinality phase 方案使 FOUR_TICK_BURST 的 idle visits 增加，
  并把 pulse 的需求等待/间隔从 `0/5` 推到约 `4/9`，撤回。
- 最新“按已知成功间隔直接预约下一次服务”的方案在固定诊断中使
  `FOUR_TICK_BURST` 窗口吞吐降至 `0.5842`；完整 quick 还出现 pulse P99
  `10～13 tick`、需求等待 `13 tick` 和 rate-switch 吞吐/压力失败，撤回。

因此当前仍没有生产候选可以进入 300 tick smoke 或正式五轮候选对比；没有
更新 acceptance 逻辑、场景、窗口、阈值、缓冲或物品语义，也没有把失败候选
的模型收益解释为真实毫秒收益。

#### 是否还有优化空间

有，但当前诊断已经说明“只调 watchdog 时间/phase”缺少足够信息：生产调度
无法可靠区分“真正孤立的单 tick pulse”和“FOUR_TICK_BURST 的第一拍”。
下一步最值得验证的单一方向是：由生产端实际产生新输出时提供一个有界的
需求唤醒提示，接口只在收到提示时提前唤醒，同时保留现有 watchdog 作为
漏信号、目标 outage、scheduler rebuild 和 hot restart 的回退。该方向若要
进入生产，仍必须先加入与现有 9 行相同的模型状态/所有权/恢复验证，再跑
真实 GameTest；本轮没有实现它。

### 12.9 2026-09-05 demand-wake 负结论与批量能源核算候选撤回记录

本轮按第 12.8 节的推荐方向完成了研究，并实现、验证了一个生产候选；
候选在模型口径全部通过，但正式五轮真实 GameTest 出现一致回归，
已按第 9、10 节规则撤回。生产代码最终恢复为与 `9897232b` 等价，
本节只记录结论与证据，未修改 acceptance 逻辑、场景、窗口、阈值或缓冲。

#### 需求唤醒（demand-wake）方向评估：当前不可落地，记录负结论

按第 12.8 节要求逐项检验后，该方向在本仓库无法给出可交付实现：

- 提示由哪个生产状态变化产生：无线连接的目标是任意第三方方块实体
  （基准夹具为 1024 个原版木桶）。“目标产生新输出”发生在目标容器内部，
  接口侧没有任何可观察钩子：AE2 `ExternalStorageStrategy.createWrapper`
  的回调只在 wrapper 自身注入/抽出时触发，不响应外部写入；NeoForge 对
  任意 `IItemHandler` 也没有外部内容变更监听。要给木桶类目标发提示只能
  全局拦截所有容器写入（mixin），风险面与本基准的授权修改范围不匹配。
- 不丢 wake、不重复转移：若强行实现，watchdog 保留为回退（唤醒只提前、
  不推迟截止期），提示为一次性边沿并在服务时清除，访问复用同一条
  extract 路径——语义上可以保住，但这不改变下面的可行性与收益结论。
- 没有新输出时哪些 idle visits 可以安全跳过：只有“提示通道可信”的
  连接才能跳过 watchdog 轮询；对第三方目标该通道不可得，因此不存在
  可证明安全的跳过集合，pulse 场景的 5-tick 需求等待预算不允许放宽。
- FOUR_TICK_BURST：连续热负载下提示命中的都是“下一 tick 本来就会
  访问”的连接，visits 与工作量都不会减少。
- 真实压力组实测为连续热负载：1024 连接每 tick 全部 productive，
  `configuredConnectionVisits=1024/tick`，采样窗口内无线 I/O 恒定约
  `19.7 ms/tick`，仅停产后最后 3 个样本衰减。提示没有收益空间。
- 模型镜像：模型 machine 可在 `produce()` 的空→非空边沿置 hint 并在
  同 tick 服务，所有权守恒不受影响；但该镜像不对应任何可交付的生产
  信号源，因此本轮没有实现，也没有为它保留测试或生产代码。

#### 候选：import 提取 pass 的 visit 级能源核算合并（已撤回）

真实基线数据显示每 visit 对 27 个 key 各调用一次
`PowerCostUtil.maxAffordable`（`extractAEPower(SIMULATE)`）与一次
`consume`（`MODULATE`），约 54 次 grid 能源服务遍历、每 tick 约 5.5 万次。
假设：当一次 `canAfford`（与 `maxAffordable` 同一谓词）能证明整批成本
上界加空闲储备可支付时，把逐 key 能源核算合并为整批一次 SIMULATE 与
一次 MODULATE；预算截断或证明不了时逐 key 路径原样回退。逐 key 等价性
由归纳证明（`need_K ≤ bound` ⇒ 每 key cap 都返回全额；批量计费等于
Σ 每 key 实际成本）。未混入 cache、phase、buffer、阈值或 acceptance 改动。

验证结果：

- `compileJava compileTestJava test wirelessInterfaceIoModelQuick
  wirelessInterfaceIoModel` 与 `wirelessInterfaceIoModelAcceptance` 全部
  PASS（模型不建模能源路径，改动不触及调度器）。
- 固定 9 行需求唤醒诊断候选前后逐字节一致：
  `benchmark-results/wireless-io-wake/head-9897232-recheck/`（与
  `head-9897232-baseline/` 一致）对比
  `benchmark-results/wireless-io-wake/hb-batched-import-power/`。
- 300 tick smoke（40/200 预热、300 采样）同参数 A/B 中候选更快
  （基线 mean/P95/P99/max `50.566/59.673/69.512/184.229 ms`、>50ms 170 个，
  候选 `47.572/55.908/67.133/139.730 ms`、>50ms 106 个；候选 JSON/CSV 保存在
  `benchmark-results/wireless-io-smoke-hb-batched-import-power/`）。该结论
  是冷 JIT 伪影：短运行的解释调用开销放大了能源调用成本。
- 正式五轮（200/1200，C1→S1→…→C5→S5）推翻 smoke 结论。原始结果在
  `benchmark-results/wireless-io-live-hb-batched-import-power/`（含归档的
  `live-comparison.md`）。压力组中位数（最差轮）与
  `wireless-io-live-current-head-baseline` 对比：

| 指标 | 基线中位（最差轮） | 候选中位（最差轮） |
|---|---:|---:|
| mean MSPT | 26.760493（27.007414） | 27.774114（28.032586） |
| P95 MSPT | 32.088900（32.511800） | 35.219100（35.523000） |
| P99 MSPT | 35.996400（36.342000） | 39.181500（40.163300） |
| max MSPT | 85.095900（93.369800） | 84.561200（106.779200） |
| wireless I/O P99 | 26.855700（27.409100） | 29.990000（30.295700） |
| 容量 TPS | 20 / 20 | 20 / 20 |
| >50 ms 比例 | 0.000833（0.000833） | 0.001667（0.001667） |
| GC 次数 / ms | 266 / 655 | 246 / 647 |
| 控制校正 mean/P99 | 26.318345 / 34.050500 | 27.426982 / 37.716200 |

- `compare-wireless-io-gametest-benchmarks.ps1` 判定 mean、P99、控制校正
  mean/P99 与 wireless I/O P99 全部回归且超出容差；控制组同期不升反降
  （中位 mean `0.335` vs 基线 `0.439`），排除机器状态漂移。五轮压力组
  逐轮全部劣于基线中位，方向一致，是真实回归。
- 机制解释：稳态 C2 内联后，小网格上每次 `extractAEPower` 近乎免费，
  合并 54 次调用的收益趋零；新增的成本上界遍历成为净增开销。冷 JIT
  smoke 的收益方向与热稳态相反——这再次说明最终性能结论只能来自正式
  200/1200 五轮，不能来自 smoke。

撤回即日生效：`scanImportKeys`/`extractExactImportKeys` 恢复原实现，
恢复后 `compileJava compileTestJava test wirelessInterfaceIoModelQuick
wirelessInterfaceIoModel wirelessInterfaceIoModelAcceptance` 全部 PASS，
工作树与 `93c96572` 逐字节一致。

#### 剩余失败与未执行项目

- 真实 GameTest 绝对门槛仍未通过：压力 mean > 25 ms、控制校正 mean/P99
  超标、wireless I/O P99 > 12 ms，与 12.8 节基线状态相同；本轮没有把
  模型 PASS 或已撤回候选的任何数字解释为性能通过。
- 本轮未运行 `wirelessInterfaceIoModelEndurance`、普通
  `runGameTestServer` transition 用例与手工世界校准（与 12.8 节一致）。
- 需求唤醒方向若要重启，前提是出现可信的目标侧变更信号（例如本模组
  自有方块实体接入提示接口），届时仍需按第 12.6/12.7 节契约与遥测、
  再按第 8、9、10 节流程验证。

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
- 常规性能 GameTest 仍固定为一个 AUTO import 接口、1024 个原版桶和 27 个 item key；
  本轮另增加 1024 桶 × 27 唯一 key 的高基数拒收/恢复 GameTest。它能实测生产路径的
  拒收、跨片 flush、背压和恢复，但仍不等价于任意第三方 capability，不能从中外推所有
  真实服务器性能。
- 模糊/反向过滤、fluid、Applied Flux FE、第三方机器 capability、部分插入和真实区块卸载属于真实服务器/现有专项测试互补项，不应从 item-only 固定桶模型外推。
- 绝对 MSPT 门槛以本项目目标服务器为准；更慢硬件仍必须记录控制组，但不能只凭相对增量忽略整服已低于 20 TPS。

## 16. 2026-09-05 用户目标与下一轮优化交接

用户明确要求：保持吞吐量和语义正确，尽可能降低延迟和服务器开销；允许修改、增加测试，并要求留下可供新 AI 继续执行的说明。本节将“降低 TPS”按服务器性能语境解释为降低 MSPT/掉 TPS 的风险、维持接近 20 TPS，而不是降低每秒 tick 数。

### 16.1 优化目标与不可交换的约束

- 硬约束：物品所有权与守恒、过滤/能源/插入抽取语义、每台机器与滑动窗口吞吐、恢复能力不退化。不能通过少生产、少搬运、丢弃积压、扩容槽位或缓冲来制造收益。
- 延迟必须分开报告：目标产出到抽取的 P50/P95/P99/max、最长需求等待，以及产出到真正进入 ME 网络的端到端延迟。当前调度模型主要覆盖前一段，不能将“抽进持久 buffer”当作“已进入网络”。积压 item-tick 和最终未服务量用于补充有限窗口的延迟统计。
- 在上述约束下，优先降低真实 P99 MSPT、超 50/100 ms tick 和无线 I/O P99，并降低平均 MSPT。候选也必须满足第 8、9 节的绝对预算和相对回归门槛；仅部分指标改善应标为阶段性结果。
- 调度 visits、idle visits、模型 work、GC/分配用于定位原因。idle service gap 可以增加，只要有需求时的等待、吞吐和恢复不退化。不得把减少 visits 或模型 elapsed 当作真实 TPS 收益。
- `capacityTps` 是 `min(20, 1000 / meanMSPT)` 推导的容量指标，不是独立测得的实际 TPS；`configuredConnectionVisits` 是配置连接数累计，不能证明每条连接每 tick 都 productive。

### 16.2 本轮新增可执行回归保护

生产代码基线是 `c42fcbf4`，其 `src/main` 与 `9897232b` 一致。本轮只更新测试与文档，没有实现新的生产优化。

新增 `WirelessInterfaceOptimizationGuardrailTest`，读取版本控制中的
`src/test/resources/wireless-io/optimization-outcomes-c42fcbf4.csv`，对原有固定 9 行诊断逐场景检查：

- 窗口/最差机器吞吐不能降低，生产受阻次数不能增加；
- P99 抽取延迟、最长需求等待、积压 item-tick、窗口结束剩余输出不能增加；
- 产出量、抽取量不能减少，并继续验证所有权守恒与非负状态；
- 场景集合必须与基线完全相符，不能悄悄删掉难通过的行。

固定值来自优化前的 `build/reports/wireless-interface-io-wake/demand-wake-optimization.csv`，与第 12.8 节记录对应。此文件不是测试运行时自动重录的快照。吞吐浮点容差仅为 CSV 八位小数的 `1e-8` 舍入误差。

九行的 visits/work 仍由原遥测输出，不在新测试中逐值冻结；原有完整模型工作量门槛仍保留。新测试允许实现改变和延迟改善，不允许用更多等待换取更少访问。它补充而不替代 25 个语义/178 个压力场景以及真实服务器验收。

两点需要正确解释：outage 行包含目标不可用期间的原始损失（需求等待 40 tick、受阻 39,936 次），不能要求凭空消除不可用时间，但不允许新增损失；hashed IO-first pulse 行在固定 80 tick 窗口末仍有 3,904 个输出，不能删除末尾样本来隐藏这些物品。新测试保留并约束这两类结果。

另修正原遥测中 `P99 >= mean` 的无效断言：罕见尖峰可以让平均值高于 P99。现在检查非负以及 `max >= P99`、`max >= mean`，避免拒绝统计上合法的分布。此修改没有放宽业务延迟或真实性能预算。

运行方式（新保护属于普通 `test`，不要只跑压力模型任务）：

```powershell
.\gradlew.bat test --tests '*WirelessInterfaceDemandWake*' --tests '*WirelessInterfaceOptimizationGuardrailTest' --rerun
.\gradlew.bat test wirelessInterfaceIoModelAcceptance
```

本轮实际执行以上两条命令成功：普通测试 798 个、0 failures、0 errors；完整模型严格验收通过（25 个语义场景、178 个压力场景）。本轮未运行 endurance、真实 GameTest transition 或五轮实测；生产代码未改，因此不宣称新增毫秒收益。验证日志位于 `build/optimization-handoff-verification.log`（本地生成文件，不作为版本控制基线）。

### 16.3 优先待验证的代码问题与测试缺口

以下是交接时记录的代码审查发现；本轮完成的修复和实测结果以 16.5 为准，仍未完成的
覆盖缺口保留在条目中：

1. `flushImportBuffer` 的 16,384 是初始片大小，后续 `flushLimit` 会随新增键增大；没有新增键时可刷新全部剩余积压。验证停产后大积压、持续新增唯一键、部分接收下的每 tick 尝试数、排空时间和端到端延迟。不能直接改成固定硬上限而放任输入速率高于排空速率。
2. 分片内同类型全部拒收时，可能锁住整个类型 20 tick，即使未访问尾部仍有可接收键。本轮已用按键可编程的真实生产 `MEStorage` helper 覆盖“前 16,384 键拒收、尾部同类型接受”、混合类型、部分插入、拒收恢复；持续高基数的每 tick 成本仍见 16.5，真实第三方 capability 端到端覆盖未完成。
3. `runExport` 的 `fastRejectRetry` 已增加 `IOSpeedMode.FAST` 门槛，并用生产 helper 检查 NORMAL 不会选中 FAST 重试；现有模型覆盖 FAST 的 IMPORT/EXPORT、满载/目标短长期不可用和调度切换。NORMAL + EXPORT 的完整真实方块实体模式切换场景仍未补齐，不能据此扩大性能结论。
4. 当前主压力模型是 FAST item I/O，真实性能夹具是单接口 1024×27 桶。新增真实 export、双向、多接口、目标冷热切换、高基数/拒收及端到端延迟观测，才能支持更广泛的优化结论。流体、过滤、能源不足、区块卸载、持久化/重载还需对应生产路径测试。

优先用同负载 JFR/分配采样或可关闭的阶段计时区分 wrapper 扫描、抽取、能源服务、buffer 插入与对象分配；诊断轮与正式计时轮分开。持续热负载的主要机会可能是降低每次服务成本，具体优先级由采样决定。第 12.9 节的 C2 内联机制解释应视为待 profiler 验证的解释；五轮回归数据本身不等于已证明 JIT 原因或排除了所有环境漂移。

已有 phase/watchdog 调参、空 cache TTL 延迟、按历史成功间隔预约以及批量能源核算存在失败记录。没有新证据不要原样重做。需求唤醒只对能提供可信变更通知的目标考虑；一般第三方容器保留 watchdog，不使用虚构的监听 API。

### 16.4 新 AI 的执行与记录要求

允许为真实覆盖缺口修改测试、夹具和增加场景，但要保留旧场景、原始样本和比较结果。若发现夹具错误，先用独立证据证明错误，再对基线与候选同时重跑；不能只改候选的阈值、窗口或预热设置。

每个候选只改一个可解释的热点。记录代码身份（包括未提交 diff）、Java/硬件/电源策略、负载、原始 CSV/JSON、模型和实测结论。先过新增保护与完整模型，再运行必要的真实语义测试；最终用相同环境的五轮控制/压力组、200 tick 预热与 1200 tick 采样比较。已有历史数据用于参考，不能替代新环境下的配对基线。失败候选撤回其生产改动，保留有效测试与负结论。

### 16.5 2026-09-05 实际热点定位、候选撤回与最终验证

本轮从交接提交 `e4c7bf76` 的实际工作树开始，分支为
`test/wireless-interface-io-benchmark`。仓库根目录没有适用的 `AGENTS.md`；发现的
`Source Code/1.21.1/SuperFactoryManager-1.21.1/docs/AGENTS.md` 属于嵌套的第三方工程，
未套用到本仓库。交接提交本身没有生产代码修改。最终未提交身份为：生产跟踪 diff
`ce948744ac34cee47ce43c6f2c374433ccc392a1`，新增生产路径测试文件 SHA-1 为
`10a7af51521fb4e76e528afee516be4d9cad5fa2`；工作树中的其他既有修改均未覆盖。

#### 诊断证据

正式计时之外另跑了延迟启动 JFR：
`benchmark-results/diagnostics/current-head-jfr-delayed/wireless-io-33212.jfr`。
该轮有效时间约 18 秒，包含启动/世界成本，只用于定位，未混入五轮 JSON/CSV。JFR
分配站点中 `BlockPos.relative` 占 41.22%（主要是 GameTest 夹具/世界工作），
`Object2LongOpenHashMap.<init>` 占 20.64%；调用栈对应 `scanImportKeys → freshScanBuffer`
的 `KeyCounter` 临时 map 分配。CPU 样本中 `ServerChunkCache.getChunk` 占 40.72%，
`Long2ObjectLinkedOpenHashMap.get` 占 11.93%，`EnergyService.extractProviderPower`
占 0.82%，`scanImportKeys` 占 0.45%，`isImportAllowed` 占 0.60%。因此没有把
`configuredConnectionVisits` 当作实际 capability 访问证明，也没有把夹具的
`BlockPos.relative` 误报成无线接口本身的唯一热点。

#### 保留的生产修改与语义保护

- `flushImportBufferEntries` 是生产 flush 路径的可测试提取；类型锁只有在本片已完整
  检查且没有未访问同类型正积压时才建立。前 16,384 个同类型键全部拒收时，尾部
  可接收键不会再被错误锁住；部分插入、混合类型、拒收恢复仍按键保留所有权。
- `runExport` 的拒收快速重试现在必须同时满足 `IOSpeedMode.FAST` 和失败次数门槛，
  NORMAL 不会意外获得 FAST 重试。现有 FAST import/export 的目标拒收、短/长期
  outage 和模式切换模型继续通过；NORMAL 的完整真实方块实体双向场景仍是后续覆盖项，
  因而本轮不把这条小修复夸大成全模式语义验收。
- 新的 `OverloadedInterfaceBufferPathTest` 直接调用上述生产 helper 和可编程
  `MEStorage`，没有复制一份简化生产逻辑。它覆盖：前 16,384 键拒收/尾部同类型
  接收、部分插入、混合类型、拒收恢复、FAST/NORMAL 重试门槛，以及 49,152 键停产
  积压的排空与逐键守恒。

高基数测试实际观测到：首 tick 尝试 16,384 键、剩余 32,768 键；下一 tick 为保持
排空能力会尝试 32,768 键并完全清空，未丢失 49,152 个键。这确认 16,384 只是首片
预算，不是硬上限；本轮没有用简单固定上限制造 MSPT 收益，也没有放任输入速率高于
排空速率。持续高基数/部分接收的生产路径仍需以后用真实第三方 capability 做端到端
延迟观测。

#### 两个有证据候选的正式对照

基线和候选均由 `scripts/run-wireless-io-gametest-benchmark.ps1` 以 5 轮、每轮
预热 200 tick、采样 1200 tick、独立 GameTestServer JVM 完成。基线 manifest 为
`e4c7bf76-dirty-f6aa23ee29bb`，原始目录为
`benchmark-results/wireless-io-live-baseline-f6aa23ee29bb`。候选一只做了 JFR 指向的
远端连接重复校验消除，目录为
`benchmark-results/wireless-io-live-candidate-b8f405ffad70`；它的 compare 状态为
`NO_MEASURABLE_IMPROVEMENT`，均值反而 `26.459880→26.525482 ms`，已撤回。

候选二只把 `freshScanBuffer` 的新建 `KeyCounter` 改为 `clear()` 后复用，正式目录为
`benchmark-results/wireless-io-live-candidate2-6f469e174337`，manifest 身份为
`e4c7bf76-dirty-6f469e174337`。它降低了堆和 GC，但没有达到项目定义的可测优化门槛，
因此也已撤回生产改动。官方比较命令的结果为 `FAIL / NO_MEASURABLE_IMPROVEMENT`；
原始报告仍保留在 `build/reports/wireless-interface-io-live-comparison/live-comparison.md`。

| 压力组中位数 | 基线 | 候选二（已撤回） | 变化 |
|---|---:|---:|---:|
| mean MSPT | 26.459880 | 26.141824 | -1.20% |
| P95 MSPT | 31.7697 | 31.5660 | -0.64% |
| P99 MSPT | 36.0126 | 35.0907 | -2.56% |
| max MSPT（五轮 max 的中位数） | 84.1662 | 75.5524 | -10.23% |
| >50 ms tick | 1/1200（0.0833%） | 1/1200（0.0833%） | 持平 |
| >100 ms tick | 0 | 0 | 持平 |
| 无线 I/O P99 | 26.3064 ms | 26.1739 ms | -0.50% |
| GC 次数 / GC ms | 240 / 610 | 238 / 577 | -0.83% / -5.41% |
| GC 比例 | 1.0167% | 0.9617% | -5.41% |
| 峰值已用堆 | 905,618,864 B | 733,424,416 B | -19.01% |
| `capacityTps` | 20.000 | 20.000 | 持平（由 mean MSPT 推导） |

逐轮控制校正的中位数为 mean `26.127743→25.786079 ms`、P99
`34.5911→33.4790 ms`；控制组自身 mean/P99 为 `0.332137/1.5539` 到
`0.350606/1.6117 ms`，所以低负载控制波动也保留在原始数据中。候选的
`configuredConnectionVisits=1,228,800`、`interfaceCalls=1,200` 与基线完全相同；
这只说明配置列表累计访问量相同，不能推出每次都访问了远端 capability 或都是
productive。`capacityTps=20` 同样只是 `min(20,1000/meanMSPT)` 的容量推导，不是
独立 TPS 实测。

比较结果的绝对门槛：P95、P99、>50 ms、>100 ms、GC 比例和推导容量在本表满足，
但 mean MSPT `26.141824>25`、控制校正 mean `25.786079>10`、控制校正 P99
`33.4790>15`、无线 I/O P99 `26.1739>12`，所以绝对性能验收失败。相对门槛没有
发现候选回归，但改善不足以被 compare 认定为 `MEASURABLE_IMPROVEMENT`；不能把
堆/GC 改善宣称为正式性能通过。

#### 吞吐、守恒、积压和两段搬运延迟

固定九行 outcome 与交接基线逐字段比较为 0 个差异；窗口/最差机器吞吐、压力事件、
P99 抽取延迟、最长需求等待、backlog item-ticks、最终输出、产出和抽取量均未变。
完整 `wirelessInterfaceIoModelAcceptance` 的 25 个语义场景和 178 个压力场景全部
PASS，`wirelessInterfaceIoModelEnduranceAcceptance` 的 20,000 tick import/export/
bidirectional 场景也全部 PASS。

模型报告仍把两段延迟分开。例如 `import-fast-1024x32-continuous` 中产生/回收
`9,830,400`，但真正进入 ME 网络为 `9,748,480`，结束时持久 import buffer 仍有
`81,920`；`import-fast-1024-burst-20t` 则产生、回收和入网均为 `819,200`，结束
buffer 为 0，最大 buffer key 数为 `32,768`。因此“产出→接口 buffer”不能写成完整
“产出→ME 网络”延迟；当前真实 MSPT JSON 没有逐物品端到端时间戳，模型的
`network_imported`、`final_import_buffer`、backlog item-ticks 和需求等待只能作为
补充，不能替代真实第三方容器端到端观测。

#### 最终测试状态、环境与后续

- 选择性回归：`test` 803 个、0 failures、0 errors；包含交接的 DemandWake/Guardrail
  与新增 buffer path 测试。
- `test wirelessInterfaceIoModelAcceptance`：通过；25/178 全部通过。
- `wirelessInterfaceIoModelEnduranceAcceptance`：通过。
- 普通 `runGameTestServer`：只失败既有 `fastimport256transitions`，阻塞生产率
  `0.1328125>0.001`；日志中 256 connections/valid、buffer=0、最终 network 有量，
  与文档既有 256 连接 transition 失败一致，未发现本轮新增的第二个 GameTest 失败。
- 正式环境：Windows 11，i7-13650HX，20 logical processors，AC Balanced，Gradle
  8.8，Eclipse Adoptium Java 21.0.11；每个正式场景均为独立 JVM。原始 JSON/CSV、
  manifest 和 compare 报告均保留在上述目录。

本轮没有保留可宣称的 MSPT 优化候选。未执行/未保留的项目包括：硬性固定每 tick
buffer 上限、需求唤醒 API、phase/watchdog/空 cache TTL/历史 cadence 调参、批量能源
核算，以及未具备可信变更通知的第三方容器 demand-wake。下一步应在不改变排空延迟和
吞吐的前提下，为高基数/拒收真实 capability 增加端到端时间戳与阶段计时，优先验证
buffer 全量排空的单 tick 成本；同时补充 NORMAL + EXPORT、满载/短长期 outage 和
模式切换的真实 GameTest，再以同样五轮口径重新筛选单一热点候选。

### 16.6 2026-09-05 e5b177b4 高基数拒收复核与实际修复

本节是对 16.5 的增补。审查对象为提交 `e5b177b4` 的生产实现；主工作树开始时
干净，现有交接修改没有被覆盖。高基数正式对照使用同一份夹具、探针、Java、模组
顺序、200 tick 预热、1200 tick 采样和 5 轮独立 JVM；基线只保留 e5b177b4 的生产
代码，夹具/探针/脚本移植到临时基线工作树仅用于保证负载一致。

#### 复现与修复

新增测试直接调用 `flushImportBufferEntries` 这一生产 helper，并使用可编程的真实
`MEStorage` 接口，不复制一份简化算法。当前验证覆盖：

- `16,385` 个同类型键，前片拒收、尾部可接收；尾部被访问前不建立类型锁；
- `16,385` 个同类型键全部拒收，多轮刷新期间持续追加新键，跨片完成后建立类型锁，
  接收恢复后逐键排空 `16,387` 个键；
- `49,152` 个积压中出现部分接收和拒收时，首片及后续片的 `visitedKeys` 都严格为
  `16,384`，后续拒收片不再扫描整个剩余 buffer；无拒收的排空路径仍允许第二片一次
  处理剩余 `32,768` 个键；
- `ProgrammableStorage` 的无限容量、有限容量耗尽、同键连续插入和 `SIMULATE` 不
  改变状态；无限容量不再被 `merge(key, -accepted)` 写成负数。

生产实现新增按 `AEKeyType` 维护的 pending/untested/progress 计数。计数在新增键、
逐键尝试、部分接收、删除和加载/清空时更新；完整拒收判定跨多个片累计，不再通过
遍历 untouched tail 推断类型。一个拒收片发生后，继续片保持 `16,384` 的真实接口
访问预算；没有拒收的路径不受这个限制，可以保留快速排空。模式切换和无线唤醒会
重置这一观察轮次，持久化加载会重建计数，因此新增键、部分接收和模式变化不会复用
过期判定。类型锁只影响后续抽取调度，恢复后的真实成功插入会立即移除锁。

#### 真实生产路径覆盖

新增 `fastImportHighCardinalityRejectRecovery` GameTest：1024 个原版桶、每桶 27 个
带唯一自定义名称的 item key（共 27,648 个键），先用有限 1K cell 填满网络使高基数
插入真实拒收；生产者在空桶上持续补货，网络恢复后替换为无限 cell，并以最终机器、
buffer、ME 网络守恒和键数检查排空。控制组不生产物品，压力组记录产出、抽取、入网、
当前/最大 buffer、最大键数，以及产出到抽取和产出到入网的 tick 延迟。诊断延迟统计使用
固定直方图，不在正式 tick 中对数百万 item 逐个分配或排序。

高基数正式原始报告保留在：

- `benchmark-results/wireless-io-high-baseline-e5b177b4/`
- `benchmark-results/wireless-io-high-candidate-e5b177b4/`

两组均为 5 个控制/压力对，且所有 10 个高基数 GameTestServer 运行通过。压力组五轮
中位数如下；这些数字是实测记录，不等于已经通过项目的性能优化门槛：

| 压力组中位数 | e5b177b4 基线 | 当前候选 | 解释 |
|---|---:|---:|---|
| mean MSPT | 5.006613 ms | 0.889803 ms | 候选建立拒收背压后，工作量已改变 |
| P95 / P99 MSPT | 42.0187 / 47.6081 ms | 1.7165 / 4.6285 ms | 不能当作等吞吐收益 |
| >50 ms tick | 7 | 2 | 同上 |
| 无线 I/O P99 | 38.0168 ms | 3.5993 ms | 不是同产出量对照 |
| GC 次数 / GC ms | 60 / 236 | 4 / 23 | 受拒收期间产出差异影响 |
| 峰值已用堆 | 898,009,168 B | 813,825,376 B | 不能单独归因于算法改进 |
| 产出 / 抽取 / 入网 | 417,595,392 / 417,595,392 / 417,595,392 | 3,538,944 / 3,538,944 / 3,538,944 | 两者均守恒，但负载量不同 |
| 最大 buffer item / 最大 buffer key | 417,595,392 / 27,648 | 1,769,472 / 27,648 | 基线持续抽取，候选在拒收时背压 |
| 产出→抽取 P95 / 产出→入网 P95 | 1 / 226 tick | 241 / 244 tick | 批次与背压状态不同，不能比较为延迟回归/收益 |

因此本轮不宣称 MSPT、GC 或端到端延迟提升。表中的重要结果是：候选没有通过丢弃
物品制造收益，压力组的产出、抽取、入网逐项守恒；它阻止了拒收期间继续把目标输出
无限搬入 buffer，并在恢复后排空。若要形成“保持相同产出量下的性能提升”结论，仍
需另跑一个固定产出量、固定恢复时刻的高基数配对基准；本轮正式五轮虽然完成，但
拒收背压改变了生产者可继续产出的数量，不能替代该对照。

#### 失败区分与最终状态

第一轮正式候选曾因夹具在第二批生产时强制要求 1024 个桶全部为空而失败；日志明确
为 `high-cardinality target 0 was already occupied`，不是生产语义失败。夹具已改为只
在真实空桶上补货、保留被背压占用的桶；修复后基线和候选的 5 轮均通过，未新增高基数
GameTest 失败。历史普通 `fastimport256transitions` 失败仍按 16.5 记录为既有失败，
没有在本轮重新分类为回归。

本轮最终验证命令均成功：

```powershell
.\gradlew.bat test --tests '*WirelessInterfaceDemandWake*' --tests '*WirelessInterfaceOptimizationGuardrailTest' --tests '*OverloadedInterfaceBufferPathTest' --rerun
.\gradlew.bat test wirelessInterfaceIoModelAcceptance
.\gradlew.bat wirelessInterfaceIoModelEnduranceAcceptance
```

目标结果保持不变：固定九行 outcome 未放宽，原有 25 个语义场景、178 个压力场景、
需求唤醒、过滤/能源/所有权/持久化/恢复检查保留；模型和 endurance 全部 PASS。仍未
完成的项目是固定等产出量的高基数五轮性能对照、第三方 capability 的端到端时间戳、
新的正式 profiler 轮，以及 NORMAL + EXPORT 的完整真实模式切换场景。任何后续性能
结论都必须先补齐这些对照，不能把本节的背压差异写成无条件吞吐提升。

### 16.7 2026-09-05 ce601b9d 持续补货背压、两段延迟与等负载复验

本轮审查对象是提交 `ce601b9d`，从分支
`test/wireless-interface-io-benchmark` 的实际工作树继续。先检查工作树并保留了已有
修改；仓库根目录没有适用的 `AGENTS.md`。唯一发现的
`Source Code/1.21.1/SuperFactoryManager-1.21.1/docs/AGENTS.md` 属于嵌套第三方工程，
内容为转交其自身 1.19.2 说明，未套用到本仓库。本节之前已阅读并以 16.6 的结论为
起点，尤其保留了“高基数拒收旧对照不等负载、不能据此宣称收益”的限制。

#### 先复现：已有键补货会重启拒收观察

旧实现的真实生产 helper 测试先复现了问题：在 `49,152` 个同类型键全部拒收时，
每轮给已访问键和未访问键追加数量，并同时加入新键，旧的
`onBuffered(key, false)` 会把 `untestedKeys` 重置为新的 `pendingKeys`。实际测试在
`attempts=49,152` 后仍剩 `untested=2`，而 buffer 已到 `49,164`，拒收遍历不能收敛。
这不是由模型推断出来的故障。

新增的 `OverloadedInterfaceBufferPathTest` 共 11 个测试，直接调用生产
`flushImportBufferEntries`，以可编程 `MEStorage` 实现拒收、部分接收、恢复和混合类型，
不复制一份测试算法。覆盖内容包括：

- `16,385` 和 `49,152` 个同类型键全拒收，逐片访问不超过 `16,384`；
- 每轮分别给已测试键、未测试键追加数量，同时追加新键，观察轮最终收敛并建立类型锁；
- 前片拒收、未访问尾部可接收时，尾部访问前不建立锁；网络恢复后逐键守恒；
- 部分接收、混合类型、FAST/NORMAL 重试门槛，以及实际模式/唤醒重置观察轮次。

最终生产修复按类型维护 `pendingKeys`、`untestedKeys`、进度和拒收轮状态。已有键的
数量合并不再使整轮观察重启；拒收轮开始后新出现的键只进入稀疏 `lateKeys` 集合，
完成对应新键尝试后释放集合。任何进度都会重建待观察计数；模式切换和无线唤醒仍会
显式重置观察轮。这样拒收片仍保持有界的 `16,384` 真实尝试，不扫描 untouched tail，
不丢弃 buffer，也不无条件锁住整个类型；未访问的可接收尾部必须先被尝试。无拒收的
排空路径仍允许一次处理剩余尾部，而不是被简单的每 tick 上限削弱。

#### 延迟口径修正与实际观测

`HighCardWorkloadState` 的诊断分支现在按目标/键观察真实桶库存下降，而不是把每个
tick 的抽取增量误当成累计抽取量。一次诊断运行确实暴露了这个观测器 bug：在
`extracted=0, buffered=1,769,472` 的空闲观察 tick 产生了假负网络归属；修复为使用
tracker 的累计值后，等负载和高基数诊断均重跑通过。该失败运行被排除，没有当作生产
故障或性能数据。

两段数据明确分开：

- 产出 → 接口 buffer：诊断模式逐目标/逐键观察 barrel 数量下降，归属为
  `target-key-observed`；这是本夹具可观测范围内的真实段延迟。正式计时不执行每 tick
  的目标/键扫描，报告写 `not-recorded-formal`，避免诊断开销污染 MSPT。
- 产出 → 真正进入 ME 网络：从累计抽取与当前 buffer 的差额得到网络归属，并用生产
  批次队列做 FIFO 估算，报告明确写 `aggregate-delta-fifo-estimate`，不能把它当作
  真实逐目标/逐键 P99。未完成批次数、最大等待、当前/最大 buffer 和最终剩余量仍保留。

同键合并的约定写入报告：`earliest_active_batch_owns_merged_amount`。新增的真实
`fastImportOutOfOrderTargetAttribution` 会先阻塞早目标、再生产晚目标，并实际切换
`OFF → AUTO`；候选和干净 ce601b9d 基线都验证晚目标先完成，证明不能用全局 FIFO 把
这个分布当作真实搬运顺序。高基数拒收诊断报告记录了 buffer P99 `251` tick、网络段
估算 P99 `243` tick（恢复阶段）；两者含义不同，不能合并成一个端到端 P99。

诊断原始产物为：

- `benchmark-results/wireless-io-equal-recovery-diagnostics-final-f6e3/`；
- `benchmark-results/wireless-io-high-reject-diagnostics-final-f6e3/`。

高基数拒收 stress 诊断中计划产出 `424,673,280`、实际产出 `12,386,304`，记录了
`238,592` 次源端受阻；这是保留背压行为场景的证据，不是等负载性能对照，也没有把
生产计划随候选抽取速度静默减少。

#### 固定负载的五轮真实对照

新增并固定了三种真实 GameTest 夹具：恢复、部分接收恢复、网络可接收期间的持续
负载。control 与 stress 使用完全相同的目标集合、初始状态、生产计划和恢复 tick；
区别只保留为正式探针是否计时的 control/stress 运行。没有扩大生产槽位或接口 buffer，
部分接收的有限 1K cell 保留在 drive slot 0，恢复时把无限 cell 放到 slot 1，避免覆盖
已被接受的物品。

基线使用干净 `ce601b9d` 生产代码，候选为本节最终生产改动；每个 profile 均为 5 个
control/stress 对、每次独立 JVM、200 tick 预热和 1200 tick 采样。所有 manifest 的
完整 Git HEAD 都是 `ce601b9d88e2f872c3db4fe550037870ef5aebb9`，Java 为 21.0.11，
服务端为 1.21.1；基线与候选 JSON/CSV 分目录保存。主要目录为：

- recovery：基线 `benchmark-results/wireless-io-equal-recovery-baseline-f5/`，候选
  `benchmark-results/wireless-io-equal-recovery-candidate-final-f6e4/`；
- partial：基线 `C:\Project\AE2-Lightning-Tech-baseline-ce601b9d\benchmark-results\wireless-io-equal-partial-baseline-f5/`，
  候选 `benchmark-results/wireless-io-equal-partial-candidate-final-f6e4/`；
- sustained：基线 `C:\Project\AE2-Lightning-Tech-baseline-ce601b9d\benchmark-results\wireless-io-equal-sustained-baseline-f5/`，
  候选 `benchmark-results/wireless-io-equal-sustained-candidate-final-f6e4/`。

每个目录有 10 份完整 JSON 和 10 份逐 tick CSV，`samples=1200`、`partial=false`。固定
负载守恒为：恢复/部分接收均计划、实际、抽取、入网 `1,769,472`，共 `27,648` 个
键，恢复 tick `1300`，最终剩余 `0`；持续负载均为 `30,081,024`，固定键集合、
80 tick 周期，计划与实际相等，最终剩余 `0`。三个候选组的最差窗口/目标吞吐均为
`1.0`，没有用大量空闲 tick 稀释平均值。

stress 五轮中位数如下，单位为 ms；百分比是候选相对基线的变化：

| Profile | mean MSPT 基线 → 候选 | P95 基线 → 候选 | P99 基线 → 候选 | wireless I/O P99 基线 → 候选 | GC ms 基线 → 候选 | 峰值堆基线 → 候选 |
|---|---:|---:|---:|---:|---:|---:|
| recovery | 0.865868 → 0.826980（−4.49%） | 3.2815 → 3.1597 | 5.3789 → 4.2870（−20.30%） | 4.7339 → 3.8738（−18.17%） | 58 → 47 | 1,039,784,880 → 872,294,448 B |
| partial | 0.899162 → 0.870999（−3.13%） | 3.2782 → 3.2531 | 4.8292 → 5.0252（+4.06%） | 4.1738 → 4.1363（−0.90%） | 133 → 59 | 1,475,273,920 → 834,754,560 B |
| sustained | 0.807921 → 0.772669（−4.36%） | 4.978499 → 4.8833 | 7.3774 → 7.7356（+4.86%） | 6.087601 → 6.0149（−1.19%） | 81 → 35 | 1,596,439,608 → 715,554,912 B |

五轮 `max MSPT` 中位数依次为 recovery `67.1899 → 62.1531`、partial
`64.8956 → 59.9115`、sustained `14.9253 → 12.6533`；超过 50 ms 的 tick 为
`1/1`、`1/1`、`0/0`（基线/候选）。GC 暂停占 1200×50 ms 采样时长的比例分别为
recovery `0.0967% → 0.0783%`、partial `0.2217% → 0.0983%`、sustained
`0.1350% → 0.0583%`。因此三组的实测 MSPT、尾部和 GC 绝对值均在既定门槛内；
这不改变 sustained 的相对 P99 回归，也不把由 mean MSPT 推导的 `capacityTps` 当成
TPS 实测。

完整比较器结果为：recovery `PASS / MEASURABLE_IMPROVEMENT`，partial
`PASS / MEASURABLE_IMPROVEMENT`，sustained `FAIL / NO_MEASURABLE_IMPROVEMENT`。
sustained 的控制校正 P99 从 `−0.453602` 变为 `0.083100`，超过既定 `0.5 ms` 绝对
容差；partial 和 sustained 的原始压力 P99 也分别上升 `4.06%`、`4.86%`，虽未超过
回归容差，不能被平均 MSPT、GC 或堆峰值掩盖。比较报告保存在：

- `benchmark-results/wireless-io-equal-recovery-comparison-final-f6e4.md`；
- `benchmark-results/wireless-io-equal-partial-comparison-final-f6e4.md`；
- `benchmark-results/wireless-io-equal-sustained-comparison-final-f6e4.md`。

因此本轮的真实性结论是：语义、守恒、生产计划和恢复吞吐通过；恢复/部分 profile
有实际相对改善证据，但持续负载 profile 未通过完整回归门槛，不能宣称该生产候选在
整体上降低了 MSPT、掉 TPS 或端到端延迟。报告中的 `capacityTps=20.000` 均明确标为
`derived_from_mean_mspt_not_measured_tps`，没有用它替代实测 TPS，也没有用配置连接数
替代 capability 访问证据。

#### 最终验证、已知失败与未完成项

本轮最终验证包括：

- `OverloadedInterfaceBufferPathTest`：11/11 PASS；
- `test wirelessInterfaceIoModelAcceptance`：25 个语义场景、178 个压力场景全部 PASS；
- `wirelessInterfaceIoModelEnduranceAcceptance`：20,000 tick import/export/bidirectional
  全部 PASS；
- 固定九行 outcome 未改动，原有语义/压力矩阵未删除或放宽；
- 最终候选普通 `runGameTestServer` 与干净 ce601b9d 基线均为 6 个测试中 5 个 PASS，
  只有既有 `fastimport256transitions` 失败。基线阻塞率 `0.1337209302`，候选
  `0.1337512112`，状态、连接数和 buffer 状态一致且都最终有网络量；这只是
  `0.0000302809` 的测量差异，按既有失败归类。新增 out-of-order、模式切换及其它生产
  覆盖没有新增回归；
- `wireless-io-equal-recovery-diagnostics-final-f6e3` 和
  `wireless-io-high-reject-diagnostics-final-f6e3` 均真实服务端通过。

保留的失败候选和负结论包括：逐拒收键 full `HashSet`、释放时机变体和自定义完整
开放寻址集合曾分别增加堆/GC或出现相对回归；“全局无 untouched gate”的标量方案又
无法保证 `>16K` 尾部收敛。没有重复缺乏新证据的 phase/watchdog、cache TTL、历史
cadence 或批量能源核算方案。

未完成项仍明确保留：第三方真实 capability 的逐物品端点时间戳、正式 profiler/JFR
对最终候选热路径的新一轮采样、NORMAL + EXPORT 的完整真实模式切换、以及把持续负载
控制校正 P99 回归消除后的下一候选。正式计时故意不采集逐目标最大 item buffer 和
逐目标网络 P99；这些只能在诊断模式或加入可信端点事件后补齐。下一轮应继续以实际
刷新/扫描/抽取/类型状态维护/对象分配成本为证据选择单一候选，而不是先固定每 tick
上限或以空闲样本制造收益。
