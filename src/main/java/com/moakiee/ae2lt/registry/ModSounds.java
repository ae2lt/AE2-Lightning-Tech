package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

/**
 * 鑷畾涔夐煶鏁堟敞鍐屻€?
 *
 * <p>褰撳墠姣忎釜 SoundEvent 鍦?sounds.json 閲岄€氳繃 {@code "type": "event"} 閲嶅畾鍚戝埌
 * vanilla 绛変环闊虫晥锛坅methyst chime / thunder / generic explode锛夛紝鍏呭綋 placeholder锛?
 * 鍗充娇娌℃斁浠讳綍 .ogg 鏂囦欢锛岃繍琛屾椂鍚劅涓庢浛鎹㈠墠瀹屽叏涓€鑷淬€傛湭鏉ュ～鍏呰嚜瀹氫箟 .ogg
 * 鏃朵粎闇€淇敼 sounds.json锛岃皟鐢ㄤ唬鐮佹棤闇€鏀瑰姩銆?
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, AE2LightningTech.MODID);

    /** EHV1 / EHV2 瀛愭。钃勫姏閲婃斁鐨勪富闊炽€?*/
    public static final RegistryObject<SoundEvent> RAILGUN_FIRE_CHARGED =
            register("railgun.fire.charged");

    /** EHV3 婊℃。钃勫姏閲婃斁鐨勪富闊筹紙闆锋毚杞伴福灞傦級銆?*/
    public static final RegistryObject<SoundEvent> RAILGUN_FIRE_MAX =
            register("railgun.fire.max");

    /** 婊℃。閲婃斁 / 鍦板舰鐮村潖鐨勫啿鍑荤垎瑁傚彔鍔犲眰銆?*/
    public static final RegistryObject<SoundEvent> RAILGUN_FIRE_IMPACT =
            register("railgun.fire.impact");

    /** 宸﹂敭鎸佺画鍏夋潫鐨勯摼璺虫竻鑴嗗弽棣堛€?*/
    public static final RegistryObject<SoundEvent> RAILGUN_BEAM_CHAIN =
            register("railgun.beam.chain");

    /** 宸﹂敭鎸佺画鍏夋潫鐨勫惊鐜?hum锛堟縺鍏夊彂灏勯鏍肩殑杩炵画闊筹級銆?*/
    public static final RegistryObject<SoundEvent> RAILGUN_BEAM_LOOP =
            register("railgun.beam.loop");

    /** 鍙抽敭钃勫姏涓殑涓€娆℃€т笂鍗囬煶銆?*/
    public static final RegistryObject<SoundEvent> RAILGUN_CHARGE_RAMP =
            register("railgun.charge.ramp");

    /** 鍙抽敭婊¤搫鍚庣户缁寜浣忔椂鐨勯珮鍘嬬淮鎸佸惊鐜煶銆?*/
    public static final RegistryObject<SoundEvent> RAILGUN_CHARGE_SUSTAIN =
            register("railgun.charge.loop");

    private static RegistryObject<SoundEvent> register(String name) {
        var id = ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    private ModSounds() {
    }
}
