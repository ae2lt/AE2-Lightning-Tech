package com.moakiee.ae2lt.logic;

import java.util.EnumSet;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.component.CustomData;

import appeng.api.orientation.RelativeSide;
import appeng.util.SettingsFrom;

import com.moakiee.ae2lt.registry.ModDataComponents;

/**
 * Shared helpers for packing/unpacking custom machine configuration into
 * {@link ModDataComponents#EXPORTED_MACHINE_CONFIG}. AE2's generic memory card
 * export only walks {@code IUpgradeableObject / IConfigurableObject /
 * IPriorityHost / IConfigInvHost}; fields living directly on our BEs (auto
 * export flags, per-face output enables, interface mode, etc.) stay invisible
 * unless we export them ourselves.
 *
 * Same-block copy/paste is the only supported case: on paste we rely on
 * AE2's {@code this.getName().equals(savedName)} check to route back to the
 * machine's own {@code importSettings}, which then calls {@link #readCustomTag}
 * to hydrate its fields.
 */
public final class MemoryCardConfigSupport {
    private static final String TAG_AUTO_EXPORT = "AutoExport";
    private static final String TAG_ALLOWED_OUTPUTS = "AllowedOutputs";

    private MemoryCardConfigSupport() {}

    /**
     * Store {@code tag} on the memory card. No-op when the tag is empty to
     * avoid polluting the card with trivia and so vanilla "was anything
     * actually saved?" heuristics stay correct for empty configs.
     */
    public static void writeCustomTag(DataComponentMap.Builder builder, CompoundTag tag) {
        if (tag.isEmpty()) {
            return;
        }
        builder.set(ModDataComponents.EXPORTED_MACHINE_CONFIG.get(), CustomData.of(tag));
    }

    /**
     * Retrieve the machine-config tag previously stored via {@link #writeCustomTag}.
     * Returns {@code null} if no such component exists on the card.
     */
    @Nullable
    public static CompoundTag readCustomTag(DataComponentMap input) {
        var data = input.get(ModDataComponents.EXPORTED_MACHINE_CONFIG.get());
        if (data == null) {
            return null;
        }
        return data.copyTag();
    }

    /**
     * AE2 calls machine hooks for multiple settings sources. This helper keeps
     * our custom machine-data export scoped to memory cards and centralizes the
     * empty-tag behavior.
     */
    public static void exportMemoryCardSettings(SettingsFrom mode, DataComponentMap.Builder builder,
                                                Consumer<CompoundTag> writer) {
        if (mode != SettingsFrom.MEMORY_CARD) {
            return;
        }

        var tag = new CompoundTag();
        writer.accept(tag);
        writeCustomTag(builder, tag);
    }

    /**
     * Reads our custom memory-card tag if present. The reader is only invoked
     * for memory-card imports with an exported AE2LT machine config component.
     */
    public static void importMemoryCardSettings(SettingsFrom mode, DataComponentMap input,
                                                Consumer<CompoundTag> reader) {
        if (mode != SettingsFrom.MEMORY_CARD) {
            return;
        }

        var tag = readCustomTag(input);
        if (tag != null) {
            reader.accept(tag);
        }
    }

    public static void exportAutoExportSettings(SettingsFrom mode, DataComponentMap.Builder builder,
                                                boolean autoExport, EnumSet<RelativeSide> allowedOutputs,
                                                Consumer<CompoundTag> extraWriter) {
        exportMemoryCardSettings(mode, builder, tag -> {
            tag.putBoolean(TAG_AUTO_EXPORT, autoExport);
            writeRelativeSideSet(tag, TAG_ALLOWED_OUTPUTS, allowedOutputs);
            extraWriter.accept(tag);
        });
    }

    public static void importAutoExportSettings(SettingsFrom mode, DataComponentMap input,
                                                Consumer<Boolean> autoExportSetter,
                                                Consumer<EnumSet<RelativeSide>> allowedOutputsSetter,
                                                Consumer<CompoundTag> extraReader,
                                                Runnable afterImport) {
        importMemoryCardSettings(mode, input, tag -> {
            ifBoolean(tag, TAG_AUTO_EXPORT, autoExportSetter);
            if (tag.contains(TAG_ALLOWED_OUTPUTS)) {
                allowedOutputsSetter.accept(readRelativeSideSet(tag, TAG_ALLOWED_OUTPUTS));
            }
            extraReader.accept(tag);
            afterImport.run();
        });
    }

    // ── RelativeSide set serialization ────────────────────────────────────

    public static void writeRelativeSideSet(CompoundTag tag, String key, EnumSet<RelativeSide> sides) {
        if (sides == null) {
            return;
        }
        var list = new ListTag();
        for (var side : sides) {
            list.add(StringTag.valueOf(side.name()));
        }
        tag.put(key, list);
    }

    public static EnumSet<RelativeSide> readRelativeSideSet(CompoundTag tag, String key) {
        var result = EnumSet.noneOf(RelativeSide.class);
        if (!tag.contains(key, Tag.TAG_LIST)) {
            return result;
        }
        var list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                result.add(RelativeSide.valueOf(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // forward-compatible: skip sides that no longer exist
            }
        }
        return result;
    }

    // ── Direction (nullable) serialization ────────────────────────────────

    public static void writeDirection(CompoundTag tag, String key, @Nullable Direction direction) {
        if (direction == null) {
            return;
        }
        tag.putByte(key, (byte) direction.get3DDataValue());
    }

    @Nullable
    public static Direction readDirection(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return null;
        }
        int idx = tag.getByte(key);
        if (idx < 0 || idx >= 6) {
            return null;
        }
        return Direction.from3DDataValue(idx);
    }

    // ── Generic enum serialization ────────────────────────────────────────

    public static <E extends Enum<E>> void writeEnum(CompoundTag tag, String key, @Nullable E value) {
        if (value == null) {
            return;
        }
        tag.putString(key, value.name());
    }

    public static <E extends Enum<E>> E readEnum(CompoundTag tag, String key, Class<E> type, E fallback) {
        if (!tag.contains(key)) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, tag.getString(key));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    // ── Apply-if-present helpers ──────────────────────────────────────────

    public static void ifBoolean(CompoundTag tag, String key, Consumer<Boolean> setter) {
        if (tag.contains(key)) {
            setter.accept(tag.getBoolean(key));
        }
    }
}
