---
navigation:
  title: Celestweave Mimicry Staff
  icon: ae2lt:celestweave_mimicry_staff
  parent: devices-index.md
  position: 30
item_ids:
  - ae2lt:celestweave_mimicry_staff
  - ae2lt:mimicry_module_mattock
  - ae2lt:mimicry_module_shears
  - ae2lt:mimicry_module_knife
  - ae2lt:mimicry_module_wrench
  - ae2lt:mimicry_module_netherite
  - ae2lt:mimicry_module_unrestricted
  - ae2lt:mimicry_module_speed
  - ae2lt:mimicry_module_damage
  - ae2lt:mimicry_module_harvest
  - ae2lt:mimicry_module_smelting
  - ae2lt:mimicry_module_collection
---

# Celestweave Mimicry Staff

<ItemImage id="ae2lt:celestweave_mimicry_staff" />

A diamond-tier pickaxe, sword and axe in one staff. It starts with mining efficiency 8, total attack damage 7 and attack speed 1.6. Installed abilities work together.

## Modules and settings

Hold the staff and press the **device configuration key (default G)** to open its tab in the existing equipment hub. Sneak + use in the air opens the same hub. Select a module to edit its parameters; speed, smelting and collection use the list toggles. Both hands are supported.

Install and remove modules at the **Overload Device Workbench** using its existing input slot and module list. Each purpose accepts one module. The two harvest tiers share a slot, so remove the old tier before upgrading. Stored enchantments survive removal. The staff needs no separate structural core.

| Module | Effect |
| --- | --- |
| Mattock | Shovel and hoe abilities together; choose land off, till or path. Campfire dousing remains available |
| Shearing | Shear blocks and entities; carve pumpkins and harvest honeycombs |
| Knife | Farmer's Delight cutting, harvesting and native knife loot recognition |
| Wrench | Native AE2 wrench interactions and Mek configuration; defaults to item configuration |
| Netherite tier | Netherite harvest qualification, efficiency 9 |
| Maximum harvest tier | Removes ordinary material-tier restrictions, efficiency 9; respects each block's own breaking rules |
| Mining time | Target time = base + original time × scale |
| Damage enhancement | Adjust damage and attack area; defaults to 10, single target; carries sword enchantments |
| Harvest enhancement | Normal / Fortune III+ / Silk Touch I and tool enchantments |
| Auto-smelting | Smelts each operation's outputs once; off by default |
| Collection enhancement | Inserts operation outputs into inventory and drops overflow; off by default |

Mek configuration follows its native GUI, side configuration and pipe interactions. Rotation and dismantling require explicit selection. Mek settings do not change AE2 wrench behavior. Land settings affect only the conflicting till/path actions.

## Combat

Damage enhancement selects single target, native sweep, 3×3, 5×5 or 7×7. Square areas trigger on a fully charged successful primary hit, centered on the primary target before knockback, from one block below to two above. They test intersecting hitboxes, skip allies, your pets, armor stands and occluded targets, and run each secondary through native attack events and enchantments without recursion. Each successful hit costs 1,000 FE; native sweep uses its ordinary one-swing cost and secondary damage, without secondary execution.

Base damage options are **1, 5, 10, 20, 100, 500, infinity**. Existing **Overload Core** unlocks 500; **Multidimensional Execution** unlocks infinity. Locked options are skipped, and removing a module immediately falls back to an available tier. Base damage includes player base damage; native cooldown, enchantments, critical hits and armor still apply. Infinity supplies a very large finite number at the hurt call; the attribute tooltip stays finite and the configuration page shows infinity.

Install the existing Overload Execution or Multidimensional Execution module in the shared execution slot, then select Off, Normal death or Forced removal on that module. Execution requires Damage Enhancement and a fully charged melee hit. Off keeps only the selected native damage; active modes use the railgun's shared HP records, decay, kill attribution and death/removal engine, including its global switch and player protection. Ordinary hurt runs first, then Overload subtracts the attack damage from the HP record, matching railgun order. Every Overload settlement adds **20,000,000 FE**, reserving 1,000 FE for the native hit. Insufficient surcharge leaves only ordinary damage. Multidimensional execution has no such surcharge. Square area targets can each execute and pay individually; native sweep secondaries cannot.

## Lightning costs

Ordinary tool actions and single-target damage 1 / 5 / 10 / 20 keep their FE-only cost. Enhanced combat additionally draws lightning from the ME grid linked by inserting the staff into an Overload Device Workbench. The bound workbench must remain loaded and active; range and dimension rules match the railgun, and the link survives saving.

| Attack setting | Default lightning per native hit |
| --- | --- |
| Damage 100 | 32 EHV |
| Damage 500 | 96 EHV |
| Infinite damage | 256 EHV |
| Fully charged execution enabled | At least 256 EHV; take the higher damage/execution rate, never add both |
| Fully charged sweep or square area | Add 1 HV per native hit; native sweep pays once per swing, square areas pay for each actual target |

EHV amounts follow the railgun's `railgun.energy.ehvCostTier1/2/3` settings. An installed Overload Core permits **16 HV per missing EHV** compensation, spending available EHV first. Multidimensional execution still avoids the 20-million-FE Overload surcharge, but requires lightning.

The complete lightning bill is checked and reserved before damage or execution. Insufficient lightning produces neither the hit nor its base FE cost. If both native hurt and execution fail, the reservation is refunded. Partial extraction after a successful simulation is rolled back. Square areas stop when remaining FE or lightning cannot pay for the next target.

The equipment hub shows read-only EHV/HV costs for the current fully charged attack under Damage Enhancement and execution modules. Combat unpowered means the current attack lacks FE, lightning or an active linked grid; ordinary tool actions still check their own FE supply.

## Enchantments

Enchant enhancement modules with books in an anvil. The staff itself rejects direct enchantments.

Harvest enhancement accepts enchantments defined for tools, including modded ones, except Efficiency and directly applied Silk Touch. Normal mode disables only Fortune and Silk Touch. Fortune uses `max(3, module effective level)`; Silk Touch is always I.

Damage enhancement accepts Sharpness, Smite, Bane of Arthropods, Looting, Knockback, Fire Aspect and Sweeping Edge. Farmer's Delight Backstabbing additionally requires the knife module. Conflicting loadouts or settings are rejected.

## Mining time

Base time: 0, 3, 5, 10 or 20 ticks. Time scale: 0.01, 0.05, 0.1, 0.2, 0.5 or 1. At 20 ticks per second, an original 40-tick block with base 3 and scale 0.1 takes 7 ticks.

Player effects and custom block progress are calculated first, then transformed once. Speed never grants missing harvest qualification or makes bedrock breakable.

## Energy and outputs

The staff stores **100,000,000 FE** and accepts standard item FE charging. Crafted staffs start empty. Each native durability point costs **1,000 FE**; mining and attacks cost one point. Successful shearing and cutting use their native payment callback once. Smelting and collection add no duplicate charge. Native wrench actions with no durability cost remain free, but require at least 1,000 FE.

Insufficient power stops tool actions while preserving the staff, modules and settings. Configuration remains accessible. Unbreaking does not reduce FE cost, and Mending neither charges the staff nor consumes XP. Apothic Enchanting Nature's Blessing converts its native level-dependent cost to FE and checks the full amount before growing a crop.

Output order: native loot, shearing or cutting recipe → one smelting conversion → inventory insertion → ground overflow. Smelting uses the world's current furnace recipes and adds no furnace XP. Collection never searches nearby drops or routes items to ME.

Farmer's Delight cutting boards use the installed tool abilities and native recipe/Fortune calculation once. Knife food slicing outside cutting boards and mob kill drops are not routed through the staff's smelting/collection pipeline.

## Phase lock

The last configuration option enables phase lock. It defaults to off and needs no extra module. The original staff moves into server-private storage; its inventory slot holds a dedicated projection. Supported abilities, enchantments, FE and lightning use the single original.

The projection cannot be extracted, dropped or moved, including GUI swaps and swapping hands. Unlock before rearranging the staff or installing modules. Unlocking costs no resources and returns the original. Projections carry no real modules, item containers or third-party component payloads and cannot be dismantled.

Missing projections are replaced with a new generation, invalidating old handles without copying tool contents. Respawning also replaces the handle. An unrelated occupant is moved into free inventory space; replacement waits if no space is available. Extraction, GUI and dropped-entity guards remain, including tested PneumaticCraft, Rod of Lyssa and Chance Cubes paths.
