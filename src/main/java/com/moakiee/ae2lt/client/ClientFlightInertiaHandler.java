package com.moakiee.ae2lt.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;

@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class ClientFlightInertiaHandler {
    private static final double INERTIA_OFF_DECAY = 0.1;

    private ClientFlightInertiaHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || event.getEntity() != minecraft.player) {
            return;
        }
        var player = minecraft.player;
        if (!player.getAbilities().flying) {
            return;
        }
        if (CelestweaveArmorState.getClientFlightInertia()) {
            return;
        }

        Input input = player.input;
        Vec3 motion = player.getDeltaMovement();
        double x = motion.x;
        double y = motion.y;
        double z = motion.z;

        boolean anyHorizontalInput = input.forwardImpulse != 0 || input.leftImpulse != 0;
        if (!anyHorizontalInput) {
            x *= INERTIA_OFF_DECAY;
            z *= INERTIA_OFF_DECAY;
        }

        boolean verticalInput = minecraft.options.keyJump.isDown() || minecraft.options.keyShift.isDown();
        if (!verticalInput) {
            y *= INERTIA_OFF_DECAY;
        }

        if (x != motion.x || y != motion.y || z != motion.z) {
            // phase flight sets noPhysics=true which causes setDeltaMovement to be ignored;
            // temporarily disable it so our decay takes effect
            boolean wasNoPhysics = player.noPhysics;
            if (wasNoPhysics) {
                player.noPhysics = false;
            }
            player.setDeltaMovement(new Vec3(x, y, z));
            if (wasNoPhysics) {
                player.noPhysics = true;
            }
        }
    }
}
