package com.moakiee.ae2lt.me.cell;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import com.moakiee.ae2lt.util.ItemStackTagSupport;

/**
 * Native 1.20.1 ItemStack-NBT persistence for the void cell.
 *
 * <p>This deliberately replaces the 1.21 data components used by ExtendedAE.
 * The data is namespaced and versioned so future migrations stay local to this
 * item instead of adding a cross-version component emulation layer.</p>
 */
public final class VoidCellData {
    static final String ROOT_TAG = "ae2lt:void_cell";
    private static final String TAG_VERSION = "version";
    private static final String TAG_MODE = "mode";
    private static final String TAG_ENERGY = "energy";
    private static final String TAG_INVENTORY = "inventory";
    private static final int VERSION = 1;

    private VoidCellData() {
    }

    public static State read(ItemStack stack) {
        CompoundTag data = getDataTag(stack);
        VoidCellMode mode = VoidCellMode.fromSerializedName(data.getString(TAG_MODE));
        double energy = Math.max(0.0, data.getDouble(TAG_ENERGY));
        var inventory = new Object2LongOpenHashMap<AEKey>();

        ListTag entries = data.getList(TAG_INVENTORY, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            GenericStack genericStack = GenericStack.readTag(entries.getCompound(i));
            if (genericStack != null && genericStack.amount() > 0) {
                inventory.addTo(genericStack.what(), genericStack.amount());
            }
        }
        return new State(mode, energy, inventory);
    }

    public static VoidCellMode readMode(ItemStack stack) {
        return VoidCellMode.fromSerializedName(getDataTag(stack).getString(TAG_MODE));
    }

    public static void writeMode(ItemStack stack, VoidCellMode mode) {
        ItemStackTagSupport.updateTag(stack, root -> {
            CompoundTag data = root.contains(ROOT_TAG, Tag.TAG_COMPOUND)
                    ? root.getCompound(ROOT_TAG)
                    : new CompoundTag();
            if (mode == VoidCellMode.TRASH) {
                data.remove(TAG_MODE);
            } else {
                data.putString(TAG_MODE, mode.getSerializedName());
            }
            attachOrRemove(root, data);
        });
    }

    public static void write(ItemStack stack, VoidCellMode mode, double energy,
                             Object2LongMap<AEKey> inventory) {
        ItemStackTagSupport.updateTag(stack, root -> {
            CompoundTag data = new CompoundTag();
            if (mode != VoidCellMode.TRASH) {
                data.putString(TAG_MODE, mode.getSerializedName());
            }
            if (energy > 0.0) {
                data.putDouble(TAG_ENERGY, energy);
            }

            ListTag entries = new ListTag();
            for (var entry : inventory.object2LongEntrySet()) {
                if (entry.getLongValue() > 0) {
                    entries.add(GenericStack.writeTag(
                            new GenericStack(entry.getKey(), entry.getLongValue())));
                }
            }
            if (!entries.isEmpty()) {
                data.put(TAG_INVENTORY, entries);
            }
            attachOrRemove(root, data);
        });
    }

    private static CompoundTag getDataTag(ItemStack stack) {
        CompoundTag root = stack.getTag();
        return root != null && root.contains(ROOT_TAG, Tag.TAG_COMPOUND)
                ? root.getCompound(ROOT_TAG)
                : new CompoundTag();
    }

    private static void attachOrRemove(CompoundTag root, CompoundTag data) {
        data.remove(TAG_VERSION);
        if (data.isEmpty()) {
            root.remove(ROOT_TAG);
        } else {
            data.putInt(TAG_VERSION, VERSION);
            root.put(ROOT_TAG, data);
        }
    }

    public record State(VoidCellMode mode, double energy, Object2LongOpenHashMap<AEKey> inventory) {
    }
}
