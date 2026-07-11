---
navigation:
  title: Patterns, Port, and Operation
  icon: ae2lt:matter_warping_matrix_pattern_storage_t1
  parent: matrix/matrix-index.md
  position: 30
item_ids:
  - ae2lt:matter_warping_matrix_pattern_storage_t1
  - ae2lt:matter_warping_matrix_pattern_storage_t2
  - ae2lt:matter_warping_matrix_pattern_storage_upgrade
---

# Patterns, Port, and Operation

## Pattern Storage

The structure provides 50 optional Pattern Storage positions. At least one storage is required for the matrix to form.

| Pattern Storage | Capacity each | Total with all 50 installed |
|-----------------|--------------:|----------------------------:|
| T1 | 36 | 1,800 |
| T2 | 72 | 3,600 |

T1 and T2 storages may be mixed in one structure. The total capacity is the sum of every installed storage, with one encoded pattern per slot.

The matrix only accepts **encoded crafting patterns** for workbench crafting supported by Molecular Assemblers. Processing patterns are rejected and are never published by the matrix as available patterns.

## Inserting and Removing Patterns

Once the matrix is formed and connected to an active ME network, all of its pattern slots appear as a group represented by the Matrix Port in the **Pattern Access Terminal**. Patterns can be inserted, replaced, or removed there without opening the outer structure.

The Matrix Port also exposes item insertion for automation. Item pipes can insert valid encoded crafting patterns into available slots. Breaking a Pattern Storage drops its stored patterns; upgrading it in place from the Controller preserves them.

## Upgrading Pattern Storage

Place Matter Warping Matrix Pattern Storage Upgrades in the player inventory, then use **Upgrade** from the Controller. The matrix replaces T1 storages with T2 storages in place, limited by the number of upgrades carried:

* One upgrade item is consumed per upgraded storage
* Stored patterns are preserved
* If too few upgrades are available, only the affordable number is upgraded
* No upgrade items are consumed when no T1 storages remain

## Matrix Port

Exactly one Port must be installed. Any of the three candidate positions is valid. When no existing Port is found, auto-build places it directly opposite the Controller.

The Port:

* Publishes the matrix as a crafting provider to the ME network
* Aggregates and exposes all Pattern Storages
* Receives ingredients from crafting jobs and inserts completed results directly into the network
* Accepts ME cable connections on all six sides, uses 8 AE/t while idle, and does not consume a channel

The matrix accepts no new crafting work while the Port is disconnected from a powered network or belongs to an unformed structure.

## Troubleshooting

* **Structure does not form:** Check the central main core, all 80 sub cores, at least one Pattern Storage, the unique Port, and every fixed shell position
* **Auto-build does nothing:** Remove invalid blocks from target positions and verify that all materials are in the player inventory
* **Port is absent from the network:** Check the cable connection, network power, and formation state
* **Pattern cannot be inserted:** Confirm that it is an encoded crafting pattern rather than a processing pattern
* **A stored pattern is not selected for a job:** Confirm that the pattern is stored in this matrix and that the Port is online, then check for other providers advertising the same pattern
