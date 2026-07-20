---
navigation:
  title: Port, Jobs, and Fast Planning
  icon: ae2lt:tianshu_supercomputer_port
  parent: tianshu/tianshu-index.md
  position: 30
item_ids:
  - ae2lt:tianshu_supercomputer_controller
  - ae2lt:tianshu_supercomputer_port
  - ae2lt:tianshu_pattern_encoding_terminal
---

# Port, Jobs, and Fast Planning

## Tianshu Supercomputer Port

After formation, the <ItemLink id="ae2lt:tianshu_supercomputer_port" /> publishes the multiblock to the ME network as a crafting CPU named “Tianshu Supercomputer.” ME cables can connect on all six sides only while the structure is formed; they cannot connect while it is unformed. The port uses **8 AE/t** while idle, and the formed multiblock consumes **1 channel**.

The supercomputer does not accept new jobs while its port is offline, its ME network is unpowered, or the structure is unformed. Saved job state persists across chunk unloads and can continue after the structure and network return.

The supercomputer does not force-load any chunk in its footprint. If any structure chunk is unloaded, the Port disconnects and jobs pause. It rescans and resumes automatically after the complete structure is loaded again. A cross-chunk build therefore needs a chunk loader covering the whole structure.

## Tianshu Pattern Encoding Terminal

The <ItemLink id="ae2lt:tianshu_pattern_encoding_terminal" /> extends the normal Pattern Encoding Terminal with closed-loop authoring, upload routing, and inventory-maintenance controls. The menu binds the first available formed Tianshu in stable order; if none is available when it opens, it can perform that initial binding when the first Tianshu comes online. Once a machine UUID is bound, that menu session never switches or falls back to another machine, and related writes fail if the bound machine becomes unavailable.

The bound Tianshu's closed-loop pattern warehouse is added to this terminal's network-content list. It is exposed only while the Tianshu CPU is formed and its port is online, and disappears immediately if the structure becomes invalid. Patterns can be extracted for inspection or editing; returning them to the warehouse still uses the terminal's explicit upload action.

Use the maintenance overview to see configured items even when their stored amount is zero. Shift-click an entry there to edit its maintenance rule. If migrated data exceeds the current hard limit, the overview marks the bounded recovery page explicitly; deleting visible entries reveals the remaining persisted entries in later revisions.

<RecipeFor id="ae2lt:tianshu_pattern_encoding_terminal" />

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
* Storage, Parallel, and Amplifier Unit counts
* Total crafting storage, successful dispatches per tick, maximum copies per tick, and dispatch-cap status; the AE2 CPU selection list reports parallelism from successful dispatches only
* Controls for shell auto-build and Fast Planning

Replacing a core-chamber unit temporarily unforms the structure. If the port still retains active jobs, the new profile takes effect after all existing jobs end; restoring the structure and network allows retained jobs to continue.

## Fast Planning

**Fast Planning** is enabled by default and reduces the calculation time of large crafting plans. It affects only planning before job submission; it does not alter recipes, material requirements, crafting storage, or execution speed.

When a plan is unsupported by the fast path or an error occurs, calculation automatically falls back to AE2's standard planner. Disable Fast Planning in the controller when diagnosing pattern compatibility or comparing calculation results.

Fast-planning eligibility is aggregated across the entire ME network. If another active crafting CPU with fast-planning support still permits the feature, that network may continue to use fast planning. Disable the corresponding setting or take those CPUs offline to force the standard planner exclusively.

## Troubleshooting

* **The structure will not form:** Check every fixed casing, glass, and cooling position; require exactly one port, one centered main unit, and 26 peripherals valid for that tier. Finite tiers require a Parallel Unit; Multidimensional rejects Storage, Parallel, and Amplifier Units
* **All visible structure blocks are present:** Clear the required-air positions omitted from the displayed structure; cables, lights, and decorations are not permitted there
* **Auto-build does not start:** Clear the coordinates reported by the controller and ensure all materials are in the player inventory
* **The CPU is absent from crafting confirmation:** Check the port's cable connection, ME power, and structure formation state
* **The Port stays offline after a cross-chunk load:** Ensure the chunk loader covers the entire Tianshu structure and allow time for the automatic rescan
* **The CPU rejects a new job:** Check remaining crafting storage; active jobs may already reserve the total capacity
* **A high dispatch budget does not improve throughput:** Check Pattern Provider and processing-machine counts, machine speed, material supply, and network transfer capacity
* **A high copy budget still sends ordinary processing patterns one at a time:** General AE2 processing machines use the native single-copy path; batch copies require compatible patterns, providers, and targets
