---
navigation:
  title: Lightning Collector
  icon: ae2lt:lightning_collector
  parent: lightning/lightning-index.md
item_ids:
  - ae2lt:lightning_collector
  - ae2lt:electro_chime_crystal
  - ae2lt:perfect_electro_chime_crystal
---

# Lightning Collector

<Row>
  <BlockImage id="ae2lt:lightning_collector" scale="4" />
</Row>

The **Lightning Collector** is the starting point of the Lightning system. When lightning strikes the collector, it injects Lightning energy directly into the ME network.

## Basic Usage

Place the Lightning Collector **out in the open where lightning can reach it**, and connect it to an ME network. Two conditions must be met for a strike to be productive:

* The network must be online
* The network must have enough storage capacity to accept the Lightning about to be produced (otherwise the strike is wasted)

The tier of Lightning produced depends on what hits the collector:

* **Artificial lightning** (summoned by a player carrying an Overload Crystal, or discharged by a Tesla Coil) → **High Voltage Lightning**
* **Natural lightning** (a real thunderstorm bolt) → **Extreme High Voltage Lightning**

After each successful collection the collector enters a short cooldown (default 0 ticks, configurable).

## Electro-Chime Crystal and Yield

<ItemImage id="ae2lt:electro_chime_crystal" scale="2" float="left" />

The collector has a dedicated **crystal slot** where you can insert an **Electro-Chime Crystal**. Whether a crystal is installed and how far along its cultivation is determines the yield of each strike.

| Crystal State | Yield |
|---------------|-------|
| No crystal | Random within a fixed **base range** (default HV 1~2, EHV 1~4) |
| Cultivating Electro-Chime Crystal | Scales linearly between "starting yield" and "final yield" with cultivation progress, plus a small random jitter |
| Perfect Electro-Chime Crystal | **Fixed yield** (default 16 for both HV and EHV), no randomness |

## Cultivating the Electro-Chime Crystal

The Electro-Chime Crystal progresses through four stages based on cultivation value:

| Stage | Progress |
|-------|----------|
| Dormant | < 20% |
| Stirring | 20% ~ 55% |
| Resonant | 55% ~ 85% |
| Overcharged | ≥ 85% |

* The crystal only gains cultivation value when the collector is struck by **natural lightning** (the strikes that produce Extreme High Voltage Lightning). Artificial lightning does not contribute to cultivation
* Once the cultivation value reaches its maximum, the Electro-Chime Crystal automatically transforms into a **Perfect Electro-Chime Crystal**
* The Perfect Electro-Chime Crystal no longer cultivates further, but provides a stable maximum yield

## Automation Tips

* The collector must be connected to an online ME network, otherwise lightning strikes on it are wasted
* If you do not want to wait for natural thunderstorms, use the [Atmospheric Ionizer](../machines/atmospheric-ionizer.md) with Thunderstorm Condensate to force thunderstorm weather
* For a fully weather-independent solution, use the [Tesla Coil](tesla-coil.md) to produce HV / EHV Lightning on demand
