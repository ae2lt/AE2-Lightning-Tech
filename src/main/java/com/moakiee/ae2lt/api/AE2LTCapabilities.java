package com.moakiee.ae2lt.api;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

import com.moakiee.ae2lt.api.lightning.ILightningEnergyHandler;

/**
 * Public AE2 Lightning Tech capabilities, modeled on Forge 1.20.1's
 * capability system.
 *
 * <p>Forge 1.20.1 keys capabilities by interface token rather than by
 * {@link net.minecraft.resources.ResourceLocation}. To keep the addon-facing API
 * source-compatible with the newer branch, block and item access keep separate
 * field names while aliasing the same underlying Forge capability token.
 *
 * <p>The historical API names remain:
 * <ul>
 *   <li>{@code ae2lt:lightning_energy} for {@link #LIGHTNING_ENERGY_BLOCK}</li>
 *   <li>{@code ae2lt:lightning_energy_item} for {@link #LIGHTNING_ENERGY_ITEM}</li>
 * </ul>
 */
public final class AE2LTCapabilities {

    /**
     * Block-side capability for reading or writing lightning energy on a block
     * entity. Direction context is still supplied through Forge's normal
     * {@code getCapability(cap, side)} calls.
     */
    public static final Capability<ILightningEnergyHandler> LIGHTNING_ENERGY_BLOCK =
            CapabilityManager.get(new CapabilityToken<>() {
            });

    /**
     * Item-side capability for reading or writing lightning energy on an
     * {@link net.minecraft.world.item.ItemStack}. Reserved: this mod does not
     * currently expose any items as providers, but addons may still register
     * implementations against their own items.
     */
    public static final Capability<ILightningEnergyHandler> LIGHTNING_ENERGY_ITEM =
            LIGHTNING_ENERGY_BLOCK;

    private AE2LTCapabilities() {
    }
}

