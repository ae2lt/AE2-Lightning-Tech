package com.moakiee.ae2lt.registry;

import com.mojang.serialization.Codec;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.celestweave.state.CelestweaveModuleContainer;
import com.moakiee.ae2lt.item.railgun.RailgunModuleEntries;
import com.moakiee.ae2lt.item.railgun.RailgunSettings;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom DataComponent types used by AE2LT. Currently focused on carrying
 * machine-specific memory-card configuration that AE2's generic memory-card
 * export (IUpgradeable/IConfigurableObject/IPriorityHost/IConfigInvHost)
 * does not cover — e.g. per-face output toggles, auto-export flags, interface
 * mode switches, etc.
 */
public final class ModDataComponents {
    private ModDataComponents() {}

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, AE2LightningTech.MODID);

    /**
     * A machine-specific configuration blob written by a block entity's
     * {@code exportSettings(MEMORY_CARD, ...)} and read back by
     * {@code importSettings(MEMORY_CARD, ...)}.
     *
     * The schema of the inner CompoundTag is owned by each BE — there's no
     * cross-machine compatibility guarantee. Same-block copy/paste is the
     * only use case we care about.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CustomData>>
            EXPORTED_MACHINE_CONFIG = DATA_COMPONENTS.registerComponentType(
                    "exported_machine_config",
                    builder -> builder
                            .persistent(CustomData.CODEC)
                            .networkSynchronized(CustomData.STREAM_CODEC));

    /** Per-stack railgun module entries. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RailgunModuleEntries>>
            RAILGUN_MODULE_ENTRIES = DATA_COMPONENTS.registerComponentType(
                    "railgun_module_entries",
                    builder -> builder
                            .persistent(RailgunModuleEntries.CODEC)
                            .networkSynchronized(RailgunModuleEntries.STREAM_CODEC));

    /** Structural core installed in an electromagnetic railgun. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemStack>>
            RAILGUN_STRUCTURAL_CORE = DATA_COMPONENTS.registerComponentType(
                    "railgun_structural_core",
                    builder -> builder
                            .persistent(ItemStack.OPTIONAL_CODEC)
                            .networkSynchronized(ItemStack.OPTIONAL_STREAM_CODEC));

    /** Persistent UI toggles for the railgun (terrain destruction, PVP). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RailgunSettings>>
            RAILGUN_SETTINGS = DATA_COMPONENTS.registerComponentType(
                    "railgun_settings",
                    builder -> builder
                            .persistent(RailgunSettings.CODEC)
                            .networkSynchronized(RailgunSettings.STREAM_CODEC));

    /** Server-authoritative charge ticks while the player is holding right-click. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>>
            RAILGUN_CHARGE_TICKS = DATA_COMPONENTS.registerComponentType(
                    "railgun_charge_ticks",
                    builder -> builder
                            .persistent(Codec.LONG)
                            .networkSynchronized(ByteBufCodecs.VAR_LONG));

    /** Per-stack FE energy buffer for v4 overload-device railguns. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>>
            RAILGUN_ENERGY_BUFFER = DATA_COMPONENTS.registerComponentType(
                    "railgun_energy_buffer",
                    builder -> builder
                            .persistent(Codec.LONG)
                            .networkSynchronized(ByteBufCodecs.VAR_LONG));

    /** Structural core installed in one Celestweave armor piece. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemStack>>
            CELESTWEAVE_STRUCTURAL_CORE = DATA_COMPONENTS.registerComponentType(
                    "celestweave_structural_core",
                    builder -> builder
                            .persistent(ItemStack.OPTIONAL_CODEC)
                            .networkSynchronized(ItemStack.OPTIONAL_STREAM_CODEC));

    /** Per-stack FE energy buffer for v4 Celestweave armor pieces. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>>
            CELESTWEAVE_ENERGY_BUFFER = DATA_COMPONENTS.registerComponentType(
                    "celestweave_energy_buffer",
                    builder -> builder
                            .persistent(Codec.LONG)
                            .networkSynchronized(ByteBufCodecs.VAR_LONG));

    /** Installed modules, toggles and per-submodule data for one Celestweave armor piece. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CelestweaveModuleContainer>>
            CELESTWEAVE_MODULES = DATA_COMPONENTS.registerComponentType(
                    "celestweave_modules",
                    builder -> builder
                            .persistent(CelestweaveModuleContainer.CODEC)
                            .networkSynchronized(CelestweaveModuleContainer.STREAM_CODEC)
                            .cacheEncoding());

    /**
     * Server-authoritative "modules are powered this tick" flag. Absent means powered (the common
     * case); only an unpowered piece carries {@code false}. Synced so the client can mirror the
     * server's energy-based forced-off state when deriving submodule activity from the stack.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>>
            CELESTWEAVE_MODULES_POWERED = DATA_COMPONENTS.registerComponentType(
                    "celestweave_modules_powered",
                    builder -> builder
                            .networkSynchronized(ByteBufCodecs.BOOL));
}
