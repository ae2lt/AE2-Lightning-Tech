package com.moakiee.ae2lt.logic;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import appeng.me.helpers.MachineSource;
import appeng.parts.automation.AnnihilationPlanePart;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.BlockHitResult;

/** Handles conversion of Shulker bullets that strike the front of an Annihilation Plane. */
public final class FloatingMatterCapture {

    private FloatingMatterCapture() {
    }

    public static boolean tryCapture(ShulkerBullet bullet, BlockHitResult hit) {
        if (!AE2LTCommonConfig.shulkerBulletCollectionEnabled()
                || !bullet.isAlive()
                || bullet.level().isClientSide()) {
            return false;
        }

        if (!(bullet.level().getBlockEntity(hit.getBlockPos()) instanceof IPartHost host)) {
            return false;
        }

        // A block hit reports the outward normal of the surface that was struck. AE2 stores a
        // face-mounted part under that same direction, so this deliberately accepts only the
        // plane on the impacted (front) face and never one on the cable/back side.
        if (!(host.getPart(hit.getDirection()) instanceof AnnihilationPlanePart plane)
                || !plane.getMainNode().isActive()
                || !hasSilkTouch(plane)) {
            return false;
        }

        var grid = plane.getMainNode().getGrid();
        if (grid == null) {
            return false;
        }

        IActionSource source = new MachineSource(plane);
        long inserted = StorageHelper.poweredInsert(
                grid.getEnergyService(),
                grid.getStorageService().getInventory(),
                AEItemKey.of(ModItems.FLOATING_MATTER.get()),
                1L,
                source,
                Actionable.MODULATE);
        if (inserted != 1L) {
            return false;
        }

        bullet.discard();
        return true;
    }

    private static boolean hasSilkTouch(AnnihilationPlanePart plane) {
        for (Holder<Enchantment> enchantment : plane.getEnchantments().keySet()) {
            if (enchantment.is(Enchantments.SILK_TOUCH)) {
                return true;
            }
        }
        return false;
    }
}
