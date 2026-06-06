---
navigation:
  title: Overloaded Pattern Provider
  icon: ae2lt:overloaded_pattern_provider
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:overloaded_pattern_provider
  - ae2lt:overload_pattern
  - ae2lt:overload_pattern_encoder
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

## Operating Modes

### Normal Mode

In Normal Mode, the Overloaded Pattern Provider pushes materials into **physically adjacent** machines, identical to the vanilla Pattern Provider. The main difference is the larger number of pattern slots.

### Wireless Mode

In Wireless Mode, the Overloaded Pattern Provider can dispatch materials to remote machines across a distance. Bind remote machines via the provider's in-game UI; one provider can bind to multiple remote machines.

Wireless links must be in the same dimension and within **128 blocks** by default. The distance limit is configurable; setting it to 0 disables the limit. One provider can store up to **1,024** wireless connections. Hold **Ctrl** while right-clicking a target to batch-toggle contiguous machines of the same type.

Once connections are in place, the provider dispatches materials to the remote machines according to the selected distribution strategy.

### Distribution Strategies

| Strategy | Description |
|----------|-------------|
| Round Robin | Dispatches to one remote machine at a time, in order |
| Balanced Distribution | Distributes materials evenly across all connected remote machines |

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
