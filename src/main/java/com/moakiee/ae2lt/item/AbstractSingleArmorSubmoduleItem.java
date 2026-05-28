package com.moakiee.ae2lt.item;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.ModuleTooltip;
import com.moakiee.ae2lt.overload.armor.ArmorPart;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmodule;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmoduleItem;

public abstract class AbstractSingleArmorSubmoduleItem extends Item implements OverloadArmorSubmoduleItem {
    private final ArmorPart armorPart;
    private final OverloadArmorSubmodule submodule;
    private final String tooltipKey;
    private final Function<ItemStack, List<DeviceCapability>> capabilityFactory;

    protected AbstractSingleArmorSubmoduleItem(
            Properties properties,
            ArmorPart armorPart,
            OverloadArmorSubmodule submodule,
            String tooltipKey,
            Function<ItemStack, List<DeviceCapability>> capabilityFactory) {
        super(properties);
        this.armorPart = armorPart;
        this.submodule = submodule;
        this.tooltipKey = tooltipKey;
        this.capabilityFactory = capabilityFactory;
    }

    @Override
    public ArmorPart armorPart() {
        return armorPart;
    }

    @Override
    public void collectSubmodules(ItemStack stack, Consumer<OverloadArmorSubmodule> output) {
        output.accept(submodule);
    }

    @Override
    public List<DeviceCapability> capabilities(ItemStack stack) {
        return capabilityFactory.apply(stack.copyWithCount(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
        ModuleTooltip.appendInstallInfo(this, tooltip);
    }
}
