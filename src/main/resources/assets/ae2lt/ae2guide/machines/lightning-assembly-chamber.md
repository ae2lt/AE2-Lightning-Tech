---
navigation:
  title: Lightning Assembly Chamber
  icon: ae2lt:lightning_assembly_chamber
  parent: machines/machines-index.md
item_ids:
  - ae2lt:lightning_assembly_chamber
---

# Lightning Assembly Chamber

<Row>
  <BlockImage id="ae2lt:lightning_assembly_chamber" scale="4" />
</Row>

The **Lightning Assembly Chamber** handles advanced recipes with larger input counts. Where the Lightning Simulation Room has 3 input slots, the Assembly Chamber has 9 — which makes it well-suited to complex assembly tasks that need many different materials at once.

## Slots and Capacity

| Slot | Capacity | Notes |
|------|----------|-------|
| Input × 9 | 8,192 | Feed in the materials; slot order does not matter |
| Matrix × 1 | 1 | Optional Lightning Collapse Matrix |
| Output × 1 | 8,192 | Processed output; written by the machine only |
| Speed Card Slots | — | Up to 4 AE2 Speed Cards |

## Operating Flow

1. Put the ingredients into the 9 input slots
2. The machine matches the current inputs against registered recipes
3. Once a recipe is matched and the ME network has enough Lightning and FE, the machine locks in the recipe and starts processing
4. FE is spent during processing, and the required Lightning is consumed when the operation completes
5. The output goes to the output slot when processing finishes

## Lightning Consumption

Every recipe defines its required Lightning tier and amount. If the ME network cannot supply enough Lightning when the operation is ready to complete, the machine waits until supply recovers.

## Compared to the Lightning Simulation Room

| Feature | Simulation Room | Assembly Chamber |
|---------|-----------------|------------------|
| Input slots | 3 | 9 |
| Max stack per slot | 8,192 | 8,192 |
| Intended use | Simple conversion | Complex multi-material assembly |
| Matrix substitution | Supported | Supported |
| Speed Cards | Up to 4 | Up to 4 |

## Matrix Substitution

<ItemImage id="ae2lt:lightning_collapse_matrix" scale="2" float="left" />

Like the Simulation Room, the Assembly Chamber supports **matrix substitution**. With a **Lightning Collapse Matrix** installed, some recipes that would normally require **Extreme High Voltage Lightning** can be fulfilled by consuming several times the amount of **High Voltage Lightning** instead.

## Auto Export

With Auto Export enabled, finished outputs are automatically pushed to adjacent containers on the allowed sides. The sides are configured in the "Configure Output Sides" screen of the GUI.
