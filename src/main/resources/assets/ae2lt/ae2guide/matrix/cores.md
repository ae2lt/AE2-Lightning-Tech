---
navigation:
  title: Main Cores, Peripheral Units, and Heat
  icon: ae2lt:matter_warping_matrix_stable_main_core
  parent: matrix/matrix-index.md
  position: 20
item_ids:
  - ae2lt:matter_warping_matrix_stable_main_core
  - ae2lt:matter_warping_matrix_quantum_main_core
  - ae2lt:matter_warping_matrix_overload_main_core
  - ae2lt:matter_warping_matrix_creative_main_core
  - ae2lt:matter_warping_matrix_blank_unit
  - ae2lt:matter_warping_matrix_thread_unit_t1
  - ae2lt:matter_warping_matrix_thread_unit_t2
  - ae2lt:tianshu_amplifier_unit
  - ae2lt:matter_warping_matrix_thermal_control_unit_t1
  - ae2lt:matter_warping_matrix_thermal_control_unit_t2
---

# Main Cores, Peripheral Units, and Heat

The core chamber contains 81 positions. Its geometric center must hold one Main Core, and the remaining 80 positions must all contain Peripheral Units. The scene below removes the outer structure to show an example configuration. It distinguishes the available core positions and is not the only or universally optimal layout.

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
| Creative Main Core | Logical operations are unbounded, but provider calls retain the 16,384/t fuse | Heat is ignored; all other 80 slots must be Blank Units |

Here `R=1+N`, where `N` is the number of Tianshu Amplifier Units, and `P` is the sum of thread-power points supplied by Thread Units. The matrix calculates base operations as `128×P×Gd×Gt`, then applies the tier ceiling and thermal efficiency. It does not maintain the CPU successful-dispatch budget `D` and has no separate batch width `q`.

## Peripheral Units

| Peripheral Unit | Raw contribution | Purpose |
|-----------------|-----------------:|---------|
| Thread Unit T1 | `P+1` | Supplies 128 raw thread points, amplified by the main core's `Gd` |
| Thread Unit T2 | `P+2` | Supplies 256 raw thread points at a higher crafting cost to save core slots |
| Tianshu Amplifier Unit | `N+1` | Shared by Tianshu and the matrix; raises `Gd` and `Gt` for Quantum and Overload cores |
| Thermal Control Unit T1 | thermal `+1` | Raises heat capacity and cooling rate; effective power still depends on distance from the main core |
| Thermal Control Unit T2 | thermal `+2` | Supplies twice the raw thermal-control power before the same distance decay |
| Blank Unit | — | Fills a required core slot without adding performance attributes |

Thread and Thermal Control T1/T2 blocks remain the same logical types but contribute fixed raw powers of 1 and 2. Amplification no longer has T1/T2 variants. Quantum and Overload configurations allow at most **15 Tianshu Amplifier Units**; a sixteenth invalidates the configuration. Stable and Creative Main Cores reject amplifiers.

## Cooling Distance

Thermal Control Units scale their effective power by Manhattan distance from the central main core:

| Distance | Effective cooling power |
|---------:|------------------------:|
| 1 block | 100% |
| 2 blocks | 75% |
| 3 blocks | 50% |
| 4 blocks | 25% |
| 5 or more blocks | 0% |
