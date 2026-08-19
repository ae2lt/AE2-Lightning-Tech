package com.moakiee.ae2lt.mixin.thunderbolt;

import java.util.ArrayList;
import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.core.sync.packets.CraftingStatusPacket;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.menu.AEBaseMenu;
import appeng.menu.me.common.IncrementalUpdateHelper;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.menu.me.crafting.CraftingStatus;
import appeng.menu.me.crafting.CraftingStatusEntry;

import com.moakiee.ae2lt.crafting.timewheel.Ae2LtTimeWheelCraftingCpuLogic;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU;

@Mixin(value = CraftingCPUMenu.class, remap = false)
public abstract class TimeWheelCraftingCPUMenuMixin extends AEBaseMenu {
    @Unique
    private static final long thunderbolt$PROGRESS_SCALE = Integer.MAX_VALUE;

    @Final
    @Shadow
    private IncrementalUpdateHelper incrementalUpdateHelper;

    @Final
    @Shadow
    private Consumer<AEKey> cpuChangeListener;

    @Shadow
    private CraftingCPUCluster cpu;

    @Shadow
    public CpuSelectionMode schedulingMode;

    @Shadow
    public boolean cantStoreItems;

    @Unique
    private TimeWheelCraftingCPU thunderbolt$timeWheelCpu;

    protected TimeWheelCraftingCPUMenuMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Inject(method = "setCPU(Lappeng/api/networking/crafting/ICraftingCPU;)V", at = @At("HEAD"), cancellable = true)
    private void thunderbolt$setTimeWheelCpu(ICraftingCPU selected, CallbackInfo ci) {
        if (this.thunderbolt$timeWheelCpu != null) {
            this.thunderbolt$timeWheelCpu.getCraftingLogic().removeListener(cpuChangeListener);
            this.thunderbolt$timeWheelCpu = null;
        }

        if (!(selected instanceof TimeWheelCraftingCPU timeWheelCpu)) {
            return;
        }

        if (this.cpu != null) {
            this.cpu.craftingLogic.removeListener(cpuChangeListener);
            this.cpu = null;
        }

        this.incrementalUpdateHelper.reset();
        this.thunderbolt$timeWheelCpu = timeWheelCpu;

        var allItems = new KeyCounter();
        timeWheelCpu.getCraftingLogic().getAllItems(allItems);
        for (var entry : allItems) {
            this.incrementalUpdateHelper.addChange(entry.getKey());
        }
        timeWheelCpu.getCraftingLogic().addListener(cpuChangeListener);

        ci.cancel();
    }

    @Inject(method = "cancelCrafting", at = @At("TAIL"))
    private void thunderbolt$cancelTimeWheelCrafting(CallbackInfo ci) {
        if (!isClientSide() && this.thunderbolt$timeWheelCpu != null) {
            this.thunderbolt$timeWheelCpu.cancelJob();
        }
    }

    @Inject(
            method = "removed(Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("TAIL"),
            remap = true)
    private void thunderbolt$removed(Player player, CallbackInfo ci) {
        if (this.thunderbolt$timeWheelCpu != null) {
            this.thunderbolt$timeWheelCpu.getCraftingLogic().removeListener(cpuChangeListener);
        }
    }

    @Inject(method = "broadcastChanges()V", at = @At("HEAD"), remap = true)
    private void thunderbolt$broadcastTimeWheelStatus(CallbackInfo ci) {
        if (!isServerSide() || this.thunderbolt$timeWheelCpu == null) {
            return;
        }

        var logic = this.thunderbolt$timeWheelCpu.getCraftingLogic();
        this.schedulingMode = this.thunderbolt$timeWheelCpu.getSelectionMode();
        this.cantStoreItems = logic.isCantStoreItems();

        if (this.incrementalUpdateHelper.hasChanges()) {
            var status = thunderbolt$createStatus(this.incrementalUpdateHelper, logic);
            this.incrementalUpdateHelper.commitChanges();
            sendPacketToClient(new CraftingStatusPacket(containerId, status));
        }
    }

    @Unique
    private static CraftingStatus thunderbolt$createStatus(IncrementalUpdateHelper changes,
                                                            Ae2LtTimeWheelCraftingCpuLogic logic) {
        boolean full = changes.isFullUpdate();
        var entries = new ArrayList<CraftingStatusEntry>();

        for (var what : changes) {
            long storedCount = logic.getStored(what);
            long activeCount = logic.getWaitingFor(what);
            long pendingCount = logic.getPendingOutputs(what);

            var sentStack = what;
            if (!full && changes.getSerial(what) != null) {
                sentStack = null;
            }

            var entry = new CraftingStatusEntry(
                    changes.getOrAssignSerial(what),
                    sentStack,
                    storedCount,
                    activeCount,
                    pendingCount);
            entries.add(entry);

            if (entry.isDeleted()) {
                changes.removeSerial(what);
            }
        }

        var tracker = logic.getElapsedTimeTracker();
        long remaining = thunderbolt$remainingProgressUnits(tracker.getProgress());
        return new CraftingStatus(
                full,
                tracker.getElapsedTime(),
                remaining,
                thunderbolt$PROGRESS_SCALE,
                entries);
    }

    @Unique
    private static long thunderbolt$remainingProgressUnits(float progress) {
        long remaining = (long) (thunderbolt$PROGRESS_SCALE
                - (double) progress * thunderbolt$PROGRESS_SCALE);
        return Math.max(0L, Math.min(thunderbolt$PROGRESS_SCALE, remaining));
    }
}
