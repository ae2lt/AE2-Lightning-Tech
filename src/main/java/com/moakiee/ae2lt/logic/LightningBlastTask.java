package com.moakiee.ae2lt.logic;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;

public class LightningBlastTask {
    public record TickResult(int consumedBlocks, int consumedLightning) {}
    private record BlastCandidate(BlockPos pos, double priority) implements Comparable<BlastCandidate> {
        @Override
        public int compareTo(BlastCandidate other) {
            return Double.compare(this.priority, other.priority);
        }
    }

    public static final int DEFAULT_RADIUS = 48;
    public static final int DEFAULT_BLOCKS_PER_TICK = 1800;
    public static final int DEFAULT_INITIAL_LIGHTNING_COUNT = 16;
    public static final int DEFAULT_AFTERSHOCK_TICKS = 8;
    public static final int DEFAULT_AFTERSHOCK_MIN_LIGHTNING = 3;
    public static final int DEFAULT_AFTERSHOCK_MAX_LIGHTNING = 6;
    public static final int DEFAULT_SHORT_THUNDERSTORM_TICKS = 160;
    public static final int DEFAULT_LIGHTNING_HORIZONTAL_RADIUS = 24;
    public static final int DEFAULT_SHELLS_PER_TICK = 3;
    private static final float INITIAL_BLAST_DAMAGE_CORE = 100.0F;
    private static final float INITIAL_BLAST_DAMAGE_EDGE = 50.0F;
    private static final double CORE_RADIUS_RATIO = 0.28D;
    private static final double INNER_BLAST_SCORE = 24.0D;
    private static final double OUTER_BLAST_SCORE = 3.5D;
    private static final double CORE_BLAST_BONUS = 10.0D;
    private static final double HARDNESS_WEIGHT = 1.05D;
    private static final double RESISTANCE_WEIGHT = 0.14D;
    private static final double CHANCE_FLOOR = 3.0D;
    private static final double CHANCE_DIVISOR = 9.0D;
    private static final int QUEUE_LOOKAHEAD_SHELLS = 8;
    private static final double SHELL_PRIORITY_JITTER = 2.75D;

    private static final Set<Block> PROTECTED_BLOCKS = Set.of(
            Blocks.BEDROCK,
            Blocks.BARRIER,
            Blocks.END_PORTAL_FRAME,
            Blocks.COMMAND_BLOCK,
            Blocks.CHAIN_COMMAND_BLOCK,
            Blocks.REPEATING_COMMAND_BLOCK);

    private final ServerLevel level;
    private final BlockPos center;
    private final int radius;
    private final int radiusSquared;
    private final int blocksPerTick;
    private final int shellsPerTick;

    private final PriorityQueue<BlastCandidate> pendingBlastBlocks;
    private final Long2BooleanOpenHashMap chunkProtectionCache = new Long2BooleanOpenHashMap();
    private FakePlayer breakerPlayer;
    private int nextShellRadiusToQueue;
    private boolean scanFinished;
    private int destroyedBlocks;
    private boolean strikeSequenceStarted;
    private int remainingAftershockTicks;
    private int pendingLightningStrikes;
    private boolean centerStrikeSpawned;
    private boolean completed;

    public LightningBlastTask(ServerLevel level, BlockPos center) {
        this(level, center, DEFAULT_RADIUS, DEFAULT_BLOCKS_PER_TICK, DEFAULT_SHELLS_PER_TICK);
    }

    public LightningBlastTask(ServerLevel level, BlockPos center, int radius, int blocksPerTick) {
        this(level, center, radius, blocksPerTick, DEFAULT_SHELLS_PER_TICK);
    }

    public LightningBlastTask(ServerLevel level, BlockPos center, int radius, int blocksPerTick, int shellsPerTick) {
        this.level = level;
        this.center = center.immutable();
        this.radius = Math.max(0, radius);
        this.radiusSquared = this.radius * this.radius;
        this.blocksPerTick = Math.max(1, blocksPerTick);
        this.shellsPerTick = Math.max(1, shellsPerTick);
        this.pendingBlastBlocks = new PriorityQueue<>();
        this.nextShellRadiusToQueue = 0;
    }

    public ServerLevel getLevel() {
        return this.level;
    }

    public BlockPos getCenter() {
        return this.center;
    }

    public int getRadius() {
        return this.radius;
    }

    public int getBlocksPerTick() {
        return this.blocksPerTick;
    }

    public int getShellsPerTick() {
        return this.shellsPerTick;
    }

    public int getDestroyedBlocks() {
        return this.destroyedBlocks;
    }

    public boolean isCompleted() {
        return this.completed;
    }

    public TickResult tick(int blockBudget, int lightningBudget) {
        if (this.completed) {
            return new TickResult(0, 0);
        }

        int consumedLightning = processLightning(lightningBudget);
        int consumedBlocks = processShells(blockBudget);

        if (this.scanFinished && this.remainingAftershockTicks <= 0 && this.pendingLightningStrikes <= 0) {
            this.completed = true;
        }

        return new TickResult(consumedBlocks, consumedLightning);
    }

    private int processLightning(int lightningBudget) {
        if (lightningBudget <= 0) {
            return 0;
        }

        if (!this.strikeSequenceStarted) {
            startStrikeSequence();
        }

        int spawned = 0;
        while (spawned < lightningBudget) {
            if (this.pendingLightningStrikes > 0) {
                BlockPos strikePos = this.centerStrikeSpawned
                        ? getRandomStrikePos()
                        : resolveStrikePos(this.center.getX(), this.center.getZ(), this.center.getY());
                spawnLightningAt(strikePos);
                this.centerStrikeSpawned = true;
                this.pendingLightningStrikes--;
                spawned++;
                continue;
            }

            if (this.remainingAftershockTicks <= 0) {
                break;
            }

            queueAftershockBurst();
            this.remainingAftershockTicks--;
        }

        return spawned;
    }

    private int processShells(int blockBudget) {
        if (this.scanFinished || blockBudget <= 0) {
            return 0;
        }

        queuePendingShells();
        int destroyLimit = Math.min(this.blocksPerTick, blockBudget);
        int destroyedThisTick = 0;

        while (!this.scanFinished && destroyedThisTick < destroyLimit) {
            if (this.pendingBlastBlocks.isEmpty()) {
                queuePendingShells();
                if (this.pendingBlastBlocks.isEmpty()) {
                    this.scanFinished = true;
                    break;
                }
            }

            BlockPos pos = this.pendingBlastBlocks.poll().pos();
            if (!this.level.isInWorldBounds(pos) || !this.level.hasChunkAt(pos)) {
                continue;
            }

            BlockState state = this.level.getBlockState(pos);
            if (isBreakProtected(pos, state)) {
                continue;
            }
            if (!shouldDestroy(pos, state, this.level, this.center, this.radius)) {
                continue;
            }

            destroyBlockFast(pos, state);
            destroyedThisTick++;
            this.destroyedBlocks++;

            if (this.pendingBlastBlocks.size() < destroyLimit) {
                queuePendingShells();
            }
        }

        if (this.nextShellRadiusToQueue > this.radius && this.pendingBlastBlocks.isEmpty()) {
            this.scanFinished = true;
        }

        return destroyedThisTick;
    }

    private void queuePendingShells() {
        int queuedShells = 0;
        while (this.nextShellRadiusToQueue <= this.radius && queuedShells < QUEUE_LOOKAHEAD_SHELLS) {
            int shellRadius = this.nextShellRadiusToQueue++;
            for (BlockPos pos : buildShell(shellRadius)) {
                double jitter = (this.level.random.nextDouble() * 2.0D - 1.0D) * SHELL_PRIORITY_JITTER;
                this.pendingBlastBlocks.add(new BlastCandidate(pos, shellRadius + jitter));
            }
            queuedShells++;
        }
    }

    private void startStrikeSequence() {
        this.strikeSequenceStarted = true;
        this.remainingAftershockTicks = DEFAULT_AFTERSHOCK_TICKS;
        this.pendingLightningStrikes = DEFAULT_INITIAL_LIGHTNING_COUNT;

        applyInitialBlastDamage();
        tryStartShortThunderstorm();
    }

    private void applyInitialBlastDamage() {
        double r = Math.max(1, this.radius);
        AABB aabb = new AABB(
                this.center.getX() - r, this.center.getY() - r, this.center.getZ() - r,
                this.center.getX() + r + 1, this.center.getY() + r + 1, this.center.getZ() + r + 1);
        Vec3 centerVec = Vec3.atCenterOf(this.center);
        double radiusSq = r * r;
        DamageSource source = this.level.damageSources().lightningBolt();
        for (LivingEntity entity : this.level.getEntitiesOfClass(LivingEntity.class, aabb)) {
            double dSq = entity.position().distanceToSqr(centerVec);
            if (dSq > radiusSq) {
                continue;
            }
            double falloff = 1.0D - Math.sqrt(dSq) / r;
            float damage = (float) (INITIAL_BLAST_DAMAGE_EDGE
                    + (INITIAL_BLAST_DAMAGE_CORE - INITIAL_BLAST_DAMAGE_EDGE) * falloff);
            entity.hurt(source, damage);
        }
    }

    private void queueAftershockBurst() {
        this.pendingLightningStrikes += DEFAULT_AFTERSHOCK_MIN_LIGHTNING
                + this.level.random.nextInt(DEFAULT_AFTERSHOCK_MAX_LIGHTNING - DEFAULT_AFTERSHOCK_MIN_LIGHTNING + 1);
    }

    private BlockPos getRandomStrikePos() {
        int horizontalRadius = Math.min(this.radius, DEFAULT_LIGHTNING_HORIZONTAL_RADIUS);
        while (true) {
            int dx = this.level.random.nextInt(horizontalRadius * 2 + 1) - horizontalRadius;
            int dz = this.level.random.nextInt(horizontalRadius * 2 + 1) - horizontalRadius;
            if (dx * dx + dz * dz > horizontalRadius * horizontalRadius) {
                continue;
            }

            return resolveStrikePos(this.center.getX() + dx, this.center.getZ() + dz, this.center.getY());
        }
    }

    private BlockPos resolveStrikePos(int x, int z, int fallbackY) {
        int minY = this.level.getMinBuildHeight();
        int maxY = this.level.getMaxBuildHeight() - 1;
        int y = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

        if (y <= minY) {
            y = fallbackY;
        } else {
            y = Math.min(y, maxY);
        }

        BlockPos candidate = new BlockPos(x, clamp(y, minY, maxY), z);
        if (!this.level.isInWorldBounds(candidate)) {
            candidate = new BlockPos(this.center.getX(), clamp(fallbackY, minY, maxY), this.center.getZ());
        }

        if (!this.level.hasChunkAt(candidate)) {
            return new BlockPos(this.center.getX(), clamp(fallbackY, minY, maxY), this.center.getZ());
        }

        if (this.level.getBlockState(candidate).isAir()) {
            for (int dy = 1; dy <= 6 && candidate.getY() - dy >= minY; dy++) {
                BlockPos lower = candidate.below(dy);
                if (!this.level.getBlockState(lower).isAir()) {
                    return lower.above();
                }
            }
        }

        return candidate;
    }

    private void spawnLightningAt(BlockPos strikePos) {
        LightningBolt lightningBolt = EntityType.LIGHTNING_BOLT.create(this.level);
        if (lightningBolt == null) {
            return;
        }

        Vec3 centerPos = Vec3.atBottomCenterOf(strikePos);
        lightningBolt.moveTo(centerPos.x, strikePos.getY(), centerPos.z);
        lightningBolt.setVisualOnly(false);
        this.level.addFreshEntity(lightningBolt);
    }

    private void tryStartShortThunderstorm() {
        if (!WeatherControlHelper.supportsWeather(this.level)) {
            return;
        }

        WeatherControlHelper.setThunderstorm(this.level, DEFAULT_SHORT_THUNDERSTORM_TICKS);
    }

    private static boolean shouldDestroy(BlockPos pos, BlockState state, ServerLevel level, BlockPos center, int radius) {
        if (state.isAir()) {
            return false;
        }

        if (isProtectedBlock(state)) {
            return false;
        }

        if (!state.getFluidState().isEmpty()) {
            return false;
        }

        double hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0F) {
            return false;
        }

        double safeRadius = Math.max(1.0D, radius);
        double distanceRatio = Math.min(1.0D, Math.sqrt(center.distSqr(pos)) / safeRadius);
        double blastScore = computeBlastScore(distanceRatio, radius);
        double blockScore = hardness * HARDNESS_WEIGHT + state.getBlock().getExplosionResistance() * RESISTANCE_WEIGHT;

        if (distanceRatio <= CORE_RADIUS_RATIO) {
            return blockScore <= blastScore + CORE_BLAST_BONUS;
        }

        double chance = clampDouble((blastScore - blockScore + CHANCE_FLOOR) / CHANCE_DIVISOR, 0.0D, 1.0D);
        return chance > 0.0D && level.random.nextDouble() < chance;
    }

    private static double computeBlastScore(double distanceRatio, int radius) {
        double radialStrength = 1.0D - clampDouble(distanceRatio, 0.0D, 1.0D);
        double radiusBonus = Math.min(4.0D, radius / 16.0D);
        return OUTER_BLAST_SCORE + radialStrength * (INNER_BLAST_SCORE + radiusBonus);
    }

    private void destroyBlockFast(BlockPos pos, BlockState state) {
        state.getBlock().destroy(this.level, pos, state);
        this.level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_CLIENTS, 0);
    }

    private static boolean isProtectedBlock(BlockState state) {
        for (Block block : PROTECTED_BLOCKS) {
            if (state.is(block)) {
                return true;
            }
        }
        return false;
    }

    private List<BlockPos> buildShell(int shellRadius) {
        if (shellRadius <= 0) {
            return List.of(this.center);
        }

        int outer = shellRadius * shellRadius;
        int inner = (shellRadius - 1) * (shellRadius - 1);
        List<BlockPos> shell = new ArrayList<>(shellRadius * shellRadius * 8);

        for (int dx = -shellRadius; dx <= shellRadius; dx++) {
            int dx2 = dx * dx;
            for (int dy = -shellRadius; dy <= shellRadius; dy++) {
                int dy2 = dy * dy;
                for (int dz = -shellRadius; dz <= shellRadius; dz++) {
                    int distanceSq = dx2 + dy2 + dz * dz;
                    if (distanceSq > outer || distanceSq <= inner) {
                        continue;
                    }

                    shell.add(this.center.offset(dx, dy, dz).immutable());
                }
            }
        }

        return shell;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean isBreakProtected(BlockPos pos, BlockState state) {
        long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        if (this.chunkProtectionCache.containsKey(key)) {
            return this.chunkProtectionCache.get(key);
        }
        Player breaker = getBreakerPlayer();
        boolean cancelled;
        try {
            BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(this.level, pos, state, breaker);
            NeoForge.EVENT_BUS.post(event);
            cancelled = event.isCanceled();
        } catch (Throwable ignored) {
            cancelled = true;
        }
        this.chunkProtectionCache.put(key, cancelled);
        return cancelled;
    }

    private Player getBreakerPlayer() {
        if (this.breakerPlayer == null) {
            this.breakerPlayer = OverloadBlastFakePlayer.get(this.level);
        }
        return this.breakerPlayer;
    }

}
