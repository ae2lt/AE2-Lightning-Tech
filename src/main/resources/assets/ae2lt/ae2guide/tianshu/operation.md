---
navigation:
  title: Port, Jobs, and Fast Planning
  icon: ae2lt:tianshu_supercomputer_port
  parent: tianshu/tianshu-index.md
  position: 30
item_ids:
  - ae2lt:tianshu_supercomputer_controller
  - ae2lt:tianshu_supercomputer_port
---

# Port, Jobs, and Fast Planning

## Tianshu Supercomputer Port

After formation, the <ItemLink id="ae2lt:tianshu_supercomputer_port" /> publishes the multiblock to the ME network as a crafting CPU named “Tianshu Supercomputer.” ME cables can connect on all six sides only while the structure is formed; they cannot connect while it is unformed. The port uses **8 AE/t** while idle, and the formed multiblock consumes **1 channel**.

The supercomputer does not accept new jobs while its port is offline, its ME network is unpowered, or the structure is unformed. Saved job state persists across chunk unloads and can continue after the structure and network return.

The supercomputer does not force-load any chunk in its footprint. If any structure chunk is unloaded, the Port disconnects and jobs pause. It rescans and resumes automatically after the complete structure is loaded again. A cross-chunk build therefore needs a chunk loader covering the whole structure.

## Tianshu Pattern Encoding Terminal

The [Tianshu Pattern Encoding Terminal](pattern-encoding-terminal.md) extends the normal Pattern Encoding Terminal with enhanced processing-pattern encoding, pattern upload, closed-loop pattern authoring, and inventory-maintenance configuration. It is the entry point for authoring and managing Tianshu-specific patterns; see its dedicated page.

## Shared Capacity and Concurrent Jobs

Unlike a conventional crafting CPU, the Tianshu Supercomputer can retain and execute multiple crafting jobs at once.

* Each new job reserves its plan's byte requirement from the total crafting storage
* Additional jobs can use the same supercomputer while sufficient unreserved storage remains
* Completing or cancelling a job releases its reservation
* All active jobs share the core configuration's successful-dispatch budget; each job receives its own full per-tick copy budget, but only batch-compatible patterns and targets can use more than one copy per dispatch

If the crafting confirmation screen reports insufficient CPU storage, wait for active jobs to release capacity, add Storage Units, or install a higher-tier main unit.

## Controller Screen

Use the <ItemLink id="ae2lt:tianshu_supercomputer_controller" /> to view:

* Formation state and the first detected problem when unformed
* Current main-unit tier
* Counts of Dispatch, Amplifier, and Capacity Supercomputing Units, plus Closed-Loop Pattern and Seed Storage counts
* `R`, dispatch gain `Gd`, storage gain `Gs`, raw dispatch, and the effective successful-dispatch budget `D`
* Internal capacity, amplified external capacity, total crafting storage, each virtual CPU's per-tick copy budget `T`, and dispatch-cap status; the AE2 CPU selection list reports parallelism from successful dispatches only
* Controls for shell auto-build and Fast Planning

Replacing a core-chamber unit, or removing closed-loop storage from a cooling-compatible position, temporarily unforms the structure while the position is empty. If the port retains active jobs, the new profile takes effect after all existing jobs end; restoring the structure and network allows retained jobs to continue. A Phase-Change Cooling Unit is valid in the same cooling-compatible position but provides no corresponding closed-loop capacity.

## Fast Planning

**Fast Planning** is enabled by default and reduces the calculation time of large crafting plans. It affects only planning before job submission; it does not alter recipes, material requirements, crafting storage, or execution speed.

When a plan is unsupported by the fast path or an error occurs, calculation automatically falls back to AE2's standard planner. Disable Fast Planning in the controller when diagnosing pattern compatibility or comparing calculation results.

Fast-planning eligibility is aggregated across the entire ME network. If another active crafting CPU with fast-planning support still permits the feature, that network may continue to use fast planning. Disable the corresponding setting or take those CPUs offline to force the standard planner exclusively.

## Troubleshooting

While the structure is unformed, the controller screen shows the first detected problem directly; address that hint first.

**Structure and building**

* **The structure will not form:** Check each point of the [manual construction rules](construction.md#manual-construction-rules) — complete casing and glass, exactly one port, all 17 non-port cooling-compatible positions filled with Phase-Change Cooling Units or closed-loop storage, the main unit centered in the core chamber, and all 26 peripheral cells filled with units supported by that main core
* **All visible structure blocks are present:** Clear the required-air positions omitted from the displayed structure; cables, lights, and decorations are not permitted there
* **Auto-build does not start:** Clear the coordinates reported by the controller and ensure all materials are in the player inventory

**Network and jobs**

* **The CPU is absent from crafting confirmation:** Check the port's cable connection, ME power, and structure formation state
* **The Port stays offline after a cross-chunk load:** Ensure the chunk loader covers the entire Tianshu structure and allow time for the automatic rescan
* **The CPU rejects a new job:** The remaining crafting storage may already be reserved by other active jobs; wait for jobs to finish or add Storage Units

**Performance below expectations**

* **A high dispatch budget does not improve throughput:** The bottleneck is usually on the execution side — check Pattern Provider and processing-machine counts, machine speed, material supply, and network transfer capacity
* **A high copy budget still sends ordinary processing patterns one at a time:** General AE2 processing machines accept one copy per call; batch copies require compatible patterns, providers, and targets — see [Batch Copies](computing-units.md#batch-copies)
