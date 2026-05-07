---
navigation:
  title: Overloaded Cable
  icon: ae2lt:overloaded_cable
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:overloaded_cable
  - ae2lt:overloaded_cable_white
  - ae2lt:overloaded_cable_orange
  - ae2lt:overloaded_cable_magenta
  - ae2lt:overloaded_cable_light_blue
  - ae2lt:overloaded_cable_yellow
  - ae2lt:overloaded_cable_lime
  - ae2lt:overloaded_cable_pink
  - ae2lt:overloaded_cable_gray
  - ae2lt:overloaded_cable_light_gray
  - ae2lt:overloaded_cable_cyan
  - ae2lt:overloaded_cable_purple
  - ae2lt:overloaded_cable_blue
  - ae2lt:overloaded_cable_brown
  - ae2lt:overloaded_cable_green
  - ae2lt:overloaded_cable_red
  - ae2lt:overloaded_cable_black
---

# Overloaded Cable

The **Overloaded Cable** is the upgraded version of the vanilla AE2 Dense Cable, with no per-cable channel limit.

## Core Features

* **No per-cable channel limit**: a single Overloaded Cable has no channel bottleneck; it can carry any number of channels
* **Network channel total determined by controllers**: the total channels available on an Overloaded network depend on the number of **Overloaded ME Controllers** — each Overloaded controller supplies 128 channels (default, configurable)
* **Smart channel allocation**: the Overloaded network uses a **max-flow algorithm** in place of vanilla AE2's BFS, using the total channel budget more efficiently on complex layouts
* **Dense cable appearance**: visually identical to the vanilla Dense Cable
* **Color variants**: 17 color variants are available; different colors do not connect to each other (same coloring rule as vanilla AE2 cables)

## Channel Mechanics

Unlike the fixed per-cable channel caps in vanilla AE2 (8 for smart cable, 32 for dense smart cable), Overloaded Cable follows these rules:

1. **No per-cable bottleneck**: every Overloaded Cable can carry any number of channels
2. **Network total channels = Overloaded controllers × 128**: the total channels available on the network are determined by the number of Overloaded ME Controllers
3. **Max-flow allocation**: channels are allocated across the network using a max-flow algorithm, not deducted segment-by-segment along a path

What this means for the player:

* You no longer have to worry about a backbone cable running out of channels
* As long as the network total channels are sufficient, every device gets its channel
* Add more Overloaded ME Controllers to scale the network's total channel budget

> **Important**: Overloaded Cable only delivers its full unlimited-capacity behavior when **both ends are Overloaded devices** (Overloaded Cable, Overloaded ME Controller, etc.). If an Overloaded Cable is directly connected to a vanilla AE2 device, that connection still counts toward the vanilla channel limits.

## Color Variants

Overloaded Cable has 17 color variants: the default Fluix color, plus all 16 Minecraft dye colors:

White, Orange, Magenta, Light Blue, Yellow, Lime, Pink, Gray, Light Gray, Cyan, Purple, Blue, Brown, Green, Red, Black.

Different colors of Overloaded Cable do not connect to each other (same rule as vanilla AE2 cable coloring).

## How to Use

Overloaded Cable is used the same way as vanilla Dense Smart Cable — just place it on the side of a block to connect to the network.

## Good Places to Use It

* Main trunk lines in a large base, where a single Overloaded Cable can absorb the channel load of many dense cables
* High-capacity networks built around Overloaded ME Controllers
* Simplifying network layouts — you no longer need to route specifically around channel bottlenecks
