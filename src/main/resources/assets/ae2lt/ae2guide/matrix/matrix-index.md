---
navigation:
  title: Matter Warping Matrix
  icon: ae2lt:matter_warping_matrix_controller
  parent: index.md
  position: 45
---

# Matter Warping Matrix

The **Matter Warping Matrix** is a large ME-network crafting multiblock. It executes patterns stored in its Pattern Storages, using configurable parallelism, batch size, and thermal control for high-throughput production of base materials and intermediate components.

The matrix is a **crafting provider**, not crafting storage. An ME Crafting CPU still plans each job and supplies its ingredients. The matrix accepts the work, assembles the results, and inserts them directly back into the network.

<SubPages />

## Basic Workflow

1. [Build the fixed 7×11×7 structure](construction.md), place one main core at its center, and fill the remaining 80 sub-core positions
2. Insert patterns into at least one Pattern Storage
3. Connect the Matrix Port to a powered ME network
4. Request a crafting job from an ME terminal; the matrix participates as a provider for its stored patterns

## Operating Requirements

* The structure must be formed and its core configuration must be valid
* Exactly one Matrix Port and one main core must be present
* At least one Pattern Storage must be installed
* The port must be connected to an active ME network; its idle power usage is **8 AE/t**, and it does not consume a channel

The structure requires no external FE and does not directly consume Lightning.
