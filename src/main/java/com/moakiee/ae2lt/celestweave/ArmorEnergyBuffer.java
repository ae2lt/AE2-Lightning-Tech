package com.moakiee.ae2lt.celestweave;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;

import com.moakiee.ae2lt.device.network.ArmorNetworkBinding;
import com.moakiee.ae2lt.logic.energy.AppFluxBridge;
import com.moakiee.ae2lt.registry.ModDataComponents;

public final class ArmorEnergyBuffer {

    /**
     * Authoritative runtime energy, keyed by armor UUID. The
     * {@code CELESTWEAVE_ENERGY_BUFFER} data component is NOT mutated on
     * every inventory tick — every such mutation dirties the ItemStack
     * and causes Minecraft to resync the armor slot to the client, which
     * unconditionally calls {@code LivingEntity.onEquipItem} → plays
     * {@code ARMOR_EQUIP_GENERIC} even though only the energy value
     * changed (issue #20, "after charging" variant).
     *
     * <p>Call {@link #flushToStack} to persist the runtime value back to
     * the data component before logout / clone / save.
     */
    private static final ConcurrentMap<UUID, Long> RUNTIME_ENERGY = new ConcurrentHashMap<>();

    private ArmorEnergyBuffer() {
    }

    public static long read(ItemStack stack) {
        return read(stack, null);
    }

    /**
     * Read the current energy. Prefer the runtime map; fall back to the
     * data component for newly-loaded armor that hasn't entered a
     * player's inventory yet (e.g. freshly crafted or picked up from
     * the ground).
     */
    public static long read(ItemStack stack, HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty()) {
            return 0L;
        }
        UUID id = CelestweaveArmorState.getArmorId(stack);
        if (id != null) {
            Long runtime = RUNTIME_ENERGY.get(id);
            if (runtime != null) {
                return Math.max(0L, Math.min(capacity(stack, registries), runtime));
            }
        }
        Long value = stack.get(ModDataComponents.CELESTWEAVE_ENERGY_BUFFER.get());
        return Math.max(0L, Math.min(capacity(stack, registries), value == null ? 0L : value));
    }

    public static long capacity(ItemStack stack) {
        return capacity(stack, null);
    }

    public static long capacity(ItemStack stack, HolderLookup.Provider registries) {
        return ArmorEnergyRules.capacityForExtraModuleFe(ArmorEnergyModuleStorage.capacityFe(stack, registries));
    }

    public static void write(ItemStack stack, long value) {
        write(stack, null, value);
    }

    /**
     * Store energy in the runtime map only — never in the
     * {@code CELESTWEAVE_ENERGY_BUFFER} data component.
     * See the class-level javadoc for the rationale.
     */
    public static void write(ItemStack stack, HolderLookup.Provider registries, long value) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        UUID id = CelestweaveArmorState.ensureArmorId(stack);
        long clamped = Math.max(0L, Math.min(capacity(stack, registries), value));
        RUNTIME_ENERGY.put(id, clamped);
    }

    public static void clamp(ItemStack stack) {
        write(stack, read(stack));
    }

    public static void clamp(ItemStack stack, HolderLookup.Provider registries) {
        write(stack, registries, read(stack, registries));
    }

    /**
     * Persist the runtime energy to the ItemStack data component.
     * Call only on save / logout / clone — never during
     * {@code inventoryTick}, because that is exactly what triggers the
     * spurious equip sound (see class-level javadoc).
     */
    public static void flushToStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        UUID id = CelestweaveArmorState.getArmorId(stack);
        if (id == null) {
            return;
        }
        Long runtime = RUNTIME_ENERGY.get(id);
        if (runtime == null) {
            return;
        }
        Long current = stack.get(ModDataComponents.CELESTWEAVE_ENERGY_BUFFER.get());
        if (current != null && current == runtime) {
            return;
        }
        stack.set(ModDataComponents.CELESTWEAVE_ENERGY_BUFFER.get(), runtime);
    }

    /**
     * Drop the runtime cache entry for an armor UUID (e.g. after
     * logout / clone or when the armor is discarded).
     */
    public static void clearRuntime(UUID armorId) {
        if (armorId != null) {
            RUNTIME_ENERGY.remove(armorId);
        }
    }

    public static boolean tryConsume(ItemStack stack, ServerPlayer player, long amount) {
        if (amount <= 0L) {
            return true;
        }
        var registries = player.registryAccess();
        long buffered = read(stack, registries);
        if (buffered >= amount) {
            write(stack, registries, buffered - amount);
            return true;
        }
        return false;
    }

    public static long refillFromNetwork(ItemStack stack, ServerPlayer player, long maxAmount) {
        if (stack == null || stack.isEmpty() || player == null || maxAmount <= 0L) {
            return 0L;
        }
        var registries = player.registryAccess();
        long room = capacity(stack, registries) - read(stack, registries);
        if (room <= 0L) {
            return 0L;
        }
        if (!AppFluxBridge.isAvailable() || AppFluxBridge.FE_KEY == null) {
            return 0L;
        }

        var bound = ArmorNetworkBinding.INSTANCE.resolve(stack, player);
        if (!bound.success()) {
            return 0L;
        }
        IGrid grid = bound.grid();
        if (grid == null) {
            return 0L;
        }
        var storage = grid.getStorageService().getInventory();
        IActionSource source = IActionSource.ofPlayer(player);
        long request = Math.min(room, maxAmount);
        long got = storage.extract(AppFluxBridge.FE_KEY, request, Actionable.MODULATE, source);
        if (got <= 0L) {
            return 0L;
        }
        write(stack, registries, read(stack, registries) + got);
        return got;
    }

    public static int receiveFe(ItemStack stack, int amount, boolean simulate) {
        return receiveFe(stack, null, amount, simulate);
    }

    public static int receiveFe(ItemStack stack, HolderLookup.Provider registries, int amount, boolean simulate) {
        int accepted = ArmorEnergyRules.receivableFe(read(stack, registries), capacity(stack, registries), amount);
        if (!simulate && accepted > 0) {
            write(stack, registries, read(stack, registries) + accepted);
        }
        return accepted;
    }

    public static IEnergyStorage asEnergyStorage(ItemStack stack) {
        return new IEnergyStorage() {
            @Override
            public int receiveEnergy(int maxReceive, boolean simulate) {
                return receiveFe(stack, maxReceive, simulate);
            }

            @Override
            public int extractEnergy(int maxExtract, boolean simulate) {
                return 0;
            }

            @Override
            public int getEnergyStored() {
                return (int) Math.min(Integer.MAX_VALUE, read(stack));
            }

            @Override
            public int getMaxEnergyStored() {
                return (int) Math.min(Integer.MAX_VALUE, capacity(stack));
            }

            @Override
            public boolean canExtract() {
                return false;
            }

            @Override
            public boolean canReceive() {
                return true;
            }
        };
    }
}