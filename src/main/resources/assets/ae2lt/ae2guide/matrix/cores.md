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

| Main Core | Crafting Cap per Tick | Thermal strategy |
|-----------|----------------------:|------------------|
| Stable Main Core | 1,024 executions | Keep it cool; efficiency falls with heat but never below 45% |
| Quantum Main Core | 10,240 executions | Lower heat provides higher efficiency |
| Overload Main Core | 4,194,304 executions | Peak efficiency is near 50% heat; keep it in the 42%–58% sweet spot |
| Creative Main Core | Unlimited | Heat is ignored; all other 80 slots must be Blank Units |

The number of pattern executions the matrix performs each tick is determined in three steps:

1. **Base capacity** = `256 × thread points × amplification factor`. Thread points come from Thread Units (1 point per T1, 2 points per T2). The amplification factor depends on the Main Core: Stable is always 1, Quantum is `R²`, and Overload is `R³`, where `R = 1 + the number of Tianshu Amplifier Units`.
2. Base capacity beyond the Main Core's per-tick cap is cut off at the cap.
3. The result is multiplied by the current thermal efficiency to give the actual throughput.

The Creative Main Core has no execution cap, but the matrix still issues at most 16,384 crafting calls per tick. Matrix throughput depends only on its own core configuration and heat; it is independent of the Tianshu Supercomputing Array's dispatch and copy budgets.

## Peripheral Units

| Peripheral Unit | Provides | Purpose |
|-----------------|----------|---------|
| Thread Unit T1 | 1 thread point | Raises base crafting capacity per tick |
| Thread Unit T2 | 2 thread points | Twice the thread points per slot at a higher crafting cost |
| Tianshu Amplifier Unit | `R` +1 | Raises the amplification factor of Quantum and Overload cores; the same block is shared with the Tianshu Supercomputing Array |
| Thermal Control Unit T1 | 1 cooling point | Raises heat capacity and cooling rate; the actual effect decays with distance from the main core |
| Thermal Control Unit T2 | 2 cooling points | Twice the cooling points per slot, with the same distance decay |
| Blank Unit | — | Fills a required core slot without adding performance attributes |

Quantum and Overload configurations allow at most **15 Tianshu Amplifier Units**; a sixteenth prevents the structure from forming. Stable and Creative Main Cores reject amplifiers.

## Cooling Distance

Thermal Control Units scale their effective power by Manhattan distance from the central main core:

| Distance | Effective cooling power |
|---------:|------------------------:|
| 1 block | 100% |
| 2 blocks | 75% |
| 3 blocks | 50% |
| 4 blocks | 25% |
| 5 or more blocks | 0% |
