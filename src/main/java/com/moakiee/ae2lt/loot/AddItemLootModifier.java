package com.moakiee.ae2lt.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

/** Generic GLM: appends a fixed ItemStack to the rolled loot when conditions pass. */
public class AddItemLootModifier extends LootModifier {
    public static final Codec<AddItemLootModifier> CODEC = RecordCodecBuilder.create(
            inst -> codecStart(inst)
                    .and(ItemStack.CODEC.fieldOf("item").forGetter(m -> m.item))
                    .apply(inst, AddItemLootModifier::new));

    private final ItemStack item;

    protected AddItemLootModifier(LootItemCondition[] conditions, ItemStack item) {
        super(conditions);
        this.item = item;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        generatedLoot.add(item.copy());
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
