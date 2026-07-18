package com.moakiee.ae2lt.integration.jei.multiblock;

import java.util.ArrayList;
import java.util.List;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockRole;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockTemplate;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockRole;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockTemplate;
import com.moakiee.ae2lt.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Builds the two code-defined multiblock pages exposed through JEI. */
public final class MultiblockStructureRecipes {
    private static final BlockPos MATRIX_DEFAULT_PATTERN = new BlockPos(1, 1, 1);
    private static final BlockPos MATRIX_DEFAULT_PORT = new BlockPos(6, 5, 3);
    private static final int TIANSHU_DEFAULT_STORAGE_CORES = 16;

    public static List<MultiblockStructureRecipe> all() {
        return List.of(matrix(), tianshu());
    }

    private static MultiblockStructureRecipe matrix() {
        Block casing = ModBlocks.MATTER_WARPING_MATRIX_CASING.get();
        Block frame = ModBlocks.MATTER_WARPING_MATRIX_CONSTRAINT_FRAME.get();
        Block glass = ModBlocks.MATTER_WARPING_MATRIX_GLASS.get();
        Block controller = ModBlocks.MATTER_WARPING_MATRIX_CONTROLLER.get();
        Block port = ModBlocks.MATTER_WARPING_MATRIX_PORT.get();
        Block patternT1 = ModBlocks.MATTER_WARPING_MATRIX_PATTERN_STORAGE_T1.get();
        Block patternT2 = ModBlocks.MATTER_WARPING_MATRIX_PATTERN_STORAGE_T2.get();

        List<Block> mainCores = List.of(
                ModBlocks.MATTER_WARPING_MATRIX_STABLE_MAIN_CORE.get(),
                ModBlocks.MATTER_WARPING_MATRIX_QUANTUM_MAIN_CORE.get(),
                ModBlocks.MATTER_WARPING_MATRIX_OVERLOAD_MAIN_CORE.get(),
                ModBlocks.MATTER_WARPING_MATRIX_CREATIVE_MAIN_CORE.get());
        List<Block> subCores = List.of(
                ModBlocks.MATTER_WARPING_MATRIX_BLANK_SUB_CORE.get(),
                ModBlocks.MATTER_WARPING_MATRIX_THREAD_SUB_CORE_T1.get(),
                ModBlocks.MATTER_WARPING_MATRIX_THREAD_SUB_CORE_T2.get(),
                ModBlocks.MATTER_WARPING_MATRIX_MULTIPLIER_SUB_CORE_T1.get(),
                ModBlocks.MATTER_WARPING_MATRIX_MULTIPLIER_SUB_CORE_T2.get(),
                ModBlocks.MATTER_WARPING_MATRIX_COOLING_SUB_CORE_T1.get(),
                ModBlocks.MATTER_WARPING_MATRIX_COOLING_SUB_CORE_T2.get());

        Component casingRole = role("casing");
        Component frameRole = role("constraint_frame");
        Component glassRole = role("glass");
        Component controllerRole = role("controller");
        Component portRole = role("port_candidate");
        Component patternRole = role("pattern_bay");
        Component mainCoreRole = role("main_core");
        Component subCoreRole = role("sub_core");

        Component portRule = rule("matrix_port");
        Component patternRule = rule("matrix_pattern");
        Component mainCoreRule = rule("matrix_main_core");
        Component subCoreRule = rule("matrix_sub_core");
        Component multiplierRule = rule("matrix_multiplier");

        var cells = new ArrayList<MultiblockStructureRecipe.Cell>();
        for (var entry : MatrixMultiblockTemplate.entries()) {
            BlockPos pos = entry.localPos();
            MatrixMultiblockRole role = entry.role();
            switch (role) {
                case EMPTY -> {
                    // Template air is intentionally not rendered or selectable.
                }
                case CASING -> cells.add(cell(pos, casing, casingRole, List.of(casing), true));
                case CONSTRAINT_FRAME -> cells.add(cell(pos, frame, frameRole, List.of(frame), true));
                case GLASS -> cells.add(cell(pos, glass, glassRole, List.of(glass), true));
                case CONTROLLER -> cells.add(cell(
                        pos,
                        facing(controller, Direction.EAST),
                        controllerRole,
                        List.of(controller),
                        List.of(),
                        false));
                case PORT_CANDIDATE -> {
                    Block displayed = pos.equals(MATRIX_DEFAULT_PORT) ? port : frame;
                    cells.add(cell(
                            pos,
                            displayed,
                            portRole,
                            List.of(port, frame),
                            List.of(portRule),
                            false));
                }
                case PATTERN_BAY -> {
                    Block displayed = pos.equals(MATRIX_DEFAULT_PATTERN) ? patternT1 : Blocks.AIR;
                    cells.add(cell(
                            pos,
                            displayed,
                            patternRole,
                            List.of(Blocks.AIR, patternT1, patternT2),
                            List.of(patternRule),
                            false));
                }
                case CRAFTING_BAY -> {
                    if (pos.equals(MatrixMultiblockTemplate.CRAFTING_CENTER_LOCAL)) {
                        cells.add(cell(
                                pos,
                                mainCores.getFirst(),
                                mainCoreRole,
                                mainCores,
                                List.of(mainCoreRule),
                                false));
                    } else {
                        cells.add(cell(
                                pos,
                                subCores.getFirst(),
                                subCoreRole,
                                subCores,
                                List.of(subCoreRule, multiplierRule),
                                false));
                    }
                }
            }
        }

        List<MultiblockStructureRecipe.MaterialSpec> materialOrder = List.of(
                material(casing),
                material(frame, portRule),
                material(glass),
                material(controller),
                material(port, portRule),
                material(patternT1, patternRule),
                material(mainCores.getFirst(), mainCoreRule),
                material(subCores.getFirst(), subCoreRule));

        return MultiblockStructureRecipe.create(
                id("matter_warping_matrix"),
                Component.translatable("jei.ae2lt.multiblock.matrix"),
                MatrixMultiblockTemplate.SIZE_X,
                MatrixMultiblockTemplate.SIZE_Y,
                MatrixMultiblockTemplate.SIZE_Z,
                cells,
                materialOrder);
    }

    private static MultiblockStructureRecipe tianshu() {
        Block casing = ModBlocks.TIANSHU_SUPERCOMPUTER_CASING.get();
        Block cooling = ModBlocks.PHASE_CHANGE_COOLING_UNIT.get();
        Block glass = ModBlocks.TIANSHU_SUPERCOMPUTER_GLASS.get();
        Block controller = ModBlocks.TIANSHU_SUPERCOMPUTER_CONTROLLER.get();
        Block port = ModBlocks.TIANSHU_SUPERCOMPUTER_PORT.get();
        Block blank = ModBlocks.BLANK_SUPERCOMPUTING_UNIT.get();
        Block storage = ModBlocks.STORAGE_SUPERCOMPUTING_UNIT.get();
        Block parallel = ModBlocks.PARALLEL_SUPERCOMPUTING_UNIT.get();
        Block patternStorage = ModBlocks.CLOSED_LOOP_PATTERN_STORAGE.get();
        Block seedStorage = ModBlocks.CLOSED_LOOP_SEED_STORAGE.get();
        List<Block> peripheralUnits = List.of(blank, storage, parallel, patternStorage, seedStorage);
        List<Block> mainCores = List.of(
                ModBlocks.BASELINE_SUPERCOMPUTING_UNIT.get(),
                ModBlocks.QUANTUM_SUPERCOMPUTING_UNIT.get(),
                ModBlocks.OVERLOAD_SUPERCOMPUTING_UNIT.get(),
                ModBlocks.MULTIDIMENSIONAL_SUPERCOMPUTING_UNIT.get());

        Component casingRole = role("casing");
        Component coolingRole = role("cooling");
        Component glassRole = role("glass");
        Component controllerRole = role("controller");
        Component portRole = role("port_candidate");
        Component mainCoreRole = role("main_core");
        Component peripheralRole = role("peripheral_core");

        Component portRule = rule("tianshu_port");
        Component mainCoreRule = rule("tianshu_main_core");
        Component peripheralRule = rule("tianshu_peripheral");

        var cells = new ArrayList<MultiblockStructureRecipe.Cell>();
        int peripheralIndex = 0;
        for (int y = 0; y < TianshuMultiblockTemplate.SIZE; y++) {
            for (int z = 0; z < TianshuMultiblockTemplate.SIZE; z++) {
                for (int x = 0; x < TianshuMultiblockTemplate.SIZE; x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    TianshuMultiblockRole role = TianshuMultiblockTemplate.roleAt(pos);
                    switch (role) {
                        case IGNORED -> {
                            // Required air is omitted from the rendered model and material list.
                        }
                        case CASING -> cells.add(cell(pos, casing, casingRole, List.of(casing), true));
                        case COOLING -> cells.add(cell(pos, cooling, coolingRole, List.of(cooling), true));
                        case GLASS -> cells.add(cell(pos, glass, glassRole, List.of(glass), true));
                        case CONTROLLER -> cells.add(cell(
                                pos,
                                facing(controller, Direction.WEST),
                                controllerRole,
                                List.of(controller),
                                List.of(),
                                false));
                        case PORT_CANDIDATE -> {
                            Block displayed = pos.equals(TianshuMultiblockTemplate.LOWER_PORT) ? port : cooling;
                            cells.add(cell(
                                    pos,
                                    displayed,
                                    portRole,
                                    List.of(port, cooling),
                                    List.of(portRule),
                                    false));
                        }
                        case CORE_RESERVED -> {
                            if (pos.equals(new BlockPos(3, 3, 3))) {
                                cells.add(cell(
                                        pos,
                                        mainCores.getFirst(),
                                        mainCoreRole,
                                        mainCores,
                                        List.of(mainCoreRule),
                                        false));
                            } else {
                                Block displayed = peripheralIndex < TIANSHU_DEFAULT_STORAGE_CORES ? storage : parallel;
                                peripheralIndex++;
                                cells.add(cell(
                                        pos,
                                        displayed,
                                        peripheralRole,
                                        peripheralUnits,
                                        List.of(peripheralRule),
                                        false));
                            }
                        }
                    }
                }
            }
        }

        List<MultiblockStructureRecipe.MaterialSpec> materialOrder = List.of(
                material(casing),
                material(cooling, portRule),
                material(glass),
                material(controller),
                material(port, portRule),
                material(mainCores.getFirst(), mainCoreRule),
                material(blank, peripheralRule),
                material(storage, peripheralRule),
                material(parallel, peripheralRule),
                material(patternStorage, peripheralRule),
                material(seedStorage, peripheralRule));

        return MultiblockStructureRecipe.create(
                id("tianshu_supercomputer"),
                Component.translatable("jei.ae2lt.multiblock.tianshu"),
                TianshuMultiblockTemplate.SIZE,
                TianshuMultiblockTemplate.SIZE,
                TianshuMultiblockTemplate.SIZE,
                cells,
                materialOrder);
    }

    private static MultiblockStructureRecipe.Cell cell(
            BlockPos pos,
            Block block,
            Component role,
            List<Block> alternatives,
            boolean shell) {
        return cell(pos, block.defaultBlockState(), role, alternatives, List.of(), shell);
    }

    private static MultiblockStructureRecipe.Cell cell(
            BlockPos pos,
            Block block,
            Component role,
            List<Block> alternatives,
            List<Component> rules,
            boolean shell) {
        return cell(pos, block.defaultBlockState(), role, alternatives, rules, shell);
    }

    private static MultiblockStructureRecipe.Cell cell(
            BlockPos pos,
            BlockState state,
            Component role,
            List<Block> alternatives,
            List<Component> rules,
            boolean shell) {
        return new MultiblockStructureRecipe.Cell(pos.immutable(), state, role, alternatives, rules, shell);
    }

    private static BlockState facing(Block block, Direction direction) {
        BlockState state = block.defaultBlockState();
        return state.hasProperty(HorizontalDirectionalBlock.FACING)
                ? state.setValue(HorizontalDirectionalBlock.FACING, direction)
                : state;
    }

    private static Component role(String path) {
        return Component.translatable("jei.ae2lt.multiblock.role." + path);
    }

    private static Component rule(String path) {
        return Component.translatable("jei.ae2lt.multiblock.rule." + path);
    }

    private static MultiblockStructureRecipe.MaterialSpec material(Block block) {
        return MultiblockStructureRecipe.MaterialSpec.of(block);
    }

    private static MultiblockStructureRecipe.MaterialSpec material(Block block, Component note) {
        return MultiblockStructureRecipe.MaterialSpec.of(block, note);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, path);
    }

    private MultiblockStructureRecipes() {
    }
}
