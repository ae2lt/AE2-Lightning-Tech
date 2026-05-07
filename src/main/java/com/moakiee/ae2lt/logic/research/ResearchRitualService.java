package com.moakiee.ae2lt.logic.research;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import com.moakiee.ae2lt.blockentity.AtmosphericIonizerBlockEntity;
import com.moakiee.ae2lt.item.FixedInfiniteCellItem;
import com.moakiee.ae2lt.item.ResearchNoteItem;
import com.moakiee.ae2lt.lightning.ProtectedItemEntityHelper;
import com.moakiee.ae2lt.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ResearchRitualService {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int EXPECTED_ITEM_COUNT = 9;

    private ResearchRitualService() {
    }

    public static void handleLightning(ServerLevel level, LightningBolt lightningBolt) {
        BlockPos center = BlockPos.containing(lightningBolt.position());
        LOG.debug("[ae2lt/ritual] handleLightning: dim={} strike={} ", level.dimension().location(), center);
        Set<BlockPos> visited = new HashSet<>();
        int ionizerHits = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -4, -1), center.offset(1, 1, 1))) {
            if (!visited.add(pos.immutable())) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof AtmosphericIonizerBlockEntity ionizer) {
                ionizerHits++;
                LOG.debug("[ae2lt/ritual] handleLightning: found ionizer at {}", pos);
                tryHandleIonizer(level, ionizer);
            }
        }
        if (ionizerHits == 0) {
            LOG.debug("[ae2lt/ritual] handleLightning: no ionizer within 3x6x3 below {}", center);
        }
    }

    private static void tryHandleIonizer(ServerLevel level, AtmosphericIonizerBlockEntity ionizer) {
        BlockPos ionizerPos = ionizer.getBlockPos();
        AABB box = ritualSearchBox(ionizerPos);
        List<ItemEntity> scanned = level.getEntitiesOfClass(ItemEntity.class, box,
                itemEntity -> itemEntity.isAlive() && !itemEntity.getItem().isEmpty());
        LOG.debug("[ae2lt/ritual] tryHandleIonizer: ionizer={} box={} scanned={}", ionizerPos, box, scanned.size());
        if (scanned.isEmpty()) {
            LOG.debug("[ae2lt/ritual] tryHandleIonizer: reaction zone empty, abort.");
            return;
        }

        ItemEntity anchorNote = scanned.stream()
                .filter(itemEntity -> ResearchNoteItem.isUsableGeneratedNote(itemEntity.getItem()))
                .max(Comparator.comparingInt(ItemEntity::getAge))
                .orElse(null);
        if (anchorNote == null) {
            LOG.debug("[ae2lt/ritual] tryHandleIonizer: no usable generated research note in zone (items={}).",
                    scanned.size());
            return;
        }

        ResearchNoteData note = ResearchNoteItem.getData(anchorNote.getItem());
        if (note == null) {
            LOG.debug("[ae2lt/ritual] tryHandleIonizer: anchor note has no ResearchNoteData, abort.");
            return;
        }
        LOG.debug("[ae2lt/ritual] tryHandleIonizer: anchor age={} goal={} recipe={}", anchorNote.getAge(),
                note.goal(), note.recipeItems());

        List<ItemEntity> candidates = scanned.stream()
                .filter(itemEntity -> itemEntity != anchorNote)
                .filter(itemEntity -> itemEntity.getAge() <= anchorNote.getAge())
                .sorted(Comparator.comparingInt(ItemEntity::getAge).reversed())
                .toList();
        if (candidates.size() != EXPECTED_ITEM_COUNT) {
            LOG.debug("[ae2lt/ritual] tryHandleIonizer: candidate count {} != expected {} (ages: {})",
                    candidates.size(), EXPECTED_ITEM_COUNT,
                    candidates.stream().map(ItemEntity::getAge).toList());
            return;
        }

        List<ResourceLocation> thrownSequence = candidates.stream()
                .map(itemEntity -> BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem()))
                .toList();
        LOG.debug("[ae2lt/ritual] tryHandleIonizer: thrown sequence (oldest->newest) = {}", thrownSequence);
        if (!sameMultiset(thrownSequence, note.recipeItems())) {
            LOG.debug("[ae2lt/ritual] tryHandleIonizer: multiset mismatch, abort (expected={}, got={})",
                    note.recipeItems(), thrownSequence);
            return;
        }

        if (thrownSequence.equals(note.recipeItems())) {
            LOG.info("[ae2lt/ritual] SUCCESS at ionizer={} goal={} (ordered recipe matched).", ionizerPos, note.goal());
            succeed(level, anchorNote, note, candidates);
        } else {
            LOG.info("[ae2lt/ritual] FAIL at ionizer={} (items correct but order wrong). expected={} got={}",
                    ionizerPos, note.recipeItems(), thrownSequence);
            fail(level, anchorNote, candidates);
        }
    }

    private static void succeed(ServerLevel level, ItemEntity anchorNote, ResearchNoteData note, List<ItemEntity> candidates) {
        long gameTime = level.getGameTime();
        consumeParticipants(candidates);

        ItemStack noteStack = anchorNote.getItem().copy();
        ResearchNoteItem.applyGeneratedState(noteStack, note.withConsumed(true));
        anchorNote.setItem(noteStack);

        ItemEntity reward = new ItemEntity(level, anchorNote.getX(), anchorNote.getY() + 0.25D, anchorNote.getZ(),
                createRewardStack(note.goal()));
        reward.setDeltaMovement(Vec3.ZERO);
        ProtectedItemEntityHelper.applyOutputProtection(reward, gameTime);
        level.addFreshEntity(reward);

        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, anchorNote.getX(), anchorNote.getY() + 0.15D, anchorNote.getZ(),
                30, 0.45D, 0.35D, 0.45D, 0.02D);
        level.playSound(null, anchorNote.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 1.0F,
                1.0F);
    }

    private static void fail(ServerLevel level, ItemEntity anchorNote, List<ItemEntity> candidates) {
        long gameTime = level.getGameTime();
        List<Vec3> positions = consumeParticipants(candidates);

        int fragmentCount = 1 + level.random.nextInt(3);
        for (int i = 0; i < fragmentCount; i++) {
            ItemEntity fragment = new ItemEntity(level, anchorNote.getX(), anchorNote.getY() + 0.1D, anchorNote.getZ(),
                    new ItemStack(ModItems.CHARRED_RITUAL_FRAGMENT.get()));
            fragment.setDeltaMovement((level.random.nextDouble() - 0.5D) * 0.08D, 0.05D,
                    (level.random.nextDouble() - 0.5D) * 0.08D);
            ProtectedItemEntityHelper.applyOutputProtection(fragment, gameTime);
            level.addFreshEntity(fragment);
        }

        for (Vec3 position : positions) {
            level.sendParticles(ParticleTypes.SMOKE, position.x, position.y + 0.1D, position.z, 12, 0.1D, 0.05D, 0.1D,
                    0.01D);
        }
        level.playSound(null, anchorNote.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.9F, 0.7F);
    }

    private static List<Vec3> consumeParticipants(List<ItemEntity> candidates) {
        List<Vec3> positions = new ArrayList<>(candidates.size());
        for (ItemEntity candidate : candidates) {
            positions.add(candidate.position());
            ItemStack stack = candidate.getItem().copy();
            stack.shrink(1);
            if (stack.isEmpty()) {
                candidate.discard();
            } else {
                candidate.setItem(stack);
            }
        }
        return positions;
    }

    private static ItemStack createRewardStack(RitualGoal goal) {
        return switch (goal) {
            case HIGH_VOLTAGE -> {
                ItemStack stack = new ItemStack(ModItems.MYSTERIOUS_CELL.get());
                FixedInfiniteCellItem.setType(stack, FixedInfiniteCellItem.CellOutcome.HIGH_VOLTAGE.typeId());
                yield stack;
            }
            case EXTREME_HIGH_VOLTAGE -> {
                ItemStack stack = new ItemStack(ModItems.MYSTERIOUS_CELL.get());
                FixedInfiniteCellItem.setType(stack, FixedInfiniteCellItem.CellOutcome.EXTREME_HIGH_VOLTAGE.typeId());
                yield stack;
            }
            case LIGHTNING_COLLAPSE_MATRIX -> {
                ItemStack stack = new ItemStack(ModItems.MYSTERIOUS_CELL.get());
                FixedInfiniteCellItem.setType(stack, FixedInfiniteCellItem.CellOutcome.LIGHTNING_COLLAPSE_MATRIX.typeId());
                yield stack;
            }
            case INFINITE_STORAGE_CELL -> new ItemStack(ModItems.INFINITE_STORAGE_CELL.get());
        };
    }

    private static boolean sameMultiset(List<ResourceLocation> left, List<ResourceLocation> right) {
        if (left.size() != right.size()) {
            return false;
        }

        Map<ResourceLocation, Integer> counts = new HashMap<>();
        for (ResourceLocation id : left) {
            counts.merge(id, 1, Integer::sum);
        }
        for (ResourceLocation id : right) {
            Integer current = counts.get(id);
            if (current == null) {
                return false;
            }
            if (current <= 1) {
                counts.remove(id);
            } else {
                counts.put(id, current - 1);
            }
        }
        return counts.isEmpty();
    }

    private static AABB ritualSearchBox(BlockPos ionizerPos) {
        // 反应场:电离仪**正上方** 3x3 水平 × 4 格纵深。
        // 上界 +5 给一点缓冲,允许物品在落地瞬间还未完全稳定时仍被识别。
        return new AABB(
                ionizerPos.getX() - 1.0D,
                ionizerPos.getY() + 1.0D,
                ionizerPos.getZ() - 1.0D,
                ionizerPos.getX() + 2.0D,
                ionizerPos.getY() + 5.0D,
                ionizerPos.getZ() + 2.0D);
    }
}
