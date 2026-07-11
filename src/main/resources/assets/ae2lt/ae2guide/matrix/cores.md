---
navigation:
  title: Main Cores, Sub Cores, and Heat
  icon: ae2lt:matter_warping_matrix_stable_main_core
  parent: matrix/matrix-index.md
  position: 20
item_ids:
  - ae2lt:matter_warping_matrix_stable_main_core
  - ae2lt:matter_warping_matrix_quantum_main_core
  - ae2lt:matter_warping_matrix_overload_main_core
  - ae2lt:matter_warping_matrix_creative_main_core
  - ae2lt:matter_warping_matrix_blank_sub_core
  - ae2lt:matter_warping_matrix_thread_sub_core_t1
  - ae2lt:matter_warping_matrix_thread_sub_core_t2
  - ae2lt:matter_warping_matrix_multiplier_sub_core_t1
  - ae2lt:matter_warping_matrix_multiplier_sub_core_t2
  - ae2lt:matter_warping_matrix_cooling_sub_core_t1
  - ae2lt:matter_warping_matrix_cooling_sub_core_t2
---

# Main Cores, Sub Cores, and Heat

The core chamber contains 81 positions. Its geometric center must hold one main core, and the remaining 80 positions must all contain sub cores. The scene below removes the outer structure to show an example configuration. It distinguishes the available core positions and is not the only or universally optimal layout.

<GameScene zoom="3" background="transparent" interactive={true}>
  <ImportStructure src="../assets/assemblies/matter_warping_matrix_core.snbt" />
  <BoxAnnotation min="3 5 3" max="4 6 4" color="#f2d37a" alwaysOnTop={true}>The only valid main-core position</BoxAnnotation>
  <IsometricCamera yaw="215" pitch="25" />
</GameScene>

## Main Core Modes

| Main core | Operating behavior | Thermal strategy |
|-----------|--------------------|------------------|
| Stable Main Core | Runs at the base batch size; efficiency falls as heat rises but never below 45% | Keep it cool for consistently high throughput |
| Quantum Main Core | Applies a Quantum factor of up to 64× to the base batch; the factor bottoms out at 28.8× when hot | Lower heat provides a higher factor |
| Overload Main Core | Drives its Overload factor with heat; the factor peaks around 50% heat and collapses toward both extremes | Hold heat within the 42%–58% sweet spot |
| Creative Main Core | Unbounded dispatch and ignores sub-core attributes, heat, and the Multiplier limit | No thermal management is required |

The main core selects the batch-factor formula, while practical throughput remains dependent on the sub-core configuration.

## Sub Cores

| Sub core | T1 | T2 | Effect |
|----------|---:|---:|--------|
| Parallel Sub Core | Parallel power 2 | Parallel power 4 | Raises the base dispatch capacity per tick; the gain has a soft cap and is not linear |
| Batch Multiplier Sub Core | Batch power 1 | Batch power 2 | Adds 0.4 to the base batch for every point of power; the starting base is 4 |
| Cooling Sub Core | Cooling power 1 | Cooling power 2 | Raises heat capacity and cooling rate; its effect depends on distance from the main core |
| Blank Sub Core | — | — | Fills a required core position without contributing performance |

A non-Creative configuration may contain at most **10 Batch Multiplier Sub Cores**. An eleventh makes the core configuration invalid and prevents the matrix from providing crafting capacity.

## Cooling Distance

Cooling Sub Cores scale their effective power by Manhattan distance from the central main core:

| Distance | Effective cooling power |
|---------:|------------------------:|
| 1 block | 100% |
| 2 blocks | 75% |
| 3 blocks | 50% |
| 4 blocks | 25% |
| 5 or more blocks | 0% |
