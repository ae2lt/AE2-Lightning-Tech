package com.moakiee.ae2lt.client.railgun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.config.RailgunDefaults;
import com.moakiee.ae2lt.registry.ModDataComponents;
import com.moakiee.ae2lt.registry.ModSounds;

/**
 * Looping sound for the fully charged right-click hold.
 *
 * <p>The rising charge-up is handled by {@link RailgunChargeRampSound}; this
 * sound is only the steady high-voltage sustain after the railgun reaches EHV3.
 */
public final class RailgunChargeLoopSound extends AbstractTickableSoundInstance {

    private static final int FADE_TICKS = 4;
    private static final float TARGET_VOLUME = 0.7F;

    private final LocalPlayer player;
    private boolean active = true;
    private int fadeIn = 0;
    private int fadeOut = -1;

    public RailgunChargeLoopSound(LocalPlayer player) {
        super(ModSounds.RAILGUN_CHARGE_SUSTAIN.get(), SoundSource.PLAYERS, RandomSource.create());
        this.player = player;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
        this.pitch = 1.0F;
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
        this.relative = false;
        this.attenuation = Attenuation.LINEAR;
    }

    public void requestStop() {
        this.active = false;
        if (this.fadeOut < 0) {
            this.fadeOut = FADE_TICKS;
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (player.isRemoved() || !player.isAlive() || mc.level == null) {
            stop();
            return;
        }
        ItemStack using = player.getUseItem();
        if (!player.isUsingItem() || !(using.getItem() instanceof ElectromagneticRailgunItem)) {
            requestStop();
        }
        long chargeTicks = using.getOrDefault(ModDataComponents.RAILGUN_CHARGE_TICKS.get(), 0L);
        if (RailgunChargeSoundPhase.fromChargeTicks(chargeTicks, RailgunDefaults.CHARGE_TICKS_TIER3)
                == RailgunChargeSoundPhase.RAMP) {
            requestStop();
        }
        this.x = player.getX();
        this.y = player.getY() + player.getEyeHeight() * 0.5D;
        this.z = player.getZ();

        if (active) {
            if (fadeIn < FADE_TICKS) {
                fadeIn++;
            }
            this.volume = TARGET_VOLUME * (fadeIn / (float) FADE_TICKS);
        } else {
            if (fadeOut > 0) {
                fadeOut--;
                this.volume = TARGET_VOLUME * (fadeOut / (float) FADE_TICKS);
            } else {
                this.volume = 0.0F;
                stop();
            }
        }
    }
}
