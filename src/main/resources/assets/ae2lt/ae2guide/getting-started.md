---
navigation:
  title: Getting Started
  icon: ae2lt:overload_crystal
  parent: index.md
  position: 10
---

# Getting Started

This page walks you through the full path from scratch to a working first Lightning production line.

## Prerequisites

Before starting, make sure you already have a functioning AE2 ME network that includes at least:

* An <ItemLink id="ae2:controller" />, or a small controllerless network
* A few <ItemLink id="ae2:certus_quartz_crystal" />, <ItemLink id="ae2:fluix_crystal" /> and <ItemLink id="ae2:fluix_block" />
* A <ItemLink id="minecraft:lightning_rod" /> and some AE2 Budding Certus Quartz of various tiers

## Step 1: Obtain Overload Crystals

<ItemImage id="ae2lt:overload_crystal" scale="2" float="left" />

**Overload Crystal** is the most fundamental material in this mod — nearly every recipe relies on it or one of its derivatives.

You obtain it by growing **Budding Overload Crystal** blocks, which are produced from AE2 Budding Certus Quartz via a multiblock lightning ritual:

1. Assemble a 3×3 structure with the budding quartz in the center. The rich variant uses <ItemLink id="ae2lt:overload_crystal_block" /> at the four corners and <ItemLink id="ae2:fluix_block" /> on the four cardinal sides; the simple variant swaps the corners for <ItemLink id="ae2:quartz_block" /> (Certus Quartz Block) while keeping fluix on the sides
2. Place a <ItemLink id="minecraft:lightning_rod" /> directly above the center
3. Wait for a lightning strike — natural lightning is required for the rich variant; any lightning works for the simple fluix-only variant
4. Place the resulting **Budding Overload Crystal** in the world and wait for buds to grow on its surface

Budding Overload Crystal behaves the same way as Budding Certus Quartz:

* Buds grow in order: Small → Medium → Large → **Overload Crystal Cluster**
* The <ItemLink id="ae2:growth_accelerator" /> works on Overload Crystal buds as well
* Breaking a **not yet fully grown** bud drops **Overload Crystal Dust**
* Breaking a **fully grown** Overload Crystal Cluster drops **Overload Crystals**, and Fortune applies
* Imperfect budding blocks may decay one tier each time a bud grows on them; Silk Touch prevents decay on break

See [Overload Crystal](materials/overload-crystal.md) for the full details.

## Step 2: Obtain Starter Materials via Lightning Transmutation

Many starter materials are produced through **Lightning Transmutation** — drop items on the ground, then let lightning strike the drops to transmute them.

<ItemImage id="ae2lt:overload_alloy" scale="2" float="left" />

Typical transmutation outputs include:

* **Overload Alloy**, **Overload Circuit Board**, **Overload Inscriber Press** and other basic materials
* **Lightning Collector**, **Lightning Simulation Room** and the first wave of machines
* **Electro-Chime Crystal** and **Lightning Storage Cell I**, the starter items for the Lightning system

There are two ways to trigger lightning transmutation:

* **Natural thunderstorm**: during thunderstorm weather, open-air item drops can be struck by natural lightning
* **Active summon**: carry an Overload Crystal and stand in the open for about 10 seconds to summon an artificial lightning bolt nearby

> Artificial lightning can trigger transmutation recipes, but **cannot** trigger the multiblock conversion in the next step. The multiblock conversion only accepts natural lightning strikes.

## Step 3: Obtain a Flawless Budding Overload Crystal

<ItemImage id="ae2lt:flawless_budding_overload_crystal" scale="2" float="left" />

**Flawless Budding Overload Crystal** never decays, and is the backbone of any large-scale production line. It can only be produced through the **rich structure** variant with a **natural lightning strike**:

<GameScene zoom="4" background="transparent">
  <ImportStructure src="assets/assemblies/flawless_budding_overload.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

* Place a **Flawless Budding Quartz** in the center
* Place a <ItemLink id="ae2:fluix_block" /> on each of the four cardinal sides (E / W / N / S) at the same height
* Place an **Overload Crystal Block** on each of the four corners
* Place a **Lightning Rod** directly above the center

Once the structure is built, wait for a **natural lightning strike** to hit the Lightning Rod to complete the conversion. When the strike lands, the eight surrounding blocks are consumed, and the center block becomes a Flawless Budding Overload Crystal.

> The Flawless conversion only accepts **natural lightning** during a thunderstorm. Artificial lightning summoned by carrying Overload Crystals will not trigger it. Lower-tier budding crystals have a cheaper "simple structure" variant described on the [Overload Crystal](materials/overload-crystal.md) page.

## Step 4: Build Your First Lightning Line

Once you have starter materials and basic machines, you can put together the minimum viable line: **collect → store → consume**.

1. **Collect**: place a **Lightning Collector** out in the open and connect it to your ME network. When lightning strikes, the collector injects Lightning energy directly into the network
2. **Store**: put a **Lightning Storage Cell I** into a <ItemLink id="ae2:drive" />; Lightning will appear in the ME terminal alongside items and fluids
3. **Consume**: the **Lightning Simulation Room** and **Lightning Assembly Chamber** automatically pull Lightning from the ME network while processing

Useful supporting devices:

* [Tesla Coil](lightning/tesla-coil.md) — consumes Overload Crystal Dust and FE to steadily produce High / Extreme High Voltage Lightning
* [Atmospheric Ionizer](machines/atmospheric-ionizer.md) — consumes AE energy and **Weather Condensate** to force the world weather to change

## Step 5: Mid- and Late-Game Goals

Once your starter line is running, you can unlock the following in turn:

* **Lightning Simulation Room** (3 input slots, simple conversions) and **Lightning Assembly Chamber** (9 input slots, complex assembly)
* **Overload Processing Factory** (9 input slots, supports item + fluid I/O, and parallel processing via the Lightning Collapse Matrix)
* **Crystal Catalyzer** (water + slotted item, with Crystal / Dust modes)
* **Overloaded ME Controller** — each controller adds 128 channels to the network
* **Overloaded Cable** — a single cable has no per-cable channel limit
* **Overloaded Pattern Provider** and **Overloaded ME Interface** — 36 slots each, with wireless dispatch and wireless I/O

See the category pages in the left sidebar for the full details.
