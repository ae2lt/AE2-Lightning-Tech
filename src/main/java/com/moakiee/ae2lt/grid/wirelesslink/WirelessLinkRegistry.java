package com.moakiee.ae2lt.grid.wirelesslink;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.blockentity.networking.ControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.grid.FrequencyAccessLevel;
import com.qianchang.ae2lt_core.ae2.channel.OverloadedChannelOwnerHelper;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class WirelessLinkRegistry extends SavedData {
    private static final String DATA_NAME = "ae2lt_wireless_links";
    private static final int RESTORE_BATCH_SIZE = 64;
    private static final int RESTORE_INTERVAL_TICKS = 20;

    private final WirelessLinkIndex links = new WirelessLinkIndex();
    private final Map<UUID, IGridConnection> runtimeConnections = new HashMap<>();
    private final List<PendingAutoConnect> pendingAutoConnect = new ArrayList<>();

    private int restoreCooldown;
    private long nextCleanupGameTime;

    @Nullable
    private static WirelessLinkRegistry instance;

    public record ActionFeedback(String translationKey, ChatFormatting style, Object... args) {
        public static ActionFeedback green(String key, Object... args) {
            return new ActionFeedback(key, ChatFormatting.GREEN, args);
        }

        public static ActionFeedback yellow(String key, Object... args) {
            return new ActionFeedback(key, ChatFormatting.YELLOW, args);
        }

        public static ActionFeedback red(String key, Object... args) {
            return new ActionFeedback(key, ChatFormatting.RED, args);
        }
    }

    private record LinkTarget(
            WirelessLinkMode mode,
            IGridNode node,
            String sideName,
            String blockId,
            String blockEntityTypeId,
            String partId,
            String partClassName
    ) {
    }

    private record TargetResolution(@Nullable LinkTarget target, @Nullable String failureKey) {
        static TargetResolution target(LinkTarget target) {
            return new TargetResolution(target, null);
        }

        static TargetResolution fail(String key) {
            return new TargetResolution(null, key);
        }
    }

    private record PendingAutoConnect(UUID playerId, String dimensionId, long posLong, String sideName, int delayTicks) {
        PendingAutoConnect tickDown() {
            return new PendingAutoConnect(playerId, dimensionId, posLong, sideName, delayTicks - 1);
        }
    }

    public WirelessLinkRegistry() {
    }

    private WirelessLinkRegistry(CompoundTag root, HolderLookup.Provider registries) {
        read(root);
    }

    public static void onServerStart(MinecraftServer server) {
        instance = server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WirelessLinkRegistry::new, WirelessLinkRegistry::new),
                DATA_NAME);
        for (var link : instance.links.values()) {
            instance.registerDevice(link);
        }
    }

    public static void onServerStop() {
        if (instance != null) {
            instance.runtimeConnections.clear();
            instance.pendingAutoConnect.clear();
        }
        instance = null;
    }

    public static WirelessLinkRegistry get(MinecraftServer server) {
        if (instance == null) {
            onServerStart(server);
        }
        return instance;
    }

    @Nullable
    public static WirelessLinkRegistry get() {
        return instance;
    }

    public void queueAutoConnect(ServerPlayer player, ResourceKey<Level> dimension, BlockPos pos, @Nullable Direction side, int delayTicks) {
        pendingAutoConnect.add(new PendingAutoConnect(
                player.getUUID(),
                dimension.location().toString(),
                pos.asLong(),
                side == null ? "" : side.getName(),
                Math.max(1, delayTicks)));
    }

    public void tick(MinecraftServer server) {
        processPendingAutoConnect(server);

        if (++restoreCooldown < RESTORE_INTERVAL_TICKS) {
            return;
        }
        restoreCooldown = 0;

        boolean cleanupPass = shouldRunCleanup(server);
        if (cleanupPass) {
            nextCleanupGameTime = server.overworld().getGameTime()
                    + (long) AE2LTCommonConfig.frequencyCardCleanupIntervalSeconds() * 20L;
        }

        processLinks(server, cleanupPass);
    }

    public void onBlockChanged(ServerLevel level, BlockPos changedPos) {
        var candidates = links.findAllInDimension(level.dimension().location().toString());
        if (candidates.isEmpty()) {
            return;
        }

        long changedPosLong = changedPos.asLong();
        long now = currentGameTime(level.getServer());
        boolean changed = false;

        for (var link : candidates) {
            if (!links.contains(link.linkId())) {
                continue;
            }

            if (link.posLong() == changedPosLong) {
                removeLink(link);
                changed = true;
                continue;
            }

            if (!runtimeConnections.containsKey(link.linkId())) {
                continue;
            }

            var target = resolvePersistedTarget(link, level.getServer());
            if (target.target() == null) {
                continue;
            }

            IGridNode targetNode = target.target().node();
            if (MultiblockLinkReadiness.isKnownMultiblockAffectedByChange(targetNode, changedPos)) {
                destroyRuntimeConnection(link, targetNode);
                if (links.contains(link.linkId())) {
                    links.put(link.withState(WirelessLinkState.TARGET_NOT_READY, now));
                }
                changed = true;
            }
        }

        if (changed) {
            setDirty();
        }
    }

    public ActionFeedback handleManualUse(
            ServerPlayer player,
            int frequencyId,
            ServerLevel level,
            BlockPos pos,
            Direction face,
            Vec3 hitVec) {
        var nativeFeedback = handleNativeFrequencyHost(player, frequencyId, level, pos);
        if (nativeFeedback.isPresent()) {
            return nativeFeedback.get();
        }

        var resolution = resolveTarget(level, pos, face, hitVec);
        if (resolution.failureKey() != null) {
            return ActionFeedback.red(resolution.failureKey());
        }

        return connectOrDisconnectTarget(player, frequencyId, level, pos, resolution.target(), false);
    }

    /**
     * @return whether the block at {@code pos} is an AE2 network-related block
     *         (controller, frequency-binding host, part host, or any in-world
     *         node host) that a frequency card would attempt to link. Used by
     *         the terminal-held right-click handler to decide whether to
     *         intercept the interaction for linking instead of letting the
     *         block's (or the terminal's) own GUI open.
     */
    public boolean isPotentialLinkTarget(ServerLevel level, BlockPos pos) {
        var be = level.getBlockEntity(pos);
        if (be instanceof OverloadedControllerBlockEntity
                || be instanceof WirelessOverloadedControllerBlockEntity
                || be instanceof ControllerBlockEntity) {
            return true;
        }
        if (be instanceof com.moakiee.ae2lt.api.frequency.FrequencyBindingHost) {
            return true;
        }
        if (be instanceof IPartHost) {
            return true;
        }
        return GridHelper.getNodeHost(level, pos) != null;
    }

    private Optional<ActionFeedback> handleNativeFrequencyHost(
            ServerPlayer player,
            int frequencyId,
            ServerLevel level,
            BlockPos pos) {
        var be = level.getBlockEntity(pos);
        if (!(be instanceof com.moakiee.ae2lt.api.frequency.FrequencyBindingHost host)
                || be instanceof WirelessOverloadedControllerBlockEntity) {
            return Optional.empty();
        }

        int currentFrequency = host.getFrequencyId();
        if (currentFrequency == frequencyId) {
            host.clearFrequency();
            return Optional.of(ActionFeedback.green("ae2lt.frequency_card.disconnected"));
        }
        if (currentFrequency > 0) {
            return Optional.of(ActionFeedback.red("ae2lt.frequency_card.other_frequency"));
        }

        var safety = evaluateNativeHostSafety(host, frequencyId, level.getServer());
        if (safety == NativeHostSafety.PENDING) {
            host.setFrequency(frequencyId);
            return Optional.of(feedbackForNativeHostSafety(safety, false));
        }
        if (safety != NativeHostSafety.READY) {
            return Optional.of(feedbackForNativeHostSafety(safety, false));
        }

        host.setFrequency(frequencyId);
        return Optional.of(ActionFeedback.green("ae2lt.frequency_card.connected", frequencyId));
    }

    private ActionFeedback connectOrDisconnectTarget(
            @Nullable ServerPlayer player,
            int frequencyId,
            ServerLevel level,
            BlockPos pos,
            LinkTarget target,
            boolean automatic) {
        var manager = WirelessFrequencyManager.get();
        var frequency = manager == null ? null : manager.getFrequency(frequencyId);
        if (frequency == null) {
            return ActionFeedback.red("ae2lt.frequency_card.frequency_unavailable");
        }
        FrequencyAccessLevel actorAccess = player == null
                ? FrequencyAccessLevel.BLOCKED
                : frequency.getPlayerAccess(player);

        var existing = findLink(frequencyId, level.dimension(), pos, target.mode(), target.sideName());
        if (existing != null) {
            if (player == null || !existing.canBeRemovedBy(player.getUUID(), actorAccess.isManager())) {
                return ActionFeedback.red("ae2lt.frequency_card.no_frequency_permission");
            }
            removeLink(existing);
            return ActionFeedback.green("ae2lt.frequency_card.disconnected");
        }

        if (findAnyLink(level.dimension(), pos, target.mode(), target.sideName()) != null) {
            return ActionFeedback.red(target.mode() == WirelessLinkMode.PART
                    ? "ae2lt.frequency_card.part_other_frequency"
                    : "ae2lt.frequency_card.other_frequency");
        }

        // Frequency-card links require an advanced transmitter. Reject creation
        // when the frequency's transmitter is missing or a normal controller.
        if (!manager.isAdvancedTransmitter(frequencyId)) {
            return ActionFeedback.red("ae2lt.frequency_card.requires_advanced_transmitter");
        }

        IGridNode transmitterNode = manager.resolveNode(frequencyId, level.getServer());
        if (transmitterNode != null && alreadyHasFrequencyChannel(target.node(), transmitterNode)) {
            return automatic
                    ? ActionFeedback.green("ae2lt.frequency_card.auto_silent_skip")
                    : ActionFeedback.yellow("ae2lt.frequency_card.already_in_frequency");
        }

        if (transmitterNode != null && wouldMergeControllerNetworks(target.node().getGrid(), transmitterNode.getGrid())) {
            return ActionFeedback.red("ae2lt.frequency_card.controller_conflict");
        }

        UUID owner = player == null ? new UUID(0L, 0L) : player.getUUID();
        long now = level.getGameTime();
        var link = target.mode() == WirelessLinkMode.PART
                ? WirelessLink.createPart(
                        UUID.randomUUID(),
                        frequencyId,
                        level.dimension().location().toString(),
                        pos.asLong(),
                        target.sideName(),
                        target.blockId(),
                        target.blockEntityTypeId(),
                        target.partId(),
                        target.partClassName(),
                        owner,
                        now)
                : WirelessLink.createDevice(
                        UUID.randomUUID(),
                        frequencyId,
                        level.dimension().location().toString(),
                        pos.asLong(),
                        target.blockId(),
                        target.blockEntityTypeId(),
                        owner,
                        now);
        links.put(link);
        registerDevice(link);
        setDirty();

        var updated = establishOrUpdate(link, level.getServer(), false);
        links.put(updated);
        setDirty();

        if (updated.state() == WirelessLinkState.CONNECTED) {
            return ActionFeedback.green("ae2lt.frequency_card.connected", frequencyId);
        }
        return ActionFeedback.yellow("ae2lt.frequency_card.pending");
    }

    private void processPendingAutoConnect(MinecraftServer server) {
        if (pendingAutoConnect.isEmpty()) {
            return;
        }

        var ready = new ArrayList<PendingAutoConnect>();
        for (int i = pendingAutoConnect.size() - 1; i >= 0; i--) {
            var pending = pendingAutoConnect.get(i).tickDown();
            if (pending.delayTicks() <= 0) {
                ready.add(pending);
                pendingAutoConnect.remove(i);
            } else {
                pendingAutoConnect.set(i, pending);
            }
        }

        for (var pending : ready) {
            processOnePendingAutoConnect(server, pending);
        }
    }

    private void processOnePendingAutoConnect(MinecraftServer server, PendingAutoConnect pending) {
        var player = server.getPlayerList().getPlayer(pending.playerId());
        if (player == null) {
            return;
        }

        var dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(pending.dimensionId()));
        var level = server.getLevel(dim);
        if (level == null) {
            return;
        }

        var stack = OverloadedFrequencyCardItem.findAutoConnectCard(player).orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) {
            if (OverloadedFrequencyCardItem.hasMultipleAutoConnectCandidates(player)) {
                player.displayClientMessage(Component.translatable("ae2lt.frequency_card.auto_ambiguous")
                        .withStyle(ChatFormatting.RED), true);
            }
            return;
        }

        var data = OverloadedFrequencyCardItem.getData(stack);
        if (!data.isBound()) {
            return;
        }

        var manager = WirelessFrequencyManager.get();
        var frequency = manager == null ? null : manager.getFrequency(data.frequencyId());
        if (frequency == null || !frequency.canPlayerAccess(player, "")) {
            return;
        }

        var nativeFeedback = autoConnectNativeFrequencyHost(level, BlockPos.of(pending.posLong()), data.frequencyId());
        if (nativeFeedback.isPresent()) {
            var feedback = nativeFeedback.get();
            if (!"ae2lt.frequency_card.auto_silent_skip".equals(feedback.translationKey())
                    && feedback.style() != ChatFormatting.GREEN) {
                player.displayClientMessage(Component.translatable(feedback.translationKey(), feedback.args())
                        .withStyle(feedback.style()), true);
            }
            return;
        }

        Direction side = parseDirection(pending.sideName());
        var resolution = resolveTarget(level, BlockPos.of(pending.posLong()), side, null);
        if (resolution.target() == null) {
            return;
        }

        var feedback = connectOrDisconnectTarget(
                player,
                data.frequencyId(),
                level,
                BlockPos.of(pending.posLong()),
                resolution.target(),
                true);
        if (!"ae2lt.frequency_card.auto_silent_skip".equals(feedback.translationKey())
                && feedback.style() != ChatFormatting.GREEN) {
            player.displayClientMessage(Component.translatable(feedback.translationKey(), feedback.args())
                    .withStyle(feedback.style()), true);
        }
    }

    private Optional<ActionFeedback> autoConnectNativeFrequencyHost(ServerLevel level, BlockPos pos, int frequencyId) {
        var be = level.getBlockEntity(pos);
        if (!(be instanceof com.moakiee.ae2lt.api.frequency.FrequencyBindingHost host)
                || be instanceof WirelessOverloadedControllerBlockEntity) {
            return Optional.empty();
        }

        int currentFrequency = host.getFrequencyId();
        if (currentFrequency <= 0) {
            var safety = evaluateNativeHostSafety(host, frequencyId, level.getServer());
            if (safety == NativeHostSafety.PENDING) {
                host.setFrequency(frequencyId);
                return Optional.of(feedbackForNativeHostSafety(safety, true));
            }
            if (safety != NativeHostSafety.READY) {
                return Optional.of(feedbackForNativeHostSafety(safety, true));
            }
            host.setFrequency(frequencyId);
            return Optional.of(ActionFeedback.green("ae2lt.frequency_card.connected", frequencyId));
        }
        return Optional.of(currentFrequency == frequencyId
                ? ActionFeedback.green("ae2lt.frequency_card.auto_silent_skip")
                : ActionFeedback.red("ae2lt.frequency_card.other_frequency"));
    }

    private ActionFeedback feedbackForNativeHostSafety(NativeHostSafety safety, boolean automatic) {
        return switch (safety) {
            case READY -> ActionFeedback.green("ae2lt.frequency_card.connected");
            case PENDING -> ActionFeedback.yellow("ae2lt.frequency_card.pending");
            case ALREADY_IN_FREQUENCY -> automatic
                    ? ActionFeedback.green("ae2lt.frequency_card.auto_silent_skip")
                    : ActionFeedback.yellow("ae2lt.frequency_card.already_in_frequency");
            case CONTROLLER_CONFLICT -> ActionFeedback.red("ae2lt.frequency_card.controller_conflict");
        };
    }

    private NativeHostSafety evaluateNativeHostSafety(
            com.moakiee.ae2lt.api.frequency.FrequencyBindingHost host,
            int frequencyId,
            MinecraftServer server) {
        var manager = WirelessFrequencyManager.get();
        IGridNode targetNode = host.getFrequencyBindingBlockEntity().getMainNode().getNode();
        IGridNode transmitterNode = manager == null ? null : manager.resolveNode(frequencyId, server);
        boolean nodesReady = targetNode != null && transmitterNode != null;
        return NativeHostSafety.classify(
                targetNode != null,
                transmitterNode != null,
                nodesReady && alreadyHasFrequencyChannel(targetNode, transmitterNode),
                nodesReady && wouldMergeControllerNetworks(targetNode.getGrid(), transmitterNode.getGrid()));
    }

    private void processLinks(MinecraftServer server, boolean cleanupPass) {
        if (links.isEmpty()) {
            return;
        }

        int batch = cleanupPass
                ? Math.max(1, AE2LTCommonConfig.frequencyCardCleanupBatchSize())
                : RESTORE_BATCH_SIZE;
        for (var link : links.nextBatch(batch)) {
            if (links.contains(link.linkId())) {
                var updated = establishOrUpdate(link, server, cleanupPass);
                if (links.contains(updated.linkId())) {
                    links.put(updated);
                }
            }
        }
    }

    private WirelessLink establishOrUpdate(WirelessLink link, MinecraftServer server, boolean cleanupPass) {
        var target = resolvePersistedTarget(link, server);
        if (target.state() != null) {
            return markState(link, target.state(), server, cleanupPass);
        }

        var manager = WirelessFrequencyManager.get();
        var frequency = manager == null ? null : manager.getFrequency(link.frequencyId());
        if (frequency == null) {
            return markState(link, WirelessLinkState.FREQUENCY_INVALID, server, cleanupPass);
        }

        // Frequency-card links are only valid while the transmitter is an
        // advanced controller. If the frequency lost its transmitter or it was
        // swapped for a normal controller, sever any runtime connection and
        // report the link as transmitter-pending (it reconnects automatically
        // if an advanced transmitter takes the frequency again).
        if (!manager.isAdvancedTransmitter(link.frequencyId())) {
            if (runtimeConnections.containsKey(link.linkId())) {
                destroyRuntimeConnection(link, target.target().node());
            }
            return markState(link, WirelessLinkState.PENDING_TRANSMITTER, server, cleanupPass);
        }

        if (!link.ownerCanUseFrequency(frequency.getPlayerAccess(link.ownerUuid()).canUse())) {
            destroyRuntimeConnection(link, target.target().node());
            return markState(link, WirelessLinkState.PERMISSION_DENIED, server, cleanupPass);
        }

        var transmitterNode = manager.resolveNode(link.frequencyId(), server);
        if (transmitterNode == null) {
            return markState(link, WirelessLinkState.PENDING_TRANSMITTER, server, cleanupPass);
        }

        IGridNode targetNode = target.target().node();
        var runtime = runtimeConnections.get(link.linkId());
        if (WirelessLinkOps.isConnectedTo(runtime, targetNode, transmitterNode)) {
            if (!MultiblockLinkReadiness.canKeepVirtualConnection(targetNode)) {
                destroyRuntimeConnection(link, targetNode);
                return markState(link, WirelessLinkState.TARGET_NOT_READY, server, cleanupPass);
            }
            return link.withState(WirelessLinkState.CONNECTED, currentGameTime(server)).clearInvalidTracking(currentGameTime(server));
        }
        runtimeConnections.remove(link.linkId());

        if (!MultiblockLinkReadiness.canKeepVirtualConnection(targetNode)) {
            destroyRuntimeConnection(link, targetNode);
            return markState(link, WirelessLinkState.TARGET_NOT_READY, server, cleanupPass);
        }

        if (alreadyHasFrequencyChannel(targetNode, transmitterNode)) {
            return markState(link, WirelessLinkState.REDUNDANT_LINK, server, cleanupPass);
        }

        if (wouldMergeControllerNetworks(targetNode.getGrid(), transmitterNode.getGrid())) {
            return markState(link, WirelessLinkState.DISCONNECTED, server, cleanupPass);
        }

        try {
            var connection = WirelessLinkOps.createVirtualConnection(targetNode, transmitterNode);
            runtimeConnections.put(link.linkId(), connection);
            return link.withState(WirelessLinkState.CONNECTED, currentGameTime(server)).clearInvalidTracking(currentGameTime(server));
        } catch (IllegalStateException e) {
            return markState(link, WirelessLinkState.PENDING_TRANSMITTER, server, cleanupPass);
        }
    }

    private record PersistedTarget(@Nullable LinkTarget target, @Nullable WirelessLinkState state) {
        static PersistedTarget target(LinkTarget target) {
            return new PersistedTarget(target, null);
        }

        static PersistedTarget state(WirelessLinkState state) {
            return new PersistedTarget(null, state);
        }
    }

    private PersistedTarget resolvePersistedTarget(WirelessLink link, MinecraftServer server) {
        var dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(link.dimensionId()));
        var level = server.getLevel(dim);
        if (level == null) {
            return PersistedTarget.state(WirelessLinkState.PENDING_TARGET_CHUNK);
        }

        var pos = BlockPos.of(link.posLong());
        if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
            return PersistedTarget.state(WirelessLinkState.PENDING_TARGET_CHUNK);
        }

        var be = level.getBlockEntity(pos);
        if (be == null) {
            return PersistedTarget.state(WirelessLinkState.TARGET_MISSING);
        }

        var currentBlockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
        var currentBeType = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString();
        if (!currentBlockId.equals(link.blockId()) || !currentBeType.equals(link.blockEntityTypeId())) {
            return PersistedTarget.state(link.mode() == WirelessLinkMode.PART
                    ? WirelessLinkState.PART_TYPE_CHANGED
                    : WirelessLinkState.TARGET_TYPE_CHANGED);
        }

        if (link.mode() == WirelessLinkMode.PART) {
            if (!(be instanceof IPartHost host)) {
                return PersistedTarget.state(WirelessLinkState.PART_MISSING);
            }
            var side = parseDirection(link.sideName());
            var part = side == null ? null : host.getPart(side);
            if (part == null) {
                return PersistedTarget.state(WirelessLinkState.PART_MISSING);
            }
            var partId = partId(part);
            if (!partId.equals(link.partId()) || !part.getClass().getName().equals(link.partClassName())) {
                return PersistedTarget.state(WirelessLinkState.PART_TYPE_CHANGED);
            }
            var node = part.getGridNode();
            if (node == null) {
                return PersistedTarget.state(WirelessLinkState.PART_NOT_NETWORK_DEVICE);
            }
            return PersistedTarget.target(new LinkTarget(
                    WirelessLinkMode.PART,
                    node,
                    link.sideName(),
                    currentBlockId,
                    currentBeType,
                    partId,
                    part.getClass().getName()));
        }

        var resolution = resolveDeviceTarget(level, pos, null);
        if (resolution.target() == null) {
            return PersistedTarget.state(WirelessLinkState.TARGET_NOT_NETWORK_DEVICE);
        }
        return PersistedTarget.target(resolution.target());
    }

    private WirelessLink markState(WirelessLink link, WirelessLinkState state, MinecraftServer server, boolean cleanupPass) {
        long now = currentGameTime(server);
        if (!state.isCleanupCandidate()) {
            return link.withState(state, now).clearInvalidTracking(now);
        }

        // The bound target block/part is confirmed gone or replaced (chunk is
        // loaded). Remove the link right away — including its device
        // registration and any runtime virtual connection — so a destroyed
        // device never leaves a dangling link, even when periodic auto-cleanup
        // is disabled or its delay/threshold has not elapsed.
        if (state.isDeterministicFailure()) {
            var updated = link.withState(state, now);
            removeLink(updated);
            return updated;
        }

        long firstInvalid = link.firstInvalidTime() <= 0 ? now : link.firstInvalidTime();
        int checks = link.invalidCheckCount() + (cleanupPass ? 1 : 0);
        var updated = link.withState(state, now).withInvalidTracking(firstInvalid, now, checks);

        if (cleanupPass && shouldRemoveInvalid(updated, now)) {
            removeLink(updated);
        }
        return updated;
    }

    private boolean shouldRemoveInvalid(WirelessLink link, long now) {
        if (!AE2LTCommonConfig.frequencyCardEnableAutoCleanup()) {
            return false;
        }
        long delayTicks = (long) AE2LTCommonConfig.frequencyCardInvalidCleanupDelaySeconds() * 20L;
        return link.state().isCleanupCandidate()
                && link.invalidCheckCount() >= AE2LTCommonConfig.frequencyCardInvalidCleanupRequiredChecks()
                && link.firstInvalidTime() > 0
                && now - link.firstInvalidTime() >= delayTicks;
    }

    private boolean shouldRunCleanup(MinecraftServer server) {
        if (!AE2LTCommonConfig.frequencyCardEnableAutoCleanup()) {
            return false;
        }
        return server.overworld().getGameTime() >= nextCleanupGameTime;
    }

    private void removeLink(WirelessLink link) {
        destroyRuntimeConnection(link, resolveRuntimeTargetNode(link));
        links.remove(link.linkId());
        unregisterDevice(link);
        setDirty();
    }

    private void destroyRuntimeConnection(WirelessLink link, @Nullable IGridNode targetNode) {
        var runtime = runtimeConnections.remove(link.linkId());
        WirelessLinkOps.destroy(runtime, targetNode);
        if (targetNode != null) {
            MultiblockLinkReadiness.refreshAfterVirtualConnectionRemoved(targetNode);
        }
    }

    @Nullable
    private IGridNode resolveRuntimeTargetNode(WirelessLink link) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        var target = resolvePersistedTarget(link, server);
        return target.target() == null ? null : target.target().node();
    }

    @Nullable
    private WirelessLink findLink(int frequencyId, ResourceKey<Level> dimension, BlockPos pos, WirelessLinkMode mode, String sideName) {
        return links.find(frequencyId, dimension.location().toString(), pos.asLong(), mode, sideName);
    }

    @Nullable
    private WirelessLink findAnyLink(ResourceKey<Level> dimension, BlockPos pos, WirelessLinkMode mode, String sideName) {
        return links.findAny(dimension.location().toString(), pos.asLong(), mode, sideName);
    }

    private TargetResolution resolveTarget(ServerLevel level, BlockPos pos, @Nullable Direction face, @Nullable Vec3 hitVec) {
        var be = level.getBlockEntity(pos);
        if (be instanceof OverloadedControllerBlockEntity
                || be instanceof WirelessOverloadedControllerBlockEntity
                || be instanceof ControllerBlockEntity) {
            return TargetResolution.fail("ae2lt.frequency_card.target_is_controller");
        }

        if (be instanceof IPartHost partHost) {
            var partTarget = resolvePartTarget(level, pos, partHost, face, hitVec);
            return partTarget.orElseGet(() -> TargetResolution.fail("ae2lt.frequency_card.target_is_cable"));
        }

        return resolveDeviceTarget(level, pos, face);
    }

    private Optional<TargetResolution> resolvePartTarget(
            ServerLevel level,
            BlockPos pos,
            IPartHost partHost,
            @Nullable Direction face,
            @Nullable Vec3 hitVec) {
        IPart part = null;
        Direction side = null;
        if (hitVec != null) {
            var selected = partHost.selectPartWorld(hitVec);
            if (selected != null && selected.part != null) {
                part = selected.part;
                side = selected.side;
            }
        }
        if (part == null && face != null) {
            part = partHost.getPart(face);
            side = face;
        }
        if (part == null || side == null) {
            return Optional.empty();
        }

        var node = part.getGridNode();
        if (node == null) {
            return Optional.of(TargetResolution.fail("ae2lt.frequency_card.unsupported_target"));
        }

        var be = level.getBlockEntity(pos);
        String beType = be == null
                ? "minecraft:empty"
                : BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString();
        return Optional.of(TargetResolution.target(new LinkTarget(
                WirelessLinkMode.PART,
                node,
                side.getName(),
                BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString(),
                beType,
                partId(part),
                part.getClass().getName())));
    }

    private TargetResolution resolveDeviceTarget(ServerLevel level, BlockPos pos, @Nullable Direction face) {
        IInWorldGridNodeHost host = GridHelper.getNodeHost(level, pos);
        IGridNode node = null;
        if (host != null) {
            if (host instanceof IPartHost) {
                return TargetResolution.fail("ae2lt.frequency_card.target_is_cable");
            }
            for (var sideName : WirelessLinkSideProbeOrder.forPreferredSide(face == null ? "" : face.getName())) {
                var side = parseDirection(sideName);
                if (side != null) {
                    node = host.getGridNode(side);
                    if (node != null) {
                        break;
                    }
                }
            }
        }
        if (node == null) {
            for (var sideName : WirelessLinkSideProbeOrder.forPreferredSide(face == null ? "" : face.getName())) {
                var side = parseDirection(sideName);
                if (side != null) {
                    node = GridHelper.getExposedNode(level, pos, side);
                    if (node != null) {
                        break;
                    }
                }
            }
        }
        if (node == null) {
            return TargetResolution.fail("ae2lt.frequency_card.unsupported_target");
        }

        var be = level.getBlockEntity(pos);
        if (be == null) {
            return TargetResolution.fail("ae2lt.frequency_card.unsupported_target");
        }

        return TargetResolution.target(new LinkTarget(
                WirelessLinkMode.DEVICE,
                node,
                "",
                BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString(),
                BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString(),
                "",
                ""));
    }

    private static String partId(IPart part) {
        var item = part.getPartItem();
        var id = item == null ? null : IPartItem.getId(item);
        return id == null ? part.getClass().getName() : id.toString();
    }

    private static boolean isAlreadyInFrequencyGrid(IGridNode targetNode, IGridNode transmitterNode) {
        IGrid targetGrid = targetNode.getGrid();
        IGrid transmitterGrid = transmitterNode.getGrid();
        return targetGrid != null && transmitterGrid != null && targetGrid == transmitterGrid;
    }

    private static boolean alreadyHasFrequencyChannel(IGridNode targetNode, IGridNode transmitterNode) {
        return isAlreadyInFrequencyGrid(targetNode, transmitterNode)
                && targetNode.meetsChannelRequirements();
    }

    private static boolean wouldMergeControllerNetworks(@Nullable IGrid targetGrid, @Nullable IGrid frequencyGrid) {
        if (targetGrid == null || targetGrid == frequencyGrid) {
            return false;
        }
        return !OverloadedChannelOwnerHelper.getAllControllerNodes(targetGrid).isEmpty();
    }

    private void registerDevice(WirelessLink link) {
        var manager = WirelessFrequencyManager.get();
        if (manager == null) return;
        var dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(link.dimensionId()));
        manager.registerDevice(link.frequencyId(), new WirelessFrequencyManager.DeviceEntry(
                dim,
                BlockPos.of(link.posLong()),
                false,
                false,
                link.mode() == WirelessLinkMode.PART
                        ? "ae2lt.frequency_card.device.part"
                        : "block." + link.blockId().replace(':', '.')));
    }

    private void unregisterDevice(WirelessLink link) {
        var manager = WirelessFrequencyManager.get();
        if (manager == null) return;
        var dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(link.dimensionId()));
        manager.unregisterDevice(link.frequencyId(), dim, BlockPos.of(link.posLong()));
    }

    private static long currentGameTime(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    @Nullable
    private static Direction parseDirection(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (var direction : Direction.values()) {
            if (direction.getName().equals(name)) {
                return direction;
            }
        }
        return null;
    }

    private void read(CompoundTag root) {
        links.clear();
        var list = root.getList("links", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var loaded = loadLink(list.getCompound(i));
            loaded.ifPresent(links::put);
        }
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var link : links.values()) {
            list.add(saveLink(link));
        }
        root.put("links", list);
        return root;
    }

    private static CompoundTag saveLink(WirelessLink link) {
        var tag = new CompoundTag();
        for (var entry : link.toPersistentSnapshot().entrySet()) {
            tag.putString(entry.getKey(), entry.getValue());
        }
        return tag;
    }

    private static Optional<WirelessLink> loadLink(CompoundTag tag) {
        var map = new HashMap<String, String>();
        for (var key : tag.getAllKeys()) {
            map.put(key, tag.getString(key));
        }
        return WirelessLink.fromPersistentSnapshot(map);
    }
}
