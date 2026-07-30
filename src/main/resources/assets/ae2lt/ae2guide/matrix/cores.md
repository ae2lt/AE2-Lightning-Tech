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
  - ae2lt:matter_warping_matrix_multidimensional_main_core
  - ae2lt:tianshu_blank_unit
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
| Stable Main Core | 4,096 executions | Keep it cool; efficiency falls with heat but never below 45% |
| Quantum Main Core | 122,880 executions | Lower heat provides higher efficiency |
| Overload Main Core | 4,194,304 executions | Peak efficiency is near 50% heat; keep it in the 42%–58% sweet spot |
| Multidimensional Main Core | Unlimited | Heat is ignored; all other 80 slots must be Tianshu Blank Units |

The number of pattern executions the matrix performs each tick is determined in three steps:

1. **Base capacity**: Stable uses a dedicated table: each T1 Thread Unit provides `1,024`, each T2 provides `3,584`, and the total is capped at `4,096`. Quantum and Overload use `256 × thread points × amplification factor`; T1 provides 1 thread point and T2 provides 2. The amplification factor is `R²` for Quantum and `R³` for Overload, where `R = 1 + the number of Tianshu Amplifier Units`.
2. Base capacity beyond the Main Core's per-tick cap is cut off at the cap.
3. The result is multiplied by the current thermal efficiency to give the actual throughput.

The Multidimensional Main Core has no execution cap, but the matrix still issues at most 16,384 crafting calls per tick. Matrix throughput depends only on its own core configuration and heat; it is independent of the Tianshu Supercomputing Array's dispatch and copy budgets.

## Peripheral Units

| Peripheral Unit | Provides | Purpose |
|-----------------|----------|---------|
| Thread Unit T1 | Stable: 1,024; Quantum/Overload: 1 thread point | Raises base crafting capacity per tick |
| Thread Unit T2 | Stable: 3,584; Quantum/Overload: 2 thread points | Provides dedicated Stable capacity; in Quantum and Overload, two thread points per slot save core positions |
| Tianshu Amplifier Unit | `R` +1 | Raises the amplification factor of Quantum and Overload cores; the same block is shared with the Tianshu Supercomputing Array |
| Thermal Control Unit T1 | 1 cooling point | Raises heat capacity and cooling rate; the actual effect decays with distance from the main core |
| Thermal Control Unit T2 | 2 cooling points | Twice the cooling points per slot, with the same distance decay |
| Tianshu Blank Unit | — | Shared by both Tianshu multiblocks; fills a required core slot without adding performance attributes |

Quantum and Overload configurations allow at most **15 Tianshu Amplifier Units**; a sixteenth prevents the structure from forming. Stable and Multidimensional Main Cores reject amplifiers.

## Cooling Distance

Thermal Control Units scale their effective power by Manhattan distance from the central main core:

| Distance | Effective cooling power |
|---------:|------------------------:|
| 1 block | 100% |
| 2 blocks | 75% |
| 3 blocks | 50% |
| 4 blocks | 25% |
| 5 or more blocks | 0% |

## Heat and Cooling Formulas

First calculate the effective cooling points of all Thermal Control Units:

`C = Σ(unit cooling points × distance factor)`

A T1 unit provides 1 point and a T2 unit provides 2 points; use the distance factors in the table above. For example, a T2 unit two blocks from the Main Core provides `2 × 75% = 1.5` effective cooling points.

Effective cooling points determine both heat capacity and dissipation per tick:

* **Heat capacity:** `K = 2048 + 150 × C`
* **Dissipation rate:** `r = 0.00008 + 0.000025 × C`
* **Displayed heat:** `h = clamp(H ÷ K, 0, 1)`

Here, `H` is the matrix's stored heat and `h` is the 0%–100% heat shown by the Controller. Dissipation is proportional rather than a fixed subtraction: while idle, heat after each tick is `Hnext = Hcurrent × (1 - r)`. For example, at `C = 20`, heat capacity is `5,048` and the matrix dissipates `0.058%` of its current heat each tick.

While working, the matrix generates heat from its actual load before applying that tick's dissipation:

`Hnext = (Hcurrent + P × g × L) × (1 - r)`

* `P` is the total thread points: 1 for each T1 Thread Unit and 2 for each T2
* `L` is the executions accepted by the matrix this tick divided by the executions available, clamped to 0–1; it is 0 when there is no work
* Stable and Quantum Main Cores use the heat coefficient `g = 0.256`
* The Overload Main Core uses the heat coefficient `g = 1.2032`

Heat then affects actual throughput through thermal efficiency:

* **Stable and Quantum:** `efficiency = clamp(1 - h, 0.45, 1)`, so efficiency never falls below 45%
* **Overload:** `efficiency = 0.05 + 0.95 × [4h(1 - h)]⁵`, peaking at `h = 50%`
* **Multidimensional:** Fixed at 100%; it neither reads nor accumulates heat
