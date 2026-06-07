---
navigation:
  title: Electromagnetic Railgun
  icon: ae2lt:electromagnetic_railgun
  parent: devices-index.md
  position: 10
item_ids:
  - ae2lt:electromagnetic_railgun
  - ae2lt:railgun_module_core
  - ae2lt:railgun_module_compute
  - ae2lt:railgun_module_acceleration
  - ae2lt:railgun_module_overload_execution
  - ae2lt:energy_module_t1
  - ae2lt:energy_module_t2
  - ae2lt:energy_module_t3
---

# Electromagnetic Railgun

<Row>
  <ItemImage id="ae2lt:electromagnetic_railgun" scale="4" />
</Row>

The **Electromagnetic Railgun** is a modular overload weapon. It requires a bound ME network for Lightning ammunition and a local FE buffer for charging and firing.

## Required Setup

Prepare the railgun at the <ItemLink id="ae2lt:overload_device_workbench" />:

* Install an <ItemLink id="ae2lt:ultimate_overload_core" /> in the structural core slot; this is required for railgun operation
* Install the <ItemLink id="ae2lt:railgun_module_core" /> to enable charged shots and HV compensation when EHV is short

The workbench binds the railgun to its ME network when the railgun is inserted. The bound network must stay loaded and online. Without an Energy Module, the railgun stores 1,000,000 FE.

## Fire Modes

### Beam Fire

Hold the attack key while the railgun is in the main hand to fire a 64-block beam.

Beam fire uses High Voltage Lightning only. By default, each 2-tick settle deals 20 damage, ignores 40% of armor reduction, consumes 400 FE, and consumes 1 High Voltage Lightning every 8 settles.

### Charged Shot

Hold use to charge, then release to fire. Releasing before the first charge tier produces no shot.

| Tier | Base Charge Time | Default Damage | Firing Cost |
|------|------------------|----------------|-------------|
| EHV1 | 0.5 s | 100 | 8,000 FE + 32 EHV |
| EHV2 | 1.2 s | 300 | 40,000 FE + 96 EHV |
| EHV3 | 2.0 s | 600 | 200,000 FE + 256 EHV |

Charging also drains FE from the railgun buffer each tick: 1,000 / 4,000 / 10,000 FE for EHV1 / EHV2 / EHV3 charge progress. Charged shots have 80% armor bypass, apply electromagnetic paralysis for 2 seconds, and gain 25% damage during thunderstorms when the shooter can see the sky.

If EHV ammunition is missing, the core module can automatically substitute High Voltage Lightning at a ratio of **16 HV = 1 EHV**.

## Modules

| Module | Limit | Effect |
|--------|-------|--------|
| Energy Module T1 / T2 / T3 | 1 | Sets railgun FE capacity to 100,000,000 / 500,000,000 / 2,000,000,000 FE |
| Overload Compute Module | 2 | Enables and improves chain arcs; at max charge, strengthens the EMP pulse |
| Overload Acceleration Module | 2 | Each module adds +1 charge unit per tick, reducing charge time |
| Overload Execution Module | 1 | Only works on EHV3 charged hits; spends 20,000,000 FE to record target health and can force execution after repeated hits |

Without compute modules, chain arcs are disabled. With two acceleration modules, charge progress accumulates at three times the base rate.

## Device Hub Settings

Open the Overload Device Hub with the default key G while holding the railgun.

* **Terrain Destruction** controls whether charged shots break terrain. It is off by default and can still be disabled by server config
* **PVP Lock** makes targeting and chain behavior avoid other players
* The hub also shows module counts, FE storage and bound network status

## Combat Notes

Charged shots deal splash damage at the impact point. EHV3 also adds penetration and an EMP pulse around the first target. Compute modules increase chain and pulse strength, while thunderstorms increase chain reach and fork count.
