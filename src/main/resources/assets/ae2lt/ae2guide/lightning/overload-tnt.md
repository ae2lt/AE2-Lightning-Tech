---
navigation:
  title: Overload TNT
  icon: ae2lt:overload_tnt
  parent: lightning/lightning-index.md
  position: 90
item_ids:
  - ae2lt:overload_tnt
---

# Overload TNT

<Row>
  <BlockImage id="ae2lt:overload_tnt" scale="4" />
</Row>

**Overload TNT** is a high-risk explosive built around an Overload Singularity. Instead of producing a normal blast, it calls down repeated lightning strikes around the detonation point and scorches terrain over a wide area.

It is mainly used for terrain excavation, hard-rock clearing, or controlled destructive testing.

## Crafting

<RecipeFor id="ae2lt:overload_tnt" />

The **Overload Singularity** is the active component; the gunpowder provides the explosive trigger.

## Detonation Behavior

After ignition or activation, Overload TNT has a fuse of roughly **4 seconds**. On detonation, effects occur in the following sequence:

1. A **lead-off lightning bolt** strikes directly above the blast center, followed by multiple bolts that fall outward in layered rings, **searing the terrain** — this is the primary destructive effect, and its range is large.
2. At detonation, **all mobs in a wide area around the blast center take heavy lightning damage**. Damage falls off with distance, but the inner radius is lethal.
3. After the first wave, a period of **aftershocks** continues for several seconds, dropping random additional strikes across the area.
4. A short burst of **thunderstorm weather** is forced into the area around the blast center, reverting after about 8 seconds.

Overload TNT respects cleanup safeguards: **bedrock, barrier blocks, end portal frames, and command blocks** are not broken, and area-protected zones are respected.

> Once Overload TNT detonates, it cannot be recovered. Treat it as a high-risk lightning event.

## Configuration and Disabling

The config option `overloadTnt.enableTerrainDamage` in `ae2lt-common.toml` (default **enabled**) controls the entire detonation behavior:

* **Enabled**: the "Detonation Behavior" above plays out in full — lightning rain, terrain searing, thunderstorm, and mob damage included.
* **Disabled**: Overload TNT **does nothing** when triggered — no lightning, no destruction, no damage. Effectively a dud.

## Usage Tips

* Place it on top of a **solid block**, keep a large safety distance, and prefer open terrain. The blast radius is large enough to catch players who remain near the detonation area.
* If you want terrain damage without killing nearby mobs, move anything you want to preserve outside the blast area first.

## Field Log: An Anomalous Drop

> **Trial 7.** Conditions matched the previous six; observed curve diverged. At detonation, a [Lightning Collapse Matrix](../materials/lightning-collapse-matrix.md) was lying on the ground near the impact point — no scorch crater, just a few bolts and a cell that wasn't on the manifest.

> Reproduction failed. Controlling for every variable points to the same conclusion: **the matrix has to be out in the open.**
