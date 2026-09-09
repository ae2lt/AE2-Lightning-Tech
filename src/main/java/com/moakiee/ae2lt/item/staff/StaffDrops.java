package com.moakiee.ae2lt.item.staff;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;

/** Only operation-owned outputs enter this pipeline; it never searches the world for entities. */
public final class StaffDrops {
    private StaffDrops() {}

    public static boolean active(ItemStack staff) {
        if (!(staff.getItem() instanceof MimicryStaffItem)) return false;
        var settings = StaffState.settings(staff);
        return settings.smelting() && StaffState.has(staff, StaffModule.SMELTING)
                || settings.collection() && StaffState.has(staff, StaffModule.COLLECTION);
    }

    public static List<ItemStack> process(ServerLevel level, Player player, ItemStack staff, ItemStack input) {
        if (input.isEmpty()) return List.of();
        var settings = StaffState.settings(staff);
        ItemStack output = input.copy();
        long count = input.getCount();
        if (settings.smelting() && StaffState.has(staff, StaffModule.SMELTING)) {
            var recipeInput = new SingleRecipeInput(input.copyWithCount(1));
            var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, recipeInput, level);
            if (recipe.isPresent()) {
                var smelted = recipe.get().value().assemble(recipeInput, level.registryAccess());
                if (!smelted.isEmpty()) {
                    output = smelted;
                    count *= smelted.getCount();
                }
            }
        }
        boolean collect = settings.collection() && StaffState.has(staff, StaffModule.COLLECTION);
        var leftovers = new ArrayList<ItemStack>();
        while (count > 0) {
            int amount = (int) Math.min(count, output.getMaxStackSize());
            var part = output.copyWithCount(amount);
            // Inventory.add mutates the remainder, including the partially accepted case.
            if (collect) player.getInventory().add(part);
            if (!part.isEmpty()) leftovers.add(part);
            count -= amount;
        }
        return leftovers;
    }
}
