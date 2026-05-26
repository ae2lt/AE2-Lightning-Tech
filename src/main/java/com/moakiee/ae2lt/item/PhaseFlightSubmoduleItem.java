package com.moakiee.ae2lt.item;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.capability.FlightKind;
import com.moakiee.ae2lt.device.module.ModuleTooltip;
import com.moakiee.ae2lt.overload.armor.ArmorOverloadRules;
import com.moakiee.ae2lt.overload.armor.ArmorPart;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmodule;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmoduleItem;
import com.moakiee.ae2lt.overload.armor.module.PhaseFlightSubmodule;

public final class PhaseFlightSubmoduleItem extends Item implements OverloadArmorSubmoduleItem {

    private static final PhaseFlightSubmodule INSTANCE = PhaseFlightSubmodule.INSTANCE;

    public PhaseFlightSubmoduleItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void collectSubmodules(ItemStack stack, Consumer<OverloadArmorSubmodule> output) {
        output.accept(INSTANCE);
    }

    @Override
    public ArmorPart armorPart() {
        return ArmorPart.LEGS;
    }

    @Override
    public List<DeviceCapability> capabilities(ItemStack stack) {
        return List.of(
                new DeviceCapability.FlightMode(FlightKind.PHASE),
                new DeviceCapability.PassiveDrain(ArmorOverloadRules.PHASE_FLIGHT_PASSIVE_DRAIN_FE));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ae2lt.module_phase_flight.tooltip")
                .withStyle(ChatFormatting.GRAY));
        ModuleTooltip.appendInstallInfo(this, tooltip);
    }
}
