package com.moakiee.ae2lt.item.staff;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.moakiee.ae2lt.item.railgun.RailgunModuleType;
import net.minecraft.world.item.ItemStack;

/** Client presentation and prediction only; server gameplay always resolves the private original. */
public record StaffProjectionView(StaffSettings settings, int modules, int energy) {
    public static final StaffProjectionView EMPTY = new StaffProjectionView(StaffSettings.DEFAULT, 0, 0);
    public static final Codec<StaffProjectionView> CODEC = RecordCodecBuilder.create(i -> i.group(
            StaffSettings.CODEC.fieldOf("settings").forGetter(StaffProjectionView::settings),
            Codec.intRange(0, 16383).fieldOf("modules").forGetter(StaffProjectionView::modules),
            Codec.intRange(0, StaffEnergy.CAPACITY).fieldOf("energy").forGetter(StaffProjectionView::energy)).apply(i, StaffProjectionView::new));

    public boolean has(StaffModule type) { return (modules & 1 << type.ordinal()) != 0; }
    public boolean has(RailgunModuleType type) { return (modules & railgunBit(type)) != 0; }
    private static int railgunBit(RailgunModuleType type) {
        return switch (type) { case CORE -> 1 << 11; case OVERLOAD_EXECUTION -> 1 << 12;
            case MULTIDIMENSIONAL_EXECUTION -> 1 << 13; default -> 0; };
    }
    public static StaffProjectionView of(ItemStack original) {
        int bits = 0;
        for (var module : StaffModule.values()) if (StaffState.has(original, module)) bits |= 1 << module.ordinal();
        for (var module : RailgunModuleType.values()) if (StaffState.has(original, module)) bits |= railgunBit(module);
        return new StaffProjectionView(StaffState.settings(original), bits, StaffEnergy.stored(original));
    }
}
