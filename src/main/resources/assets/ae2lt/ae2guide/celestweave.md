---
navigation:
  title: Celestweave
  icon: ae2lt:celestweave_core
  parent: devices-index.md
  position: 20
item_ids:
  - ae2lt:celestweave_oculus
  - ae2lt:celestweave_core
  - ae2lt:celestweave_conduit
  - ae2lt:celestweave_stride
  - ae2lt:energy_module_t1
  - ae2lt:energy_module_t2
  - ae2lt:energy_module_t3
  - ae2lt:module_night_vision
  - ae2lt:module_water_breathing
  - ae2lt:module_saturation
  - ae2lt:module_reach_extension
  - ae2lt:module_matrix_shield
  - ae2lt:module_phase_shield
  - ae2lt:module_reflect
  - ae2lt:module_undying
  - ae2lt:module_purification
  - ae2lt:module_creative_flight
  - ae2lt:module_phase_flight
  - ae2lt:module_dash
  - ae2lt:module_dig_affinity
---

# Celestweave

<ItemGrid>
  <ItemIcon id="ae2lt:celestweave_oculus" />
  <ItemIcon id="ae2lt:celestweave_core" />
  <ItemIcon id="ae2lt:celestweave_conduit" />
  <ItemIcon id="ae2lt:celestweave_stride" />
</ItemGrid>

**Celestweave** is a modular armor set. Each piece has its own FE buffer, network binding, core slot and module slots.

| Piece | Armor Slot | Module Slots |
|-------|------------|--------------|
| Celestweave Oculus | Helmet | 4 |
| Celestweave Core | Chestplate | 5 |
| Celestweave Conduit | Leggings | 4 |
| Celestweave Stride | Boots | 4 |

## Assembly

Use the <ItemLink id="ae2lt:overload_device_workbench" /> for each piece:

1. Insert the armor piece; it binds to the workbench's ME network
2. Install an <ItemLink id="ae2lt:ultimate_overload_core" /> in the core slot
3. Install one optional Energy Module and any compatible armor modules
4. Equip the armor and open the Overload Device Hub with the default key G to enable, disable or configure modules

An Energy Module also occupies one module slot. Without an Energy Module, each piece stores 10,000,000 FE. T1 / T2 / T3 modules raise armor capacity to 1,000,000,000 / 5,000,000,000 / 20,000,000,000 FE.

## Runtime

A module only works while its armor piece is equipped, has a core installed and the module is enabled. Active modules consume FE from worn Celestweave pieces and usually consume Lightning from the bound ME network.

If FE or required Lightning is missing, the affected effects cannot be maintained.

## Helmet Modules

| Module | Effect |
|--------|--------|
| Night Vision | Maintains Night Vision while active |
| Water Breathing | Maintains Water Breathing while active |
| Saturation | Restores food and saturation toward full, spending HV when it restores |

## Chestplate Modules

| Module | Effect |
|--------|--------|
| Reach Extension | Adds block / entity interaction range; configurable as 1x, 2x or 4x |
| Matrix Shield | Cancels environmental damage, reduces ordinary damage by about 80%, and reduces hard damage by about 50% |
| Phase Shield | Blocks incoming damage completely, spending EHV based on prevented damage |
| Reflect | Reflects up to 30% of attacker damage, spending FE and HV |
| Undying | Intercepts fatal damage, spending large FE and EHV costs |
| Purification | Removes and blocks configured status effects; by default, harmful effects only |

Matrix Shield and Phase Shield share the same install group, so only one of them can be installed on the chestplate.

## Leggings Modules

| Module | Effect |
|--------|--------|
| Creative Flight | Grants creative-style flight; speed and inertia are configurable |
| Phase Flight | Grants flight through blocks without switching game mode; speed and inertia are configurable |

Creative Flight and Phase Flight share the same install group, so only one can be installed on the leggings. Phase Flight is disabled by default after installation and must be enabled in the Device Hub.

## Boots Modules

| Module | Effect |
|--------|--------|
| Dash | Press the Dash key, default V, to dash forward; 40 tick cooldown |
| Dig Affinity | Compensates underwater and airborne mining penalties |

## Cost Notes

Most active modules consume 1 HV per tick in addition to their FE drain. Creative Flight uses more HV while flying, and Phase Flight consumes EHV every tick. Shield, Purification and Undying costs increase when they trigger repeatedly in a short time.
