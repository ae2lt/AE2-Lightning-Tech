package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 自定义音效注册。
 *
 * <p>当前每个 SoundEvent 在 sounds.json 里通过 {@code "type": "event"} 重定向到
 * vanilla 等价音效（amethyst chime / thunder / generic explode），充当 placeholder：
 * 即使没放任何 .ogg 文件，运行时听感与替换前完全一致。未来填充自定义 .ogg
 * 时仅需修改 sounds.json，调用代码无需改动。
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, AE2LightningTech.MODID);

    /** EHV1 / EHV2 子档蓄力释放的主音。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> RAILGUN_FIRE_CHARGED =
            register("railgun.fire.charged");

    /** EHV3 满档蓄力释放的主音（雷暴轰鸣层）。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> RAILGUN_FIRE_MAX =
            register("railgun.fire.max");

    /** 满档释放 / 地形破坏的冲击爆裂叠加层。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> RAILGUN_FIRE_IMPACT =
            register("railgun.fire.impact");

    /** 左键持续光束的链跳清脆反馈。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> RAILGUN_BEAM_CHAIN =
            register("railgun.beam.chain");

    /** 左键持续光束的循环 hum（激光发射风格的连续音）。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> RAILGUN_BEAM_LOOP =
            register("railgun.beam.loop");

    /** 右键蓄力中的一次性上升音。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> RAILGUN_CHARGE_RAMP =
            register("railgun.charge.ramp");

    /** 右键满蓄后继续按住时的高压维持循环音。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> RAILGUN_CHARGE_SUSTAIN =
            register("railgun.charge.loop");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        var id = ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    private ModSounds() {
    }
}
