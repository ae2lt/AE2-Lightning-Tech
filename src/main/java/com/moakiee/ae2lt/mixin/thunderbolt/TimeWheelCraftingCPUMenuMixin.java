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
import appeng.core.network.clientbound.CraftingStatusPacket;
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
    @Final
    @Shadow
    private IncrementalUpdateHelper incrementalUpdateHelper;

    @Final
    @Shadow
    private Consumer<AEKey> cpuChangeListener;

    @Shadow
    private boolean cachedSuspend;

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
        this.cachedSuspend = false;
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

    @Inject(method = "toggleScheduling", at = @At("TAIL"))
    private void thunderbolt$toggleTimeWheelScheduling(CallbackInfo ci) {
        if (!isClientSide() && this.thunderbolt$timeWheelCpu != null) {
            var logic = this.thunderbolt$timeWheelCpu.getCraftingLogic();
            logic.setJobSuspended(!logic.isJobSuspended());
        }
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void thunderbolt$removed(Player player, CallbackInfo ci) {
        if (this.thunderbolt$timeWheelCpu != null) {
            this.thunderbolt$timeWheelCpu.getCraftingLogic().removeListener(cpuChangeListener);
        }
    }

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void thunderbolt$broadcastTimeWheelStatus(CallbackInfo ci) {
        if (!isServerSide() || this.thunderbolt$timeWheelCpu == null) {
            return;
        }

        var logic = this.thunderbolt$timeWheelCpu.getCraftingLogic();
        this.schedulingMode = this.thunderbolt$timeWheelCpu.getSelectionMode();
        this.cantStoreItems = logic.isCantStoreItems();

        if (this.incrementalUpdateHelper.hasChanges() || this.cachedSuspend != logic.isJobSuspended()) {
            var status = thunderbolt$createStatus(this.incrementalUpdateHelper, logic);
            this.incrementalUpdateHelper.commitChanges();
            this.cachedSuspend = status.isSuspended();
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
        return new CraftingStatus(
                full,
                tracker.getElapsedTime(),
                tracker.getRemainingItemCount(),
                tracker.getStartItemCount(),
                entries,
                logic.isJobSuspended());
    }
}
