---
navigation:
  title: Lightning Simulation Room
  icon: ae2lt:lightning_simulation_room
  parent: machines/machines-index.md
item_ids:
  - ae2lt:lightning_simulation_room
---

# Lightning Simulation Room

<Row>
  <BlockImage id="ae2lt:lightning_simulation_room" scale="4" />
</Row>

The **Lightning Simulation Room** is one of the core processing machines of this mod, suited to conversions with 1~3 inputs. Most mid-game recipes are crafted here.

## Slots and Capacity

| Slot | Capacity | Notes |
|------|----------|-------|
| Input × 3 | 8,192 | Feed in the materials; slot order does not matter |
| Matrix × 1 | 1 | Optional Lightning Collapse Matrix |
| Output × 1 | 8,192 | Processed output; written by the machine only |
| Speed Card Slots | — | Up to 4 AE2 Speed Cards |

## Operating Flow

1. Put up to 3 different ingredients into the input slots (recipes may only need 1~2)
2. The machine matches the current inputs against registered recipes
3. Once a recipe is matched and the ME network has enough Lightning and FE, the machine locks in the recipe and starts processing
4. Lightning and FE are consumed during processing
5. The output goes to the output slot when processing finishes

## Lightning Consumption

Every recipe defines the required Lightning type (HV or EHV) and amount. Lightning is pulled from the ME network in one shot at the start of processing. If the network does not have enough Lightning, the GUI shows "Lightning insufficient" and processing pauses until supply recovers.

## Matrix Substitution

<ItemImage id="ae2lt:lightning_collapse_matrix" scale="2" float="left" />

With a **Lightning Collapse Matrix** installed, some recipes that would normally require **Extreme High Voltage Lightning** can be fulfilled by consuming several times the amount of **High Voltage Lightning** instead. The ratio is defined per recipe. The GUI shows the current substitution state:

* **Substitution: Active** — the current recipe is using HV Lightning to substitute for EHV
* **Substitution: Available** — the current recipe supports substitution but has not triggered it
* **Substitution: None** — the current recipe does not support substitution

## Auto Export

With Auto Export enabled, finished outputs are automatically pushed to adjacent containers on the allowed sides. The sides are configured in the "Configure Output Sides" screen of the GUI.
