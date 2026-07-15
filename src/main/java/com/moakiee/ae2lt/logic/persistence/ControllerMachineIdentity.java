package com.moakiee.ae2lt.logic.persistence;

import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Item-stack codec for the stable UUID of a movable controller. */
public final class ControllerMachineIdentity {
    private static final String TAG_MACHINE_ID = "ae2lt:controller_machine_id";

    private ControllerMachineIdentity() {
    }

    public static UUID read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return read(tag);
    }

    public static void write(ItemStack stack, UUID id) {
        if (stack == null || stack.isEmpty() || id == null) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> write(tag, id));
    }

    static UUID read(CompoundTag tag) {
        return tag != null && tag.hasUUID(TAG_MACHINE_ID) ? tag.getUUID(TAG_MACHINE_ID) : null;
    }

    static void write(CompoundTag tag, UUID id) {
        if (tag != null && id != null) tag.putUUID(TAG_MACHINE_ID, id);
    }
}
