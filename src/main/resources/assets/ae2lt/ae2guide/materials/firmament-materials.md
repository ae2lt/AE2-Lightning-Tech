---
navigation:
  title: Firmament Materials
  icon: ae2lt:firmament_alloy_ingot
  parent: materials/materials-index.md
item_ids:
  - ae2lt:firmament_dust
  - ae2lt:firmament_essence
  - ae2lt:firmament_mixture
  - ae2lt:firmament_alloy_ingot
  - ae2lt:firmament_superconducting_wire
  - ae2lt:inactive_firmament_spirit_core
  - ae2lt:firmament_spirit_core_oculus
  - ae2lt:firmament_spirit_core_core
  - ae2lt:firmament_spirit_core_conduit
  - ae2lt:firmament_spirit_core_stride
---

# Firmament Materials

<ItemImage id="ae2lt:firmament_alloy_ingot" scale="2" float="left" />

Firmament materials are AE2 Lightning Tech's End-tier material line. Everything starts from Firmament Dust, which can only be obtained in the End, and is refined step by step — through the Firmament Conversion Core and the Overload Processing Factory — into the core components of the Celestweave armor set and the Electromagnetic Railgun.

<ItemGrid>
  <ItemIcon id="ae2lt:firmament_dust" />
  <ItemIcon id="ae2lt:firmament_essence" />
  <ItemIcon id="ae2lt:firmament_mixture" />
  <ItemIcon id="ae2lt:firmament_alloy_ingot" />
  <ItemIcon id="ae2lt:firmament_superconducting_wire" />
</ItemGrid>

## Firmament Dust

Firmament Dust is the root of the entire line and **can only be generated in the End**.

Place an <ItemLink id="ae2:annihilation_plane" /> facing **upward** on the **topmost buildable layer** of the End, and connect it to a powered ME network. Once the conditions are met, the plane generates Firmament Dust on its own — without breaking any block — inserting roughly one portion into network storage every 10 seconds.

Conditions:

* The dimension must be the End
* The Annihilation Plane must face up
* The plane must sit on the topmost buildable layer (y 255 in a default End; if the world height limit was changed, the actual limit applies)
* The plane must belong to a powered, active network

> Each qualifying Annihilation Plane generates independently, so several can be run in parallel to scale up output.

## Firmament Conversion Core

Most refining steps rely on the **Firmament Conversion Core**. This block cannot be crafted and exists only inside the **Firmament Starship**, a structure that floats above the outer End islands — travel beyond the central island to find one. The core is indestructible, cannot be mined or moved, and only operates inside the starship structure, so use it right where it stands.

The core consumes neither energy nor lightning, and each job finishes after a short wait: right-click it with a material in hand to insert, then right-click with an **empty hand** to collect the result. It accepts up to three inputs at once and produces the outputs defined by each recipe. Hoppers and pipes can insert materials and extract products through any side, so an automated production line can be built right on the starship.

## Refined Materials

Firmament Dust is processed along two production lines:

| Material | Made at | Primarily used for |
|------|---------|---------|
| Firmament Essence | Firmament Conversion Core | Firmament Superconducting Wire and higher recipes |
| Firmament Mixture | [Overload Processing Factory](../machines/overload-processing-factory.md) | refining into Firmament Alloy Ingot |
| Firmament Alloy Ingot | Firmament Conversion Core | main material of Celestweave and the Electromagnetic Railgun |
| Firmament Superconducting Wire | [Overload Processing Factory](../machines/overload-processing-factory.md) | energy conduit of Celestweave |

Besides firmament materials, these recipes also call for auxiliary ingredients such as Overload Alloy, Netherite Scrap and Phantom Membranes. Firmament Alloy Ingot is the mod's highest-tier structural material, while Firmament Superconducting Wire serves as the energy-conducting component of the Celestweave set.

## Firmament Spirit Cores

The spirit cores are the heart of the Celestweave armor set.

An <ItemLink id="ae2lt:inactive_firmament_spirit_core" /> has a chance to appear in End City treasure chests. Processing one in the Firmament Conversion Core **activates** it, splitting it into one of each of the four spirit cores at once:

<ItemGrid>
  <ItemIcon id="ae2lt:firmament_spirit_core_oculus" />
  <ItemIcon id="ae2lt:firmament_spirit_core_core" />
  <ItemIcon id="ae2lt:firmament_spirit_core_conduit" />
  <ItemIcon id="ae2lt:firmament_spirit_core_stride" />
</ItemGrid>

The four cores correspond to the Oculus, Core, Conduit and Stride pieces of the [Celestweave](../celestweave.md) set, and are the key components for assembling each piece at the [Lightning Assembly Chamber](../machines/lightning-assembly-chamber.md).
