---
navigation:
  title: Tianshu Supercomputing Array
  icon: ae2lt:tianshu_supercomputer_controller
  parent: index.md
  position: 46
---

# Tianshu Supercomputing Array

The **Tianshu Supercomputing Array** is a large multiblock crafting CPU for ME networks. It combines all crafting storage into a shared pool and, while capacity remains available, can accept multiple crafting jobs at the same time. It is intended for automation networks with both high job counts and high pattern-dispatch throughput.

The supercomputer calculates plans, retains job state, and schedules crafting steps. Patterns still come from ME Pattern Providers or equivalent devices, while the ME network and processing machines continue to supply materials and produce results.

<SubPages />

## Basic Workflow

1. [Build the 7×7×7 structure](construction.md), install one Tianshu Main Core and 26 Peripheral Units in the core chamber, and replace shell cooling positions with closed-loop storage as needed
2. Connect the Tianshu Supercomputing Array Port to a powered ME network
3. Use the controller to verify structure status, crafting storage, successful dispatches, and copy budget, then configure [Fast Planning](operation.md#fast-planning) if required
4. Start a crafting job from an ME terminal; allow automatic CPU selection or select the Tianshu Supercomputing Array in the confirmation screen
5. Use the [Tianshu Pattern Encoding Terminal](pattern-encoding-terminal.md) for enhanced pattern encoding, pattern upload, closed-loop patterns, and inventory maintenance

## Operating Requirements

* The structure must be fully formed: one Tianshu Main Core at the core-chamber center, the remaining 26 cells filled with a Peripheral Unit combination allowed by that Main Core, and all 17 non-port cooling-compatible positions filled with Phase-Change Cooling Units or closed-loop storage; see [Structure and Auto-build](construction.md) and [Main Cores, Computing Units, and Performance](computing-units.md) for the full rules
* Exactly one Tianshu Supercomputing Array Port is required, connected to a powered ME network; the port uses **8 AE/t** while idle, and the formed multiblock consumes **1 channel**

The structure requires neither external FE nor Lightning.
