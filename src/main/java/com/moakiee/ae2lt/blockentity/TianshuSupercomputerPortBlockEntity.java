package com.moakiee.ae2lt.blockentity;

import com.moakiee.ae2lt.block.TianshuSupercomputerPortBlock;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCpuPool;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCpuPoolProvider;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IGrid;
import appeng.api.orientation.BlockOrientation;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class TianshuSupercomputerPortBlockEntity extends AENetworkedBlockEntity
        implements TimeWheelCraftingCpuPoolProvider {
    private static final int BINDING_CHECK_INTERVAL_TICKS = 20;
    private BlockPos controllerPos;
    private boolean formed;
    private long nextBindingCheckTick;

    public TianshuSupercomputerPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TIANSHU_SUPERCOMPUTER_PORT.get(), pos, state);
    }

    public static void serverTick(Level level,
                                  BlockPos pos,
                                  BlockState state,
                                  TianshuSupercomputerPortBlockEntity port) {
        if (level.isClientSide || level.getGameTime() < port.nextBindingCheckTick) {
            return;
        }
        port.nextBindingCheckTick = level.getGameTime() + BINDING_CHECK_INTERVAL_TICKS;
        port.validateControllerBinding();
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setTagName("tianshu_supercomputer_port")
                .setVisualRepresentation(ModBlocks.TIANSHU_SUPERCOMPUTER_PORT.get())
                .setIdlePowerUsage(8.0D)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .addService(TimeWheelCraftingCpuPoolProvider.class, this);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return formed ? AECableType.DENSE_SMART : AECableType.NONE;
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return formed ? EnumSet.allOf(Direction.class) : Collections.emptySet();
    }

    public void bindToController(BlockPos controllerPos) {
        BlockPos newControllerPos = controllerPos == null ? null : controllerPos.immutable();
        updateControllerBinding(newControllerPos, newControllerPos != null);
    }

    public void suspendFromController(BlockPos expectedControllerPos) {
        if (formed && expectedControllerPos != null && expectedControllerPos.equals(controllerPos)) {
            updateControllerBinding(controllerPos, false);
        }
    }

    private void updateControllerBinding(BlockPos newControllerPos,
                                         boolean newFormed) {
        boolean formedChanged = formed != newFormed;
        boolean bindingChanged = formedChanged || !Objects.equals(this.controllerPos, newControllerPos);
        this.controllerPos = newControllerPos;
        this.formed = newFormed;
        if (formedChanged) {
            onGridConnectableSidesChanged();
        }
        if (level != null && !level.isClientSide) {
            var state = getBlockState();
            if (state.hasProperty(TianshuSupercomputerPortBlock.FORMED)
                    && state.getValue(TianshuSupercomputerPortBlock.FORMED) != formed) {
                level.setBlock(worldPosition, state.setValue(TianshuSupercomputerPortBlock.FORMED, formed), Block.UPDATE_ALL);
            }
            if (bindingChanged) {
                level.updateNeighborsAt(worldPosition, state.getBlock());
            }
        }
        saveChanges();
        markForUpdate();
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public boolean isFormed() {
        return formed;
    }

    @Override
    @Nullable
    public TimeWheelCraftingCpuPool getTimeWheelCraftingCpuPool() {
        var controller = getController();
        return controller != null ? controller.getTimeWheelCraftingCpuPool() : null;
    }

    public IGrid getGrid() {
        return formed ? getMainNode().getGrid() : null;
    }

    public boolean isNetworkActive() {
        return formed && getMainNode().isActive() && getMainNode().getGrid() != null;
    }

    public TianshuSupercomputerControllerBlockEntity getController() {
        var controller = formed ? resolveBoundController() : null;
        return controller != null && controller.isPortActive(worldPosition) ? controller : null;
    }

    private TianshuSupercomputerControllerBlockEntity resolveBoundController() {
        if (controllerPos == null || level == null || !level.isLoaded(controllerPos)) {
            return null;
        }
        return level.getBlockEntity(controllerPos) instanceof TianshuSupercomputerControllerBlockEntity controller
                ? controller
                : null;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        nextBindingCheckTick = level != null ? level.getGameTime() : 0L;
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return ModBlocks.TIANSHU_SUPERCOMPUTER_PORT.get().asItem();
    }

    private void validateControllerBinding() {
        if (level == null || level.isClientSide || controllerPos == null) {
            return;
        }
        if (!level.isLoaded(controllerPos)) {
            suspendFromController(controllerPos);
            return;
        }
        if (level.getBlockEntity(controllerPos) instanceof TianshuSupercomputerControllerBlockEntity controller) {
            if (controller.isPortActive(worldPosition)) {
                if (!formed) {
                    controller.scheduleStructureCheck();
                }
            } else if (controller.ownsPort(worldPosition)) {
                suspendFromController(controllerPos);
                controller.scheduleStructureCheck();
            } else {
                bindToController(null);
            }
        } else if (!level.getBlockState(controllerPos).is(ModBlocks.TIANSHU_SUPERCOMPUTER_CONTROLLER.get())) {
            bindToController(null);
        }
    }
}
