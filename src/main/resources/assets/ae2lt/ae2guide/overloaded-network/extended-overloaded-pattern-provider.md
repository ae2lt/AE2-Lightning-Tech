---
navigation:
  title: Extended Overloaded Pattern Provider
  icon: ae2lt:extended_overloaded_pattern_provider
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:extended_overloaded_pattern_provider
  - ae2lt:overloaded_pattern_provider_upgrade
---

# Extended Overloaded Pattern Provider

<Row>
  <BlockImage id="ae2lt:extended_overloaded_pattern_provider" scale="4" />
</Row>

The **Extended Overloaded Pattern Provider** is a higher-capacity version of the <ItemLink id="ae2lt:overloaded_pattern_provider" />. It inherits **all** of the Overloaded Pattern Provider's features, including wireless mode, auto return, distribution strategies and Overload Pattern support; the only difference is that it has **more pattern slots**, designed for large automation networks that need a huge number of patterns.

Wireless mode, return modes, distribution strategies, and Overload Patterns all work exactly as they do on the Overloaded Pattern Provider — see the <ItemLink id="ae2lt:overloaded_pattern_provider" /> page for details.

## Pattern Capacity

The Extended Overloaded Pattern Provider organizes its pattern slots into **pages** of 36 slots each:

* **4 pages** by default, for a total of **144 pattern slots**
* The page count is configurable in the mod config, ranging from **1 to 64 pages** (36 to 2304 slots)

For comparison, the regular Overloaded Pattern Provider has a fixed 36 slots (the equivalent of one page).

## How to Obtain

### Direct Crafting

Craft one with an Overloaded Pattern Provider and an <ItemLink id="ae2lt:ultimate_overload_core" />:

<RecipeFor id="ae2lt:extended_overloaded_pattern_provider" />

### In-Place Upgrade

<ItemImage id="ae2lt:overloaded_pattern_provider_upgrade" scale="2" float="left" />

If you already have an **Overloaded Pattern Provider** placed and configured in your base, use the **Overloaded Pattern Provider Upgrade** to upgrade it **in place** to the extended version, **fully preserving** its patterns, wireless connections, settings, and block orientation — no need to break it and reconnect everything.

Usage: hold the Overloaded Pattern Provider Upgrade and **right-click** a placed Overloaded Pattern Provider. Each upgrade consumes one upgrade item.

Notes:

* Only an **Overloaded Pattern Provider** can be upgraded; it has no effect on the vanilla Pattern Provider
* A provider that is already extended cannot be upgraded again

The upgrade item itself is crafted from one Extended Overloaded Pattern Provider and an ingot:

<RecipeFor id="ae2lt:overloaded_pattern_provider_upgrade" />
