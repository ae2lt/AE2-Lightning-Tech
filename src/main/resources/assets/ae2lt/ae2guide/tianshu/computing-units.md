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

The core chamber occupies the central 3×3×3 volume. Its exact center requires one main unit, while the remaining 26 cells accept only Blank, Storage, Parallel, or Amplifier Supercomputing Units. Closed-Loop Pattern Storage and Closed-Loop Seed Storage are installed in the shell's cooling-compatible positions, which share their placement rules with Phase-Change Cooling Units. The selected main-unit tier determines which core combinations are valid. The scene below removes the shell to show the main unit's central position.

<GameScene zoom="4" background="transparent" interactive={true}>
  <ImportStructure src="../assets/assemblies/tianshu_supercomputer_core.snbt" />
  <BoxAnnotation min="3 3 3" max="4 4 4" color="#f2d37a" alwaysOnTop={true}>Only Main-unit Position</BoxAnnotation>
  <IsometricCamera yaw="215" pitch="25" />
</GameScene>

## Main Supercomputing Units

The main unit provides internal crafting storage and sets the ceilings for dispatch parallelism, external storage, and batch copies. These parameters independently control task capacity, AE2 pattern dispatches, and the processing width of compatible batch execution paths.

| Main Unit | Internal Storage | Successful Dispatches/t Cap | Maximum Copies/t | Amplifier Units |
|-----------|-----------------:|----------------------------:|-----------------:|-----------------|
| <ItemLink id="ae2lt:baseline_supercomputing_unit" /> | 1 MiB | 512 | 1,024 | Unsupported |
| <ItemLink id="ae2lt:quantum_supercomputing_unit" /> | 256 MiB | 3,072 | 10,240 | 0–15 |
| <ItemLink id="ae2lt:overload_supercomputing_unit" /> | 64 GiB | 16,384 | 4,194,304 | 0–15 |
| <ItemLink id="ae2lt:multidimensional_supercomputing_unit" /> | Infinite | 16,384 | Infinite | Unsupported |

Baseline, Quantum, and Overload require at least one Parallel Unit; Storage Units are optional. Multidimensional uses only its main-core budget: its 26 peripheral cells cannot contain Storage, Parallel, or Amplifier Units and must all be filled with Blank Units.

## Performance Parameters

### Crafting Storage

Crafting storage holds crafting plans, intermediate results, and task state. Tianshu can run multiple crafting jobs at the same time. Each job reserves its required bytes when it starts and releases them when it completes or is cancelled.

Storage determines whether a job can be accepted and how many jobs can remain active. It does not increase pattern dispatch speed or reduce machine processing time.

### Successful Dispatches

A successful dispatch is one pattern dispatch per tick actually accepted by a Pattern Provider; it directly determines how many processing machines Tianshu can put to work each tick. The parallelism shown in the crafting confirmation screen's CPU selection list comes from this parameter and excludes the batch-copy budget described below.

A larger dispatch budget never makes an individual machine work faster. When providers, machines, materials, or network transfer capacity run short, rejected dispatch calls do not count as successful dispatches, and the surplus budget simply sits idle.

### Batch Copies

One copy means one execution of a pattern recipe. The batch-copy budget limits the total number of pattern executions that compatible execution paths can submit per tick. For example, one provider call that submits 32 executions of the same pattern consumes one successful dispatch and 32 copies.

Batch copies do not duplicate items: every execution still consumes inputs, energy, processing time, and output space exactly as the pattern declares.

Only batch-compatible targets can use copy budget beyond the successful-dispatch count: Molecular Assembler-compatible patterns, the Matter Warping Matrix, supported closed-loop batch patterns, and targets with a dedicated batch adapter. An ordinary AE2 processing pattern sent to a general-purpose machine accepts only one copy per call; throughput is then determined by successful dispatches, and unused copy budget is not converted into additional dispatches.

## Unit Counts and Formulas

Let **S**, **P**, and **A** be the counts of Storage, Parallel, and Amplifier Units. Each Storage Unit supplies 64 MiB of external storage, and each Parallel Unit supplies 128 points of base dispatch capacity. Finite tiers require `P ≥ 1`; Quantum and Overload also require `0 ≤ A ≤ 15`.

| Main Unit | Dispatch Gain | External-storage Gain | Copy Gain per Successful Dispatch |
|-----------|--------------:|----------------------:|----------------------------------:|
| Baseline | ×1 | ×1 | ×2 |
| Quantum | `×2(1+A)` | `×2(1+A)` | `×(1+A)` |
| Overload | `×2(1+A)` | `×[2(1+A)]²` | `×(1+A)²` |

The resulting parameters are calculated as follows:

* Successful dispatches equal `128 × P × dispatch gain`, capped by the main unit.
* Total crafting storage equals the internal storage plus `64 MiB × S × external-storage gain`.
* Maximum copies equal successful dispatches times the copy gain, capped by the main unit's copy ceiling.

For example, a Quantum core with 20 Parallel Units, 5 Amplifier Units, and 1 Storage Unit: the dispatch gain is ×12, so the formula gives `128 × 20 × 12 = 30,720` dispatches, capped at **3,072** by the Quantum ceiling; external storage is `64 MiB × 1 × 12 = 768 MiB`, giving **1 GiB** with the 256 MiB internal storage; the copy gain is ×6, so the formula gives `3,072 × 6 = 18,432` copies, capped at **10,240**. The controller screen indicates when a parameter has reached its cap.

To illustrate how the two budgets relate, consider a configuration with 512 successful dispatches and 1,024 maximum copies per tick: native single-copy dispatch can use at most 512 of those copies, while a compatible batch path can combine multiple executions of the same pattern within 512 accepted calls and use up to 1,024 copies. The provider and target determine the actual grouping; a single machine is not guaranteed to accept the complete budget.

## Configuration Guidance

* **Jobs rejected for insufficient capacity:** Add Storage Units or switch to a higher-tier main unit
* **Plenty of providers and machines but too few working at once:** Add Parallel Units; Quantum and Overload cores can also add Amplifier Units
* **Amplifier Units scale dispatch, external storage, and batch copies simultaneously** and are the main lever for Quantum and Overload cores; note that any formula result beyond the main unit's cap is wasted
* **Multidimensional** provides every parameter from the main core itself; simply fill the periphery with Blank Units — no ratios to balance

## Amplifier, Blank, and Closed-Loop Storage

The <ItemLink id="ae2lt:amplifier_supercomputing_unit" /> increases dispatch, external-storage, and batch-copy capacity for Quantum and Overload cores. Baseline and Multidimensional reject Amplifier Units; Quantum and Overload accept at most 15.

The <ItemLink id="ae2lt:blank_supercomputing_unit" /> is a neutral placeholder comparable to the Matter Warping Matrix's Blank Sub Core. It keeps a peripheral cell structurally valid but contributes no crafting storage, dispatch capacity, amplification, pattern slots, or seed capacity.

Closed-loop analysis and execution are built into the main unit and require no separate closed-loop compute core. Closed-Loop Pattern Storage and Closed-Loop Seed Storage remain external physical storage installed in the shell's cooling-compatible positions, where each replaces a Phase-Change Cooling Unit. They provide pattern capacity or seed storage but do not occupy the 26 core-chamber peripheral cells and do not count toward **S**, **P**, or **A**. Use Blank Units for core-chamber cells that need no compute attribute.
