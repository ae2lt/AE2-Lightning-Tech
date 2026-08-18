package com.moakiee.ae2lt.client.railgun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.registry.ModSounds;

/**
 * Looping sound for the left-click sustained beam.
 *
 * <p>Follows the local player position (updated every tick). Self-manages its
 * lifecycle based on {@link RailgunBeamInput} firing state: stops when firing
 * turns false, the player swaps off the railgun, or the player dies.
 *
 * <p>8-tick fade-in / fade-out to avoid click artifacts. Pitch fixed at 1.0.
 */
public final class RailgunBeamLoopSound extends AbstractTickableSoundInstance {

    private static final int FADE_TICKS = 8;
    private static final float TARGET_VOLUME = 0.85F;

    private final LocalPlayer player;
    private boolean active = true;
    private int fadeIn = 0;
    private int fadeOut = -1;

    public RailgunBeamLoopSound(LocalPlayer player) {
        super(ModSounds.RAILGUN_BEAM_LOOP.get(), SoundSource.PLAYERS, RandomSource.create());
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

    /** Trigger fade-out; the sound will self-stop once volume reaches zero. */
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
        ItemStack main = player.getMainHandItem();
        if (!(main.getItem() instanceof ElectromagneticRailgunItem)) {
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
