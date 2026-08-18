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
  - ae2lt:matter_warping_matrix_thread_unit_t1
  - ae2lt:matter_warping_matrix_thread_unit_t2
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

# Quick Overview

## Core Structure and Units

Before using auto-build, make sure the volume extending 3 blocks to either side of the Controller, 5 blocks above and below it, and 6 blocks behind it contains no unrelated blocks. Otherwise, auto-build cannot complete.

The core chamber supports Thread, Thermal Control, and Amplifier Units as specialized Peripheral Units, as well as Blank Units. Only a Main Core may occupy the center position. Thread and Thermal Control Units each have T1 and T2 variants, with T2 offering better performance than T1.

Thread Units determine the crafting capacity per tick, similar to adding more Molecular Assemblers. Thermal Control Units regulate heat, but their effectiveness decreases with distance from the Main Core. Amplifier Units multiply overall thread capacity, and no more than 15 may be installed. Blank Units fill required positions without providing bonuses.

> The Multidimensional Main Core cannot use specialized Peripheral Units; all other positions must contain Tianshu Blank Units. The Stable Main Core cannot use Amplifier Units.

Every position in the core chamber must be filled or the structure will not form. Higher-tier Main Cores provide higher capacity limits, summarized below. The structure also requires at least one Pattern Storage.

| Main Core | Crafting Cap per Tick | Thermal Strategy |
|-----------|----------------------:|------------------|
| Stable Main Core | 4,096 executions | Keep heat as low as possible |
| Quantum Main Core | 122,880 executions | Lower heat provides higher efficiency |
| Overload Main Core | 4,194,304 executions | Keep heat near 50% |
| Multidimensional Main Core | Unlimited | Heat is ignored |

## Outer Structure

Pattern Storages may be installed in the upper and lower internal layers of the structure to provide pattern slots. They can be installed or replaced directly through the Controller's auto-build and pattern-upgrade functions without opening the structure.

By default, the Matrix Port is installed opposite the Controller. It may instead occupy either side candidate position, but the structure must contain exactly one Port to form.

## Main Core Modes

| Main Core | Crafting Cap per Tick | Thermal Strategy |
|-----------|----------------------:|------------------|
| Stable Main Core | 4,096 executions | Keep it cool; efficiency falls with heat but never below 45% |
| Quantum Main Core | 122,880 executions | Lower heat provides higher efficiency |
| Overload Main Core | 4,194,304 executions | Peak efficiency is near 50% heat; keep it in the 42%–58% sweet spot |
| Multidimensional Main Core | Unlimited | Heat is ignored; all other 80 slots must be Tianshu Blank Units |

The number of pattern executions the matrix performs each tick is determined in three steps:

1. **Base capacity**: Stable uses dedicated values: each T1 Thread Unit provides `1,024`, each T2 provides `3,584`, and the total is capped at `4,096`. Quantum and Overload use `256 × thread points × amplification factor`; T1 provides 1 thread point and T2 provides 2. The amplification factor is `R²` for Quantum and `R³` for Overload, where `R = 1 + the number of Tianshu Amplifier Units`.
2. Base capacity beyond the Main Core's per-tick cap is cut off at the cap.
3. The result is multiplied by the current thermal efficiency to give the actual throughput.

The Multidimensional Main Core has no execution cap, but the matrix still issues at most 16,384 crafting calls per tick. Matrix throughput depends only on its own core configuration and heat; it is independent of the Tianshu Supercomputing Array's dispatch and copy budgets.

## Peripheral Units

| Peripheral Unit | Provides | Purpose |
|-----------------|----------|---------|
| Thread Unit T1 | Stable: 1,024; Quantum/Overload: 1 thread point | Raises base crafting capacity per tick |
| Thread Unit T2 | Stable: 3,584; Quantum/Overload: 2 thread points | Provides dedicated Stable capacity; in Quantum and Overload, two thread points per slot save core positions |
| Tianshu Amplifier Unit | `R` +1 | Raises the amplification factor of Quantum and Overload cores; the same block is shared with the Tianshu Supercomputing Array |
| Thermal Control Unit T1 | 1 cooling point | Raises heat capacity and cooling rate; the actual effect decays with distance from the Main Core |
| Thermal Control Unit T2 | 2 cooling points | Twice the cooling points per slot, with the same distance decay |
| Tianshu Blank Unit | — | Shared by both Tianshu multiblocks; fills a required core slot without adding performance attributes |

Quantum and Overload configurations allow at most **15 Tianshu Amplifier Units**; a sixteenth prevents the structure from forming. Stable and Multidimensional Main Cores reject amplifiers.

## Cooling Distance

Thermal Control Units scale their effective power by Manhattan distance from the central Main Core:

| Distance | Effective Cooling Power |
|---------:|------------------------:|
| 1 block | 100% |
| 2 blocks | 75% |
| 3 blocks | 50% |
| 4 blocks | 25% |
| 5 or more blocks | 0% |

## Heat and Cooling Formulas

First calculate the effective cooling points from all Thermal Control Units:

`C = Σ(unit cooling points × distance multiplier)`

A T1 unit provides 1 point and a T2 unit provides 2 points. The distance multiplier comes from the table above. For example, a T2 unit 2 blocks from the Main Core contributes `2 × 75% = 1.5` effective cooling points.

Effective cooling points determine both heat capacity and the cooling rate per tick:

* **Heat capacity:** `K = 2048 + 150 × C`
* **Cooling rate:** `r = 0.00008 + 0.000025 × C`
* **Displayed heat:** `h = clamp(H ÷ K, 0, 1)`

Here, `H` is the matrix's currently stored heat and `h` is the 0%–100% heat shown by the Controller. The cooling rate is proportional rather than fixed: while idle, the heat remaining after each tick is `H_next = H_current × (1 - r)`. For example, when `C = 20`, heat capacity is `5,048` and the matrix dissipates `0.058%` of its current heat each tick.

While the matrix is working, it generates heat from its actual load before applying that tick's cooling:

`H_next = (H_current + P × g × L) × (1 - r)`

* `P` is the total thread-point count: each T1 Thread Unit provides 1 point and each T2 provides 2
* `L` is the pattern executions accepted by the matrix this tick divided by the available executions, clamped to 0–1; it is 0 while idle
* Stable and Quantum Main Cores use the heat-generation coefficient `g = 0.256`
* The Overload Main Core uses `g = 1.2032`

Heat then affects actual throughput through thermal efficiency:

* **Stable and Quantum:** `efficiency = clamp(1 - h, 0.45, 1)`, so efficiency never falls below 45%
* **Overload:** `efficiency = 0.05 + 0.95 × [4h(1 - h)]⁵`, peaking at `h = 50%`
* **Multidimensional:** fixed at 100%; it neither reads nor accumulates heat
