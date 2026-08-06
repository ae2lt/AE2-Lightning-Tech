package com.moakiee.ae2lt.menu;

import java.util.ArrayList;
import java.util.List;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.interfaces.IProgressProvider;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.LightningCollectorBlockEntity;
import com.moakiee.ae2lt.item.ElectroChimeCrystalItem;
import com.moakiee.ae2lt.machine.lightningcollector.LightningCollectorInventory;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModItems;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class LightningCollectorMenu extends AEBaseMenu implements IProgressProvider, FrequencyBindingMenu {
    public static final MenuType<LightningCollectorMenu> TYPE = MenuTypeBuilder
            .create(LightningCollectorMenu::new, LightningCollectorBlockEntity.class)
            .withMenuTitle(host -> Component.translatable("block.ae2lt.lightning_collector"))
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "lightning_collector"));

    @GuiSync(30)
    public int catalysisValue;
    @GuiSync(31)
    public int previewHighMin;
    @GuiSync(32)
    public int previewHighMax;
    @GuiSync(33)
    public int previewExtremeMin;
    @GuiSync(34)
    public int previewExtremeMax;

    private final LightningCollectorBlockEntity host;
    private final Slot crystalSlot;

    public LightningCollectorMenu(int id, Inventory playerInventory, LightningCollectorBlockEntity host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;
        this.crystalSlot = addSlot(
                new LargeStackAppEngSlot(host.getInventory(), LightningCollectorInventory.SLOT_CRYSTAL),
                Ae2ltSlotSemantics.LIGHTNING_COLLECTOR_CRYSTAL);
        Ae2ltSlotBackgrounds.withBackground(this.crystalSlot, Ae2ltSlotBackgrounds.ELECTRO_CHIME_CRYSTAL);
        createPlayerInventorySlots(playerInventory);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            catalysisValue = host.getCatalysisValue();
            var highPreview = host.getPreview(LightningKey.Tier.HIGH_VOLTAGE);
            var extremePreview = host.getPreview(LightningKey.Tier.EXTREME_HIGH_VOLTAGE);
            previewHighMin = highPreview.min();
            previewHighMax = highPreview.max();
            previewExtremeMin = extremePreview.min();
            previewExtremeMax = extremePreview.max();
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
            remainder = moveIntoSlots(sourceStack.copy(), List.of(crystalSlot));
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

    public LightningCollectorBlockEntity getHost() {
        return host;
    }

    @Override
    public int getCurrentProgress() {
        ItemStack crystal = host.getInstalledCrystal();
        if (crystal.is(ModItems.PERFECT_ELECTRO_CHIME_CRYSTAL.get())) {
            return getMaxProgress();
        }
        return catalysisValue;
    }

    @Override
    public int getMaxProgress() {
        return Math.max(1, ElectroChimeCrystalItem.getMaxCatalysis());
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
