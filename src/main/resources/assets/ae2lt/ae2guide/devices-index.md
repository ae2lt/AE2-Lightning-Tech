---
navigation:
  title: Overload Devices
  icon: ae2lt:overload_device_workbench
  parent: index.md
  position: 50
item_ids:
  - ae2lt:overload_device_workbench
  - ae2lt:electromagnetic_railgun
  - ae2lt:celestweave_oculus
  - ae2lt:celestweave_core
  - ae2lt:celestweave_conduit
  - ae2lt:celestweave_stride
---

# Overload Devices

Overload devices are high-end equipment assembled at the <ItemLink id="ae2lt:overload_device_workbench" />. They use local FE buffers, a bound ME network, and device-specific modules.

## Available Devices

* [Electromagnetic Railgun](electromagnetic-railgun.md) — a ranged weapon with beam fire, charged shots, chain arcs and tactical modules
* [Celestweave](celestweave.md) — a modular armor set for movement, defense and utility effects

## Assembly

1. Place the Overload Device Workbench on an ME network
2. Insert an overload device; the workbench binds it to that network automatically
3. Install an <ItemLink id="ae2lt:ultimate_overload_core" /> in the core slot
4. Put a compatible module into the module input slot; one module is installed after about 20 ticks
5. Open the Overload Device Hub with the configured key, default G, to switch modules and settings

## Energy and Network

Each device has its own FE buffer and can receive FE directly. If Applied Flux FE storage is available in the bound ME network, devices can also refill from that network.

Combat and armor modules may consume **High Voltage Lightning** or **Extreme High Voltage Lightning** from the same bound network. Keep enough Lightning stored before relying on active modules.
