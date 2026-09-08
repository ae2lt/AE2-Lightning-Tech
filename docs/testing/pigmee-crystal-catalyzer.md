# 猪咪水晶催化器验证

猪咪水晶催化器使用 64 个可重复使用的催化剂，每 300 个有效游戏刻产出 16 个水晶，完成时消耗 1000 mB 水。独立放置即可运行，不接受 FE，不使用坍缩矩阵，也不切换粉碎模式。

## 外观与界面

保留普通水晶催化器的 TedXenon 模型结构，单独调整猪咪模型的 UV：外壳复用本模组 `pigmee_pattern_provider_side`，顶部徽记引用 AE2 原始 `certus_quartz_crystal` / `certus_quartz_crystal_charged` 贴图。中央沿用普通催化器的旋转催化剂物品渲染。没有新增高分辨率贴图或重新绘制 AE2 水晶。

猪咪界面隐藏绑定频率、模式切换、坍缩矩阵和 FE 能量条，状态提示只显示运行状态及进度；仍保留自动输出设置。

## 自动化回归

```sh
./gradlew -I scripts/pigmee-catalyzer-test.init.gradle runGameTestServer --no-configuration-cache
./gradlew test build --no-configuration-cache
```

六项 GameTest 使用真实方块、能力接口与 AE 网格调度，不直接调用机器 tick：

- 六面物品/流体能力与 AE 节点能力存在，FE 能力不存在；模拟插入不改变库存，拒绝矩阵，普通/猪咪配方隔离。
- 无外部网络和电源连续加工两轮，每轮恰好 300 个有效 tick；催化剂保留，完成时才扣水。
- 63 个催化剂或 999 mB 水均不推进加工，补足后可恢复。
- 输出不足一轮空间时暂停，导出已有产物后恢复且不丢物。
- 加工中 NBT 保存恢复，旧 FE 快照迁移，拆除掉落保留催化剂与产物。
- 普通催化器仍需 FE/闪电，不能凭空加工。

## 真实客户端

```sh
./gradlew -I scripts/pigmee-catalyzer-test.init.gradle runClient --no-configuration-cache
# 第一次启动时，在该独立游戏目录创建名为 PigmeeCatalyzerTest 的测试世界。
# 后续可直接打开该世界：
./gradlew -I scripts/pigmee-catalyzer-test.init.gradle -PpigmeeQuickPlay runClient --no-configuration-cache
```

游戏目录在 `build/pigmee-catalyzer-client`，GameTest 目录在 `build/pigmee-catalyzer-gametest`。此脚本启用的客户端探针会清理测试玩家背包并在 (0, 100, 0) 附近建立比较场景，仅供上述独立世界使用。调试类位于 `src/jdb`，不进入发布 JAR。

客户端探针通过真实菜单网络包倒入一桶水，验证空桶返回、模式锁定、无 FE 进度同步、产物/水量/催化剂数量，并打开实时 EMI 配方。结果写到游戏目录下 `pigmee-catalyzer-client-probe.txt`，原始游戏截图位于其 `screenshots` 目录。

## 2026-09-07 验证结果

- Java 21.0.11、Minecraft 1.21.1、NeoForge 21.1.220、AE2 19.2.17、Thunderbolt 2.0.0-beta.3。
- 全套 910 项 JUnit：0 失败、0 错误、0 跳过；发布构建成功。
- 6 项 GameTest 全部通过。修复前同一能力测试确认 Pigmee 物品能力缺失，补齐注册后通过。
- 客户端探针最终 `PASS`。已人工查看 Minecraft 原始截图，确认猪咪专属模型、水晶徽记、菜单与产物，绑定频率按钮和 FE 能量条均已移除。
- 同时加载 JEI 19.27.0.336 与 EMI 1.1.24，实际打开并核对猪咪赛特斯配方。

隔离运行未装部分可选整合模组，日志中仍有已有的缺少可选类、`extendedae:config` 条件和已禁用电源设备资源警告；未阻止本次配方加载和测试。此记录不代替包含全部可选模组的整合包回归。
