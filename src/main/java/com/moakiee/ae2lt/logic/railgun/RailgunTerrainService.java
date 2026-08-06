package com.moakiee.ae2lt.logic.railgun;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import com.moakiee.ae2lt.registry.ModSounds;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.tags.BlockTags;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.config.RailgunDefaults;
import com.moakiee.ae2lt.item.railgun.RailgunChargeTier;
import com.moakiee.ae2lt.item.railgun.RailgunSettings;

/**
 * Async block-destruction service. Each charged shot may queue thousands of
 * candidate positions; we drain them at most {@code blocksPerTick} per tick
 * so 4000+ block max-tier shots don't lock the server.
 *
 * <p>The candidate list is shuffled once when queued, so the destruction looks
 * like an irregular collapse rather than concentric rings.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class RailgunTerrainService {

    private static final Deque<DestroyJob> PENDING = new ArrayDeque<>();

    /** Lazy-built identity set of blocks in our MODID / AE2 namespaces (registry-frozen). */
    private static volatile java.util.Set<Block> PROTECTED_BLOCKS;

    public record DestroyJob(
            ResourceKey<Level> dim,
            UUID playerId,
            Deque<BlockPos> queue,
            float maxHardness,
            ItemStack toolStack) {}

    private RailgunTerrainService() {}

    public static void queueDestroy(ServerLevel level, Vec3 center, RailgunChargeTier tier,
                                     ServerPlayer player, ItemStack stack) {
        if (!AE2LTCommonConfig.railgunTerrainDestructionEnabled()) return;
        if (tier == RailgunChargeTier.HV) return;
        double radius = switch (tier) {
            case EHV1 -> RailgunDefaults.TERRAIN_RADIUS_TIER1;
            case EHV2 -> RailgunDefaults.TERRAIN_RADIUS_TIER2;
            case EHV3 -> RailgunDefaults.TERRAIN_RADIUS_TIER3;
            default -> 0.0D;
        };
        float maxHardness = (float) switch (tier) {
            case EHV1 -> RailgunDefaults.TERRAIN_HARDNESS_TIER1;
            case EHV2 -> RailgunDefaults.TERRAIN_HARDNESS_TIER2;
            case EHV3 -> RailgunDefaults.TERRAIN_HARDNESS_TIER3;
            default -> 0.0D;
        };

        BlockPos centerPos = BlockPos.containing(center);
        if (RailgunSettings.soundEnabled(stack)) {
            level.playSound(null, centerPos, ModSounds.RAILGUN_FIRE_IMPACT.get(), SoundSource.BLOCKS,
                    1.5F + tier.ordinal() * 0.3F, 0.5F);
        }

        List<BlockPos> candidates = collectSphere(centerPos, radius);
        // Fisher-Yates shuffle directly on the level's RandomSource (Collections.shuffle
        // would need a java.util.Random). Then move into an ArrayDeque so per-tick
        // consumption is O(1) head-pop instead of ArrayList.remove(0)'s O(n) shift.
        for (int i = candidates.size() - 1; i > 0; i--) {
            int j = level.random.nextInt(i + 1);
            BlockPos tmp = candidates.get(i);
            candidates.set(i, candidates.get(j));
            candidates.set(j, tmp);
        }
        PENDING.add(new DestroyJob(level.dimension(), player.getUUID(),
                new ArrayDeque<>(candidates), maxHardness, stack.copy()));
    }

    public static void queueDestroyAlongPath(ServerLevel level, List<Vec3> hitPoints,
                                              ServerPlayer player, ItemStack stack) {
        if (!AE2LTCommonConfig.railgunTerrainDestructionEnabled()) return;
        double r = RailgunDefaults.PENETRATION_DESTROY_RADIUS;
        float maxHardness = (float) RailgunDefaults.PENETRATION_DESTROY_HARDNESS;
        for (Vec3 p : hitPoints) {
            List<BlockPos> candidates = collectSphere(BlockPos.containing(p), r);
            PENDING.add(new DestroyJob(level.dimension(), player.getUUID(),
                    new ArrayDeque<>(candidates), maxHardness, stack.copy()));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        if (PENDING.isEmpty()) return;
        var server = e.getServer();
        int budget = AE2LTCommonConfig.railgunTerrainBlocksPerTick();
        boolean dropItems = AE2LTCommonConfig.railgunTerrainDropItems();

        Iterator<DestroyJob> it = PENDING.iterator();
        while (it.hasNext() && budget > 0) {
            DestroyJob job = it.next();
            ServerLevel level = server.getLevel(job.dim());
            if (level == null) {
                it.remove();
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(job.playerId());

            Deque<BlockPos> queue = job.queue();
            while (budget > 0 && !queue.isEmpty()) {
                BlockPos pos = queue.pollFirst();
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) {
                    budget--;
                    continue;
                }
                if (!canBreak(level, pos, state, job.maxHardness())) {
                    budget--;
                    continue;
                }
                if (dropItems && player != null) {
                    Block.dropResources(state, level, pos, level.getBlockEntity(pos), player, job.toolStack());
                }
                level.setBlock(pos, Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                if (level.random.nextInt(12) == 0) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            2, 0.3, 0.3, 0.3, 0.05);
                }
                budget--;
            }
            if (queue.isEmpty()) it.remove();
        }
    }

    private static boolean canBreak(Level level, BlockPos pos, BlockState state, float maxHardness) {
        if (state.is(BlockTags.WITHER_IMMUNE)) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Container) return false;
        if (be instanceof SignBlockEntity || be instanceof BannerBlockEntity || be instanceof BedBlockEntity) {
            return false;
        }
        // Identity-set lookup replaces a per-block registry id + namespace match.
        if (protectedBlocks().contains(state.getBlock())) return false;
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) return false;
        return hardness <= maxHardness;
    }

    /** Lazy-init the protected-block set on first use (after registries are frozen). */
    private static java.util.Set<Block> protectedBlocks() {
        java.util.Set<Block> cached = PROTECTED_BLOCKS;
        if (cached != null) return cached;
        java.util.Set<Block> set = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (var entry : BuiltInRegistries.BLOCK.entrySet()) {
            String ns = entry.getKey().location().getNamespace();
            if (ns.equals(AE2LightningTech.MODID) || ns.equals("ae2") || ns.equals("appliedenergistics2")) {
                set.add(entry.getValue());
            }
        }
        PROTECTED_BLOCKS = set;
        return set;
    }

    private static List<BlockPos> collectSphere(BlockPos center, double radius) {
        int r = (int) Math.ceil(radius);
        double r2 = radius * radius;
        List<BlockPos> list = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    list.add(center.offset(dx, dy, dz).immutable());
                }
            }
        }
        return list;
    }
}
