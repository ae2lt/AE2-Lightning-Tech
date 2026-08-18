package com.moakiee.ae2lt.grid.wirelesslink;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridMultiblock;
import appeng.api.networking.IGridNode;
import java.lang.reflect.Method;
import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

public final class MultiblockLinkReadiness {
    private static final String FORMED_PROPERTY = "formed";

    private MultiblockLinkReadiness() {
    }

    public static boolean canKeepVirtualConnection(IGridNode node) {
        Boolean formedState = getRelevantFormedState(node);
        if (Boolean.FALSE.equals(formedState)) {
            return false;
        }

        if (!node.hasFlag(GridFlags.MULTIBLOCK)) {
            return true;
        }

        var multiblock = node.getService(IGridMultiblock.class);
        return multiblock != null && containsSelf(multiblock.getMultiblockNodes(), node);
    }

    public static boolean isKnownMultiblockAffectedByChange(IGridNode node, BlockPos changedPos) {
        Object owner = getOwner(node);
        if (!(owner instanceof BlockEntity blockEntity)) {
            return false;
        }

        var blockId = BuiltInRegistries.BLOCK.getKey(currentBlockState(blockEntity).getBlock());
        if (!node.hasFlag(GridFlags.MULTIBLOCK)
                && !isKnownFormedStateMultiblock(owner.getClass().getName(), blockId)) {
            return false;
        }

        Object cluster = invokeNoArg(owner, "getCluster");
        if (cluster == null) {
            return false;
        }

        BlockPos min = invokeBlockPos(cluster, "getBoundsMin");
        BlockPos max = invokeBlockPos(cluster, "getBoundsMax");
        return min != null && max != null && contains(min, max, changedPos);
    }

    public static void refreshAfterVirtualConnectionRemoved(IGridNode node) {
        Object owner = getOwner(node);
        if (!(owner instanceof BlockEntity blockEntity)) {
            return;
        }

        var state = currentBlockState(blockEntity);
        var blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (readBooleanProperty(state, FORMED_PROPERTY) == null
                || (!node.hasFlag(GridFlags.MULTIBLOCK)
                        && !isKnownFormedStateMultiblock(owner.getClass().getName(), blockId))) {
            return;
        }

        invokeUpdateSubType(blockEntity);
    }

    static boolean containsSelf(Iterator<?> nodes, Object self) {
        while (nodes.hasNext()) {
            if (nodes.next() == self) {
                return true;
            }
        }
        return false;
    }

    static boolean contains(BlockPos min, BlockPos max, BlockPos pos) {
        int minX = Math.min(min.getX(), max.getX());
        int minY = Math.min(min.getY(), max.getY());
        int minZ = Math.min(min.getZ(), max.getZ());
        int maxX = Math.max(min.getX(), max.getX());
        int maxY = Math.max(min.getY(), max.getY());
        int maxZ = Math.max(min.getZ(), max.getZ());
        return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    static boolean isKnownFormedStateMultiblock(String ownerClassName, String blockNamespace, String blockPath) {
        return KnownFormedStateMultiblocks.matches(ownerClassName, blockNamespace, blockPath);
    }

    private static boolean isKnownFormedStateMultiblock(String ownerClassName, ResourceLocation blockId) {
        return blockId != null && isKnownFormedStateMultiblock(ownerClassName, blockId.getNamespace(), blockId.getPath());
    }

    private static Boolean getRelevantFormedState(IGridNode node) {
        Object owner = getOwner(node);
        if (!(owner instanceof BlockEntity blockEntity)) {
            return null;
        }

        var state = currentBlockState(blockEntity);
        Boolean formed = readBooleanProperty(state, FORMED_PROPERTY);
        if (formed == null) {
            return null;
        }

        var blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (node.hasFlag(GridFlags.MULTIBLOCK)
                || isKnownFormedStateMultiblock(owner.getClass().getName(), blockId)) {
            return formed;
        }
        return null;
    }

    private static Object getOwner(IGridNode node) {
        try {
            return node.getOwner();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Object invokeNoArg(Object target, String methodName) {
        Method method = findMethod(target.getClass(), methodName);
        if (method == null) {
            return null;
        }
        try {
            method.setAccessible(true);
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static BlockPos invokeBlockPos(Object target, String methodName) {
        Object value = invokeNoArg(target, methodName);
        return value instanceof BlockPos pos ? pos : null;
    }

    private static BlockState currentBlockState(BlockEntity blockEntity) {
        var level = blockEntity.getLevel();
        if (level == null) {
            return blockEntity.getBlockState();
        }
        return level.getBlockState(blockEntity.getBlockPos());
    }

    private static Boolean readBooleanProperty(BlockState state, String propertyName) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof BooleanProperty booleanProperty && property.getName().equals(propertyName)) {
                return state.getValue(booleanProperty);
            }
        }
        return null;
    }

    private static void invokeUpdateSubType(BlockEntity blockEntity) {
        Method updateSubType = findMethod(blockEntity.getClass(), "updateSubType", boolean.class);
        if (updateSubType == null) {
            return;
        }

        try {
            updateSubType.setAccessible(true);
            updateSubType.invoke(blockEntity, false);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
