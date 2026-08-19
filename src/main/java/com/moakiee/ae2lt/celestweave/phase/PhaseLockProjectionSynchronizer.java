package com.moakiee.ae2lt.celestweave.phase;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import net.minecraftforge.registries.ForgeRegistries;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.registry.ModDataComponents;
import com.moakiee.ae2lt.util.ItemStackTagSupport;

/**
 * Maintains the equipped projection as a writable mirror of the one authoritative armor stack in
 * the private vault.
 *
 * <p>All effective data is mirrored except phase-lock bookkeeping and Celestweave's private
 * control state. This exposes vanilla and third-party affix/attribute data on the equipped stack
 * without allowing a projection to become a second functional armor controller.</p>
 *
 * <p>1.20.1 移植：1.21 版按 {@code DataComponentType} 镜像，这里改为按 ItemStack NBT key 镜像——
 * AE2LT 私有数据统一带 {@code ae2lt:} 前缀（见 {@link ModDataComponents#TAG_PREFIX}），被排除；
 * 附魔特殊处理（投影诅咒），其余 key 整体复制。</p>
 */
final class PhaseLockProjectionSynchronizer {
    private PhaseLockProjectionSynchronizer() {
    }

    static void synchronize(ServerPlayer player, ItemStack armor, ItemStack projection) {
        clearMisplacedPrivateComponents(armor, projection);
        UUID armorId = CelestweaveArmorState.ensureArmorId(armor);
        long armorUpdate = ModDataComponents.PHASE_LOCK_ARMOR_UPDATE.getOrDefault(armor, 0L);
        PhaseLockProjectionLink link = ModDataComponents.PHASE_LOCK_PROJECTION_LINK.get(projection);
        ProjectionCurses projectionCurses = projectionCurses();
        boolean equal = armorSnapshot(armor).equals(projectionSnapshot(projection, armor, projectionCurses));

        PhaseLockProjectionSyncRules.Direction direction = PhaseLockProjectionSyncRules.direction(
                armorId,
                armorUpdate,
                link,
                equal);
        if (direction == PhaseLockProjectionSyncRules.Direction.NONE) {
            // A newer projection with identical fields still advances the authoritative clock.
            if (link != null && link.armorId().equals(armorId) && link.update() > armorUpdate) {
                ModDataComponents.PHASE_LOCK_ARMOR_UPDATE.set(armor, link.update());
            }
            ensureProjectionCurses(projection, projectionCurses);
            return;
        }

        long nextUpdate = PhaseLockProjectionSyncRules.nextUpdate(armorUpdate, link);
        if (direction == PhaseLockProjectionSyncRules.Direction.ARMOR_TO_PROJECTION) {
            ModDataComponents.PHASE_LOCK_ARMOR_UPDATE.set(armor, nextUpdate);
            replaceMirroredComponents(armor, projection, false);
            ensureProjectionCurses(projection, projectionCurses);
        } else {
            replaceMirroredComponents(projection, armor, true);
            copyProjectionEnchantmentsToArmor(projection, armor, projectionCurses);
            ModDataComponents.PHASE_LOCK_ARMOR_UPDATE.set(armor, nextUpdate);
        }
        ModDataComponents.PHASE_LOCK_PROJECTION_LINK.set(
                projection,
                new PhaseLockProjectionLink(armorId, nextUpdate));
        ensureProjectionCurses(projection, projectionCurses);
    }

    static MirroredSnapshot captureArmorFields(ItemStack armor) {
        return armorSnapshot(armor);
    }

    /** Publishes real-armor mutations made by its manual armor tick without a one-tick rollback. */
    static void publishArmorChanges(
            ServerPlayer player,
            ItemStack armor,
            ItemStack projection,
            MirroredSnapshot before) {
        if (before.equals(armorSnapshot(armor))) {
            return;
        }
        UUID armorId = CelestweaveArmorState.ensureArmorId(armor);
        long armorUpdate = ModDataComponents.PHASE_LOCK_ARMOR_UPDATE.getOrDefault(armor, 0L);
        PhaseLockProjectionLink link = ModDataComponents.PHASE_LOCK_PROJECTION_LINK.get(projection);
        long nextUpdate = PhaseLockProjectionSyncRules.nextUpdate(armorUpdate, link);
        ModDataComponents.PHASE_LOCK_ARMOR_UPDATE.set(armor, nextUpdate);
        replaceMirroredComponents(armor, projection, false);
        ensureProjectionCurses(projection, projectionCurses());
        ModDataComponents.PHASE_LOCK_PROJECTION_LINK.set(
                projection,
                new PhaseLockProjectionLink(armorId, nextUpdate));
    }

    private static MirroredSnapshot armorSnapshot(ItemStack armor) {
        return snapshot(armor, null, null);
    }

    private static MirroredSnapshot projectionSnapshot(
            ItemStack projection,
            ItemStack armor,
            ProjectionCurses projectionCurses) {
        return snapshot(projection, armor, projectionCurses);
    }

    /**
     * 收集 stack 上所有可镜像的 NBT 字段（key → Tag）。"Enchantments" 在投影侧需先经过诅咒过滤，
     * 因此单独重算；其余字段直接取原始 Tag 引用。
     */
    private static MirroredSnapshot snapshot(
            ItemStack stack,
            ItemStack authoritativeArmor,
            ProjectionCurses projectionCurses) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return new MirroredSnapshot(Map.of());
        }
        Map<String, Tag> fields = new HashMap<>();
        for (String key : tag.getAllKeys()) {
            if (!isMirrored(key)) {
                continue;
            }
            Tag value;
            if (TAG_ENCHANTMENTS.equals(key)) {
                // Canonicalize both sides. Vanilla removes an empty Enchantments tag, while a
                // projection always carries two private curses that become empty after filtering.
                Map<Enchantment, Integer> normalizedEnchantments = authoritativeArmor == null
                        ? enchantments(stack)
                        : projectionEnchantmentsForArmor(stack, authoritativeArmor, projectionCurses);
                if (normalizedEnchantments.isEmpty()) {
                    continue;
                }
                value = enchantmentsToTag(normalizedEnchantments);
            } else {
                value = tag.get(key);
            }
            fields.put(key, value);
        }
        return new MirroredSnapshot(Map.copyOf(fields));
    }

    /** 把 source 的可镜像字段整体覆盖到 target（可选跳过附魔，用于投影→本体方向先算诅咒过滤）。 */
    private static void replaceMirroredComponents(
            ItemStack source,
            ItemStack target,
            boolean skipEnchantments) {
        ItemStackTagSupport.updateTag(target, targetTag -> {
            for (String key : new ArrayList<>(targetTag.getAllKeys())) {
                if (isMirrored(key) && (!skipEnchantments || !TAG_ENCHANTMENTS.equals(key))) {
                    targetTag.remove(key);
                }
            }
        });
        CompoundTag sourceTag = source.getTag();
        if (sourceTag == null) {
            return;
        }
        for (String key : sourceTag.getAllKeys()) {
            if (isMirrored(key) && (!skipEnchantments || !TAG_ENCHANTMENTS.equals(key))) {
                Tag value = sourceTag.get(key);
                ItemStackTagSupport.updateTag(target, t -> t.put(key, value.copy()));
            }
        }
    }

    private static void copyProjectionEnchantmentsToArmor(
            ItemStack projection,
            ItemStack armor,
            ProjectionCurses projectionCurses) {
        setEnchantments(armor, projectionEnchantmentsForArmor(projection, armor, projectionCurses));
    }

    /**
     * 投影附魔 = 投影的附魔，去掉"本体上没有的诅咒"（投影诅咒不能通过相位锁定反向写入本体）。
     * 1.20.1：附魔为 {@code Map<Enchantment, Integer>}，经 EnchantmentHelper 读写 NBT "Enchantments"。
     */
    private static Map<Enchantment, Integer> projectionEnchantmentsForArmor(
            ItemStack projection,
            ItemStack armor,
            ProjectionCurses projectionCurses) {
        Map<Enchantment, Integer> projectionEnchantments = enchantments(projection);
        Map<Enchantment, Integer> armorEnchantments = enchantments(armor);
        removeProjectionOnlyCurse(projectionEnchantments, armorEnchantments, projectionCurses.binding());
        removeProjectionOnlyCurse(projectionEnchantments, armorEnchantments, projectionCurses.vanishing());
        return projectionEnchantments;
    }

    private static void removeProjectionOnlyCurse(
            Map<Enchantment, Integer> projectionEnchantments,
            Map<Enchantment, Integer> armorEnchantments,
            Enchantment curse) {
        if (armorEnchantments.getOrDefault(curse, 0) == 0) {
            projectionEnchantments.remove(curse);
        }
    }

    /** 投影始终携带绑定/消失诅咒（无论本体是否有），防止玩家直接卸下投影作弊。 */
    private static void ensureProjectionCurses(
            ItemStack projection,
            ProjectionCurses projectionCurses) {
        Map<Enchantment, Integer> enchantments = enchantments(projection);
        enchantments.put(projectionCurses.binding(), 1);
        enchantments.put(projectionCurses.vanishing(), 1);
        setEnchantments(projection, enchantments);
    }

    private static Map<Enchantment, Integer> enchantments(ItemStack stack) {
        return new HashMap<>(EnchantmentHelper.getEnchantments(stack));
    }

    /** 1.20.1：把附魔 Map 编码成 vanilla "Enchantments" ListTag（id + lvl）。 */
    private static ListTag enchantmentsToTag(Map<Enchantment, Integer> enchantments) {
        ListTag list = new ListTag();
        var entries = new ArrayList<>(enchantments.entrySet());
        entries.sort(Comparator.comparing(
                entry -> ForgeRegistries.ENCHANTMENTS.getKey(entry.getKey()).toString()));
        for (Map.Entry<Enchantment, Integer> entry : entries) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("id", ForgeRegistries.ENCHANTMENTS.getKey(entry.getKey()).toString());
            entryTag.putShort("lvl", entry.getValue().shortValue());
            list.add(entryTag);
        }
        return list;
    }

    private static void setEnchantments(ItemStack stack, Map<Enchantment, Integer> enchantments) {
        EnchantmentHelper.setEnchantments(enchantments, stack);
    }

    /** 1.20.1：Enchantments.BINDING_CURSE 等是注册表单例的直接引用（1.20.2+ 才改为 Holder）。 */
    private static ProjectionCurses projectionCurses() {
        return new ProjectionCurses(
                Enchantments.BINDING_CURSE,
                Enchantments.VANISHING_CURSE);
    }

    private static final String TAG_ENCHANTMENTS = "Enchantments";

    /** AE2LT 私有数据（ae2lt: 前缀）不镜像，防止投影变成第二个功能控制器。 */
    private static boolean isMirrored(String key) {
        return !key.startsWith(ModDataComponents.TAG_PREFIX);
    }

    private static void clearMisplacedPrivateComponents(ItemStack armor, ItemStack projection) {
        ModDataComponents.PHASE_LOCK_PROJECTION_LINK.remove(armor);
        ModDataComponents.PHASE_LOCK_ARMOR_UPDATE.remove(projection);
        ModDataComponents.CELESTWEAVE_STRUCTURAL_CORE.remove(projection);
        ModDataComponents.CELESTWEAVE_ENERGY_BUFFER.remove(projection);
        ModDataComponents.CELESTWEAVE_MODULES.remove(projection);
        ModDataComponents.CELESTWEAVE_MODULES_POWERED.remove(projection);
    }

    /** 可镜像字段快照：key → NBT Tag（NBT 的 equals 为深度比较，可直接用于变更检测）。 */
    record MirroredSnapshot(Map<String, Tag> fields) {
    }

    private record ProjectionCurses(
            Enchantment binding,
            Enchantment vanishing) {
    }
}
