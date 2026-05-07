---
navigation:
  title: Overload Processing Factory
  icon: ae2lt:overload_processing_factory
  parent: machines/machines-index.md
item_ids:
  - ae2lt:overload_processing_factory
---

# Overload Processing Factory

<Row>
  <BlockImage id="ae2lt:overload_processing_factory" scale="4" />
</Row>

The **Overload Processing Factory** is the largest and highest-throughput processing device in this mod. It supports mixed item and fluid I/O, and unlocks parallel processing via the Lightning Collapse Matrix — making it the backbone of any large-scale industrial Lightning production line.

## Slots and Capacity

| Slot / Component | Capacity | Notes |
|------------------|----------|-------|
| Item Input × 9 | 8,192 | Feed in the items |
| Matrix × 1 | 64 | Install Lightning Collapse Matrices to unlock parallelism |
| Item Output × 1 | 8,192 | Processed output; written by the machine only |
| Fluid Input | 512,000 mB | Feed in the required fluid through pipes |
| Fluid Output | 512,000 mB | Holds fluid output |
| FE Buffer | 640,000,000 FE (default) | Built-in energy buffer |
| Speed Card Slots | — | Up to 4 AE2 Speed Cards |

## Operating Flow

1. Feed in the item and fluid inputs
2. The machine matches the current inputs against registered recipes
3. Once a recipe is matched and enough Lightning and FE are available, processing starts
4. Lightning and FE are consumed throughout processing
5. Item and fluid outputs go to their respective output slots

## Parallel Processing

<ItemImage id="ae2lt:lightning_collapse_matrix" scale="2" float="left" />

The factory's parallelism is determined by the number of **Lightning Collapse Matrices** installed. The matrix slot holds up to 64 matrices, with each one providing a configurable amount of parallelism (default 4 per matrix, max 64 × 4 = 256):

* With no matrices installed, the factory cannot parallelize
* Installing more matrices linearly raises the parallel ceiling
* Effective parallelism is also constrained by available input materials and each recipe's per-operation cost
* Higher parallelism means more output per cycle, with a linear increase in FE consumption

## Speed Cards and Energy Consumption

The Overload Processing Factory accepts up to 4 Speed Cards. Each card significantly raises the FE cap per tick:

| Speed Cards | FE cap per tick (default) |
|-------------|---------------------------|
| 0 | 400,000 FE |
| 1 | 2,000,000 FE |
| 2 | 8,000,000 FE |
| 3 | 32,000,000 FE |
| 4 | 128,000,000 FE |

## Fluid I/O

Fluid input and output can use any fluid pipe system. The input slot strictly validates the fluid type against the recipe.

## Auto Export

With Auto Export enabled, item outputs are automatically pushed to adjacent containers on the allowed sides. The sides are configured in the "Configure Output Sides" screen of the GUI.

## Power Supply

The Overload Processing Factory is powered by **external FE** on its sides — it does not draw AE from the ME network directly. Use any FE pipe or cable to connect power to one of its sides.
