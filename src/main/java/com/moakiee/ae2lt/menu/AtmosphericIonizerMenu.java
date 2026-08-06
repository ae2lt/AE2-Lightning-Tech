package com.moakiee.ae2lt.menu;

import java.util.ArrayList;
import java.util.List;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.AtmosphericIonizerBlockEntity;
import com.moakiee.ae2lt.item.WeatherCondensateItem;
import com.moakiee.ae2lt.machine.atmosphericionizer.AtmosphericIonizerInventory;
import com.moakiee.ae2lt.machine.atmosphericionizer.AtmosphericIonizerStatus;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class AtmosphericIonizerMenu extends AEBaseMenu implements FrequencyBindingMenu {
    public static final MenuType<AtmosphericIonizerMenu> TYPE = MenuTypeBuilder
            .create(AtmosphericIonizerMenu::new, AtmosphericIonizerBlockEntity.class)
            .withMenuTitle(host -> Component.translatable("block.ae2lt.atmospheric_ionizer"))
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "atmospheric_ionizer"));

    @GuiSync(50)
    public long consumedEnergy;
    @GuiSync(51)
    public long totalEnergy;
    @GuiSync(52)
    public int statusOrdinal;
    @GuiSync(53)
    public int typeOrdinal;

    private final AtmosphericIonizerBlockEntity host;
    private final Slot condensateSlot;

    public AtmosphericIonizerMenu(int id, Inventory playerInventory, AtmosphericIonizerBlockEntity host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;
        this.condensateSlot = addSlot(
                new LargeStackAppEngSlot(host.getInventory(), AtmosphericIonizerInventory.SLOT_CONDENSATE),
                Ae2ltSlotSemantics.ATMOSPHERIC_IONIZER_CONDENSATE);
        createPlayerInventorySlots(playerInventory);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            consumedEnergy = host.getConsumedEnergy();
            totalEnergy = host.getTotalEnergy();
            statusOrdinal = host.getStatus().ordinal();
            WeatherCondensateItem.Type selectedType = host.getSelectedType();
            typeOrdinal = selectedType == null ? -1 : selectedType.ordinal();
        }
        super.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (isClientSide() || index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot sourceSlot = getSlot(index);
        if (!sourceSlot.hasItem() || !sourceSlot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }

        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack original = sourceStack.copy();
        ItemStack remainder;

        if (isPlayerSideSlot(sourceSlot)) {
            remainder = moveIntoSlots(sourceStack.copy(), List.of(condensateSlot));
        } else {
            remainder = moveIntoSlots(sourceStack.copy(), getPlayerDestinationSlots());
        }

        int moved = original.getCount() - remainder.getCount();
        if (moved <= 0) {
            return ItemStack.EMPTY;
        }

        sourceSlot.remove(moved);
        sourceSlot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        if (host.isRemoved() || host.getLevel() == null) {
            return false;
        }

        return host.getLevel().getBlockEntity(host.getBlockPos()) == host
                && player.level() == host.getLevel()
                && player.distanceToSqr(
                        host.getBlockPos().getX() + 0.5D,
                        host.getBlockPos().getY() + 0.5D,
                        host.getBlockPos().getZ() + 0.5D) <= 64.0D;
    }

    public long getConsumedEnergy() {
        return consumedEnergy;
    }

    public long getTotalEnergyRequired() {
        return totalEnergy;
    }

    public double getProgress() {
        if (totalEnergy <= 0L) {
            return 0.0D;
        }
        return Math.min(1.0D, (double) consumedEnergy / (double) totalEnergy);
    }

    public AtmosphericIonizerStatus getStatus() {
        return AtmosphericIonizerStatus.fromOrdinal(statusOrdinal);
    }

    public WeatherCondensateItem.Type getSelectedCondensateType() {
        return typeOrdinal < 0 ? null : WeatherCondensateItem.Type.fromOrdinal(typeOrdinal);
    }

    public Component getStatusMessage() {
        return Component.translatable(
                "ae2lt.gui.atmospheric_ionizer.status.label",
                Component.translatable(getStatus().translationKey()));
    }

    public Component getTargetWeatherMessage() {
        WeatherCondensateItem.Type type = getSelectedCondensateType();
        Component weatherName = type == null
                ? Component.translatable("ae2lt.gui.atmospheric_ionizer.target.none")
                : type.getWeatherName();
        return Component.translatable("ae2lt.gui.atmospheric_ionizer.target_weather", weatherName);
    }

    public Component getEnergyDemandMessage() {
        WeatherCondensateItem.Type type = getSelectedCondensateType();
        return Component.translatable(
                "ae2lt.gui.atmospheric_ionizer.energy_need",
                type == null ? 0L : type.totalEnergy());
    }

    private List<Slot> getPlayerDestinationSlots() {
        var result = new ArrayList<Slot>(getSlots(SlotSemantics.PLAYER_INVENTORY));
        result.addAll(getSlots(SlotSemantics.PLAYER_HOTBAR));
        return result;
    }

    private static ItemStack moveIntoSlots(ItemStack stack, List<Slot> destinations) {
        ItemStack remainder = stack;

        for (Slot slot : destinations) {
            if (!slot.hasItem()) {
                continue;
            }
            remainder = slot.safeInsert(remainder);
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }

        for (Slot slot : destinations) {
            if (slot.hasItem()) {
                continue;
            }
            remainder = slot.safeInsert(remainder);
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }

        return remainder;
    }
}
