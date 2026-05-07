---
navigation:
  title: Crystal Catalyzer
  icon: ae2lt:crystal_catalyzer
  parent: machines/machines-index.md
item_ids:
  - ae2lt:crystal_catalyzer
---

# Crystal Catalyzer

<Row>
  <BlockImage id="ae2lt:crystal_catalyzer" scale="4" />
</Row>

The **Crystal Catalyzer** is a specialty processing machine that uses water, FE, and the item in its catalyst slot. It has two operating modes: **Crystal Mode** and **Dust Mode**.

## Slots and Capacity

| Slot | Capacity | Notes |
|------|----------|-------|
| Catalyst slot | 256 | Holds the item required by the current mode; the item is **not consumed** during processing |
| Matrix slot | 1 | Optional Lightning Collapse Matrix for a yield bonus |
| Output slot | 1,024 | Processed output; written by the machine only, no external input accepted |
| Fluid slot | 16,000 mB | Fed with water through fluid pipes; each operation consumes 1,000 mB |
| FE Buffer | 1,000,000 FE | Built-in energy buffer |

## Operating Modes

| Mode | Purpose | Example inputs | Example outputs |
|------|---------|----------------|-----------------|
| Crystal Mode | Extract crystals from matching crystal blocks | Certus Quartz Block, Fluix Block, Overload Crystal Block | Certus Quartz Crystal, Fluix Crystal, Overload Crystal |
| Dust Mode | Extract crystal dust from matching crystal blocks | Certus Quartz Block, Fluix Block, Overload Crystal Block | Certus Quartz Dust, Fluix Dust, Overload Crystal Dust |

Both modes share the same catalyst slot, fluid slot, and output slot. The item in the catalyst slot is used for recipe matching and parallel count calculation, but it is not consumed.

## Operating Flow

1. Select Crystal Mode or Dust Mode with the left-side mode button
2. Feed water into the fluid slot through pipes
3. Put an item matching the selected mode into the catalyst slot
4. Supply FE
5. Once a recipe matches, the machine processes automatically
6. Finished output goes into the output slot

## Parallel Output and Water

Each operation always consumes **1,000 mB of water**. Parallel output and the matrix bonus do not increase the water cost.

The stack size in the catalyst slot determines the parallel count: parallel count = slot amount / recipe required amount. The built-in recipes currently require 1 matching block each, so inserting 64 valid blocks makes the machine calculate 64 parallel outputs per operation.

The parallel count is locked when processing starts. Adding or removing items from the catalyst slot during processing will not change the already locked output for that operation.

## Lightning Collapse Matrix Bonus

<ItemImage id="ae2lt:lightning_collapse_matrix" scale="2" float="left" />

With a **Lightning Collapse Matrix** installed in the matrix slot, the Crystal Catalyzer's per-operation output is increased to **4×**. The matrix is not consumed during processing.

Final output = base output × parallel count × matrix multiplier.

## Notes

* The Crystal Catalyzer is powered by **external FE** on its sides, not by AE from the ME network
* The machine itself is also an ME network device — connecting it to the network lets you feed it through AE2 Interfaces or Pattern Providers
* Supports Auto Export; output sides can be configured in the GUI
* The Crystal Catalyzer **does not** support Speed Cards
