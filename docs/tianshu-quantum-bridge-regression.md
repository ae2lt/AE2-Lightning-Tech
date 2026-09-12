# 天枢无线终端量子桥失效回归

## 故障与修复

AE2 19.2.17 销毁量子桥结构后会清空 `QuantumCluster.center`。AE2WTLib 19.5.1 的 `WTMenuHost` 仍可能缓存该结构与已连接状态。天枢编码和合成菜单在父菜单刷新连接前查询天枢节点，因此会调用旧桥的 `getActionableNode()`，触发空指针异常。普通连接刷新也没有释放这个失效引用，影响后续重建发现。

`WTMenuHostQuantumBridgeMixin` 在读取节点、连接状态，以及正常刷新连接之前检查缓存的量子桥。结构已经销毁或中心不存在时，释放缓存，并在提前读取时调用原生连接与状态刷新。正常桥、升级卡、网络匹配、供电校验、本地接入点与 LT 频率卡仍沿用原逻辑。修复适用于内嵌的无线 API；依赖完整 AE2WTLib 的其他集成仍保持可选。

## 原生测试

使用 Java 21：

```sh
./gradlew runGameTestServer -I scripts/tianshu-quantum-bridge-test.init.gradle \
  -Pae2wtlib_version=19.5.1 -Pae2ltQuantumCraftingRegression=true

./gradlew build runGameTestServer -I scripts/tianshu-quantum-bridge-test.init.gradle \
  -Pae2ltQuantumCraftingRegression=true

./gradlew runGameTestServer -I scripts/tianshu-quantum-bridge-test.init.gradle \
  -Pae2ltDisableWtlibDevRuntime=true -Pae2ltQuantumCraftingRegression=true
```

省略 `ae2ltQuantumCraftingRegression` 时仅运行 5 项量子桥用例；启用后包含原有合成和无线集成检查，共 12 项。游戏目录为 `build/tianshu-quantum-bridge-gametest`，使用独立测试世界。首次运行需要下载开发依赖与资源；已有有效 `build/moddev/minecraft_assets.properties` 时可增加 `--offline -x downloadAssets`。

- 两种真实菜单：注入与崩溃相同的失效缓存状态，调用 `broadcastChanges()`；验证不崩溃、离线、缓存释放及真实合成输入保留。
- 先读取连接状态、只执行普通连接刷新：均能释放旧缓存。
- 真实 3×3 量子桥、升级卡、奇点、ME 驱动器与物品存储元件：开着增强无线合成菜单拆环、重建，验证旧结构销毁、新结构自动发现，以及恢复后的批量合成。
- 无线批量合成：联网时从真实 ME 补料，离线时不提取原 ME 库存；输入与产物数量守恒。
- 本地无线接入点：通道重算完成后，先查询状态仍可回退到有效本地连接并访问 ME 库存。
- 原有回归：四种合成动作与 AE2 对照，满背包、材料不足、供电与连接边界，铁砧/锻造、单元工作台、WUT、磁铁过滤与页面访问检查。

仅安装 API 时，3 项失效缓存检查与可用的合成回归正常执行；需要完整 WTLib 量子卡的 2 项真实桥测试跳过。已有两个关闭菜单的测试补齐了切换到玩家背包菜单、完成服务端 tick 的生命周期，匹配现有工作台会话在 tick 末返还物品的行为。

## 复现依据与边界

修复验证：AE2WTLib 19.5.1 与默认 19.4.1 各 12 项原生测试通过；19.4.1 API-only 配置通过可用用例（上述 2 项量子卡用例跳过）。默认单元测试 910 项通过，失败、错误与跳过均为 0。

未修复的远端 alpha `b5bd356a` 上，两个菜单用例均在 `QuantumCluster.getActionableNode()` 抛出与报告一致的 `center` 空指针。修复覆盖桥失效的共同入口；日志本身不能区分玩家拆桥、区块卸载或结构重建等具体触发动作。原生世界测试直接覆盖拆除与重建，没有模拟真实区块卸载或整服长时间多人运行。
