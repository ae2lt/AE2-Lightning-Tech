package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.component.CustomData;
import net.minecraftforge.registries.DeferredHolder;
import net.minecraftforge.registries.DeferredRegister;

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
}

