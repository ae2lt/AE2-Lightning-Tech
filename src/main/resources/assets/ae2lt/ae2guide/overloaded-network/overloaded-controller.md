---
navigation:
  title: Overloaded ME Controller
  icon: ae2lt:overloaded_controller
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:overloaded_controller
---

# Overloaded ME Controller

<Row>
  <BlockImage id="ae2lt:overloaded_controller" scale="4" />
</Row>

The **Overloaded ME Controller** is the upgraded version of the vanilla <ItemLink id="ae2:controller" /> and the core device of the Overloaded network. Each Overloaded controller supplies extra channels to the ME network, and provides a larger internal energy buffer with passive energy injection.

## Core Features

* **Extra channel supply**: each Overloaded controller adds **128 channels** to the network (default, configurable)
* **Internal energy capacity**: 16,000,000 AE
* **Passive energy injection**: automatically injects 100 AE per tick into the network (default, configurable) — keeps some of the network running even without an external energy source
* **Cable connections**: uses the Dense Smart cable connection type

## Channel Supply Mechanics

Channel allocation on an Overloaded network differs fundamentally from vanilla AE2:

* **Vanilla AE2**: uses a BFS algorithm, with channels deducted segment-by-segment along the path. Each dense cable carries at most 32 channels.
* **Overloaded network**: uses a **max-flow algorithm** to allocate channels globally.

What this means for the player:

* **Network total channels = Overloaded controller count × 128**
* Overloaded cable has no per-cable channel bottleneck — a single cable can carry any number of channels
* Placing more Overloaded controllers linearly scales the network's channel ceiling

### Examples

| Overloaded Controllers | Network Total Channels |
|------------------------|------------------------|
| 1 | 128 |
| 2 | 256 |
| 4 | 512 |
| 8 | 1,024 |

## How to Use

The Overloaded ME Controller follows the same placement and multiblock rules as the vanilla controller.

For full effect, pair it with [Overloaded Cable](overloaded-cable.md).

## Compared to the Vanilla Controller

| Feature | Vanilla Controller | Overloaded ME Controller |
|---------|--------------------|--------------------------|
| Channel supply | 32 channels per controller (routed along dense cable) | 128 channels per controller (max-flow global allocation) |
| Internal energy capacity | Lower | 16,000,000 AE |
| Passive energy injection | None | 100 AE per tick |
| Channel allocation algorithm | BFS, segment-by-segment | Max-flow, global |

> The Overloaded ME Controller keeps all of the basic behavior of the vanilla controller (multiblock, network management, etc.), and augments the channel and energy layers on top.
