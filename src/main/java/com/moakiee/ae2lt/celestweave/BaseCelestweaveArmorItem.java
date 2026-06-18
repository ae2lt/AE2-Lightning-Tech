package com.moakiee.ae2lt.celestweave;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;

import com.moakiee.ae2lt.device.DeviceItem;
import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.celestweave.service.ArmorTickService;
import com.moakiee.ae2lt.util.EnergyText;

public abstract class BaseCelestweaveArmorItem extends ArmorItem implements DeviceItem {
    private final ArmorPart armorPart;

    protected BaseCelestweaveArmorItem(ArmorPart armorPart, Properties properties) {
        super(
                CelestweaveArmorMaterials.CELESTWEAVE,
                armorType(armorPart),
                properties.stacksTo(1).fireResistant().setNoRepair());
        this.armorPart = armorPart;
    }

    public ArmorPart armorPart() {
        return armorPart;
    }

    @Override
    public DeviceKind deviceKind() {
        return armorPart.deviceKind();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (!(entity instanceof Player player)) {
            return;
        }
        CelestweaveArmorState.ensureArmorId(stack);
        if (level.isClientSide()) {
            return;
        }
        boolean equipped = player.getItemBySlot(equipmentSlot(armorPart)) == stack;
        ArmorTickService.tickEquipped(player, stack, equipped, player.level().registryAccess(), resolveDist(level));
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltip,
            TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltip, tooltipFlag);
        var level = context.level();
        long current = ArmorEnergyBuffer.read(stack, level == null ? null : level.registryAccess());
        long capacity = ArmorEnergyBuffer.capacity(stack, level == null ? null : level.registryAccess());
        tooltip.add(EnergyText.storedFe(current, capacity));
        tooltip.add(Component.translatable("ae2lt.celestweave.tooltip.workbench"));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        long capacity = ArmorEnergyBuffer.capacity(stack);
        if (capacity <= 0L) {
            return 0;
        }
        double filled = (double) ArmorEnergyBuffer.read(stack) / (double) capacity;
        return Mth.clamp((int) Math.round(filled * 13), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(1 / 3.0F, 1.0F, 1.0F);
    }

    private static Dist resolveDist(Level level) {
        return level.isClientSide() ? Dist.CLIENT : Dist.DEDICATED_SERVER;
    }

    private static EquipmentSlot equipmentSlot(ArmorPart part) {
        return switch (part) {
            case HEAD -> EquipmentSlot.HEAD;
            case CHEST -> EquipmentSlot.CHEST;
            case LEGS -> EquipmentSlot.LEGS;
            case FEET -> EquipmentSlot.FEET;
        };
    }

    private static ArmorItem.Type armorType(ArmorPart part) {
        return switch (part) {
            case HEAD -> ArmorItem.Type.HELMET;
            case CHEST -> ArmorItem.Type.CHESTPLATE;
            case LEGS -> ArmorItem.Type.LEGGINGS;
            case FEET -> ArmorItem.Type.BOOTS;
        };
    }
}
