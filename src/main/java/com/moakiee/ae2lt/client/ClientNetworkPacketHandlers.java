package com.moakiee.ae2lt.client;

import java.util.List;

import appeng.client.render.crafting.AssemblerAnimationStatus;
import com.moakiee.ae2lt.blockentity.PigmeeMolecularAssemblerBlockEntity;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.PhaseFlightPlayerState;
import com.moakiee.ae2lt.client.gui.FrequencyScreen;
import com.moakiee.ae2lt.entity.RitualHyperdimensionalPigmeeEntity;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.menu.hub.DeviceHubMenu;
import com.moakiee.ae2lt.network.CelestweaveSubmoduleActivePacket;
import com.moakiee.ae2lt.network.FlightInertiaSyncPacket;
import com.moakiee.ae2lt.network.PigmeeAssemblerAnimationPacket;
import com.moakiee.ae2lt.network.PhaseLockProtectionSyncPacket;
import com.moakiee.ae2lt.network.RitualItemBurstPacket;
import com.moakiee.ae2lt.network.SyncFrequencyDetailPacket;
import com.moakiee.ae2lt.network.SyncFrequencyListPacket;
import com.moakiee.ae2lt.network.UpdateFrequencyBasicPacket;
import com.moakiee.ae2lt.network.hub.DeviceHubSyncPacket;
import com.moakiee.ae2lt.network.tianshu.MaintenanceEditorSyncPacket;
import com.moakiee.ae2lt.network.tianshu.MaintenanceSummarySyncPacket;
import com.moakiee.ae2lt.network.tianshu.ClosedLoopResultPagePacket;
import com.moakiee.ae2lt.network.tianshu.UploadTargetsSyncPacket;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

public final class ClientNetworkPacketHandlers {

    private ClientNetworkPacketHandlers() {
    }

    public static void handleEasterEgg() {
        EasterEggOverlay.trigger();
    }

    public static void handleFrequencyResponse(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        if (minecraft.screen instanceof FrequencyScreen fs) {
            fs.showInlineError(message);
        } else {
            player.displayClientMessage(message, true);
        }
    }

    public static void handleFrequencyList(List<SyncFrequencyListPacket.FrequencyEntry> entries) {
        ClientFrequencyCache.updateFromSync(entries);
    }

    public static void handleFrequencyDetail(int frequencyId, byte syncType, CompoundTag data) {
        if (syncType == SyncFrequencyDetailPacket.TYPE_MEMBERS) {
            ClientFrequencyCache.updateMembers(frequencyId, data);
        } else if (syncType == SyncFrequencyDetailPacket.TYPE_CONNECTIONS) {
            ClientFrequencyCache.updateConnections(frequencyId, data);
        }
    }

    public static void handleFrequencyBasicUpdate(UpdateFrequencyBasicPacket packet) {
        if (packet.deleted()) {
            ClientFrequencyCache.removeFrequency(packet.frequencyId());
        } else {
            ClientFrequencyCache.upsertFrequency(
                    packet.frequencyId(),
                    packet.name(),
                    packet.color(),
                    packet.ownerUUID(),
                    packet.security());
        }
    }

    public static void handlePigmeeAssemblerAnimation(PigmeeAssemblerAnimationPacket packet) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        var blockEntity = level.getBlockEntity(packet.pos());
        if (blockEntity instanceof PigmeeMolecularAssemblerBlockEntity assembler) {
            assembler.setAnimationStatus(new AssemblerAnimationStatus(packet.speed(), packet.output()));
        }
    }

    public static void handleRitualItemBurst(RitualItemBurstPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        var entity = minecraft.level.getEntity(packet.entityId());
        if (!(entity instanceof RitualHyperdimensionalPigmeeEntity)) {
            return;
        }

        ItemStack activationItem = switch (packet.stage()) {
            case RitualItemBurstPacket.PIGMEE_CORE -> new ItemStack(ModItems.PIGMEE_CORE.get());
            case RitualItemBurstPacket.UNDYING_MODULE ->
                    new ItemStack(ModItems.CELESTWEAVE_SUBMODULE_UNDYING.get());
            case RitualItemBurstPacket.PHASE_LOCK_MODULE ->
                    new ItemStack(ModItems.CELESTWEAVE_SUBMODULE_PHASE_LOCK.get());
            default -> ItemStack.EMPTY;
        };
        if (activationItem.isEmpty()) {
            return;
        }

        minecraft.particleEngine.createTrackingEmitter(entity, ParticleTypes.TOTEM_OF_UNDYING, 30);
        minecraft.level.playLocalSound(
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                SoundEvents.TOTEM_USE,
                entity.getSoundSource(),
                1.0F,
                1.0F,
                false);
        minecraft.gameRenderer.displayItemActivation(activationItem);
    }

    public static void handleCelestweaveSubmoduleActive(CelestweaveSubmoduleActivePacket packet) {
        CelestweaveArmorState.markClientActive(packet.armorId(), packet.submoduleId(), packet.active());
    }

    public static void handleFlightInertia(FlightInertiaSyncPacket packet) {
        CelestweaveArmorState.setClientFlightSettings(
                packet.armorId(), packet.inertiaEnabled(), packet.phaseModeEnabled());

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (packet.flightControlActive() || packet.flightLockEnabled()) {
            PhaseFlightPlayerState.activate(player);
            PhaseFlightPlayerState.setFlightLocked(player, packet.flightLockEnabled());
            PhaseFlightPlayerState.synchronizeFlying(player, packet.flying());
        } else {
            PhaseFlightPlayerState.endControl(player, packet.flying());
        }
    }

    public static void handlePhaseLockProtection(PhaseLockProtectionSyncPacket packet) {
        CelestweaveArmorState.setClientPhaseLockProtection(
                packet.armorId(), packet.blockExternalForces());
    }

    public static void handleDeviceHubSync(DeviceHubSyncPacket packet) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null
                || !(player.containerMenu instanceof DeviceHubMenu menu)
                || menu.containerId != packet.containerId()) {
            return;
        }
        menu.receiveSync(
                packet.deviceName(),
                packet.hasCore(),
                packet.powered(),
                packet.terrainDestruction(),
                packet.pvp(),
                packet.soundEnabled(),
                packet.chainDamage(),
                packet.executionMode(),
                packet.chargedSplash(),
                packet.moduleNameKeys(),
                packet.moduleCounts(),
                packet.moduleEnabled(),
                packet.selectedModuleIndex(),
                packet.moduleConfigKeys(),
                packet.moduleConfigLabels(),
                packet.moduleConfigValues(),
                packet.moduleConfigEditable());
    }

    public static void handleMaintenanceEditorSync(MaintenanceEditorSyncPacket packet) {
        TianshuPatternEncodingTermMenu menu = getTianshuMenu(packet.containerId());
        if (menu != null) {
            menu.receiveMaintenanceEditorData(packet.selectionRevision(), packet.data());
        }
    }

    public static void handleMaintenanceSummarySync(MaintenanceSummarySyncPacket packet) {
        TianshuPatternEncodingTermMenu menu = getTianshuMenu(packet.containerId());
        if (menu != null) {
            menu.receiveMaintenanceSummary(
                    packet.selectionRevision(), packet.revision(), packet.overflow(), packet.entries());
        }
    }

    public static void handleUploadTargetsSync(UploadTargetsSyncPacket packet) {
        TianshuPatternEncodingTermMenu menu = getTianshuMenu(packet.containerId());
        if (menu != null) {
            menu.receiveUploadTargets(packet.targets());
        }
    }

    public static void handleClosedLoopResultPage(ClosedLoopResultPagePacket packet) {
        TianshuPatternEncodingTermMenu menu = getTianshuMenu(packet.containerId());
        if (menu != null) {
            menu.receiveClosedLoopResultPage(packet.page());
        }
    }

    private static TianshuPatternEncodingTermMenu getTianshuMenu(int containerId) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null
                && player.containerMenu instanceof TianshuPatternEncodingTermMenu menu
                && menu.containerId == containerId) {
            return menu;
        }
        return null;
    }
}
