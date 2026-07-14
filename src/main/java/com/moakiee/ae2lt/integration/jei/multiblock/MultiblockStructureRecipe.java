package com.moakiee.ae2lt.integration.jei.multiblock;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Immutable client-side description of one JEI multiblock structure page.
 *
 * <p>The displayed state is a concrete, valid example. {@link Cell#alternatives()}
 * contains every block accepted at that position and is used by the selection
 * panel to explain substitutions. Material counts are derived from the displayed
 * cells so the list cannot drift from the preview.</p>
 */
public record MultiblockStructureRecipe(
        ResourceLocation id,
        Component title,
        int sizeX,
        int sizeY,
        int sizeZ,
        List<Cell> cells,
        List<MaterialEntry> materials,
        List<ItemStack> focusStacks) {

    public MultiblockStructureRecipe {
        Objects.requireNonNull(id);
        Objects.requireNonNull(title);
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("Multiblock dimensions must be positive");
        }
        cells = List.copyOf(cells);
        materials = List.copyOf(materials);
        focusStacks = copyStacks(focusStacks);
    }

    public static MultiblockStructureRecipe create(
            ResourceLocation id,
            Component title,
            int sizeX,
            int sizeY,
            int sizeZ,
            List<Cell> cells,
            List<MaterialSpec> materialOrder) {
        List<Cell> cellCopy = List.copyOf(cells);
        var materials = new ArrayList<MaterialEntry>(materialOrder.size());
        for (var spec : materialOrder) {
            int count = 0;
            for (var cell : cellCopy) {
                if (cell.state().is(spec.block())) {
                    count++;
                }
            }
            if (count > 0) {
                materials.add(new MaterialEntry(spec.block(), count, spec.note()));
            }
        }

        var focusBlocks = new LinkedHashSet<Block>();
        for (var cell : cellCopy) {
            if (!cell.state().isAir()) {
                focusBlocks.add(cell.state().getBlock());
            }
            for (var alternative : cell.alternatives()) {
                if (alternative != Blocks.AIR) {
                    focusBlocks.add(alternative);
                }
            }
        }
        var focusStacks = focusBlocks.stream().map(ItemStack::new).toList();
        return new MultiblockStructureRecipe(
                id, title, sizeX, sizeY, sizeZ, cellCopy, materials, focusStacks);
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).toList();
    }

    public record Cell(
            BlockPos localPos,
            BlockState state,
            Component role,
            List<Block> alternatives,
            List<Component> rules,
            boolean shell) {
        public Cell {
            Objects.requireNonNull(localPos);
            Objects.requireNonNull(state);
            Objects.requireNonNull(role);
            alternatives = List.copyOf(alternatives);
            rules = List.copyOf(rules);
            if (alternatives.isEmpty()) {
                throw new IllegalArgumentException("A structure cell must have at least one alternative");
            }
        }
    }

    public record MaterialEntry(Block block, int count, Component note) {
        public MaterialEntry {
            Objects.requireNonNull(block);
            Objects.requireNonNull(note);
            if (count <= 0) {
                throw new IllegalArgumentException("Material count must be positive");
            }
        }
    }

    public record MaterialSpec(Block block, Component note) {
        public MaterialSpec {
            Objects.requireNonNull(block);
            Objects.requireNonNull(note);
        }

        public static MaterialSpec of(Block block) {
            return new MaterialSpec(block, Component.empty());
        }

        public static MaterialSpec of(Block block, Component note) {
            return new MaterialSpec(block, note);
        }
    }
}
