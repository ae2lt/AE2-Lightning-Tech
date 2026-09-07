# alpha.3 无线接口测试移植

移植来源：`test/wireless-interface-io-benchmark` 的
`5897ebb907d9d77e4bb3fa44f94a2c77a26bfb32`。
被测 alpha：`16c4c95d47f10b6808104f183ec8eb6b27485029`。

仅移植真实 AE 网络夹具、语义测试、可选计时探针和性能比较器；
保留 alpha.3 的调度与传输实现。原有 alpha / Pigmee 测试在专用性能运行中直接跳过，
避免创建其他活动网络污染采样；普通 GameTest 仍执行这些回归测试。
`WirelessInterfaceGameTests`、`WirelessInterfaceExportGameTests`、
`WirelessIoPerformanceProbe` 及 Windows 脚本直接取自上述固定提交。
没有移植 test 分支的另一套调度算法或纯 Java 调度模型。

## 复现

要求 Java 21，依赖已下载到 Gradle 缓存，两个独立工作树。
初始化脚本为双方使用相同的最小运行依赖、512 MiB 初始堆和 2 GiB 最大堆。
专用性能任务仅编译、注册移植的两个 GameTest 类，双方均为 23 项；
比较脚本记录并校验注册数量。普通语义运行仍包含 alpha 自己的回归测试。

```sh
./gradlew test compileJdbJava --offline --no-configuration-cache
./gradlew runGameTestServer --offline --no-configuration-cache \
  -I scripts/wireless-io-benchmark.init.gradle

python3 scripts/run-wireless-export-benchmark.py \
  --baseline /absolute/path/to/test-worktree \
  --profiles export-empty-1024 export-mismatch-1024 export-continuous-256x27 \
  --runs 5 --output build/reports/wireless-export-ab
```

输出目录必须是新目录，以免覆盖旧结果。每轮按 control/stress 顺序，逐一运行两版，
偶数轮交换版本顺序；不同时运行两台服务器。每次 Gradle 任务启动新的 GameTestServer JVM。
保存源码散列、Git HEAD/dirty 标记、命令日志、JSON 及逐 tick CSV；运行期间拒绝源码变化。
`--runs 1` 仅用于试跑，不能作为五轮验收。

三种场景均预热 200 tick、采样 1200 tick，外层计时包括 I/O 资格检查。
空配置和类型不匹配按每版十次空闲重复测量，不做 control 相减；持续发配按五组配对控制。
使用原比较器的吞吐、守恒、尾延迟及内存门槛，I/O P99 预算为 4 ms。
GameTest 按其服务器运行方式推进 tick；capacity TPS 是根据测量耗时计算的容量指标。
这些测试不代表包含第三方机器和完整模组包的实际服务器 MSPT。

## 初始语义结果（2026-09-07）

- alpha 普通 JUnit：897 项通过，0 失败、0 跳过。
- alpha 注册 40 项 GameTest，其中 2 项失败；性能专用入口在普通模式跳过。
- test 基线注册 23 项 GameTest，无失败；其中也包含跳过的性能专用入口。
- 新增五项导出配置语义测试全部通过；组件变体、限量/无限量、类型切换、
  独立导入及缓冲守恒的断言未放宽。

| 导入侧测试 | test 基线 | alpha.3 |
|---|---:|---:|
| `fastImportColdOutputAllPhases`，最慢输出至缓冲 | 5 tick | 19 tick（FAIL，上限 6） |
| `fastImport256Transitions`，最长连续生产阻塞 | 4 tick | 24 tick（FAIL，上限 5） |
| 同一变速场景，最低目标稳态吞吐 | 100% | 77.78%（门槛 99%） |
| 同一变速场景，最长输出至缓冲 | 5 tick | 63 tick（门槛 6） |

这是两项新增暴露的导入等待/吞吐问题，与先前供应器压力测试保留的两项调用成本失败不同。
保留原测试失败，未通过跳过测试、修改消费计划或放宽门槛来消除差异。

## 待修复

- FAST 冷启动轮询：alpha 长时间空闲后可退避到 20 tick，test 使用更短的空闲检查。
- 变速后的历史周期：检查 `TransferPollSchedule` 对停产间隔的学习及失败时的等待。
  `failure` 的活跃学习分支可能返回超过 `maximumIdleDelay` 的历史剩余周期；
  需要在保留慢速生产成本优势的同时，约束恢复等待。此轮不修改生产算法。

初次测量发现双方注册数量为 40 / 23：直接跳过测试仍会创建模板结构，
可能影响堆峰值和整服计时。因此停止该轮、保留原始文件，统一专用性能任务的注册范围后重跑。
旧记录位于 `build/reports/wireless-export-alpha3-ab-20260907/`，
标记 `ABORTED_REGISTRATION_CONFOUND`，不用于判断内存或性能退步。

正式 A/B 结果保存在 `build/reports/wireless-export-alpha3-matched-20260907/`。

## 持续导出结果

256 个目标、每目标 27 种物品、每种物品每 tick 消耗 64 个。
双方五个压力 JVM 均达到最低目标 / 最低 100-tick 窗口吞吐 100%，
稳态缺料事件为 0，物品逐种守恒；再加五个配对 control JVM。

| 五轮中位数 | test 基线 | alpha.3 |
|---|---:|---:|
| 整服 mean MSPT | 7.245190 ms | 7.086348 ms |
| 整服 P99 MSPT | 9.544083 ms | 9.383083 ms |
| I/O mean | 5.841780 ms | 5.746860 ms |
| I/O P99 | 7.986958 ms | 7.883584 ms |
| 配对 control 校正 P99 | 7.142459 ms | 6.385918 ms |
| 采样堆峰值 | 582,471,600 B | 582,646,032 B |

原比较器结果为 **FAIL / MEASURABLE_IMPROVEMENT**：
只有配对 control 校正 P99 达到改善门槛，I/O P99 仍超过 4 ms 绝对预算。
其余吞吐、守恒、整服耗时、GC 和相对回退门槛通过。
本场景中整体收益有限，不能把此前有批量空间场景的调用数收益外推为所有负载下的加速。

## 空闲导出结果

各场景双方均为十个独立 JVM 的重复空闲测量。

| 场景 / 指标 | test 基线 | alpha.3 | 原比较器 |
|---|---:|---:|---|
| 空配置 1024，mean MSPT | 0.115635 ms | 0.101196 ms | PASS |
| 空配置 1024，I/O P99 | 0.464271 ms | 0.408083 ms | 达到改善门槛 |
| 类型不匹配 1024，mean MSPT | 0.115445 ms | 0.104067 ms | PASS |
| 类型不匹配 1024，I/O P99 | 0.268250 ms | 0.248354 ms | 未达到改善门槛 |

两组的堆峰值均未出现超过容忍度的回退。全部 60 次正式 JVM 运行完成，
JSON / CSV 齐全、注册数量一致、运行期间源码散列未改变。
每次 GameTest 内的传输、消费和守恒断言均通过；
总比较状态仍为 **FAIL**，原因是持续导出的 4 ms I/O P99 预算超标。
这与普通语义运行中的两项导入恢复失败分开记录，未互相抵消。

本轮没有测量 64×27 / 1024×27 持续导出，也没有新增实际流体或第三方容器计时结论。
