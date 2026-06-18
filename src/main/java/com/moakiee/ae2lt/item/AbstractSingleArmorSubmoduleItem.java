package com.moakiee.ae2lt.item;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.ModuleTooltip;
import com.moakiee.ae2lt.celestweave.ArmorPart;
import com.moakiee.ae2lt.celestweave.module.CelestweaveArmorSubmodule;
import com.moakiee.ae2lt.celestweave.module.CelestweaveArmorSubmoduleItem;

public abstract class AbstractSingleArmorSubmoduleItem extends Item implements CelestweaveArmorSubmoduleItem {
    private final ArmorPart armorPart;
    private final CelestweaveArmorSubmodule submodule;
    private final Function<ItemStack, List<DeviceCapability>> capabilityFactory;

    protected AbstractSingleArmorSubmoduleItem(
            Properties properties,
            ArmorPart armorPart,
            CelestweaveArmorSubmodule submodule,
            Function<ItemStack, List<DeviceCapability>> capabilityFactory) {
        // Unified module stack size; submodules are stateless (config lives on the armor).
        super(properties.stacksTo(16));
        this.armorPart = armorPart;
        this.submodule = submodule;
        this.capabilityFactory = capabilityFactory;
    }

    @Override
    public ArmorPart armorPart() {
        return armorPart;
    }

    @Override
    public void collectSubmodules(ItemStack stack, Consumer<CelestweaveArmorSubmodule> output) {
        output.accept(submodule);
    }

    @Override
    public List<DeviceCapability> capabilities(ItemStack stack) {
        return capabilityFactory.apply(stack.copyWithCount(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ModuleTooltip.appendInstallInfo(this, tooltip);
    }
}
