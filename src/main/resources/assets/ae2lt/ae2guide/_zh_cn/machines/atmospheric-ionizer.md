---
navigation:
  title: 大气电离仪
  icon: ae2lt:atmospheric_ionizer
  parent: machines/machines-index.md
item_ids:
  - ae2lt:atmospheric_ionizer
  - ae2lt:clear_condensate
  - ae2lt:rain_condensate
  - ae2lt:thunderstorm_condensate
---

# 大气电离仪

<Row>
  <BlockImage id="ae2lt:atmospheric_ionizer" scale="4" />
</Row>

**大气电离仪**是一台天气控制设备。它通过消耗**天气凝核**与 AE 能量，强制将世界天气切换为指定状态，从而配合闪电收集器获得稳定的自然雷击。

## 天气凝核

天气凝核有三种类型，分别对应三种天气：

<ItemGrid>
  <ItemIcon id="ae2lt:clear_condensate" />
  <ItemIcon id="ae2lt:rain_condensate" />
  <ItemIcon id="ae2lt:thunderstorm_condensate" />
</ItemGrid>

| 凝核类型 | 目标天气 | AE 消耗 | 持续时间 |
|---------|---------|---------|---------|
| 晴空凝核 | 晴天 | 500,000 AE | 12,000 ~ 180,000 tick |
| 降雨凝核 | 雨天 | 1,000,000 AE | 12,000 ~ 24,000 tick |
| 雷暴凝核 | 雷暴 | 8,000,000 AE | 3,600 ~ 15,600 tick |

## 工作流程

1. 将大气电离仪接入 ME 网络
2. 将所需的天气凝核放入输入槽
3. 机器从 ME 网络中持续提取 AE 能量进行电离
4. 电离完成后，世界天气被强制切换为目标天气
5. 凝核在使用后被消耗

## 注意事项

* 大气电离仪消耗**AE 能量**（来自 ME 网络），而非 FE 能量
* 雷暴凝核单次消耗最高（8,000,000 AE），请确保网络能量供应充足
* 在不支持天气的维度中，机器无法工作
* 当目标天气已经是当前天气时，机器不会消耗凝核
