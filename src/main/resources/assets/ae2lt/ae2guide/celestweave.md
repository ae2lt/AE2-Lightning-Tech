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
  - ae2lt:railgun_module_core
  - ae2lt:module_night_vision
  - ae2lt:module_water_breathing
  - ae2lt:module_saturation
  - ae2lt:module_reach_extension
  - ae2lt:module_matrix_shield
  - ae2lt:module_phase_shield
  - ae2lt:module_reflect
  - ae2lt:module_undying
  - ae2lt:module_multidimensional_protection
  - ae2lt:module_purification
  - ae2lt:module_radiation_protection
  - ae2lt:module_laser_protection
  - ae2lt:module_creative_flight
  - ae2lt:module_phase_flight
  - ae2lt:module_phase_lock
  - ae2lt:module_dash
  - ae2lt:module_dig_affinity
  - ae2lt:module_movement_assist
---

# Celestweave

<ItemGrid>
  <ItemIcon id="ae2lt:celestweave_oculus" />
  <ItemIcon id="ae2lt:celestweave_core" />
  <ItemIcon id="ae2lt:celestweave_conduit" />
  <ItemIcon id="ae2lt:celestweave_stride" />
</ItemGrid>

**Celestweave** is a modular armor set. Each piece has its own FE buffer, network binding, core slot and module slots.

| Piece | Armor Slot |
|-------|------------|
| Celestweave Oculus | Helmet |
| Celestweave Core | Chestplate |
| Celestweave Conduit | Leggings |
| Celestweave Stride | Boots |

## Assembly

Use the <ItemLink id="ae2lt:overload_device_workbench" /> for each piece:

1. Insert the armor piece; it binds to the workbench's ME network
2. Install an <ItemLink id="ae2lt:ultimate_overload_core" /> in the core slot
3. Install one optional Energy Module and any compatible armor modules
4. Equip the armor and open the Overload Device Hub with the default key G to enable, disable or configure modules

Each armor piece accepts any number of compatible modules, while per-module limits and mutual-exclusion groups still apply; only one Energy Module can be installed. Without an Energy Module, each piece stores 10,000,000 FE. T1 / T2 / T3 modules raise armor capacity to 1,000,000,000 / 5,000,000,000 / 20,000,000,000 FE.

## Runtime

A module only works while its armor piece is equipped, has a core installed and the module is enabled. Active modules consume FE from worn Celestweave pieces and usually consume Lightning from the bound ME network.

If FE or required Lightning is missing, the affected effects cannot be maintained.

## Helmet Modules

| Module | Effect |
|--------|--------|
| Night Vision | Maintains Night Vision while active |
| Water Breathing | Maintains Water Breathing while active |
| Saturation | Restores food and saturation toward full, spending High Voltage Lightning when it restores |

## Chestplate Modules

| Module | Effect |
|--------|--------|
| Reach Extension | Adds block / entity interaction range; configurable as 1x, 2x or 4x |
| Matrix Shield | Cancels environmental damage and extinguishes fire, reduces ordinary damage by about 80%, and reduces hard damage by about 50% |
| Phase Shield | Blocks incoming damage completely and extinguishes fire, spending Extreme High Voltage Lightning based on prevented damage |
| Reflect | Reflects up to 30% of attacker damage, spending FE and High Voltage Lightning |
| Undying | Intercepts fatal damage, spending large amounts of FE and Extreme High Voltage Lightning |
| Multidimensional Protection | Combines complete Phase Shield cancellation with the Undying fallback; neither path consumes FE or Lightning |
| Purification | Removes and blocks configured status effects; by default, harmful effects only |
| Radiation Assimilation | Prevents new exposure and radiation damage; while ambient radiation is present, restores 1-5 hearts per second depending on radiation severity |
| Laser Protection | Absorbs Mekanism lasers completely and converts the actually absorbed Joules to FE using Mekanism's configured conversion ratio, filling the chestplate and then the rest of the equipped set |
| Phase Lock | Provides four independently toggled features: Armor Lock, Flight Lock, Block External Forces and Block External Teleports; see below |
| Overload Core Module | Unlocks Lightning compensation for the armor set. Existing Extreme High Voltage Lightning is consumed first, then any shortfall is paid at 16 High Voltage Lightning per Extreme High Voltage Lightning |

Multidimensional Protection conflicts with Matrix Shield, Phase Shield and Undying. None of those four modules can be installed together on the same chestplate.

### Phase Lock

The Phase Lock Module uses the chestplate as its controller and exposes four independently toggled features in the Device Hub:

* **Armor Lock:** Moves the currently worn Celestweave set into four player-bound private phase slots; each armor slot displays a Phase-Locked Projection to show that your corresponding armor is secured in private phase storage. The real armor keeps running inside the private slots, and newly worn Celestweave pieces join the lock automatically. Projections carry no enchantment glint and cannot be extracted or traded; replacing or removing one projection costs 1,000,000 FE and 16 Extreme High Voltage Lightning to rebuild it in place, and a failed payment releases the lock and re-equips the entire real set.
`Think of this as soulbinding.`
* **Flight Lock:** Freezes the current flight state, keeps flight active after landing, and rejects external state changes. It recognizes active Creative/Phase Flight modules, game-mode flight, and NeoForge's `CREATIVE_FLIGHT` attribute, but never grants flight by itself.
* **Block External Forces:** Blocks velocity changes from water, fans, knockback and similar external systems, while player input and armor-driven movement still work.
`For example: ignores pushing and pulling from Create's Encased Fan.`
* **Block External Teleports:** Blocks external coordinate rewrites, teleports and dimension changes, while normal player movement is unaffected.
`For example: ignores forced teleportation by Draconic Evolution's Chaos Guardian.`

## Leggings Modules

| Module | Effect |
|--------|--------|
| Creative Flight | Provides hovering and wing gliding; speed and inertia are configurable |
| Phase Flight | Provides hovering and phase-wing gliding; Phase Mode controls whether flight passes through blocks |

Creative Flight and Phase Flight share the same install group, so only one can be installed on the leggings. Phase Flight is disabled by default after installation and must be enabled in the Device Hub.

Both flight modules share vanilla-style controls: tap Jump in midair to deploy the wings, or double-tap Jump to toggle hovering. Hold Jump while gliding to thrust along the look direction. While hovering, Jump ascends and Sneak descends; holding both cancels vertical input and exposes a crouching state to other mods. Only Phase Flight exposes Phase Mode; when enabled, both hovering and gliding pass through blocks. Flight Lock is controlled centrally by the chestplate's Phase Lock Module.

## Boots Modules

| Module | Effect |
|--------|--------|
| Dash | Press the Dash key, default V, to dash forward; 40 tick cooldown |
| Dig Affinity | Compensates underwater and airborne mining penalties |
| Movement Assist | Separately tunes walking, sprinting and sneaking speed (0.5x-4x), plus automatic step height (0.6-3 blocks) |

## Cost Notes

Most active modules consume 1 High Voltage Lightning per tick in addition to their FE drain. Creative Flight and Phase Flight use more High Voltage Lightning while moving; active Phase Mode traversal uses Extreme High Voltage Lightning instead. Shield, Purification and Undying costs increase when they trigger repeatedly in a short time. Installing an Overload Core Module in the chestplate allows the whole set to compensate missing Extreme High Voltage Lightning at 16 High Voltage Lightning per Extreme High Voltage Lightning.
