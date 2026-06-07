package com.moakiee.ae2lt.item;

import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

final class CuriosFrequencyCardFinder {
    static final String SLOT_ID = "overloaded_frequency_card";

    private CuriosFrequencyCardFinder() {
    }

    static List<ItemStack> findFrequencyCards(Player player) {
        if (!ModList.get().isLoaded("curios")) {
            return List.of();
        }
        return Bridge.findFrequencyCards(player);
    }

    /**
     * Returns every item equipped in any Curios slot, so callers can look for
     * wireless terminals (which may be worn in arbitrary slots) holding a
     * frequency card upgrade. Returns an empty list when Curios is absent.
     */
    static List<ItemStack> findAllEquippedStacks(Player player) {
        if (!ModList.get().isLoaded("curios")) {
            return List.of();
        }
        return Bridge.findAllEquippedStacks(player);
    }

    private static final class Bridge {
        private Bridge() {
        }

        static List<ItemStack> findFrequencyCards(Player player) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                    .map(handler -> handler.findCurios(SLOT_ID).stream()
                            .map(top.theillusivec4.curios.api.SlotResult::stack)
                            .filter(stack -> stack.getItem() instanceof OverloadedFrequencyCardItem)
                            .toList())
                    .orElse(List.of());
        }

        static List<ItemStack> findAllEquippedStacks(Player player) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                    .map(handler -> {
                        var equipped = handler.getEquippedCurios();
                        List<ItemStack> stacks = new java.util.ArrayList<>(equipped.getSlots());
                        for (int slot = 0; slot < equipped.getSlots(); slot++) {
                            stacks.add(equipped.getStackInSlot(slot));
                        }
                        return stacks;
                    })
                    .orElse(List.of());
        }
    }
}
