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
  - ae2lt:amplifier_supercomputing_unit
  - ae2lt:closed_loop_pattern_storage
  - ae2lt:closed_loop_seed_storage
---

# Supercomputing Units and Performance

The core chamber occupies the central 3×3×3 volume. Its exact center requires one main unit, while the remaining 26 cells accept Blank, Storage, Parallel, or Amplifier Supercomputing Units, Closed-Loop Pattern Storage, and Closed-Loop Seed Storage. The selected main-unit tier determines which combinations are valid. The scene below removes the shell to show the main unit's central position.

<GameScene zoom="4" background="transparent" interactive={true}>
  <ImportStructure src="../assets/assemblies/tianshu_supercomputer_core.snbt" />
  <BoxAnnotation min="3 3 3" max="4 4 4" color="#f2d37a" alwaysOnTop={true}>Only Main-unit Position</BoxAnnotation>
  <IsometricCamera yaw="215" pitch="25" />
</GameScene>

## Main Supercomputing Units

The main unit provides internal storage and sets the ceilings for successful dispatches, external storage, and batch copies.

| Main Unit | Internal Storage | Successful Dispatches/t Cap | Maximum Copies/t | Amplifier Units |
|-----------|-----------------:|----------------------------:|-----------------:|-----------------|
| <ItemLink id="ae2lt:baseline_supercomputing_unit" /> | 1 MiB | 512 | 1,024 | Unsupported |
| <ItemLink id="ae2lt:quantum_supercomputing_unit" /> | 256 MiB | 3,072 | 10,240 | 0–15 |
| <ItemLink id="ae2lt:overload_supercomputing_unit" /> | 64 GiB | 16,384 | 4,194,304 | 0–15 |
| <ItemLink id="ae2lt:multidimensional_supercomputing_unit" /> | Infinite | 16,384 | Infinite | Unsupported |

Baseline, Quantum, and Overload require at least one Parallel Unit; Storage Units are optional. Multidimensional uses only its main-core budget: its peripheral cells cannot contain Storage, Parallel, or Amplifier Units and must instead be filled with Blank Units or the two closed-loop storage types.

## Storage, Dispatch, and Copy Budgets

Let **S**, **P**, and **A** be the counts of Storage, Parallel, and Amplifier Units. Each Storage Unit supplies 64 MiB of external storage, and each Parallel Unit supplies 128 points of base dispatch capacity. Finite tiers require `P ≥ 1`; Quantum and Overload also require `0 ≤ A ≤ 15`.

| Main Unit | Dispatch Gain | External-storage Gain | Copy Gain per Successful Dispatch |
|-----------|--------------:|----------------------:|----------------------------------:|
| Baseline | ×1 | ×1 | ×2 |
| Quantum | `×2(1+A)` | `×2(1+A)` | `×(1+A)` |
| Overload | `×2(1+A)` | `×[2(1+A)]²` | `×(1+A)²` |

Successful dispatches equal `128 × P × dispatch gain`, capped by the main unit. Total crafting storage equals the internal storage plus `64 MiB × S × external-storage gain`. Maximum copies equal successful dispatches times the copy gain, capped by the main unit's copy ceiling.

A “successful dispatch” is one call accepted by a Pattern Provider. One successful dispatch can carry multiple recipe copies, so successful dispatches and maximum copies are independent budgets. Neither shortens a processing machine's recipe duration; reaching the advertised throughput still requires sufficient Pattern Providers, processing machines, materials, and transfer capacity.

## Amplifier, Blank, and Closed-Loop Units

The <ItemLink id="ae2lt:amplifier_supercomputing_unit" /> increases dispatch, external-storage, and batch-copy capacity for Quantum and Overload cores. Baseline and Multidimensional reject Amplifier Units; Quantum and Overload accept at most 15.

## Blank and Closed-Loop Units

The <ItemLink id="ae2lt:blank_supercomputing_unit" /> is a neutral placeholder comparable to the Matter Warping Matrix's Blank Sub Core. It keeps a peripheral cell structurally valid but contributes no crafting storage, dispatch capacity, amplification, pattern slots, or seed capacity.

Closed-Loop Pattern Storage and Closed-Loop Seed Storage are also valid peripheral units. They provide their named closed-loop storage functions but do not count toward **S**, **P**, or **A**. Use Blank Units wherever no additional attribute or physical closed-loop storage is needed.
