package com.moakiee.ae2lt.machine.crystalcatalyzer.recipe;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * 水晶催化器工作模式。
 *
 * <ul>
 *     <li>{@link #CRYSTAL} —— 默认模式：使用催化剂 + 流体 + 矩阵生成水晶。</li>
 *     <li>{@link #DUST} —— 粉化模式：将"块"放入催化剂槽，研磨成对应的粉。</li>
 * </ul>
 *
 * <p>每个 {@code CrystalCatalyzerRecipe} 都隶属于其中一种模式（默认 CRYSTAL），机器
 * 当前模式只匹配同模式的配方。模式切换会中断当前正在进行的配方。</p>
 */
public enum Mode implements StringRepresentable {
    CRYSTAL("crystal"),
    DUST("dust");

    public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);

    private final String name;

    Mode(String name) {
        this.name = name;
    }

    public Mode next() {
        Mode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public String translationKey() {
        return "ae2lt.gui.crystal_catalyzer.mode." + name;
    }

    public String tooltipKey() {
        return "ae2lt.gui.crystal_catalyzer.mode.tooltip." + name;
    }
}
