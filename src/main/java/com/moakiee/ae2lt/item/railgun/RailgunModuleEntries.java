package com.moakiee.ae2lt.item.railgun;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.OverloadDeviceModuleItem;
import com.moakiee.ae2lt.celestweave.ArmorEnergyModuleItem;

/**
 * Dynamic per-stack railgun module list. Each entry is one module type, and
 * the stack count is the installed amount of that type.
 */
public record RailgunModuleEntries(List<ItemStack> entries) {
    public static final RailgunModuleEntries EMPTY = new RailgunModuleEntries(List.of());

    public static final Codec<RailgunModuleEntries> CODEC = ItemStack.OPTIONAL_CODEC.listOf()
            .xmap(RailgunModuleEntries::new, RailgunModuleEntries::entries);

    public static final StreamCodec<RegistryFriendlyByteBuf, RailgunModuleEntries> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()),
                    RailgunModuleEntries::entries,
                    RailgunModuleEntries::new);

    public RailgunModuleEntries {
        entries = compact(entries);
    }

    public boolean hasCore() {
        return getCount(RailgunModuleType.CORE) > 0;
    }

    public boolean hasOverloadExecution() {
        return getCount(RailgunModuleType.OVERLOAD_EXECUTION) > 0;
    }

    public int computeCount() {
        return getCount(RailgunModuleType.COMPUTE);
    }

    public int accelerationCount() {
        return getCount(RailgunModuleType.ACCELERATION);
    }

    public int getCount(RailgunModuleType type) {
        return getCount(type.getSerializedName());
    }

    public int getCount(String typeId) {
        int total = 0;
        for (var stack : entries) {
            if (typeId.equals(typeId(stack))) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public ItemStack first(RailgunModuleType type) {
        String typeId = type.getSerializedName();
        for (var stack : entries) {
            if (typeId.equals(typeId(stack))) {
                return stack.copyWithCount(1);
            }
        }
        return ItemStack.EMPTY;
    }

    public List<ItemStack> unitStacks(RailgunModuleType type) {
        String typeId = type.getSerializedName();
        var result = new ArrayList<ItemStack>();
        for (var stack : entries) {
            if (!typeId.equals(typeId(stack))) {
                continue;
            }
            for (int i = 0; i < stack.getCount(); i++) {
                result.add(stack.copyWithCount(1));
            }
        }
        return result;
    }

    public Stream<ItemStack> installedModuleStacks() {
        var result = new ArrayList<ItemStack>();
        for (var stack : entries) {
            for (int i = 0; i < stack.getCount(); i++) {
                result.add(stack.copyWithCount(1));
            }
        }
        return result.stream();
    }

    public List<DeviceCapability> capabilities() {
        List<DeviceCapability> out = new ArrayList<>();
        installedModuleStacks().forEach(stack -> append(out, stack));
        return out;
    }

    public static RailgunModuleEntries fromSlotStacks(List<ItemStack> stacks) {
        return new RailgunModuleEntries(stacks);
    }

    public static String typeId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        if (stack.getItem() instanceof ArmorEnergyModuleItem) {
            return ArmorEnergyModuleItem.MODULE_TYPE_ID;
        }
        if (stack.getItem() instanceof RailgunModuleItem module) {
            return module.moduleType().getSerializedName();
        }
        return "";
    }

    private static List<ItemStack> compact(List<ItemStack> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        Map<String, ItemStack> merged = new LinkedHashMap<>();
        for (var stack : source) {
            String typeId = typeId(stack);
            if (typeId.isBlank()) {
                continue;
            }
            int count = Math.max(stack.getCount(), 1);
            var existing = merged.get(typeId);
            if (existing == null) {
                merged.put(typeId, stack.copyWithCount(count));
            } else {
                existing.grow(count);
            }
        }
        return List.copyOf(merged.values());
    }

    private static void append(List<DeviceCapability> out, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (stack.getItem() instanceof OverloadDeviceModuleItem module) {
            out.addAll(module.capabilities(stack));
        }
    }
}
