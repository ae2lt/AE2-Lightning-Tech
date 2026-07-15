---
navigation:
  title: Structure and Auto-build
  icon: ae2lt:tianshu_supercomputer_casing
  parent: tianshu/tianshu-index.md
  position: 10
item_ids:
  - ae2lt:tianshu_supercomputer_casing
  - ae2lt:phase_change_cooling_unit
  - ae2lt:tianshu_supercomputer_glass
  - ae2lt:tianshu_supercomputer_controller
  - ae2lt:tianshu_supercomputer_port
---

# Structure and Auto-build

The Tianshu Supercomputer occupies a fixed **7×7×7** volume. Its controller sits at the center of the bottom edge of one side. From the controller, the structure extends six blocks forward, six blocks upward, and three blocks to either side.

The scene below shows a complete structure with a Baseline main unit, 16 Storage Units, and 10 Parallel Units. Drag to rotate the scene. The centers of the bottom and top faces are the two possible port positions.

<GameScene zoom="2.8" background="transparent" interactive={true}>
  <ImportStructure src="../assets/assemblies/tianshu_supercomputer.snbt" />
  <DiamondAnnotation pos="6.5 0.5 3.5" color="#f2d37a">Tianshu Controller</DiamondAnnotation>
  <DiamondAnnotation pos="3.5 0.5 3.5" color="#85f29e">Default Port Position</DiamondAnnotation>
  <DiamondAnnotation pos="3.5 6.5 3.5" color="#80c6ff">Alternate Port Position</DiamondAnnotation>
  <BoxAnnotation min="2 2 2" max="5 5 5" color="#d58cff" alwaysOnTop={true}>3×3×3 Core Chamber</BoxAnnotation>
  <IsometricCamera yaw="215" pitch="25" />
</GameScene>

## Auto-build Material List

These quantities assume that only the controller has been placed and every other target position is empty. Existing correct blocks are retained and deducted from the actual requirements.

| Material | Count | Auto-build Behavior |
|----------|------:|---------------------|
| <ItemLink id="ae2lt:tianshu_supercomputer_casing" /> | 99 | Fixed casing positions |
| <ItemLink id="ae2lt:phase_change_cooling_unit" /> | 17 | Includes the unused port candidate |
| <ItemLink id="ae2lt:tianshu_supercomputer_glass" /> | 98 | Fixed core-chamber observation layer |
| <ItemLink id="ae2lt:tianshu_supercomputer_port" /> | 1 | Placed in the lower candidate when no port already exists |

The controller is already present and is not consumed by the button. Auto-build also leaves the core chamber untouched. Formation requires the following units to be installed manually:

| Core-chamber Unit | Count | Requirement |
|-------------------|------:|-------------|
| Any main Supercomputing Unit | 1 | Must occupy the exact chamber center |
| <ItemLink id="ae2lt:storage_supercomputing_unit" /> | 1–25 | Combined with Parallel Units for 26 total; at least one required |
| <ItemLink id="ae2lt:parallel_supercomputing_unit" /> | 1–25 | Combined with Storage Units for 26 total; at least one required |

Starting from an empty site therefore requires **one controller, 215 non-core blocks placed by the button, and 27 core-chamber units**, for 243 structure members in total.

## Using Auto-build

1. Place the controller and reserve the full 7×7×7 volume in its facing direction
2. Put the casing, Phase-Change Cooling Units, glass, and port in the **player inventory**
3. Open the controller and select **Auto-build Shell**
4. After shell construction finishes, install the main, Storage, and Parallel Supercomputing Units manually

Auto-build reads only the player inventory; it does not extract blocks from the ME network or adjacent containers. It verifies the full material requirement before starting, then places one block per tick. Keep all required materials in the inventory until construction finishes.

An existing port may occupy either candidate. If neither position contains a port, auto-build places it in the lower candidate and installs a Phase-Change Cooling Unit in the upper candidate.

If any casing, cooling, glass, or port position handled by auto-build contains an incorrect block, auto-build places nothing. Clear the coordinates reported by the controller and try again. If materials are removed or a target becomes obstructed after construction starts, the process stops and consumes only blocks that were placed successfully.

## Manual Construction Rules

* Exactly **one** of the two port candidates must contain a Tianshu Port; the other requires a Phase-Change Cooling Unit
* The chamber center requires one main unit, and main units are invalid in the other 26 cells
* All 26 peripheral cells require Storage or Parallel Units, with at least one of each type
* The 100 omitted positions in the displayed structure are required air; decorations, cables, and other devices cannot occupy them

The structure rescans and forms automatically after the final required block is placed.
