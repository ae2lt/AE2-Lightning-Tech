---
navigation:
  title: Lightning Collapse Matrix
  icon: ae2lt:lightning_collapse_matrix
  parent: materials/materials-index.md
  position: 60
item_ids:
  - ae2lt:lightning_collapse_matrix
---

# Lightning Collapse Matrix

<ItemImage id="ae2lt:lightning_collapse_matrix" scale="2" float="left" />

The **Lightning Collapse Matrix** is one of the key end-tier components in AE2 Lightning Tech. It is not a consumable — instead, it acts as a **substitution / parallelism catalyst**. Keeping a matrix in a machine's matrix slot unlocks the machine's higher-tier operating mode.

## How to Obtain

### Crafting in the Lightning Simulation Room

| Material | Amount |
|----------|--------|
| Perfect Electro-Chime Crystal | 1 |
| Ultimate Overload Core | 16 |
| **Extreme High Voltage Lightning** | 256 |
| Energy | 500,000,000 AE |

Because the cost is already steep, you typically schedule this alongside your Overload Processing Factory and Lightning Assembly Chamber work.

## As a Machine Catalyst

The Lightning Collapse Matrix is **not consumed** during processing, but it must stay in the matrix slot:

* [Lightning Simulation Room](../machines/lightning-simulation-chamber.md) / [Lightning Assembly Chamber](../machines/lightning-assembly-chamber.md) — with a matrix installed, some recipes that would normally require **Extreme High Voltage Lightning** can be fulfilled by consuming several times the amount of **High Voltage Lightning** instead.
* [Tesla Coil](../lightning/tesla-coil.md) — Extreme High Voltage mode requires a matrix in the slot; without one, HV cannot be upconverted to EHV.
* [Crystal Catalyzer](../machines/crystal-catalyzer.md) — applies in both Crystal Mode and Dust Mode; with a matrix installed, post-parallel per-operation output is increased to **4×**.
* [Overload Processing Factory](../machines/overload-processing-factory.md) — multi-matrix parallelism: each additional matrix unlocks another tier of parallelism (up to 64 matrices in the slot, 4 parallel operations per matrix by default).

## Field Notes

> A field team filed a thin follow-up report: when the matrix is **not constrained inside any container**, an unintended reaction can occur; the case where that reaction was deliberately ignited is logged on the [Overload TNT](../lightning/overload-tnt.md) page.

The product is an unregistered cell that resolves the moment it enters an <ItemLink id="ae2:drive" />. Internal residue can be **read only once**; it is empty afterwards. As for what gets read out — the report only attaches one line: "Sample distribution is extremely uneven; the rarest one is not a cell, it is a notebook with nothing written in it yet."
