package com.moakiee.ae2lt.event;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.registry.ModFumos;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class PigmeeFumoGiftHandler {
    private static final String GIFTED_TAG = "ae2lt.pigmee_fumo_gifted";
    private static final int HOTBAR_START = 0;
    private static final int HOTBAR_END_EXCLUSIVE = 9;

    private PigmeeFumoGiftHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof FakePlayer
                || !AE2LTCommonConfig.pigmeeFumoGiftOnFirstJoin()) {
            return;
        }

        var player = event.getEntity();
        var data = player.getPersistentData();
        if (data.getBoolean(GIFTED_TAG)) {
            return;
        }

        ItemStack gift = new ItemStack(ModFumos.PIGMEE_FUMO_ITEM.get());
        insertGift(player.getInventory(), gift);
        if (!gift.isEmpty()) {
            dropOverflow(player, gift);
        }
        player.getInventory().setChanged();
        data.putBoolean(GIFTED_TAG, true);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getOriginal().getPersistentData().getBoolean(GIFTED_TAG)) {
            event.getEntity().getPersistentData().putBoolean(GIFTED_TAG, true);
        }
    }

    private static void insertGift(Inventory inventory, ItemStack gift) {
        tryInsertIntoSlots(inventory, gift, HOTBAR_START, HOTBAR_END_EXCLUSIVE);
        if (!gift.isEmpty()) {
            tryInsertIntoSlots(inventory, gift, HOTBAR_END_EXCLUSIVE, inventory.items.size());
        }
    }

    private static void dropOverflow(Player player, ItemStack gift) {
        ItemStack dropStack = gift.copy();
        gift.setCount(0);
        var dropped = player.drop(dropStack, false, false);
        if (dropped != null) {
            dropped.setNoPickUpDelay();
            dropped.setUnlimitedLifetime();
            player.level().addFreshEntity(dropped);
        }
    }

    private static void tryInsertIntoSlots(Inventory inventory, ItemStack gift, int start, int endExclusive) {
        for (int slot = start; slot < endExclusive && !gift.isEmpty(); slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (canMerge(stack, gift)) {
                int amount = Math.min(gift.getCount(), stack.getMaxStackSize() - stack.getCount());
                stack.grow(amount);
                gift.shrink(amount);
            }
        }

        for (int slot = start; slot < endExclusive && !gift.isEmpty(); slot++) {
            if (inventory.items.get(slot).isEmpty()) {
                inventory.items.set(slot, gift.copy());
                gift.setCount(0);
            }
        }
    }

    private static boolean canMerge(ItemStack stack, ItemStack gift) {
        return !stack.isEmpty()
                && ItemStack.isSameItemSameTags(stack, gift)
                && stack.getCount() < stack.getMaxStackSize();
    }
}

