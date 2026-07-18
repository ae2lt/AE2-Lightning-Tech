---
navigation:
  title: Supercomputing Units and Performance
  icon: ae2lt:baseline_supercomputing_unit
  parent: tianshu/tianshu-index.md
  position: 20
item_ids:
  - ae2lt:baseline_supercomputing_unit
  - ae2lt:quantum_supercomputing_unit
  - ae2lt:overload_supercomputing_unit
  - ae2lt:multidimensional_supercomputing_unit
  - ae2lt:blank_supercomputing_unit
  - ae2lt:storage_supercomputing_unit
  - ae2lt:parallel_supercomputing_unit
  - ae2lt:closed_loop_pattern_storage
  - ae2lt:closed_loop_seed_storage
---

# Supercomputing Units and Performance

The core chamber occupies the central 3×3×3 volume. Its exact center requires one main unit, while the remaining 26 cells accept Blank, Storage, or Parallel Supercomputing Units, Closed-Loop Pattern Storage, and Closed-Loop Seed Storage. The scene below removes the shell to show the main unit's central position.

<GameScene zoom="4" background="transparent" interactive={true}>
  <ImportStructure src="../assets/assemblies/tianshu_supercomputer_core.snbt" />
  <BoxAnnotation min="3 3 3" max="4 4 4" color="#f2d37a" alwaysOnTop={true}>Only Main-unit Position</BoxAnnotation>
  <IsometricCamera yaw="215" pitch="25" />
</GameScene>

## Main Supercomputing Units

The main unit determines both the crafting-storage multiplier and the parallelism multiplier.

| Main Unit | Storage Multiplier | Parallel Multiplier | Characteristic |
|-----------|-------------------:|--------------------:|----------------|
| <ItemLink id="ae2lt:baseline_supercomputing_unit" /> | ×1 | ×1 | Base performance |
| <ItemLink id="ae2lt:quantum_supercomputing_unit" /> | ×16 | ×3 | Substantially increased crafting storage |
| <ItemLink id="ae2lt:overload_supercomputing_unit" /> | ×256 | ×6 | Intended for exceptionally large crafting plans |
| <ItemLink id="ae2lt:multidimensional_supercomputing_unit" /> | Infinite | ×8 | No byte limit; parallelism remains globally capped |

A Multidimensional main unit still requires at least one Storage Unit for the structure to form.

## Storage and Parallelism

Let **S** be the number of Storage Units, **P** the number of Parallel Units, and **O** the number of all other valid peripheral units. A valid configuration satisfies `S + P + O = 26`, `S ≥ 1`, and `P ≥ 1`.

* Base crafting storage is `64 MiB × S`, multiplied by the main unit's storage multiplier
* Parallelism is `128 × P × the main unit's parallel multiplier`
* Parallelism is capped at **16,384**; any amount above the cap provides no further benefit

The table below gives the actual contribution of each peripheral unit with the selected main unit installed; the main-unit multiplier is already included.

| Main Unit | Storage per Storage Unit | Parallelism per Parallel Unit |
|-----------|-------------------------:|------------------------------:|
| Baseline | 64 MiB | 128 |
| Quantum | 1 GiB | 384 |
| Overload | 16 GiB | 768 |
| Multidimensional | Total storage is always infinite | 1,024 |

Parallelism increases crafting-step dispatch capacity; it does not shorten a processing machine's recipe duration. High parallelism also requires sufficient Pattern Providers, processing machines, materials, and transfer capacity.

## Blank and Closed-Loop Units

The <ItemLink id="ae2lt:blank_supercomputing_unit" /> is a neutral placeholder comparable to the Matter Warping Matrix's Blank Sub Core. It keeps a peripheral cell structurally valid but contributes no crafting storage, parallelism, pattern slots, or seed capacity.

Closed-Loop Pattern Storage and Closed-Loop Seed Storage are also valid peripheral units. They provide their named closed-loop storage functions but do not count toward **S** or **P**. Use Blank Units wherever no additional attribute or physical closed-loop storage is needed.
