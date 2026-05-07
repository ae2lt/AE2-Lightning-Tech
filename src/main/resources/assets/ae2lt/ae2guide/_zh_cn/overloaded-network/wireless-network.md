---
navigation:
  title: 无线网络
  icon: ae2lt:wireless_overloaded_controller
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:wireless_receiver
  - ae2lt:wireless_overloaded_controller
  - ae2lt:advanced_wireless_overloaded_controller
---

# 无线网络

<ItemGrid>
  <ItemIcon id="ae2lt:wireless_overloaded_controller" />
  <ItemIcon id="ae2lt:advanced_wireless_overloaded_controller" />
  <ItemIcon id="ae2lt:wireless_receiver" />
</ItemGrid>

无线网络系统允许你跨越长距离甚至跨维度扩展过载 ME 网络，无需铺设线缆。**无线过载控制器**在指定频率上广播，一个或多个**无线接收器**调谐到同一频率即可建立虚拟网格连接。

## 组件

### 无线过载控制器

<Row>
  <BlockImage id="ae2lt:wireless_overloaded_controller" scale="4" />
</Row>

**无线过载控制器**是一台兼具无线发射功能的[过载 ME 控制器](overloaded-controller.md)。它在选定的频率上广播自身的网格节点，使远程的无线接收器可以连接到它。

* 提供与普通过载控制器相同的额外频道和能量
* 每台控制器同一时间只能广播**一个频率**
* 每个频率同一时间只能被一台发射器占用

### 高级无线过载控制器

<Row>
  <BlockImage id="ae2lt:advanced_wireless_overloaded_controller" scale="4" />
</Row>

**高级无线过载控制器**是升级版本，具备两项关键改进：

* **跨维度支持**：其他维度中的无线接收器也可以连接到它
* **无限频道容量**：完全移除每接收器的频道上限

### 无线接收器

<Row>
  <BlockImage id="ae2lt:wireless_receiver" scale="4" />
</Row>

**无线接收器**是与无线过载控制器配对的接收端。将它放置在世界任意位置，设置为与控制器相同的频率，即可自动建立虚拟网格连接。

* 空闲功耗 5 AE/t
* 同一时间只能连接一个频率
* 跨维度连接需要发射端为**高级**无线过载控制器

## 搭建步骤

1. 放置一台**无线过载控制器**，右键打开频率界面
2. 选择或创建一个频率
3. 在远程位置放置一台**无线接收器**，右键打开其频率界面
4. 选择相同的频率——接收器会自动与控制器的网格建立虚拟连接

连接建立后，接收器的行为等同于通过线缆直接连接到控制器。接入接收器本地网络的设备可以访问控制器的 ME 网络，包括频道、存储和合成。

## 频率安全

频率支持访问控制：

| 级别 | 行为 |
|------|------|
| 公开 | 任何人都可以将接收器绑定到此频率 |
| 私有 | 仅频率所有者和允许的成员可以绑定 |
| 加密 | 需要输入密码才能绑定 |

未绑定的控制器和接收器始终可以访问，不受安全级别限制。

## 使用建议

* 需要跨维度桥接网络时使用高级版本
* 一台控制器可以服务多台接收器——每台接收器各自建立独立的虚拟连接
