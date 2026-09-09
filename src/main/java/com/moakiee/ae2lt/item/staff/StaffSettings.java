package com.moakiee.ae2lt.item.staff;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Indices, rather than arbitrary client floats, constrain every setting. */
public record StaffSettings(int land, int mekanism, int harvest, int base, int scale,
                            boolean speed, boolean smelting, boolean collection, int combat, int damage, int execution) {
    public static final StaffSettings DEFAULT = new StaffSettings(0, 0, 0, 0, 5, false, false, false);
    public static final Codec<StaffSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0, 2).optionalFieldOf("land", 0).forGetter(StaffSettings::land),
            Codec.intRange(0, 6).optionalFieldOf("mekanism", 0).forGetter(StaffSettings::mekanism),
            Codec.intRange(0, 2).optionalFieldOf("harvest", 0).forGetter(StaffSettings::harvest),
            Codec.intRange(0, 4).optionalFieldOf("base", 0).forGetter(StaffSettings::base),
            Codec.intRange(0, 5).optionalFieldOf("scale", 5).forGetter(StaffSettings::scale),
            Codec.BOOL.optionalFieldOf("speed", false).forGetter(StaffSettings::speed),
            Codec.BOOL.optionalFieldOf("smelting", false).forGetter(StaffSettings::smelting),
            Codec.BOOL.optionalFieldOf("collection", false).forGetter(StaffSettings::collection),
            Codec.intRange(0, 4).optionalFieldOf("combat", 0).forGetter(StaffSettings::combat),
            Codec.intRange(0, 6).optionalFieldOf("damage", 2).forGetter(StaffSettings::damage),
            Codec.intRange(0, 2).optionalFieldOf("execution", 1).forGetter(StaffSettings::execution)
    ).apply(i, StaffSettings::new));

    public StaffSettings(int land, int mekanism, int harvest, int base, int scale,
                         boolean speed, boolean smelting, boolean collection) {
        this(land, mekanism, harvest, base, scale, speed, smelting, collection, 0, 2, 1);
    }

    public StaffSettings {
        if (land < 0 || land > 2 || mekanism < 0 || mekanism > 6 || harvest < 0 || harvest > 2
                || base < 0 || base > 4 || scale < 0 || scale > 5 || combat < 0 || combat > 4
                || damage < 0 || damage > 6 || execution < 0 || execution > 2) throw new IllegalArgumentException("Invalid staff setting");
    }

    public int baseTicks() { return switch (base) { case 1 -> 3; case 2 -> 5; case 3 -> 10; case 4 -> 20; default -> 0; }; }
    public double timeScale() { return switch (scale) { case 0 -> .01; case 1 -> .05; case 2 -> .1; case 3 -> .2; case 4 -> .5; default -> 1; }; }

    public StaffSettings cycle(int button) {
        return new StaffSettings(button == 0 ? (land + 1) % 3 : land,
                button == 1 ? (mekanism + 1) % 7 : mekanism,
                button == 2 ? (harvest + 1) % 3 : harvest,
                button == 3 ? (base + 1) % 5 : base,
                button == 4 ? (scale + 1) % 6 : scale,
                button == 5 ? !speed : speed, button == 6 ? !smelting : smelting,
                button == 7 ? !collection : collection, button == 8 ? (combat + 1) % 5 : combat,
                button == 9 ? (damage + 1) % 7 : damage, button == 10 ? (execution + 1) % 3 : execution);
    }

    public float transformProgress(float original) {
        if (!speed || Float.isNaN(original) || original <= 0) return original;
        if (Float.isInfinite(original)) return baseTicks() == 0 ? original : 1.0F / baseTicks();
        return (float) (original / (baseTicks() * (double) original + timeScale()));
    }
}
