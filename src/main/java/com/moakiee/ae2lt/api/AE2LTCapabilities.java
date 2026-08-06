package com.moakiee.ae2lt.api;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.ItemCapability;

import com.moakiee.ae2lt.api.ids.AE2LTBlockEntityIds;
import com.moakiee.ae2lt.api.lightning.ILightningEnergyHandler;

/**
 * Public AE2 Lightning Tech capabilities, modeled on
 * {@code appeng.api.AECapabilities}.
 *
 * <p>The {@link ResourceLocation}s used to register these capabilities are part of
 * the frozen API contract:
 * <ul>
 *   <li>{@code ae2lt:lightning_energy} for {@link #LIGHTNING_ENERGY_BLOCK}</li>
 *   <li>{@code ae2lt:lightning_energy_item} for {@link #LIGHTNING_ENERGY_ITEM}</li>
 * </ul>
 *
 * <p>Note these IDs use this mod's own namespace ({@code ae2lt}). They are
 * deliberately not the same as any third-party bridging library's IDs; addons that
 * want to use this mod's first-party API must query these capabilities, not the
 * library's.
 */
public final class AE2LTCapabilities {

    /**
     * Block-side capability for reading or writing lightning energy on a block. The
     * direction context follows NeoForge convention: {@code null} represents an
     * "internal / all-sides" query.
     */
    public static final BlockCapability<ILightningEnergyHandler, @Nullable Direction>
            LIGHTNING_ENERGY_BLOCK = BlockCapability.createSided(
                    ResourceLocation.fromNamespaceAndPath(
                            AE2LTBlockEntityIds.MOD_ID, "lightning_energy"),
                    ILightningEnergyHandler.class);

    /**
     * Item-side capability for reading or writing lightning energy on an
     * {@link net.minecraft.world.item.ItemStack}. Reserved: this mod does not
     * currently expose any items as providers, but addons may still register
     * implementations against their own items.
     */
    public static final ItemCapability<ILightningEnergyHandler, Void>
            LIGHTNING_ENERGY_ITEM = ItemCapability.createVoid(
                    ResourceLocation.fromNamespaceAndPath(
                            AE2LTBlockEntityIds.MOD_ID, "lightning_energy_item"),
                    ILightningEnergyHandler.class);

    private AE2LTCapabilities() {
    }
}
