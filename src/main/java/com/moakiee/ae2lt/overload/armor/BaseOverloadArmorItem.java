package com.moakiee.ae2lt.overload.armor;

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
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;

import com.moakiee.ae2lt.device.DeviceItem;
import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.OverloadDeviceModuleItem;
import com.moakiee.ae2lt.util.EnergyText;

import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public abstract class BaseOverloadArmorItem extends ArmorItem implements ICurioItem, DeviceItem {
    private final ArmorPart armorPart;

    protected BaseOverloadArmorItem(ArmorPart armorPart, Properties properties) {
        super(ArmorMaterials.NETHERITE, armorType(armorPart), properties.stacksTo(1).fireResistant());
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
        OverloadArmorState.ensureArmorId(stack);
        if (level.isClientSide()) {
            return;
        }
        boolean equipped = player.getItemBySlot(equipmentSlot(armorPart)) == stack;
        tickServer(player, stack, equipped, resolveDist(level));
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) {
            return;
        }
        OverloadArmorState.ensureArmorId(stack);
        if (player.level().isClientSide()) {
            return;
        }
        tickServer(player, stack, true, resolveDist(player.level()));
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        if (slotContext.entity() instanceof Player player && !player.level().isClientSide()) {
            OverloadArmorState.ensureArmorId(stack);
            OverloadArmorState.reconcileInstalledSubmodules(
                    player, stack, player.level().registryAccess(), resolveDist(player.level()));
            OverloadArmorState.syncSubmoduleActiveState(
                    player, stack, player.level().registryAccess(), true, resolveDist(player.level()));
        }
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (slotContext.entity() instanceof Player player && !player.level().isClientSide()) {
            OverloadArmorState.syncSubmoduleActiveState(
                    player, stack, player.level().registryAccess(), false, resolveDist(player.level()));
        }
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltip,
            TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltip, tooltipFlag);
        long current = ArmorEnergyBuffer.read(stack);
        long capacity = ArmorEnergyBuffer.capacity(stack);
        tooltip.add(EnergyText.storedFe(current, capacity));

        var level = context.level();
        if (level != null) {
            var snapshot = OverloadArmorState.snapshot(stack, level.registryAccess(), false);
            tooltip.add(Component.translatable(
                    "ae2lt.overload_armor.tooltip.overload",
                    snapshot.currentLoad(),
                    snapshot.baseOverload()));
            if (snapshot.locked()) {
                tooltip.add(Component.translatable(
                        "ae2lt.overload_armor.tooltip.locked",
                        snapshot.lockedTicks() / 20));
            }
        }
        tooltip.add(Component.translatable("ae2lt.overload_armor.tooltip.workbench"));
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

    private static void tickServer(Player player, ItemStack stack, boolean equipped, Dist dist) {
        var registries = player.level().registryAccess();
        OverloadArmorState.syncSubmoduleActiveState(player, stack, registries, equipped, dist);
        if (!equipped) {
            return;
        }
        if (player instanceof ServerPlayer serverPlayer && !passiveDrain(serverPlayer, stack)) {
            OverloadArmorState.syncSubmoduleActiveState(player, stack, registries, false, dist);
            return;
        }
        OverloadArmorState.tickActiveSubmodules(player, stack, registries, dist);
        OverloadArmorState.tickEquipped(player, stack, registries);
    }

    private static boolean passiveDrain(ServerPlayer player, ItemStack armor) {
        long drain = 0L;
        double multiplier = 1.0D;
        for (ItemStack module : OverloadArmorState.loadModuleStacks(armor, player.level().registryAccess())) {
            if (!(module.getItem() instanceof OverloadDeviceModuleItem provider)) {
                continue;
            }
            for (var capability : provider.capabilities(module)) {
                if (capability instanceof DeviceCapability.PassiveDrain passiveDrain) {
                    drain += Math.max(0L, passiveDrain.fePerTick()) * Math.max(1, module.getCount());
                } else if (capability instanceof DeviceCapability.EnergyEfficiency efficiency) {
                    multiplier *= Math.max(0.0D, efficiency.drainMul());
                }
            }
        }
        long adjusted = (long) Math.ceil(drain * multiplier);
        return adjusted <= 0L || ArmorEnergyBuffer.tryConsume(armor, player, adjusted);
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
