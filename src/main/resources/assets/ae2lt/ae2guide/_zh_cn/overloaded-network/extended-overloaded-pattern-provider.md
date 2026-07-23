---
navigation:
  title: 扩展过载样板供应器
  icon: ae2lt:extended_overloaded_pattern_provider
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:extended_overloaded_pattern_provider
  - ae2lt:overloaded_pattern_provider_upgrade
---

# 扩展过载样板供应器

<Row>
  <BlockImage id="ae2lt:extended_overloaded_pattern_provider" scale="4" />
</Row>

**扩展过载样板供应器**是 <ItemLink id="ae2lt:overloaded_pattern_provider" /> 的容量强化版本。它继承过载样板供应器的**全部功能**，包括无线模式、自动回收、分配策略、过载样板支持等，唯一的区别是拥有**更多样板槽位**，专为需要海量样板的大型自动化网络设计。

无线模式、回收模式、分配策略、过载样板等的用法与过载样板供应器完全相同，详见 <ItemLink id="ae2lt:overloaded_pattern_provider" /> 页面。

## 样板容量

扩展过载样板供应器的样板槽位按**页**组织，每页 36 个槽位：

* 默认 **4 页**，共 **144 个样板槽位**
* 页数可在模组配置中调整，范围为 **1 ~ 64 页**（即 36 ~ 2304 个槽位）

相比之下，普通的过载样板供应器固定为 36 个槽位（相当于 1 页）。

## 获取方式

### 直接合成

用一个过载样板供应器与一枚 <ItemLink id="ae2lt:ultimate_overload_core" /> 合成：

<RecipeFor id="ae2lt:extended_overloaded_pattern_provider" />

### 原地升级

<ItemImage id="ae2lt:overloaded_pattern_provider_upgrade" scale="2" float="left" />

如果基地里已经铺设并配置好了**过载样板供应器**，可以用**过载样板供应器升级**将它**原地升级**为扩展版，并**完整保留**其中的样板、无线连接、各项设置以及方块朝向，无需拆机重连。

用法：手持过载样板供应器升级，**右键**一台已放置的过载样板供应器即可，每次升级消耗一个升级物品。

注意事项：

* 只能升级**过载样板供应器**，对原版样板供应器无效
* 已经是扩展版的供应器无法再次升级

升级物品本身由一个扩展过载样板供应器与一个锭合成：

<RecipeFor id="ae2lt:overloaded_pattern_provider_upgrade" />
