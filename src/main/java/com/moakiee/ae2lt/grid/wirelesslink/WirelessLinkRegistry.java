package com.moakiee.ae2lt.grid.wirelesslink;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.pathing.ChannelMode;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.parts.AEBasePart;
import com.mojang.logging.LogUtils;
import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.grid.FrequencyAccessLevel;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;
import com.moakiee.thunderbolt.ae2.channel.ChannelProviderRegistry;
import com.moakiee.thunderbolt.ae2.channel.OverloadedChannelOwnerHelper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import org.slf4j.Logger;

public final class WirelessLinkRegistry extends SavedData {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String DATA_NAME = "ae2lt_wireless_links";
    private static final int RESTORE_BATCH_SIZE = 64;
    private static final int RESTORE_INTERVAL_TICKS = 20;
    private static final int TOPOLOGY_RECONCILE_DELAY_TICKS = 2;
    private static final int CHANNEL_EXPANSION_BATCH_SIZE = 16;
    private static final Comparator<WirelessLink> LINK_PREFERENCE =
            Comparator.comparingLong(WirelessLink::createdTime)
                    .thenComparing(link -> link.linkId().toString());

    private final WirelessLinkIndex links = new WirelessLinkIndex();
    private final Map<UUID, RuntimeEntrances> runtimeConnections = new HashMap<>();
    private final Map<IGridNode, LinkedHashSet<UUID>> runtimeLinksByAnchor = new IdentityHashMap<>();
    private final LinkedHashSet<UUID> pendingChannelExpansion = new LinkedHashSet<>();
    private final List<PendingAutoConnect> pendingAutoConnect = new ArrayList<>();
    private final Map<TopologyChangeKey, PendingClusterReconcile> pendingClusterReconciles =
            new LinkedHashMap<>();

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

    private record LocatedTarget(String dimensionId, long posLong, LinkTarget target) {
        TargetLocator locator() {
            return new TargetLocator(dimensionId, posLong, target.mode(), target.sideName());
        }
    }

    private record TargetLocator(
            String dimensionId,
            long posLong,
            WirelessLinkMode mode,
            String sideName
    ) {
        TargetLocator {
            sideName = sideName == null ? "" : sideName;
        }
    }

    private record LinkInheritance(
            UUID sourceLinkId,
            int frequencyId,
            UUID ownerUuid,
            long createdTime
    ) {
        static LinkInheritance from(WirelessLink link) {
            return new LinkInheritance(
                    link.linkId(),
                    link.frequencyId(),
                    link.ownerUuid(),
                    link.createdTime());
        }
    }

    private record InheritedClusterSeed(TargetLocator locator, @Nullable LinkInheritance inheritance) {
    }

    private record TopologyChangeKey(String dimensionId, long posLong) {
    }

    private static final class PendingClusterReconcile {
        private final String dimensionId;
        private final long changedPosLong;
        private final Set<InheritedClusterSeed> inheritedSeeds = new LinkedHashSet<>();
        private final Set<UUID> sourceLinkIds = new LinkedHashSet<>();
        private boolean inspectChangedPosition;
        private int delayTicks;

        private PendingClusterReconcile(String dimensionId, long changedPosLong) {
            this.dimensionId = dimensionId;
            this.changedPosLong = changedPosLong;
            this.delayTicks = TOPOLOGY_RECONCILE_DELAY_TICKS;
        }

        private void postpone() {
            delayTicks = TOPOLOGY_RECONCILE_DELAY_TICKS;
        }
    }

    private static final class ClusterComponent {
        private final Set<IGridNode> nodes;
        private final List<LocatedTarget> anchorCandidates = new ArrayList<>();
        private final Set<LinkInheritance> inheritedLinks = new LinkedHashSet<>();

        private ClusterComponent(Set<IGridNode> nodes) {
            this.nodes = nodes;
        }
    }

    private static final class RuntimeEntrances {
        private final IdentityHashMap<IGridNode, IGridConnection> byAnchor = new IdentityHashMap<>();
        private long nextChannelCheckGameTime;

        @Nullable
        IGridConnection get(IGridNode anchor) {
            return byAnchor.get(anchor);
        }

        void put(IGridNode anchor, IGridConnection connection) {
            byAnchor.put(anchor, connection);
        }

        @Nullable
        IGridConnection remove(IGridNode anchor) {
            return byAnchor.remove(anchor);
        }

        boolean isEmpty() {
            return byAnchor.isEmpty();
        }

        Set<IGridNode> anchors() {
            return byAnchor.keySet();
        }

        Set<Map.Entry<IGridNode, IGridConnection>> entries() {
            return byAnchor.entrySet();
        }

        void deferChannelCheck(long gameTime) {
            nextChannelCheckGameTime = Math.max(nextChannelCheckGameTime, gameTime);
        }

        boolean canCheckChannels(long gameTime) {
            return gameTime >= nextChannelCheckGameTime;
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
            instance.runtimeLinksByAnchor.clear();
            instance.pendingChannelExpansion.clear();
            instance.pendingAutoConnect.clear();
            instance.pendingClusterReconciles.clear();
        }
        WirelessLinkOps.clearWirelessBridgeTracking();
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

    /**
     * Schedules a post-change pass for placements and part additions. Once AE2
     * has rebuilt its node connections, links that now land in the same physical
     * cluster are collapsed to one managed cluster link. That link may still
     * maintain several transient channel entrances at runtime.
     */
    public void queueClusterTopologyChange(ServerLevel level, BlockPos changedPos) {
        var pending = pendingClusterChange(level.dimension(), changedPos);
        pending.inspectChangedPosition = true;
        pending.postpone();
    }

    /**
     * Captures the physical neighbours of a linked cluster before a block or
     * cable-bus part is removed. Each surviving connected component inherits the
     * original frequency, so A-B-C becoming A C keeps both A and C linked.
     */
    public void prepareClusterTopologyChange(ServerLevel level, BlockPos changedPos) {
        var changedTargets = resolveAllTargetsAt(level, changedPos);
        if (changedTargets.isEmpty()) {
            return;
        }

        var alreadyHandled = PhysicalGridCluster.newIdentityNodeSet();
        PendingClusterReconcile pending = null;
        for (var changedTarget : changedTargets) {
            if (alreadyHandled.contains(changedTarget.target().node())) {
                continue;
            }

            var cluster = PhysicalGridCluster.collect(changedTarget.target().node());
            alreadyHandled.addAll(cluster);
            var clusterLinks = findLinksInCluster(cluster, level.getServer());
            var frequencies = clusterLinks.stream()
                    .map(WirelessLink::frequencyId)
                    .distinct()
                    .limit(2)
                    .toList();
            // A homogeneous cluster can safely clone its entrance onto every
            // component produced by this removal. In a conflicted cluster there
            // is no defensible way to guess which frequency a newly orphaned
            // component should inherit; surviving anchors remain authoritative.
            var inheritance = frequencies.size() == 1
                    ? clusterLinks.stream()
                            .min(LINK_PREFERENCE)
                            .map(LinkInheritance::from)
                            .orElse(null)
                    : null;
            if (clusterLinks.isEmpty()) {
                continue;
            }

            if (pending == null) {
                pending = pendingClusterChange(level.dimension(), changedPos);
                pending.inspectChangedPosition = true;
            }
            for (var link : clusterLinks) {
                pending.sourceLinkIds.add(link.linkId());
            }

            var changedNodes = changedTargets.stream()
                    .map(candidate -> candidate.target().node())
                    .filter(cluster::contains)
                    .toList();
            for (var neighbourNode : PhysicalGridCluster.directNeighbours(changedNodes)) {
                var neighbour = locateNode(neighbourNode);
                if (neighbour != null) {
                    pending.inheritedSeeds.add(new InheritedClusterSeed(
                            neighbour.locator(),
                            inheritance));
                }
            }

            // If the original entrance is not the block being removed, retain
            // it as another reconciliation seed. This also covers unusual nodes
            // whose physical neighbour cannot be converted back into a locator.
            if (inheritance != null) {
                var source = links.get(inheritance.sourceLinkId());
                if (source != null && source.posLong() != changedPos.asLong()) {
                    pending.inheritedSeeds.add(new InheritedClusterSeed(
                            locatorOf(source),
                            inheritance));
                }
            }
        }

        if (pending != null) {
            pending.postpone();
        }
    }

    private PendingClusterReconcile pendingClusterChange(ResourceKey<Level> dimension, BlockPos changedPos) {
        var key = new TopologyChangeKey(dimension.location().toString(), changedPos.asLong());
        return pendingClusterReconciles.computeIfAbsent(
                key,
                ignored -> new PendingClusterReconcile(key.dimensionId(), key.posLong()));
    }

    public void tick(MinecraftServer server) {
        processPendingClusterReconciles(server);
        processPendingAutoConnect(server);
        processPendingChannelExpansion(server);

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
        prepareClusterTopologyChange(level, changedPos);

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

        var physicalCluster = PhysicalGridCluster.collect(target.node());
        var clusterLinks = findLinksInCluster(physicalCluster, level.getServer());
        var sameFrequencyLinks = clusterLinks.stream()
                .filter(link -> link.frequencyId() == frequencyId)
                .toList();
        boolean hasOtherFrequency = clusterLinks.stream()
                .anyMatch(link -> link.frequencyId() != frequencyId);

        if (hasOtherFrequency) {
            if (automatic || sameFrequencyLinks.isEmpty()) {
                return ActionFeedback.red("ae2lt.frequency_card.cluster_frequency_conflict");
            }
            if (player == null || sameFrequencyLinks.stream()
                    .anyMatch(link -> !link.canBeRemovedBy(player.getUUID(), actorAccess.isManager()))) {
                return ActionFeedback.red("ae2lt.frequency_card.no_frequency_permission");
            }

            // A card only owns its bound frequency. Removing every entrance in
            // the conflicted cluster would let one frequency's manager delete
            // another owner's link. Remove the matching frequency, then let the
            // sole remaining frequency recover immediately.
            for (var link : sameFrequencyLinks) {
                if (links.contains(link.linkId())) {
                    removeLink(link);
                }
            }
            var component = new ClusterComponent(physicalCluster);
            component.anchorCandidates.add(new LocatedTarget(
                    level.dimension().location().toString(),
                    pos.asLong(),
                    target));
            reconcileClusterComponent(component, level.getServer());
            return ActionFeedback.green("ae2lt.frequency_card.disconnected_conflicting_frequency", frequencyId);
        }

        if (!sameFrequencyLinks.isEmpty()) {
            if (automatic) {
                return ActionFeedback.green("ae2lt.frequency_card.auto_silent_skip");
            }
            if (player == null || sameFrequencyLinks.stream()
                    .anyMatch(link -> !link.canBeRemovedBy(player.getUUID(), actorAccess.isManager()))) {
                return ActionFeedback.red("ae2lt.frequency_card.no_frequency_permission");
            }
            for (var link : sameFrequencyLinks) {
                if (links.contains(link.linkId())) {
                    removeLink(link);
                }
            }
            return ActionFeedback.green("ae2lt.frequency_card.disconnected");
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
        var updated = createAndEstablishLink(
                frequencyId,
                owner,
                new LocatedTarget(level.dimension().location().toString(), pos.asLong(), target),
                level.getServer());

        if (updated.state() == WirelessLinkState.CONNECTED) {
            return ActionFeedback.green("ae2lt.frequency_card.connected", frequencyId);
        }
        return ActionFeedback.yellow("ae2lt.frequency_card.pending");
    }

    private void processPendingClusterReconciles(MinecraftServer server) {
        if (pendingClusterReconciles.isEmpty()) {
            return;
        }

        var ready = new ArrayList<PendingClusterReconcile>();
        var iterator = pendingClusterReconciles.entrySet().iterator();
        while (iterator.hasNext()) {
            var pending = iterator.next().getValue();
            if (--pending.delayTicks <= 0) {
                ready.add(pending);
                iterator.remove();
            }
        }

        if (!ready.isEmpty()) {
            reconcileClusterTopologies(server, ready);
        }
    }

    private void reconcileClusterTopologies(
            MinecraftServer server,
            List<PendingClusterReconcile> pendingChanges) {
        var components = new ArrayList<ClusterComponent>();
        var componentByNode = new IdentityHashMap<IGridNode, ClusterComponent>();
        var sourceIds = new LinkedHashSet<UUID>();

        for (var pending : pendingChanges) {
            var dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(pending.dimensionId));
            var level = server.getLevel(dim);
            if (level == null) {
                continue;
            }

            if (pending.inspectChangedPosition) {
                for (var target : resolveAllTargetsAt(level, BlockPos.of(pending.changedPosLong))) {
                    addTargetToComponent(target, components, componentByNode);
                }
            }

            sourceIds.addAll(pending.sourceLinkIds);
            for (var seed : pending.inheritedSeeds) {
                var target = resolveLocator(seed.locator(), server);
                if (target == null) {
                    continue;
                }
                var component = addTargetToComponent(target, components, componentByNode);
                if (seed.inheritance() != null) {
                    component.inheritedLinks.add(seed.inheritance());
                }
            }
        }

        // A harmless left click also schedules this delayed check, so do not
        // reboot a linked cluster merely because removal was attempted. Only
        // tear down the transient entrances when the captured physical seeds
        // now resolve into different components (or the persisted anchor is
        // actually gone). PhysicalGridCluster excludes wireless bridges, which
        // lets us detect the split even while those entrances are still live.
        for (var sourceId : sourceIds) {
            var source = links.get(sourceId);
            if (source != null
                    && runtimeConnections.containsKey(sourceId)
                    && runtimeEntrancesSpanChangedComponents(
                            source, server, components, componentByNode)) {
                destroyRuntimeConnection(source, resolveRuntimeTargetNode(source));
            }
        }

        for (var component : components) {
            reconcileClusterComponent(component, server);
        }

        // Part removal does not necessarily fire a block-break event. Recheck
        // the original anchors immediately so a removed cable-bus part does not
        // linger in SavedData or the frequency UI until the 20-tick restore pass.
        for (var sourceId : sourceIds) {
            var source = links.get(sourceId);
            if (source == null) {
                continue;
            }
            var updated = establishOrUpdate(source, server, false);
            if (links.contains(updated.linkId())) {
                links.put(updated);
                setDirty();
            }
        }
    }

    private boolean runtimeEntrancesSpanChangedComponents(
            WirelessLink source,
            MinecraftServer server,
            List<ClusterComponent> components,
            IdentityHashMap<IGridNode, ClusterComponent> componentByNode) {
        var persisted = resolvePersistedTarget(source, server);
        if (persisted.target() == null) {
            return true;
        }
        var sourceComponent = componentByNode.get(persisted.target().node());
        if (sourceComponent == null) {
            return true;
        }

        for (var component : components) {
            if (component != sourceComponent && component.inheritedLinks.stream()
                    .anyMatch(inheritance -> inheritance.sourceLinkId().equals(source.linkId()))) {
                return true;
            }
        }

        var runtime = runtimeConnections.get(source.linkId());
        if (runtime != null) {
            for (var anchor : runtime.anchors()) {
                var component = componentByNode.get(anchor);
                if (component != null && component != sourceComponent) {
                    return true;
                }
            }
        }
        return false;
    }

    private void processPendingChannelExpansion(MinecraftServer server) {
        if (pendingChannelExpansion.isEmpty()) {
            return;
        }

        var ready = new ArrayList<UUID>(Math.min(CHANNEL_EXPANSION_BATCH_SIZE, pendingChannelExpansion.size()));
        var iterator = pendingChannelExpansion.iterator();
        while (iterator.hasNext() && ready.size() < CHANNEL_EXPANSION_BATCH_SIZE) {
            ready.add(iterator.next());
            iterator.remove();
        }

        for (var linkId : ready) {
            var link = links.get(linkId);
            if (link != null && expandRuntimeEntrances(link, server)) {
                pendingChannelExpansion.add(linkId);
            }
        }
    }

    /**
     * Adds at most one entrance after AE2 has finished the previous pathing pass.
     * Returning {@code true} keeps the cluster queued for the next stable pass.
     */
    private boolean expandRuntimeEntrances(WirelessLink link, MinecraftServer server) {
        var target = resolvePersistedTarget(link, server);
        if (target.target() == null) {
            return false;
        }

        var manager = WirelessFrequencyManager.get();
        var transmitterNode = manager == null ? null : manager.resolveNode(link.frequencyId(), server);
        if (transmitterNode == null) {
            return false;
        }

        IGridNode primaryAnchor = target.target().node();
        var runtime = runtimeConnections.get(link.linkId());
        if (runtime == null
                || !WirelessLinkOps.isConnectedTo(runtime.get(primaryAnchor), primaryAnchor, transmitterNode)) {
            return false;
        }
        long now = currentGameTime(server);
        if (!runtime.canCheckChannels(now)) {
            return true;
        }

        var cluster = PhysicalGridCluster.collect(primaryAnchor);
        boolean pruned = pruneRuntimeEntrances(link.linkId(), runtime, cluster, transmitterNode);
        if (!runtimeConnections.containsKey(link.linkId())) {
            return false;
        }
        if (pruned) {
            runtime.deferChannelCheck(now + 1);
            return true;
        }

        var grid = transmitterNode.getGrid();
        if (grid == null || grid.getPathingService().isNetworkBooting()) {
            return true;
        }
        if (!hasAvailableChannelSupply(grid)) {
            return false;
        }

        var excluded = PhysicalGridCluster.newIdentityNodeSet();
        excluded.addAll(runtime.anchors());
        IGridNode candidate;
        while ((candidate = WirelessClusterEntrancePlanner.findSupplementalEntrance(cluster, excluded)) != null) {
            if (MultiblockLinkReadiness.canKeepVirtualConnection(candidate)) {
                break;
            }
            excluded.add(candidate);
        }
        if (candidate == null) {
            return false;
        }

        try {
            var connection = WirelessLinkOps.createVirtualConnection(candidate, transmitterNode);
            runtime.put(candidate, connection);
            runtime.deferChannelCheck(now + 1);
            registerRuntimeAnchor(link.linkId(), candidate);
            LOG.debug(
                    "Added supplemental overloaded-frequency entrance for cluster link {} (entrances={})",
                    link.linkId(),
                    runtime.anchors().size());
            return true;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    private boolean pruneRuntimeEntrances(
            UUID linkId,
            RuntimeEntrances runtime,
            Set<IGridNode> physicalCluster,
            IGridNode transmitterNode) {
        boolean pruned = false;
        for (var entry : new ArrayList<>(runtime.entries())) {
            var anchor = entry.getKey();
            var connection = entry.getValue();
            if (physicalCluster.contains(anchor)
                    && WirelessLinkOps.isConnectedTo(connection, anchor, transmitterNode)) {
                continue;
            }
            runtime.remove(anchor);
            unregisterRuntimeAnchor(linkId, anchor);
            WirelessLinkOps.destroy(connection, anchor);
            MultiblockLinkReadiness.refreshAfterVirtualConnectionRemoved(anchor);
            pruned = true;
        }
        if (runtime.isEmpty()) {
            runtimeConnections.remove(linkId);
        }
        return pruned;
    }

    private boolean hasAvailableChannelSupply(IGrid grid) {
        var mode = grid.getPathingService().getChannelMode();
        if (mode == ChannelMode.INFINITE) {
            return false;
        }

        long capacity = 0;
        int factor = Math.max(1, mode.getCableCapacityFactor());
        for (var node : OverloadedChannelOwnerHelper.getAllControllerNodes(grid)) {
            if (ChannelProviderRegistry.isChannelProvider(node.getOwner())) {
                capacity += (long) OverloadedChannelOwnerHelper.channelsPerController() * factor;
            } else {
                // Match BorrowedCapacityCalculator: every vanilla controller
                // face leading out of the controller multiblock is an
                // independent 32-channel source.
                for (var connection : node.getConnections()) {
                    var other = connection.getOtherSide(node);
                    if (!(other.getOwner() instanceof ControllerBlockEntity)) {
                        capacity += 32L * factor;
                    }
                }
            }
            if (capacity >= Integer.MAX_VALUE) {
                capacity = Integer.MAX_VALUE;
                break;
            }
        }
        return OverloadedChannelOwnerHelper.countUsedChannels(grid) < capacity;
    }

    private ClusterComponent addTargetToComponent(
            LocatedTarget target,
            List<ClusterComponent> components,
            IdentityHashMap<IGridNode, ClusterComponent> componentByNode) {
        IGridNode node = target.target().node();
        var component = componentByNode.get(node);
        if (component == null) {
            var nodes = PhysicalGridCluster.collect(node);
            component = new ClusterComponent(nodes);
            components.add(component);
            for (var member : nodes) {
                componentByNode.put(member, component);
            }
        }
        boolean alreadyCandidate = component.anchorCandidates.stream()
                .anyMatch(candidate -> candidate.target().node() == node);
        if (!alreadyCandidate) {
            component.anchorCandidates.add(target);
        }
        return component;
    }

    private void reconcileClusterComponent(ClusterComponent component, MinecraftServer server) {
        var existing = findLinksInCluster(component.nodes, server);
        if (existing.isEmpty() && component.inheritedLinks.isEmpty()) {
            return;
        }

        var allInheritance = new ArrayList<LinkInheritance>(component.inheritedLinks);
        for (var link : existing) {
            allInheritance.add(LinkInheritance.from(link));
        }
        long distinctFrequencies = allInheritance.stream()
                .map(LinkInheritance::frequencyId)
                .distinct()
                .count();
        if (distinctFrequencies > 1) {
            LOG.warn(
                    "Physical ME clusters carrying different overloaded frequencies were merged; "
                            + "suspending all frequency entrances until the physical cluster is split "
                            + "or one frequency is explicitly disconnected");
            long now = currentGameTime(server);
            for (var link : existing) {
                destroyRuntimeConnection(link, resolveRuntimeTargetNode(link));
                if (links.contains(link.linkId())) {
                    links.put(link.withState(WirelessLinkState.CLUSTER_FREQUENCY_CONFLICT, now));
                }
            }
            if (!existing.isEmpty()) {
                setDirty();
            }
            return;
        }

        var winner = allInheritance.stream()
                .min(Comparator.comparingLong(LinkInheritance::createdTime)
                        .thenComparing(inheritance -> inheritance.sourceLinkId().toString()))
                .orElse(null);
        if (winner == null) {
            return;
        }

        var keep = existing.stream()
                .filter(link -> link.frequencyId() == winner.frequencyId())
                .min(LINK_PREFERENCE)
                .orElse(null);
        for (var link : existing) {
            if (keep == null || !link.linkId().equals(keep.linkId())) {
                if (links.contains(link.linkId())) {
                    removeLink(link);
                }
            }
        }

        if (keep == null) {
            var anchor = component.anchorCandidates.stream()
                    .filter(candidate -> component.nodes.contains(candidate.target().node()))
                    .findFirst()
                    .orElse(null);
            if (anchor != null) {
                createAndEstablishLink(winner.frequencyId(), winner.ownerUuid(), anchor, server);
            }
            return;
        }

        var current = links.get(keep.linkId());
        if (current != null) {
            // A removed duplicate may have shared the same block position in
            // the frequency UI index (for example two cable-bus parts). Restore
            // the surviving cluster entry before updating its runtime state.
            registerDevice(current);
            if (current.state() == WirelessLinkState.CLUSTER_FREQUENCY_CONFLICT) {
                current = current.withState(WirelessLinkState.DISCONNECTED, currentGameTime(server));
            }
            var updated = establishOrUpdate(current, server, false);
            if (links.contains(updated.linkId())) {
                links.put(updated);
                setDirty();
            }
        }
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

    private WirelessLink createAndEstablishLink(
            int frequencyId,
            UUID ownerUuid,
            LocatedTarget anchor,
            MinecraftServer server) {
        long now = currentGameTime(server);
        LinkTarget target = anchor.target();
        var link = target.mode() == WirelessLinkMode.PART
                ? WirelessLink.createPart(
                        UUID.randomUUID(),
                        frequencyId,
                        anchor.dimensionId(),
                        anchor.posLong(),
                        target.sideName(),
                        target.blockId(),
                        target.blockEntityTypeId(),
                        target.partId(),
                        target.partClassName(),
                        ownerUuid,
                        now)
                : WirelessLink.createDevice(
                        UUID.randomUUID(),
                        frequencyId,
                        anchor.dimensionId(),
                        anchor.posLong(),
                        target.blockId(),
                        target.blockEntityTypeId(),
                        ownerUuid,
                        now);
        links.put(link);
        registerDevice(link);
        setDirty();

        var updated = establishOrUpdate(link, server, false);
        if (links.contains(updated.linkId())) {
            links.put(updated);
            setDirty();
        }
        return updated;
    }

    private WirelessLink establishOrUpdate(WirelessLink link, MinecraftServer server, boolean cleanupPass) {
        var target = resolvePersistedTarget(link, server);
        if (target.state() != null) {
            return markState(link, target.state(), server, cleanupPass);
        }

        // A topology reconciliation, not the periodic restore pass, owns
        // conflict recovery. Otherwise the first record visited would silently
        // win and reconnect before the other conflicting entrance is examined.
        if (link.state() == WirelessLinkState.CLUSTER_FREQUENCY_CONFLICT) {
            return link;
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
        if (runtime != null && WirelessLinkOps.isConnectedTo(runtime.get(targetNode), targetNode, transmitterNode)) {
            registerRuntimeAnchor(link.linkId(), targetNode);
            if (!MultiblockLinkReadiness.canKeepVirtualConnection(targetNode)) {
                destroyRuntimeConnection(link, targetNode);
                return markState(link, WirelessLinkState.TARGET_NOT_READY, server, cleanupPass);
            }
            pendingChannelExpansion.add(link.linkId());
            return link.withState(WirelessLinkState.CONNECTED, currentGameTime(server)).clearInvalidTracking(currentGameTime(server));
        }
        if (runtime != null) {
            destroyRuntimeConnection(link, targetNode);
        } else {
            unregisterRuntimeAnchor(link.linkId(), targetNode);
        }

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
            var entrances = new RuntimeEntrances();
            entrances.put(targetNode, connection);
            entrances.deferChannelCheck(currentGameTime(server) + 1);
            runtimeConnections.put(link.linkId(), entrances);
            registerRuntimeAnchor(link.linkId(), targetNode);
            pendingChannelExpansion.add(link.linkId());
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
            var part = host.getPart(side);
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
        pendingChannelExpansion.remove(link.linkId());
        if (runtime == null) {
            unregisterRuntimeAnchor(link.linkId(), targetNode);
            return;
        }

        for (var entry : new ArrayList<>(runtime.entries())) {
            var anchor = entry.getKey();
            unregisterRuntimeAnchor(link.linkId(), anchor);
            WirelessLinkOps.destroy(entry.getValue(), anchor);
            MultiblockLinkReadiness.refreshAfterVirtualConnectionRemoved(anchor);
        }
        unregisterRuntimeAnchor(link.linkId(), null);
    }

    private void registerRuntimeAnchor(UUID linkId, IGridNode targetNode) {
        runtimeLinksByAnchor
                .computeIfAbsent(targetNode, ignored -> new LinkedHashSet<>())
                .add(linkId);
    }

    private void unregisterRuntimeAnchor(UUID linkId, @Nullable IGridNode targetNode) {
        if (targetNode != null) {
            var ids = runtimeLinksByAnchor.get(targetNode);
            if (ids != null) {
                ids.remove(linkId);
                if (ids.isEmpty()) {
                    runtimeLinksByAnchor.remove(targetNode);
                }
            }
            return;
        }

        var iterator = runtimeLinksByAnchor.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            entry.getValue().remove(linkId);
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }

    private List<WirelessLink> findLinksInCluster(Set<IGridNode> cluster, MinecraftServer server) {
        var ids = new LinkedHashSet<UUID>();
        var dimensions = new LinkedHashSet<String>();
        for (var node : cluster) {
            var runtimeIds = runtimeLinksByAnchor.get(node);
            if (runtimeIds != null) {
                ids.addAll(runtimeIds);
            }
            try {
                var level = node.getLevel();
                if (level != null) {
                    dimensions.add(level.dimension().location().toString());
                }
            } catch (RuntimeException ignored) {
            }
        }

        // Pending and redundant links have no live GridConnection, but their
        // loaded anchor may still belong to this cluster and must participate in
        // toggle/de-duplication decisions.
        for (var link : links.values()) {
            if (ids.contains(link.linkId())
                    || runtimeConnections.containsKey(link.linkId())
                    || !dimensions.contains(link.dimensionId())) {
                continue;
            }
            var target = resolvePersistedTarget(link, server);
            if (target.target() != null && cluster.contains(target.target().node())) {
                ids.add(link.linkId());
            }
        }

        var result = new ArrayList<WirelessLink>(ids.size());
        for (var id : ids) {
            var link = links.get(id);
            if (link != null) {
                result.add(link);
            }
        }
        result.sort(LINK_PREFERENCE);
        return result;
    }

    @Nullable
    private IGridNode resolveRuntimeTargetNode(WirelessLink link) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        var target = resolvePersistedTarget(link, server);
        return target.target() == null ? null : target.target().node();
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
            return partTarget.orElseGet(() -> TargetResolution.fail("ae2lt.frequency_card.unsupported_target"));
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
        if (part == null) {
            part = partHost.getPart(null);
            side = null;
        }
        if (part == null) {
            return Optional.empty();
        }

        var node = part.getGridNode();
        if (node == null) {
            return Optional.of(TargetResolution.fail("ae2lt.frequency_card.unsupported_target"));
        }

        return Optional.of(TargetResolution.target(
                linkTargetForPart(level, pos, part, side, node)));
    }

    private TargetResolution resolveDeviceTarget(ServerLevel level, BlockPos pos, @Nullable Direction face) {
        IInWorldGridNodeHost host = GridHelper.getNodeHost(level, pos);
        IGridNode node = null;
        if (host != null) {
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

    private List<LocatedTarget> resolveAllTargetsAt(ServerLevel level, BlockPos pos) {
        var be = level.getBlockEntity(pos);
        if (be == null
                || be instanceof OverloadedControllerBlockEntity
                || be instanceof WirelessOverloadedControllerBlockEntity
                || be instanceof ControllerBlockEntity) {
            return List.of();
        }

        var result = new ArrayList<LocatedTarget>();
        var seen = PhysicalGridCluster.newIdentityNodeSet();
        String dimensionId = level.dimension().location().toString();

        if (be instanceof IPartHost partHost) {
            addPartTarget(level, pos, partHost.getPart(null), null, dimensionId, seen, result);
            for (var side : Direction.values()) {
                addPartTarget(level, pos, partHost.getPart(side), side, dimensionId, seen, result);
            }
        }

        IInWorldGridNodeHost host = GridHelper.getNodeHost(level, pos);
        if (host != null && !(host instanceof IPartHost)) {
            for (var side : Direction.values()) {
                var node = host.getGridNode(side);
                if (node != null && seen.add(node)) {
                    result.add(new LocatedTarget(
                            dimensionId,
                            pos.asLong(),
                            linkTargetForDevice(level, pos, node)));
                }
            }
        }

        if (result.isEmpty()) {
            for (var side : Direction.values()) {
                var node = GridHelper.getExposedNode(level, pos, side);
                if (node != null && seen.add(node)) {
                    result.add(new LocatedTarget(
                            dimensionId,
                            pos.asLong(),
                            linkTargetForDevice(level, pos, node)));
                }
            }
        }
        return result;
    }

    private void addPartTarget(
            ServerLevel level,
            BlockPos pos,
            @Nullable IPart part,
            @Nullable Direction side,
            String dimensionId,
            Set<IGridNode> seen,
            List<LocatedTarget> result) {
        if (part == null) {
            return;
        }
        var node = part.getGridNode();
        if (node != null && seen.add(node)) {
            result.add(new LocatedTarget(
                    dimensionId,
                    pos.asLong(),
                    linkTargetForPart(level, pos, part, side, node)));
        }
    }

    @Nullable
    private LocatedTarget locateNode(IGridNode node) {
        Object owner;
        try {
            owner = node.getOwner();
        } catch (RuntimeException ignored) {
            return null;
        }

        if (owner instanceof AEBasePart part) {
            var be = part.getBlockEntity();
            if (be != null && be.getLevel() instanceof ServerLevel level) {
                return new LocatedTarget(
                        level.dimension().location().toString(),
                        be.getBlockPos().asLong(),
                        linkTargetForPart(level, be.getBlockPos(), part, part.getSide(), node));
            }
        }

        if (owner instanceof BlockEntity be && be.getLevel() instanceof ServerLevel level) {
            if (be instanceof IPartHost partHost) {
                var locatedPart = locatePartNode(level, be.getBlockPos(), partHost, node);
                if (locatedPart != null) {
                    return locatedPart;
                }
            }
            return new LocatedTarget(
                    level.dimension().location().toString(),
                    be.getBlockPos().asLong(),
                    linkTargetForDevice(level, be.getBlockPos(), node));
        }
        return null;
    }

    @Nullable
    private LocatedTarget locatePartNode(
            ServerLevel level,
            BlockPos pos,
            IPartHost host,
            IGridNode expectedNode) {
        var center = host.getPart(null);
        if (center != null && center.getGridNode() == expectedNode) {
            return new LocatedTarget(
                    level.dimension().location().toString(),
                    pos.asLong(),
                    linkTargetForPart(level, pos, center, null, expectedNode));
        }
        for (var side : Direction.values()) {
            var part = host.getPart(side);
            if (part != null && part.getGridNode() == expectedNode) {
                return new LocatedTarget(
                        level.dimension().location().toString(),
                        pos.asLong(),
                        linkTargetForPart(level, pos, part, side, expectedNode));
            }
        }
        return null;
    }

    @Nullable
    private LocatedTarget resolveLocator(TargetLocator locator, MinecraftServer server) {
        var dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(locator.dimensionId()));
        var level = server.getLevel(dim);
        if (level == null) {
            return null;
        }
        var pos = BlockPos.of(locator.posLong());
        if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
            return null;
        }

        if (locator.mode() == WirelessLinkMode.PART) {
            var be = level.getBlockEntity(pos);
            if (!(be instanceof IPartHost host)) {
                return null;
            }
            var side = parseDirection(locator.sideName());
            var part = host.getPart(side);
            if (part == null || part.getGridNode() == null) {
                return null;
            }
            return new LocatedTarget(
                    locator.dimensionId(),
                    locator.posLong(),
                    linkTargetForPart(level, pos, part, side, part.getGridNode()));
        }

        var resolution = resolveDeviceTarget(level, pos, null);
        if (resolution.target() == null) {
            return null;
        }
        return new LocatedTarget(locator.dimensionId(), locator.posLong(), resolution.target());
    }

    private static TargetLocator locatorOf(WirelessLink link) {
        return new TargetLocator(
                link.dimensionId(),
                link.posLong(),
                link.mode(),
                link.sideName());
    }

    private LinkTarget linkTargetForPart(
            ServerLevel level,
            BlockPos pos,
            IPart part,
            @Nullable Direction side,
            IGridNode node) {
        var be = level.getBlockEntity(pos);
        String beType = be == null
                ? "minecraft:empty"
                : BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString();
        return new LinkTarget(
                WirelessLinkMode.PART,
                node,
                side == null ? "" : side.getName(),
                BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString(),
                beType,
                partId(part),
                part.getClass().getName());
    }

    private LinkTarget linkTargetForDevice(ServerLevel level, BlockPos pos, IGridNode node) {
        var be = level.getBlockEntity(pos);
        return new LinkTarget(
                WirelessLinkMode.DEVICE,
                node,
                "",
                BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString(),
                be == null
                        ? "minecraft:empty"
                        : BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString(),
                "",
                "");
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
                "ae2lt.frequency_card.device.cluster"));
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
