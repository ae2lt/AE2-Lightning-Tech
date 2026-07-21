---
navigation:
  title: Structure and Auto-build
  icon: ae2lt:matter_warping_matrix_casing
  parent: matrix/matrix-index.md
  position: 10
item_ids:
  - ae2lt:matter_warping_matrix_casing
  - ae2lt:matter_warping_matrix_constraint_frame
  - ae2lt:matter_warping_matrix_glass
  - ae2lt:matter_warping_matrix_controller
  - ae2lt:matter_warping_matrix_port
---

# Structure and Auto-build

The matrix occupies a fixed **7×11×7** volume. The Controller is centered on one 7×11 side. The structure extends 6 blocks in the Controller's facing direction, 3 blocks to either side, and 5 blocks both above and below it.

The scene below shows a complete structure with the minimum pattern capacity, a Stable Main Core, and Blank Units. Drag to rotate it. The annotations identify the Controller, default Port position, and one Pattern Storage.

<GameScene zoom="2.4" background="transparent" interactive={true}>
  <ImportStructure src="../assets/assemblies/matter_warping_matrix.snbt" />
  <DiamondAnnotation pos="0.5 5.5 3.5" color="#f2d37a">Tianshu Matter Warping Matrix Controller</DiamondAnnotation>
  <DiamondAnnotation pos="6.5 5.5 3.5" color="#85f29e">Default Port position</DiamondAnnotation>
  <DiamondAnnotation pos="1.5 1.5 1.5" color="#80c6ff">One T1 Pattern Storage</DiamondAnnotation>
  <IsometricCamera yaw="215" pitch="25" />
</GameScene>

## Auto-build Material List

These quantities apply when only the Controller has been placed and every other target position is empty. Existing valid blocks are preserved and deducted from the actual requirement.

| Material | Count | Auto-build behavior |
|----------|------:|---------------------|
| <ItemLink id="ae2lt:matter_warping_matrix_casing" /> | 174 | Fixed outer casing |
| <ItemLink id="ae2lt:matter_warping_matrix_constraint_frame" /> | 132 | Includes the two Port candidates not used by the Port |
| <ItemLink id="ae2lt:matter_warping_matrix_glass" /> | 44 | Fixed observation layer |
| <ItemLink id="ae2lt:matter_warping_matrix_port" /> | 1 | Placed opposite the Controller unless a Port already exists |
| <ItemLink id="ae2lt:matter_warping_matrix_pattern_storage_t1" /> or <ItemLink id="ae2lt:matter_warping_matrix_pattern_storage_t2" /> | 1–50 | At least 1 is required; additional storages in the player inventory are inserted into empty bays, up to 50 |

The Controller is already in the world and is not consumed by the button. A complete structure also requires the following manually placed components:

| Internal core | Count | Notes |
|---------------|------:|-------|
| Any main core | 1 | Must occupy the geometric center |
| Any peripheral unit | 80 | Every peripheral position must be filled; Blank Units are valid fillers |

Starting from an empty site therefore requires at minimum **1 Controller, 352 structure blocks placed by the button, 1 Main Core, and 80 Peripheral Units**. The 352 structure blocks already include one minimum-required Pattern Storage.

## Using Auto-build

1. Place the Controller and reserve the full volume in its facing direction
2. Put the Casings, Frames, Glass, Port, and desired Pattern Storages in the **player inventory**
3. Use **Auto-build** from the Controller
4. After auto-build completes, manually place one Main Core and 80 Peripheral Units in the central core chamber

Auto-build only reads the player inventory. It cannot extract blocks from the ME network or adjacent containers. If only one Pattern Storage is wanted, do not carry additional Matrix Pattern Storages while using the button.

If any Casing, Frame, Glass, Port candidate, or Pattern Storage bay contains an invalid block, auto-build places nothing. Clear the blocked target position and try again.

## Manual Formation Rules

* Exactly **one** of the three Port candidates must contain a Matrix Port; the other two must be Constraint Frames
* The 50 Pattern Storage bays may be empty or contain T1/T2 storages, but at least one storage is required
* None of the 81 core positions may be empty: the center must be a Main Core and all other positions must be Peripheral Units
* Quantum and Overload Main Cores allow at most 15 Tianshu Amplifier Units; Stable rejects amplifiers, while Creative only accepts Blank Units
* Open gaps shown by the structure are not structure members and do not need to be sealed

When the final required block is placed, the matrix rescans and forms automatically. The Auto-build button does not need to be pressed again.
