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

| Main Core | Unified gain | Thermal strategy |
|-----------|--------------|------------------|
| Stable Main Core | `Gd=1`, `Gt=2`, up to 1,024 operations/t | Keep it cool; efficiency falls with heat but never below 45% |
| Quantum Main Core | `Gd=2R`, `Gt=R`, up to 10,240 operations/t | Lower heat provides higher efficiency |
| Overload Main Core | `Gd=2R`, `Gt=R²`, up to 4,194,304 operations/t | Peak efficiency is near 50% heat; keep it in the 42%–58% sweet spot |
| Creative Main Core | Logical operations are unbounded, but provider calls retain the 16,384/t fuse | Heat is ignored; all other 80 slots must be Blank Sub Cores |

Here `R=1+N`, where `N` is the number of Amplifier Sub Cores. The matrix calculates base operations as `128×P×Gd×Gt`, then applies the tier ceiling and thermal efficiency. It does not maintain the CPU successful-dispatch budget `D` and has no separate batch width `q`.

## Sub Cores

| Sub Core | T1 raw contribution | T2 raw contribution | Purpose |
|----------|--------------------:|--------------------:|---------|
| Parallel Sub Core | `P+1` | `P+1` | Each supplies 128 raw dispatch points, amplified by the main core's `Gd` |
| Amplifier Sub Core | `N+1` | `N+1` | Raises `Gd` and `Gt` for Quantum and Overload cores |
| Cooling Sub Core | cooling `+1` | cooling `+1` | Raises heat capacity and cooling rate; effective cooling still depends on distance from the main core |
| Blank Sub Core | — | — | Fills a required core slot without adding performance attributes |

T1/T2 blocks remain as structural and progression variants, but contribute the same raw logical unit under the unified model. Quantum and Overload configurations allow at most **15 Amplifier Sub Cores**; a sixteenth invalidates the configuration. Stable and Creative Main Cores reject amplifiers.

## Cooling Distance

Cooling Sub Cores scale their effective power by Manhattan distance from the central main core:

| Distance | Effective cooling power |
|---------:|------------------------:|
| 1 block | 100% |
| 2 blocks | 75% |
| 3 blocks | 50% |
| 4 blocks | 25% |
| 5 or more blocks | 0% |
