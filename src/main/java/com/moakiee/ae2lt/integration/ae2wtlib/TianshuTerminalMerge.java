package com.moakiee.ae2lt.integration.ae2wtlib;

import appeng.api.ids.AEComponents;
import com.mojang.datafixers.util.Unit;
import de.mari_023.ae2wtlib.api.AE2wtlibComponents;
import de.mari_023.ae2wtlib.api.registration.WTDefinition;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import de.mari_023.ae2wtlib.api.terminal.ItemWUT;
import java.util.Objects;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/** Lossless planning on copies: no inputs are changed when a merge is rejected. */
public final class TianshuTerminalMerge {
    private TianshuTerminalMerge() {}

    public static boolean applies(ItemStack target, ItemStack source, WTDefinition definition) {
        return definition.terminalName().equals(Ae2wtlibIntegration.TIANSHU_CRAFTING_NAME)
                || TianshuWctIntegration.hasTianshuCrafting(target) || TianshuWctIntegration.hasTianshuCrafting(source);
    }

    public static ItemStack merge(ItemStack target, ItemStack source, WTDefinition definition) {
        if (!(target.getItem() instanceof ItemWUT universal) || !(source.getItem() instanceof ItemWT terminal)) return ItemStack.EMPTY;
        var result = target.copy();
        result.set(definition.componentType(), Unit.INSTANCE);
        for (var entry : source.getComponentsPatch().entrySet()) {
            var type = entry.getKey();
            if (type == AEComponents.UPGRADES || type == AEComponents.STORED_ENERGY || type == AEComponents.ENERGY_CAPACITY) continue;
            // A removed/default component in one input must not erase the other input's data.
            if (entry.getValue().isEmpty()) continue;
            if (type == AE2wtlibComponents.CURRENT_TERMINAL && result.has(type)) continue;
            var incoming = entry.getValue().get();
            var existing = result.get(type);
            if (incoming instanceof ItemContainerContents inventory) {
                boolean nonempty = inventory.nonEmptyItems().iterator().hasNext();
                if (!nonempty) continue;
                if (existing instanceof ItemContainerContents other && other.nonEmptyItems().iterator().hasNext()) return ItemStack.EMPTY;
            } else if (incoming instanceof ItemStack stack) {
                if (stack.isEmpty()) continue;
                if (existing instanceof ItemStack other && !other.isEmpty()) return ItemStack.EMPTY;
            } else if (type == AE2wtlibComponents.PATTERN_ENCODING_LOGIC && incoming instanceof CompoundTag tag) {
                if (existing instanceof CompoundTag other && containsRealPatterns(tag) && containsRealPatterns(other)) return ItemStack.EMPTY;
                if (existing != null && !Objects.equals(existing, incoming)) return ItemStack.EMPTY;
            } else if (existing != null && !Objects.equals(existing, incoming)) {
                // Links/settings cannot both be retained when they disagree. Require the player to resolve the conflict.
                return ItemStack.EMPTY;
            }
            setComponent(result, type, incoming);
        }
        var upgrades = universal.getUpgrades(result);
        for (var card : terminal.getUpgrades(source.copy())) {
            if (!upgrades.addItems(card.copy()).isEmpty()) return ItemStack.EMPTY;
        }
        universal.onUpgradesChanged(result, upgrades);
        double energy = universal.getAECurrentPower(target) + terminal.getAECurrentPower(source);
        if (!Double.isFinite(energy) || energy < 0 || energy > universal.getAEMaxPower(result)) return ItemStack.EMPTY;
        result.set(AEComponents.STORED_ENERGY, energy);
        return result;
    }

    private static boolean containsRealPatterns(CompoundTag tag) {
        return !tag.getList("blankPattern", Tag.TAG_COMPOUND).isEmpty()
                || !tag.getList("encodedPattern", Tag.TAG_COMPOUND).isEmpty();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setComponent(ItemStack stack, DataComponentType type, Object value) { stack.set(type, value); }
}
