---
navigation:
  title: Tianshu Supercomputer
  icon: ae2lt:tianshu_supercomputer_controller
  parent: index.md
  position: 46
---

# Tianshu Supercomputer

The **Tianshu Supercomputer** is a large multiblock crafting CPU for ME networks. It combines all crafting storage into a shared pool and, while capacity remains available, can accept multiple crafting jobs at the same time. It is intended for automation networks with both high job counts and high pattern-dispatch throughput.

The supercomputer calculates plans, retains job state, and schedules crafting steps. Patterns still come from ME Pattern Providers or equivalent devices, while the ME network and processing machines continue to supply materials and produce results.

<SubPages />

## Basic Workflow

1. [Build the 7×7×7 structure](construction.md), then install one main unit and 26 peripheral units in the core chamber
2. Connect the Tianshu Supercomputer Port to a powered ME network
3. Use the controller to verify structure status, crafting storage, and parallelism, then configure [Fast Planning](operation.md#fast-planning) if required
4. Start a crafting job from an ME terminal; allow automatic CPU selection or select the Tianshu Supercomputer in the confirmation screen

## Operating Requirements

* The structure must be formed and its core configuration must be valid
* Exactly one Tianshu Supercomputer Port is required
* The chamber center requires one main unit; all 26 surrounding cells require valid peripheral units, including Blank, Storage, Parallel, or closed-loop storage units
* At least one Storage Unit and one Parallel Unit are required
* The port must be connected to an active ME network; it uses **8 AE/t** while idle, and the formed multiblock consumes **1 channel**

The structure requires neither external FE nor Lightning.
