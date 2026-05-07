package com.moakiee.ae2lt.mixin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;
import com.moakiee.ae2lt.grid.BorrowedCapacityCalculator;
import com.moakiee.ae2lt.grid.OverloadedChannelOwnerHelper;
import com.moakiee.ae2lt.grid.OverloadedSubtreeNode;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridMultiblock;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ChannelMode;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.me.GridConnection;
import appeng.me.GridNode;
import appeng.me.pathfinding.IPathItem;
import appeng.me.pathfinding.PathingCalculation;

/**
 * Replaces AE2's BFS-based channel assignment with Dinic's max-flow
 * for all controller networks.
 * <p>
 * Any network containing at least one controller (vanilla or overloaded)
 * uses max-flow for channel assignment. Only ad-hoc networks (no
 * controllers at all) fall through to vanilla AE2 logic.
 * <p>
 * Phase 1 (constructor TAIL): identify overloaded controllers, unify them
 * into a single BFS root for correct DFS tree propagation.
 * <p>
 * Phase 2 (tryUseChannel HEAD): return false for ALL devices in the network
 * so AE2 builds the routing tree without assigning channels.
 * <p>
 * Phase 3 (compute, before propagateAssignments): run max-flow, inject
 * winning devices into {@code channelNodes}, and set {@code usedChannels}
 * on each cable/node from the flow decomposition.
 */
@Mixin(PathingCalculation.class)
public abstract class PathingCalculationCapMixin {

    @Shadow @Final private IGrid grid;
    @Shadow @Final private Set<IPathItem> visited;
    @Shadow @Final private Queue<IPathItem>[] queues;
    @Shadow @Final private Set<GridNode> channelNodes;
    @Shadow @Final private Set<GridNode> multiblocksWithChannel;

    @Unique private List<IGridNode> ae2lt$overloadedControllers;
    @Unique private boolean ae2lt$useMaxFlow;
    @Unique private BorrowedCapacityCalculator.Result ae2lt$flowResult;

    // ── Phase 1: constructor – identify & unify overloaded controllers ──

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ae2lt$unifyOverloadedControllers(IGrid grid, CallbackInfo ci) {
        var allControllers = OverloadedChannelOwnerHelper.getAllControllerNodes(grid);

        List<IGridNode> overloaded = new ArrayList<>();
        for (var node : allControllers) {
            if (node.getOwner() instanceof OverloadedControllerBlockEntity) {
                overloaded.add(node);
            }
        }

        ae2lt$overloadedControllers = overloaded;
        boolean hasControllers = !allControllers.isEmpty();
        var channelMode = grid.getPathingService().getChannelMode();
        ae2lt$useMaxFlow = hasControllers && channelMode != ChannelMode.INFINITE;

        if (overloaded.size() <= 1) {
            return;
        }

        IGridNode source = overloaded.get(0);
        Set<IGridNode> nonSource = new ReferenceOpenHashSet<>(overloaded.subList(1, overloaded.size()));

        for (var node : nonSource) {
            if (node instanceof IPathItem p) {
                visited.remove(p);
            }
        }

        Queue<IPathItem> q0 = queues[0];
        var keep = new ArrayDeque<IPathItem>();
        while (!q0.isEmpty()) {
            var item = q0.poll();
            if (item instanceof GridConnection gc
                    && (nonSource.contains(gc.a()) || nonSource.contains(gc.b()))) {
                visited.remove((IPathItem) gc);
                gc.setControllerRoute(null);
                continue;
            }
            keep.add(item);
        }
        q0.addAll(keep);

        // BFS from source through ALL controllers (vanilla+OC) to reach non-source OCs.
        // This handles cases where overloaded controllers are separated by vanilla
        // controllers in the multiblock (e.g. OC_A — Vanilla_B — OC_C).
        Queue<IGridNode> bfs = new ArrayDeque<>();
        Set<IGridNode> bfsVisited = new ReferenceOpenHashSet<>();
        bfs.add(source);
        bfsVisited.add(source);
        while (!bfs.isEmpty()) {
            var cur = bfs.poll();
            for (var conn : cur.getConnections()) {
                if (!(conn instanceof GridConnection gc)) continue;
                var neighbor = gc.getOtherSide(cur);
                if (!bfsVisited.add(neighbor)) continue;
                if (!(neighbor.getOwner() instanceof ControllerBlockEntity)) continue;
                if (nonSource.remove(neighbor)) {
                    if (!visited.contains((IPathItem) gc)) {
                        gc.setControllerRoute((IPathItem) cur);
                        visited.add((IPathItem) gc);
                        q0.add((IPathItem) gc);
                    }
                }
                bfs.add(neighbor);
            }
        }
    }

    // ── Phase 2: skip AE2 channel assignment for ALL devices ──

    @Inject(method = "tryUseChannel", at = @At("HEAD"), cancellable = true)
    private void ae2lt$skipAllDevices(GridNode node, CallbackInfoReturnable<Boolean> cir) {
        if (ae2lt$useMaxFlow) {
            cir.setReturnValue(false);
        }
    }

    // ── Phase 3: run max-flow between BFS and DFS ──
    //   Sets static flow data so GridNode.propagateChannelsUpwards can read it
    //   during the subsequent DFS pass.

    @Inject(method = "compute",
            at = @At(value = "INVOKE",
                     target = "Lappeng/me/pathfinding/PathingCalculation;propagateAssignments()V"))
    private void ae2lt$runMaxFlowBeforeDFS(CallbackInfo ci) {
        BorrowedCapacityCalculator.clearActiveData();

        if (!ae2lt$useMaxFlow) {
            return;
        }

        ae2lt$flowResult = BorrowedCapacityCalculator.assignChannels(grid, ae2lt$overloadedControllers);
        if (ae2lt$flowResult == null) {
            return;
        }

        channelNodes.addAll(ae2lt$flowResult.channelNodes());

        for (var winner : ae2lt$flowResult.channelNodes()) {
            if (!winner.hasFlag(GridFlags.MULTIBLOCK)) continue;
            var multiblock = ((IGridNode) winner).getService(IGridMultiblock.class);
            if (multiblock == null) continue;
            var siblings = multiblock.getMultiblockNodes();
            while (siblings.hasNext()) {
                var sibling = siblings.next();
                if (sibling != null && sibling != winner) {
                    multiblocksWithChannel.add((GridNode) sibling);
                }
            }
        }

        BorrowedCapacityCalculator.activeNodeFlow = ae2lt$flowResult.nodeFlow();
        BorrowedCapacityCalculator.activeNetworkNodes = ae2lt$flowResult.networkNodes();
        BorrowedCapacityCalculator.activeConnectionFlow = ae2lt$flowResult.connectionFlow();
    }

    // ── Phase 4: force-apply max-flow results & cleanup after DFS ──

    @Inject(method = "compute", at = @At("TAIL"))
    private void ae2lt$applyFlowAndCleanup(CallbackInfo ci) {
        if (ae2lt$flowResult != null) {
            // Reset ALL connections of network nodes to 0 first.
            // AE2's DFS uses getMachineNodes(ControllerBlockEntity.class) which
            // misses overloaded controllers (exact class match). When no vanilla
            // controllers exist, the DFS never runs and stale usedChannels from
            // a previous pathing calculation are never cleared.
            Set<GridConnection> resetSeen = new ReferenceOpenHashSet<>();
            Set<IGridNode> networkNodes = ae2lt$flowResult.networkNodes();
            for (var node : networkNodes) {
                for (var conn : node.getConnections()) {
                    if (conn instanceof GridConnection gc && resetSeen.add(gc)) {
                        gc.setAdHocChannels(0);
                    }
                }
                if (node instanceof OverloadedSubtreeNode osn) {
                    osn.ae2lt$setUsedChannels(0);
                }
            }

            var nodeFlow = ae2lt$flowResult.nodeFlow();
            for (var node : networkNodes) {
                if (node instanceof OverloadedSubtreeNode osn) {
                    int flow = nodeFlow.getInt(node);
                    osn.ae2lt$setUsedChannels(flow);
                }
            }

            // Vanilla AE2 gives every non-winner multiblock sibling +1 via
            // incrementChannelCount at the end of propagateAssignments, so that
            // meetsChannelRequirements() is true for the entire cluster.
            // We overwrote usedChannels above, so re-apply the bonus here for
            // siblings we manage. Without this, the cluster's core block may
            // end up with usedChannels=0 and the whole multiblock (e.g. a
            // crafting CPU) reports isActive()=false even though the flow
            // reservation succeeded.
            for (var sibling : multiblocksWithChannel) {
                if (networkNodes.contains(sibling)) {
                    sibling.incrementChannelCount(1);
                }
            }

            var connFlow = ae2lt$flowResult.connectionFlow();
            for (var entry : connFlow.reference2IntEntrySet()) {
                entry.getKey().setAdHocChannels(entry.getIntValue());
            }
        }
        BorrowedCapacityCalculator.clearActiveData();
        ae2lt$flowResult = null;
    }
}
