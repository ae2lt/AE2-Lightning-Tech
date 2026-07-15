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
  - ae2lt:storage_supercomputing_unit
  - ae2lt:parallel_supercomputing_unit
---

# Supercomputing Units and Performance

The core chamber occupies the central 3×3×3 volume. Its exact center requires one main unit, while the remaining 26 cells must be filled with Storage and Parallel Supercomputing Units. The scene below removes the shell to show the center and one example 16:10 configuration.

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
| <ItemLink id="ae2lt:quantum_supercomputing_unit" /> | ×16 | ×2 | Substantially increased crafting storage |
| <ItemLink id="ae2lt:overload_supercomputing_unit" /> | ×256 | ×4 | Intended for exceptionally large crafting plans |
| <ItemLink id="ae2lt:multidimensional_supercomputing_unit" /> | Infinite | ×8 | No byte limit; parallelism remains globally capped |

A Multidimensional main unit still requires at least one Storage Unit for the structure to form.

## Storage and Parallelism

Let **S** be the number of Storage Units and **P** the number of Parallel Units. A valid configuration satisfies `S + P = 26`, `S ≥ 1`, and `P ≥ 1`.

* Base crafting storage is `64 MiB × S`, multiplied by the main unit's storage multiplier
* Parallelism is `128 × P × the main unit's parallel multiplier`
* Parallelism is capped at **16,384**; any amount above the cap provides no further benefit

| Main Unit | Storage per Storage Unit | With 25 Storage Units | Parallelism per Parallel Unit | With 25 Parallel Units |
|-----------|-------------------------:|----------------------:|------------------------------:|-----------------------:|
| Baseline | 64 MiB | 1,600 MiB (1.56 GiB) | 128 | 3,200 |
| Quantum | 1 GiB | 25 GiB | 256 | 6,400 |
| Overload | 16 GiB | 400 GiB | 512 | 12,800 |
| Multidimensional | Total storage is always infinite | Infinite | 1,024 | 16,384 (reached with 16) |

Parallelism increases crafting-step dispatch capacity; it does not shorten a processing machine's recipe duration. High parallelism also requires sufficient Pattern Providers, processing machines, materials, and transfer capacity.

## Example Configurations

| Configuration | Baseline | Quantum | Overload | Multidimensional |
|---------------|----------|---------|----------|------------------|
| 25 Storage + 1 Parallel | 1.56 GiB / 128 | 25 GiB / 256 | 400 GiB / 512 | Infinite / 1,024 |
| 16 Storage + 10 Parallel | 1 GiB / 1,280 | 16 GiB / 2,560 | 256 GiB / 5,120 | Infinite / 10,240 |
| 1 Storage + 25 Parallel | 64 MiB / 3,200 | 1 GiB / 6,400 | 16 GiB / 12,800 | Infinite / 16,384 |

Each entry lists “crafting storage / parallelism.” A Multidimensional main unit reaches the parallelism cap with 16 Parallel Units; additional Parallel Units provide no further increase.
