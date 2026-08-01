---
navigation:
  title: Overloaded Pattern Provider
  icon: ae2lt:overloaded_pattern_provider
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:overloaded_pattern_provider
  - ae2lt:overloaded_pattern_provider_upgrade
  - ae2lt:overload_pattern
  - ae2lt:overload_pattern_encoder
  - ae2lt:overloaded_wireless_connect_tool
---

# Overloaded Pattern Provider

<Row>
  <BlockImage id="ae2lt:overloaded_pattern_provider" scale="4" />
</Row>

The **Overloaded Pattern Provider** is the upgraded version of the vanilla <ItemLink id="ae2:pattern_provider" />, with **36 pattern slots** and a **wireless mode** for dispatching materials to remote machines across a distance.

## Core Features

* **36 pattern slots** (vanilla has 9)
* **Two operating modes**: Normal and Wireless
* **Overload Pattern support**: works with vanilla patterns, plus this mod's Overload Patterns
* **Auto return**: automatically pulls processed outputs back from remote machines
* **Import filter**: only allows results defined by the pattern back into the ME network
* **Adaptive doubling**: dispatches multiple recipe copies from one crafting request

## In-Place Upgrade

<ItemImage id="ae2lt:overloaded_pattern_provider_upgrade" scale="2" float="left" />

Use an **Overloaded Pattern Provider Upgrade** on a placed and configured provider to convert it in place while preserving its patterns, priority, redstone/blocking settings, and orientation. The following blocks are supported:

* AE2 Pattern Provider
* ExtendedAE Extended Pattern Provider
* AdvancedAE Advanced Pattern Provider
* AdvancedAE Advanced Extended Pattern Provider

Each conversion consumes one upgrade item. Craft the upgrade from an Overloaded Pattern Provider and an ingot:

<RecipeFor id="ae2lt:overloaded_pattern_provider_upgrade" />

## Operating Modes

### Normal Mode

In Normal Mode, the Overloaded Pattern Provider pushes materials into **physically adjacent** machines, identical to the vanilla Pattern Provider. The main difference is the larger number of pattern slots.
~~Then why not use an Extended Interface?~~

### Wireless Mode

<ItemImage id="ae2lt:overloaded_wireless_connect_tool" scale="2" float="left" />

In Wireless Mode, the Overloaded Pattern Provider can dispatch materials to remote machines across a distance. Use the **Overloaded Wireless Connect Tool** to establish wireless connections:

1. Hold the Overloaded Wireless Connect Tool and **right-click** the Overloaded Pattern Provider to select it
2. Right-click a specific face of a target machine to connect
3. One provider can bind to multiple remote machines

Wireless links must be in the same dimension and within **128 blocks** by default. The distance limit is configurable; setting it to 0 disables the limit. One provider can store up to **1,024** wireless connections. Hold **Ctrl** while right-clicking a target to batch-toggle contiguous machines of the same type.

Once connections are in place, the provider dispatches materials to the remote machines according to the selected distribution strategy.

The provider treats all linked machines as the same kind of processing machine. It cannot dispatch to the multiple input buses or input hatches of multiblock machines such as those in MI, and it cannot automatically split one pattern's contents across different containers; doing so can stall the craft.

### Distribution Strategies

| Strategy | Description |
|----------|-------------|
| Round Robin | Dispatches to one remote machine at a time, in order |
| Balanced Distribution | Distributes materials evenly across all connected remote machines |

## Interface and Advanced Settings

The left toolbar on the main screen contains controls for operating mode, return mode, adaptive doubling, and blocking mode. Select the cog button to open **Advanced Settings**, which contains:

* Wireless dispatch strategy: Round Robin / Balanced Distribution
* Wireless probe speed: Normal / Fast
* Input filtering: OFF / ON

Wireless dispatch and probe settings only take effect in Wireless Mode.

## Adaptive Doubling

Adaptive Doubling is disabled by default. When enabled and a crafting request contains several copies of the same pattern, the first dispatch starts small and gradually increases the number of copies sent at once. For each target and pattern, later dispatches start from the last chunk that entered completely without overflow, repeat that chunk once, and then continue doubling. If that starting chunk is rejected, the provider halves it until one chunk succeeds; overflow keeps the normal pending-send behavior and lowers the next starting chunk.

Before enabling it, make sure the target can hold and correctly process several recipe copies at once. Some machines or input layouts do not support batched ingredients and may mix recipes or stall the craft; disable Adaptive Doubling in those cases.

When disabled, each push sends only one recipe copy. Directional patterns and patterns that cannot push inputs to an external inventory do not use Adaptive Doubling.

## Blocking Mode

| Mode | Description |
|------|-------------|
| OFF | Does not inspect the target for pattern inputs |
| Normal | Pauses dispatch while the target still contains an input used by any loaded pattern |
| Continue Same Pattern | Allows another dispatch when the same pattern was the last one successfully sent to that target; other patterns remain blocked |

Use Continue Same Pattern when a machine can safely hold several copies of one recipe. If it must finish one copy before receiving the next, use Normal blocking.

## Return Mode

The return mode determines how processed output is recovered from remote machines:

| Mode | Description |
|------|-------------|
| OFF | No auto return |
| AUTO | Actively pulls output back from the remote machines |
| EJECT | Remote machines push output into the virtual output slot; the provider accepts it passively |

## Speed Tier

| Speed | Description |
|-------|-------------|
| Normal | Standard cooldown (5 ~ 80 ticks) |
| Fast (Probe) | Adaptive cooldown; uses a probe mechanic to detect readiness early (1 ~ 40 ticks) |

Fast Mode also accelerates wireless auto return: the polling interval is 10 to 100 ticks in Normal Mode and 1 to 20 ticks in Fast Mode.

## ME Power Cost

Dispatching materials and returning products consumes ME network power. For large crafting batches, make sure the network has a large enough AE energy buffer; if power is insufficient, the provider waits until more power is available.

Wireless Mode, wireless links, and Fast probing also increase idle power usage.

## Import Filter

With "Filtered Import" enabled, the provider only accepts items listed as outputs on the current pattern when returning products — this prevents unrelated items from entering the network.

## Overload Pattern and Overload Pattern Encoder

<Row>
  <ItemImage id="ae2lt:overload_pattern" scale="2" />
  <ItemImage id="ae2lt:overload_pattern_encoder" scale="2" />
</Row>

The **Overload Pattern** is a specialty pattern, **only usable in the Overloaded Pattern Provider**. Use the **Overload Pattern Encoder** to encode it.

The Overload Pattern Encoder supports:

* Setting primary input and primary output
* Setting byproducts
* An **Ignore NBT** switch: when enabled, item matching ignores NBT data

## Automation Tips

* In large automated crafting systems, Wireless Mode can dramatically reduce pipe complexity
* Combined with the Balanced Distribution strategy and multiple processing machines, it enables parallel crafting without extra item pipes
* Enable the Fast speed tier for better responsiveness
* Pick the return mode that matches the specific automation scenario
* Combine Adaptive Doubling with multiple equivalent machines to process large requests
* Disable Adaptive Doubling when a target has little input space or cannot accept batched ingredients
