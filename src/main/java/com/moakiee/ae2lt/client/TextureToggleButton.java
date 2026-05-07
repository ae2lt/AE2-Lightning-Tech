package com.moakiee.ae2lt.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import appeng.util.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.ITooltip;

import com.moakiee.ae2lt.AE2LightningTech;

/**
 * 通用的多态贴图切换按钮。底层模型是"状态数组 + 当前索引",每个状态自带自己的贴图和 tooltip。
 *
 * <p>设计要点:</p>
 * <ul>
 *   <li>渲染只看 {@link #stateIndex},按 {@code states[stateIndex]} 取贴图与 tooltip。</li>
 *   <li>点击只通知 {@link Listener},由调用方决定如何推进状态(通常发包让服务端 cycle,服务端再回传索引)。</li>
 *   <li>{@link ButtonType} 描述每种按钮的所有可能状态,2 态按钮就两个贴图,3 态按钮三个,易扩展到 N 态。</li>
 *   <li>为了让旧调用方不受影响,保留了 {@link #setState(boolean)} / {@link #setTooltipOn(List)}
 *       / {@link #setTooltipOff(List)} 的 2 态 API,内部分别映射到索引 1 / 0。</li>
 * </ul>
 *
 * <p>这套设计与 AE2 的 {@code SettingToggleButton<T extends Enum<T>>} 思路一致——按枚举循环——
 * 但不依赖 AE2 的 {@code Setting<T>} 注册体系,也允许任意 mod 资源命名空间下的 PNG 作为贴图,
 * 适合我们这种"自定义 BlockEntity 字段 + 自定义贴图"的场景。</p>
 */
public class TextureToggleButton extends Button implements ITooltip {

    private final List<Identifier> textures;
    private final List<List<Component>> tooltips;
    private final Listener listener;

    private int stateIndex;

    public TextureToggleButton(ButtonType type, Listener listener) {
        super(0, 0, 16, 16, Component.empty(), btn -> listener.onChange(0), DEFAULT_NARRATION);
        this.textures = type.textures;
        this.tooltips = new ArrayList<>(type.textures.size());
        for (int i = 0; i < type.textures.size(); i++) {
            this.tooltips.add(Collections.emptyList());
        }
        this.listener = listener;
    }

    private static Identifier texture(String path) {
        return Identifier.fromNamespaceAndPath(
                AE2LightningTech.MODID, "textures/gui/buttons/" + path + ".png");
    }

    public int getStateCount() {
        return this.textures.size();
    }

    /** 通用状态切换入口,索引会被 clamp 到合法范围。 */
    public void setStateIndex(int index) {
        if (this.textures.isEmpty()) {
            this.stateIndex = 0;
            return;
        }
        if (index < 0) index = 0;
        if (index >= this.textures.size()) index = this.textures.size() - 1;
        this.stateIndex = index;
    }

    public int getStateIndex() {
        return this.stateIndex;
    }

    /** 通用 tooltip 设置;对越界索引静默忽略。 */
    public void setTooltipAt(int index, List<Component> lines) {
        if (index < 0 || index >= this.tooltips.size()) {
            return;
        }
        this.tooltips.set(index, lines == null ? Collections.emptyList() : lines);
    }

    // ====== 2 态兼容 API:索引 0 = OFF,索引 1 = ON ======

    public void setState(boolean isOn) {
        setStateIndex(isOn ? 1 : 0);
    }

    public void setTooltipOn(List<Component> lines) {
        setTooltipAt(1, lines);
    }

    public void setTooltipOff(List<Component> lines) {
        setTooltipAt(0, lines);
    }

    // ====== 3 态便捷 API:索引 2 = EJECT(若该 ButtonType 声明了第三贴图)======

    /** 显式切到第三态(若 {@link ButtonType} 不存在该状态,会落到最后一个有效状态)。 */
    public void setEjectState() {
        setStateIndex(2);
    }

    public boolean isEjectState() {
        return this.stateIndex == 2 && this.textures.size() >= 3;
    }

    public void setTooltipEject(List<Component> lines) {
        setTooltipAt(2, lines);
    }

    public void setVisibility(boolean visible) {
        this.visible = visible;
        this.active = visible;
    }

    @Override
    public void onPress() {
        this.listener.onChange(this.stateIndex);
    }

    @Override
    protected void renderWidget(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }

        var yOffset = isHovered() ? 1 : 0;
        var background = isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER
                : isFocused() ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND;

        background.getBlitter()
                .dest(getX() - 1, getY() + yOffset, 18, 20)
                .zOffset(2)
                .blit(guiGraphics);

        if (this.textures.isEmpty()) {
            return;
        }

        int idx = Math.min(this.stateIndex, this.textures.size() - 1);
        var blitter = Blitter.texture(this.textures.get(idx), 16, 16).src(0, 0, 16, 16);
        if (!this.active) {
            blitter.opacity(0.5f);
        }
        blitter.dest(getX(), getY() + 1 + yOffset).zOffset(3).blit(guiGraphics);
    }

    @Override
    public List<Component> getTooltipMessage() {
        if (this.tooltips.isEmpty()) {
            return Collections.emptyList();
        }
        int idx = Math.min(this.stateIndex, this.tooltips.size() - 1);
        return this.tooltips.get(idx);
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(getX(), getY(), 16, 16);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return this.visible && !getTooltipMessage().isEmpty();
    }

    public enum ButtonType {
        // 索引: 0 = OFF/normal, 1 = ON/alt, 2 = EJECT(可选)
        MODE(texture("wired_mode"), texture("wireless_mode")),
        // 返回按钮 (3 态):对应过载样板供应器的 ReturnMode { OFF, AUTO, EJECT }。
        AUTO_RETURN(texture("auto_input_off"), texture("auto_input_on"), texture("auto_input_ejection")),
        WIRELESS_STRATEGY(texture("single_target"), texture("even_distribution")),
        FILTERED_IMPORT(texture("filtered_import_off"), texture("filtered_import_on")),
        SPEED(texture("speed_normal"), texture("speed_fast")),
        // 输出按钮 (2 态):对应 ExportMode { OFF, AUTO }。
        AUTO_EXPORT(texture("auto_export_off"), texture("auto_export_on")),
        // 输入按钮 (3 态):对应 ImportMode { OFF, AUTO, EJECT }。
        AUTO_IMPORT(texture("auto_input_off"), texture("auto_input_on"), texture("auto_input_ejection")),
        // 过载电源 PowerMode { NORMAL=off, OVERLOAD=on }。
        OVERLOAD_MODE(texture("overloaded_off"), texture("overloaded_on")),
        // 水晶催化器 Mode { CRYSTAL=off, DUST=on }。
        CRYSTAL_CATALYZER_MODE(texture("catalyzer_crystal_mode"), texture("catalyzer_dust_mode")),
        FREQUENCY_BIND(texture("frequency_select"));

        private final List<Identifier> textures;

        ButtonType(Identifier texture) {
            this.textures = List.of(texture);
        }

        ButtonType(Identifier textureOff, Identifier textureOn) {
            this.textures = List.of(textureOff, textureOn);
        }

        ButtonType(Identifier textureOff, Identifier textureOn, Identifier textureEject) {
            this.textures = List.of(textureOff, textureOn, textureEject);
        }
    }

    /**
     * 按下时由按钮回传"当前正在显示的状态索引",调用方通常忽略它,
     * 直接发个 cycle 包让服务端把状态向后推进一格。
     */
    @FunctionalInterface
    public interface Listener {
        void onChange(int previousStateIndex);
    }
}
