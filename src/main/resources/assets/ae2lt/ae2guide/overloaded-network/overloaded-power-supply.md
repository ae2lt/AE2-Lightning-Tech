---
navigation:
  title: Overloaded Power Supply
  icon: ae2lt:overloaded_power_supply
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:overloaded_power_supply
---

# Overloaded Power Supply

<Row>
  <BlockImage id="ae2lt:overloaded_power_supply" scale="4" />
</Row>

The **Overloaded Power Supply** is a wireless FE distributor for the Overloaded ME network. It pulls FE energy stored in the ME network and delivers it to up to **64 wireless-bound machines** at once — no cables, no power lines. With an **AppFlux Flux Cell** installed, it can also switch into a high-throughput **Overload Mode** that buffers and bursts energy directly from the cell.

> Requires **Applied Flux**. The block is only registered when AppFlux is present.

## Core Features

* **Wireless FE distribution** to up to 64 bound machines
* **Two power modes**: Normal (continuous, ME-network-fed) and Overload (cell-buffered, burst-mode)
* **Slot for a Flux Cell**: the cell becomes the buffer / cache for Overload Mode (AE2 ME Chest pattern)
* **Adaptive scheduling**: shares the same per-target wireless scheduling wheel as the Overloaded ME Interface
* **Live block-state visuals**: the crystal lights up when transferring; the texture animates while the supply is in active overload

## Power Modes

### Normal Mode

In Normal Mode, the supply pulls FE directly from the ME network on every tick and distributes it across all bound targets using a shared adaptive scheduler. This mode is enabled regardless of whether a Flux Cell is installed.

* Continuous low-overhead delivery
* No cell required
* Shares the wireless scheduling wheel with the Overloaded ME Interface and Overloaded Pattern Provider

### Overload Mode

Overload Mode requires a Flux Cell in the cell slot. The cell becomes the active buffer: FE is pulled from the ME network into the cell and dispatched to targets in **bursts of up to 64 calls per target per tick**, with a **2× cost multiplier** applied to balance the throughput.

* Up to **64 calls per target per tick** for extreme bandwidth
* **Ticket rotation** (~20 tick tickets) keeps active targets primed without re-scanning every tick
* **2× cost multiplier** on cell-buffered FE — the energy per delivered FE is doubled
* Refuses to run if no Flux Cell is installed (status: *Missing cell*)
* If the cell is removed mid-tick, any in-flight buffered FE is flushed back to the ME network — no FE is lost

## Setting Up

1. Place the **Overloaded Power Supply** somewhere on your ME network
2. (Optional) Insert a Flux Cell into the cell slot to unlock Overload Mode
3. Use the **Overloaded Wireless Connect Tool**:
   1. **Shift + right-click** the Overloaded Power Supply to select it
   2. **Right-click the face** of each target machine to bind it as an FE recipient
4. Open the supply's GUI and choose Normal or Overload Mode

The same wireless connect tool used for the Overloaded ME Interface and the Overloaded Pattern Provider works here — one tool, three hosts.

## Tips

* For most bases, Normal Mode is enough — it is "always-on" and zero-overhead per FE
* Use Overload Mode when you need to hammer FE into a cluster of high-draw machines (e.g. mass-crafting setups) and accept the 2× cost
* A higher-tier Flux Cell gives a larger burst buffer, which smooths short ME-network FE shortages
* You can mix the supply with the Overloaded ME Interface and Overloaded Pattern Provider — all three share the same per-target scheduler, so binding the same target to several hosts will not cause cap-listener thrashing
