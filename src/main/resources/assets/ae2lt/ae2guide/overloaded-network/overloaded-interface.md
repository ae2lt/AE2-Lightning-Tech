---
navigation:
  title: Overloaded ME Interface
  icon: ae2lt:overloaded_interface
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:overloaded_interface
  - ae2lt:overloaded_filter_component
---

# Overloaded ME Interface

<Row>
  <BlockImage id="ae2lt:overloaded_interface" scale="4" />
</Row>

The **Overloaded ME Interface** is the upgraded version of the vanilla <ItemLink id="ae2:interface" />, with **36 configuration slots** and a **wireless mode** for long-distance item transfer and energy distribution.

## Core Features

* **36 configuration slots** (vanilla has 9)
* **Two operating modes**: Normal and Wireless
* **Unlimited-mode slots**: individual slots can be switched to unlimited supply
* **Two I/O speeds**: Normal and Fast (Probe)
* **Energy transfer**: can route FE stored in the ME network to adjacent or wireless-connected machines
* **Import filtering**: uses the Overloaded Filter Component to restrict which items can be imported

## Operating Modes

### Normal Mode

In Normal Mode, the Overloaded ME Interface behaves like the vanilla interface — it keeps stocked according to the configuration and outputs items to a physically adjacent container. The differences are more configuration slots and higher transfer bandwidth.

If an energy output direction is configured, Normal Mode can also route FE from the ME network to an adjacent device on that side.

### Wireless Mode

In Wireless Mode, the Overloaded ME Interface can manage input, output, and energy supply for multiple machines remotely.

Use the **Overloaded Wireless Connect Tool** to establish wireless connections:

1. Hold the tool and **Shift + right-click** the Overloaded ME Interface to select it
2. Right-click a specific face of a target machine to connect
3. One interface can bind to multiple remote machines

## Import / Export Modes

| Mode | Description |
|------|-------------|
| Export: OFF | Does not export items to remote machines |
| Export: AUTO | Automatically exports items to remote machines according to the slot configuration |
| Import: OFF | Does not import items from remote machines |
| Import: AUTO | Continuously imports items from remote machines into the ME network |
| Import: EJECT | Remote machines push items into the virtual input slot; the interface accepts them passively |

## I/O Speed

| Speed Tier | Adaptive Cooldown Range | Description |
|------------|-------------------------|-------------|
| Normal | 5 ~ 80 ticks | Suitable for general use |
| Fast (Probe) | 1 ~ 40 ticks | Uses a probe mechanic to detect readiness early; requires a Speed Card |

The interface uses an adaptive cooldown — the cooldown shortens when items are available to move, and lengthens when there is nothing to do, balancing performance and responsiveness.

## Unlimited Mode

Shift-clicking a configuration slot switches it to **Unlimited Mode**. In this mode, the slot continuously supplies the configured item to remote machines in unlimited quantity.

## Import Filter

<ItemImage id="ae2lt:overloaded_filter_component" scale="2" float="left" />

The Overloaded ME Interface accepts an **Overloaded Filter Component** to restrict which items may be imported into the ME network. The filter component is configured the same way as an AE2 Cell Workbench configuration.

## Energy Transfer

With an AppFlux Induction Card installed (requires AppFlux), the Overloaded ME Interface can send FE stored in the ME network to target machines.

* **Normal Mode**: routes energy to the adjacent block on the configured side
* **Wireless Mode**: distributes energy across all wireless-connected machines via a round-robin scheduler

## Automation Tips

* A single interface can manage I/O for many machines wirelessly, replacing a lot of pipework
* Combined with unlimited-mode slots, it can keep a remote machine continuously supplied with a specific material
* With energy transfer, you can drop dedicated power lines
* Enable the Fast probe tier to reduce processing response latency
