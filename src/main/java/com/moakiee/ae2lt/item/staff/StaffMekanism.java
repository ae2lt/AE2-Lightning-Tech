package com.moakiee.ae2lt.item.staff;

import mekanism.common.item.ItemConfigurator.ConfiguratorMode;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.common.registries.MekanismItems;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;

/** Loaded only after ModList confirms Mekanism is present. */
public final class StaffMekanism {
    private StaffMekanism() {}

    public static InteractionResult useOn(UseOnContext context) {
        var stack = context.getItemInHand();
        ConfiguratorMode mode = switch (StaffState.settings(stack).mekanism()) {
            case 1 -> ConfiguratorMode.CONFIGURATE_FLUIDS;
            case 2 -> ConfiguratorMode.CONFIGURATE_CHEMICALS;
            case 3 -> ConfiguratorMode.CONFIGURATE_ENERGY;
            case 4 -> ConfiguratorMode.CONFIGURATE_HEAT;
            case 5 -> ConfiguratorMode.ROTATE;
            case 6 -> ConfiguratorMode.WRENCH;
            default -> ConfiguratorMode.CONFIGURATE_ITEMS;
        };
        var component = MekanismDataComponents.CONFIGURATOR_MODE.get();
        var previous = stack.get(component);
        try {
            // Use the native configurator callback with the actual player/tool context. This
            // preserves side configuration, IConfigurable, security and native feedback.
            stack.set(component, mode);
            return MekanismItems.CONFIGURATOR.get().useOn(context);
        } finally {
            if (previous == null) stack.remove(component);
            else stack.set(component, previous);
        }
    }
}
