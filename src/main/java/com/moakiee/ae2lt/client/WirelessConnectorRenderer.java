package com.moakiee.ae2lt.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import appeng.client.render.overlay.OverlayRenderType;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPowerSupplyBlockEntity;
import com.moakiee.ae2lt.item.OverloadedWirelessConnectorItem;
import com.moakiee.ae2lt.logic.WirelessConnectorTargetHelper;
import com.moakiee.ae2lt.api.patternprovider.WirelessPatternProviderHost;
import com.moakiee.ae2lt.util.ItemStackTagSupport;

/**
 * Client-side renderer for the Overloaded Wireless Connector.
 * <p>
 * When the player holds the connector, wireless-capable hosts within range are
 * highlighted with their connections. If a host is selected, only that host is rendered.
 * A preview overlay is shown for the selected host.
 * <p>
 * Performance notes (matches the optimization-report guidance):
 * <ul>
 *   <li>The chunk/BE scan is performed at most once every {@link #RESCAN_INTERVAL_TICKS}
 *       game ticks, not every frame. At 200fps this turns ~200 scans/sec into ~5 scans/sec
 *       while remaining visually instantaneous (鈮?00ms staleness).</li>
 *   <li>Hot-path objects ({@link Quaternionf}, scratch {@link HashSet}) are reused across
 *       frames instead of re-allocated, to keep GC pressure flat under high frame rates
 *       (the report flags high-FPS as an amplifier of any per-frame leak path).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public class WirelessConnectorRenderer {

    // Preview color: semi-transparent yellow (ARGB)
    private static final int COLOR_PREVIEW = 0x60FFFF00;
    // Connected face color: semi-transparent blue (ARGB)
    private static final int COLOR_CONNECTED = 0x900080FF;
    // Connected face outline color: bright white (ARGB)
    private static final int COLOR_CONNECTED_EDGE = 0xE0FFFFFF;
    // Preview line color: bright yellow
    private static final int COLOR_PREVIEW_LINE = 0xC0FFFF00;
    // Host inner cube color (unselected): semi-transparent blue (ARGB)
    private static final int COLOR_HOST = 0x800080FF;
    // Host inner cube color (selected): semi-transparent yellow (ARGB)
    private static final int COLOR_HOST_SELECTED = 0x80FFFF00;
    // Line color: blue (ARGB)
    private static final int COLOR_LINE = 0xC00080FF;

    private static final int SCAN_RANGE = 64;
    /** How many game ticks between full chunk re-scans for wireless hosts. */
    private static final int RESCAN_INTERVAL_TICKS = 4;

    /** Cached host positions from the last scan; refreshed every {@link #RESCAN_INTERVAL_TICKS}. */
    private static final List<BlockPos> cachedHostPositions = new ArrayList<>();
    /** Game time of the last scan. -1 means never scanned. */
    private static long lastScanTick = -1L;
    /** Dimension key of the last scan; if it changes we force a re-scan immediately. */
    private static ResourceKey<Level> lastScanDimension = null;

    /** Reusable rotation; avoids {@code new Quaternionf(...)} every frame. */
    private static final Quaternionf scratchRotation = new Quaternionf();
    /** Reusable scratch set used by {@link #collectConnectionsForFace}; cleared between calls. */
    private static final Set<BlockPos> scratchConnectionSet = new HashSet<>();

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }

        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        // Check if player is holding the wireless connector in either hand
        ItemStack stack = getHeldConnectorStack();
        if (stack.isEmpty()) {
            return;
        }

        // -- Set up camera offset --
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        poseStack.pushPose();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        // Reuse a single Quaternionf instance instead of allocating per frame.
        // mc.gameRenderer.getMainCamera().rotation() returns a reference Quaternionf
        // owned by the camera; copying-and-inverting through scratchRotation keeps
        // the camera's value untouched while avoiding a per-frame allocation.
        scratchRotation.set(mc.gameRenderer.getMainCamera().rotation());
        scratchRotation.invert();
        poseStack.mulPose(scratchRotation);
        poseStack.translate(-cam.x, -cam.y, -cam.z);

        // Resolve selected host for special coloring and preview rendering.
        var selectedHost = getSelectedHost(stack);
        var selectedPos = selectedHost != null ? selectedHost.pos() : null;
        var selectedDim = selectedHost != null ? selectedHost.dimension() : null;
        var selectedHostType = selectedHost != null ? selectedHost.hostType() : null;
        boolean hasSelection = selectedPos != null
                && selectedDim != null
                && selectedHostType != null;
        boolean selectionInCurrentDimension = hasSelection && mc.level.dimension().equals(selectedDim);
        long selectedPosLong = selectedPos != null ? selectedPos.asLong() : 0L;
        boolean selectedRendered = false;

        // Refresh the cached host position list at most every RESCAN_INTERVAL_TICKS,
        // or immediately if the player has changed dimensions since the last scan.
        long gameTime = mc.level.getGameTime();
        ResourceKey<Level> currentDim = mc.level.dimension();
        if (lastScanTick < 0L
                || lastScanDimension == null
                || !lastScanDimension.equals(currentDim)
                || gameTime - lastScanTick >= RESCAN_INTERVAL_TICKS) {
            rescanHosts(mc.level, player.blockPosition());
            lastScanTick = gameTime;
            lastScanDimension = currentDim;
        }

        // Render eligible cached hosts. We re-fetch the BlockEntity from the level
        // so connection state stays live; only which positions to consider is cached.
        for (BlockPos bePos : cachedHostPositions) {
            if (!mc.level.isLoaded(bePos)) continue;
            var be = mc.level.getBlockEntity(bePos);
            if (be instanceof WirelessPatternProviderHost provider) {
                if (!provider.isWirelessProvider()) {
                    continue;
                }
                boolean isSelected = hasSelection
                        && selectionInCurrentDimension
                        && OverloadedWirelessConnectorItem.HOST_PROVIDER.equals(selectedHostType)
                        && bePos.equals(selectedPos);
                if (!WirelessConnectorRenderFilter.shouldRenderHost(
                        hasSelection,
                        selectionInCurrentDimension,
                        selectedPosLong,
                        selectedHostType,
                        OverloadedWirelessConnectorItem.HOST_PROVIDER,
                        bePos.asLong())) {
                    continue;
                }
                renderProviderHost(poseStack, buffer, mc.level, bePos, provider, isSelected);
                selectedRendered |= isSelected;
            } else if (be instanceof OverloadedInterfaceBlockEntity iface) {
                if (iface.getInterfaceMode() != OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS) {
                    continue;
                }
                boolean isSelected = hasSelection
                        && selectionInCurrentDimension
                        && OverloadedWirelessConnectorItem.HOST_INTERFACE.equals(selectedHostType)
                        && bePos.equals(selectedPos);
                if (!WirelessConnectorRenderFilter.shouldRenderHost(
                        hasSelection,
                        selectionInCurrentDimension,
                        selectedPosLong,
                        selectedHostType,
                        OverloadedWirelessConnectorItem.HOST_INTERFACE,
                        bePos.asLong())) {
                    continue;
                }
                renderInterfaceHost(poseStack, buffer, mc.level, bePos, iface, isSelected);
                selectedRendered |= isSelected;
            } else if (be instanceof OverloadedPowerSupplyBlockEntity powerSupply) {
                boolean isSelected = hasSelection
                        && selectionInCurrentDimension
                        && OverloadedWirelessConnectorItem.HOST_POWER_SUPPLY.equals(selectedHostType)
                        && bePos.equals(selectedPos);
                if (!WirelessConnectorRenderFilter.shouldRenderHost(
                        hasSelection,
                        selectionInCurrentDimension,
                        selectedPosLong,
                        selectedHostType,
                        OverloadedWirelessConnectorItem.HOST_POWER_SUPPLY,
                        bePos.asLong())) {
                    continue;
                }
                renderPowerSupplyHost(poseStack, buffer, mc.level, bePos, powerSupply, isSelected);
                selectedRendered |= isSelected;
            }
        }

        if (selectionInCurrentDimension && !selectedRendered && mc.level.isLoaded(selectedPos)) {
            var selectedBe = mc.level.getBlockEntity(selectedPos);
            if (OverloadedWirelessConnectorItem.HOST_PROVIDER.equals(selectedHostType)
                    && selectedBe instanceof WirelessPatternProviderHost provider
                    && provider.isWirelessProvider()) {
                renderProviderHost(poseStack, buffer, mc.level, selectedPos, provider, true);
            } else if (OverloadedWirelessConnectorItem.HOST_INTERFACE.equals(selectedHostType)
                    && selectedBe instanceof OverloadedInterfaceBlockEntity iface
                    && iface.getInterfaceMode() == OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS) {
                renderInterfaceHost(poseStack, buffer, mc.level, selectedPos, iface, true);
            } else if (OverloadedWirelessConnectorItem.HOST_POWER_SUPPLY.equals(selectedHostType)
                    && selectedBe instanceof OverloadedPowerSupplyBlockEntity powerSupply) {
                renderPowerSupplyHost(poseStack, buffer, mc.level, selectedPos, powerSupply, true);
            }
        }

        // Preview face overlay (only for the selected wireless host).
        if (selectionInCurrentDimension) {
            var selectedBe = mc.level.getBlockEntity(selectedPos);
            if (OverloadedWirelessConnectorItem.HOST_PROVIDER.equals(selectedHostType)
                    && selectedBe instanceof WirelessPatternProviderHost selectedProvider
                    && selectedProvider.isWirelessProvider()
                    && mc.hitResult instanceof BlockHitResult bhr
                    && bhr.getType() == HitResult.Type.BLOCK
                    && !bhr.getBlockPos().equals(selectedPos)
                    && mc.level.getBlockEntity(bhr.getBlockPos()) != null) {

                var previewTargets = WirelessConnectorTargetHelper.collectTargets(
                        mc.level,
                        bhr.getBlockPos(),
                        net.minecraft.client.gui.screens.Screen.hasControlDown());
                Direction lookFace = bhr.getDirection();
                var existingConnections = collectConnectionsForFace(
                        selectedProvider.getConnections(),
                        mc.level,
                        lookFace,
                        c -> c.dimension(),
                        c -> c.pos(),
                        c -> c.boundFace());
                for (var lookPos : previewTargets) {
                    if (!existingConnections.contains(lookPos)) {
                        renderFaceOverlay(poseStack, buffer, lookPos, lookFace, COLOR_PREVIEW);
                        renderLine(poseStack, buffer, selectedPos, lookPos, lookFace, COLOR_PREVIEW_LINE, false);
                    }
                }
            } else if (OverloadedWirelessConnectorItem.HOST_INTERFACE.equals(selectedHostType)
                    && selectedBe instanceof OverloadedInterfaceBlockEntity selectedInterface
                    && mc.hitResult instanceof BlockHitResult bhr
                    && bhr.getType() == HitResult.Type.BLOCK
                    && !bhr.getBlockPos().equals(selectedPos)
                    && mc.level.getBlockEntity(bhr.getBlockPos()) != null) {

                var previewTargets = WirelessConnectorTargetHelper.collectTargets(
                        mc.level,
                        bhr.getBlockPos(),
                        net.minecraft.client.gui.screens.Screen.hasControlDown());
                Direction lookFace = bhr.getDirection();
                var existingConnections = collectConnectionsForFace(
                        selectedInterface.getConnections(),
                        mc.level,
                        lookFace,
                        c -> c.dimension(),
                        c -> c.pos(),
                        c -> c.boundFace());
                for (var lookPos : previewTargets) {
                    if (!existingConnections.contains(lookPos)) {
                        renderFaceOverlay(poseStack, buffer, lookPos, lookFace, COLOR_PREVIEW);
                        renderLine(poseStack, buffer, selectedPos, lookPos, lookFace, COLOR_PREVIEW_LINE, false);
                    }
                }
            } else if (OverloadedWirelessConnectorItem.HOST_POWER_SUPPLY.equals(selectedHostType)
                    && selectedBe instanceof OverloadedPowerSupplyBlockEntity selectedPowerSupply
                    && mc.hitResult instanceof BlockHitResult bhr
                    && bhr.getType() == HitResult.Type.BLOCK
                    && !bhr.getBlockPos().equals(selectedPos)
                    && mc.level.getBlockEntity(bhr.getBlockPos()) != null) {

                var previewTargets = WirelessConnectorTargetHelper.collectTargets(
                        mc.level,
                        bhr.getBlockPos(),
                        net.minecraft.client.gui.screens.Screen.hasControlDown());
                Direction lookFace = bhr.getDirection();
                var existingConnections = collectConnectionsForFace(
                        selectedPowerSupply.getConnections(),
                        mc.level,
                        lookFace,
                        c -> c.dimension(),
                        c -> c.pos(),
                        c -> c.boundFace());
                for (var lookPos : previewTargets) {
                    if (!existingConnections.contains(lookPos)) {
                        renderFaceOverlay(poseStack, buffer, lookPos, lookFace, COLOR_PREVIEW);
                        renderLine(poseStack, buffer, selectedPos, lookPos, lookFace, COLOR_PREVIEW_LINE, false);
                    }
                }
            }
        }

        poseStack.popPose();

        // Flush render batches
        buffer.endBatch(Ae2ltRenderTypes.getFaceSeeThrough());
        buffer.endBatch(Ae2ltRenderTypes.getLineSeeThrough());
        buffer.endBatch(OverlayRenderType.getBlockHilightFace());
        buffer.endBatch(OverlayRenderType.getBlockHilightLine());
    }

    // -- Render helpers --

    /**
     * Render a small cube inside the block using a see-through render type
     * (GREATER depth test) so it is visible through the opaque block.
     */
    private static void renderInnerCube(PoseStack poseStack, MultiBufferSource buffer,
            BlockPos pos, int color) {
        VertexConsumer vc = buffer.getBuffer(Ae2ltRenderTypes.getFaceSeeThrough());
        int[] c = OverlayRenderType.decomposeColor(color);

        poseStack.pushPose();
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        Matrix4f mat = poseStack.last().pose();

        float lo = 0.25f, hi = 0.75f;

        // DOWN (Y-)
        quad(vc, mat, c, lo, lo, lo,  hi, lo, lo,  hi, lo, hi,  lo, lo, hi,  0, -1, 0);
        // UP (Y+)
        quad(vc, mat, c, lo, hi, hi,  hi, hi, hi,  hi, hi, lo,  lo, hi, lo,  0, 1, 0);
        // NORTH (Z-)
        quad(vc, mat, c, lo, lo, lo,  lo, hi, lo,  hi, hi, lo,  hi, lo, lo,  0, 0, -1);
        // SOUTH (Z+)
        quad(vc, mat, c, hi, lo, hi,  hi, hi, hi,  lo, hi, hi,  lo, lo, hi,  0, 0, 1);
        // WEST (X-)
        quad(vc, mat, c, lo, lo, hi,  lo, hi, hi,  lo, hi, lo,  lo, lo, lo,  -1, 0, 0);
        // EAST (X+)
        quad(vc, mat, c, hi, lo, lo,  hi, hi, lo,  hi, hi, hi,  hi, lo, hi,  1, 0, 0);

        poseStack.popPose();
    }

    private static void renderProviderHost(PoseStack poseStack, MultiBufferSource buffer,
            Level level, BlockPos hostPos, WirelessPatternProviderHost provider, boolean selected) {
        renderInnerCube(poseStack, buffer, hostPos, selected ? COLOR_HOST_SELECTED : COLOR_HOST);

        for (var conn : provider.getConnections()) {
            if (!conn.dimension().equals(level.dimension())) continue;
            renderConnectedFace(poseStack, buffer, conn.pos(), conn.boundFace());
            renderLine(poseStack, buffer, hostPos, conn.pos(), conn.boundFace(), COLOR_LINE, true);
        }
    }

    private static void renderInterfaceHost(PoseStack poseStack, MultiBufferSource buffer,
            Level level, BlockPos hostPos, OverloadedInterfaceBlockEntity iface, boolean selected) {
        renderInnerCube(poseStack, buffer, hostPos, selected ? COLOR_HOST_SELECTED : COLOR_HOST);

        for (var conn : iface.getConnections()) {
            if (!conn.dimension().equals(level.dimension())) continue;
            renderConnectedFace(poseStack, buffer, conn.pos(), conn.boundFace());
            renderLine(poseStack, buffer, hostPos, conn.pos(), conn.boundFace(), COLOR_LINE, true);
        }
    }

    private static void renderPowerSupplyHost(PoseStack poseStack, MultiBufferSource buffer,
            Level level, BlockPos hostPos, OverloadedPowerSupplyBlockEntity powerSupply, boolean selected) {
        renderInnerCube(poseStack, buffer, hostPos, selected ? COLOR_HOST_SELECTED : COLOR_HOST);

        for (var conn : powerSupply.getConnections()) {
            if (!conn.dimension().equals(level.dimension())) continue;
            renderConnectedFace(poseStack, buffer, conn.pos(), conn.boundFace());
            renderLine(poseStack, buffer, hostPos, conn.pos(), conn.boundFace(), COLOR_LINE, true);
        }
    }

    /**
     * Renders the bound face of an established connection: a see-through blue
     * quad plus a bright outline, so the connected side stays visible even when
     * the face is hidden behind opaque blocks (e.g. a machine pushed against a wall).
     */
    private static void renderConnectedFace(PoseStack poseStack, MultiBufferSource buffer,
            BlockPos pos, Direction face) {
        renderFaceOverlaySeeThrough(poseStack, buffer, pos, face, COLOR_CONNECTED);
        renderFaceOutline(poseStack, buffer, pos, face, COLOR_CONNECTED_EDGE);
    }

    private static void quad(VertexConsumer vc, Matrix4f mat, int[] c,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4,
            float nx, float ny, float nz) {
        vc.vertex(mat, x1, y1, z1).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
        vc.vertex(mat, x2, y2, z2).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
        vc.vertex(mat, x3, y3, z3).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
        vc.vertex(mat, x4, y4, z4).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
    }


    /**
     * Render a single face overlay (quad) on the given block face.
     */
    private static void renderFaceOverlay(PoseStack poseStack, MultiBufferSource buffer,
            BlockPos pos, Direction face, int color) {
        renderFaceOverlayInternal(poseStack, buffer.getBuffer(OverlayRenderType.getBlockHilightFace()),
                pos, face, color);
    }

    /**
     * Render a face overlay with the see-through (GREATER depth test) render type,
     * making it visible through opaque blocks.
     */
    private static void renderFaceOverlaySeeThrough(PoseStack poseStack, MultiBufferSource buffer,
            BlockPos pos, Direction face, int color) {
        renderFaceOverlayInternal(poseStack, buffer.getBuffer(Ae2ltRenderTypes.getFaceSeeThrough()),
                pos, face, color);
    }

    private static void renderFaceOverlayInternal(PoseStack poseStack, VertexConsumer vc,
            BlockPos pos, Direction face, int color) {
        int[] c = OverlayRenderType.decomposeColor(color);

        poseStack.pushPose();
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        Matrix4f mat = poseStack.last().pose();

        // Slight offset to avoid z-fighting
        float offset = 0.001f;

        float nx = face.getStepX();
        float ny = face.getStepY();
        float nz = face.getStepZ();

        switch (face) {
            case DOWN -> {
                vc.vertex(mat, 0, -offset, 0).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
                vc.vertex(mat, 1, -offset, 0).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
                vc.vertex(mat, 1, -offset, 1).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
                vc.vertex(mat, 0, -offset, 1).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
            }
            case UP -> {
                vc.vertex(mat, 0, 1 + offset, 1).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
                vc.vertex(mat, 1, 1 + offset, 1).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
                vc.vertex(mat, 1, 1 + offset, 0).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
                vc.vertex(mat, 0, 1 + offset, 0).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
            }
            case NORTH -> {
                vc.vertex(mat, 0, 0, -offset).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
                vc.vertex(mat, 0, 1, -offset).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
                vc.vertex(mat, 1, 1, -offset).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
                vc.vertex(mat, 1, 0, -offset).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
            }
            case SOUTH -> {
                vc.vertex(mat, 1, 0, 1 + offset).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
                vc.vertex(mat, 1, 1, 1 + offset).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
                vc.vertex(mat, 0, 1, 1 + offset).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
                vc.vertex(mat, 0, 0, 1 + offset).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
            }
            case WEST -> {
                vc.vertex(mat, -offset, 0, 1).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
                vc.vertex(mat, -offset, 1, 1).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
                vc.vertex(mat, -offset, 1, 0).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
                vc.vertex(mat, -offset, 0, 0).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
            }
            case EAST -> {
                vc.vertex(mat, 1 + offset, 0, 0).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
                vc.vertex(mat, 1 + offset, 1, 0).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
                vc.vertex(mat, 1 + offset, 1, 1).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
                vc.vertex(mat, 1 + offset, 0, 1).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
            }
        }

        poseStack.popPose();
    }

    /**
     * Render the 4-edge outline of a block face with the see-through line type,
     * making the exact bound side obvious from any angle.
     */
    private static void renderFaceOutline(PoseStack poseStack, MultiBufferSource buffer,
            BlockPos pos, Direction face, int color) {
        VertexConsumer vc = buffer.getBuffer(Ae2ltRenderTypes.getLineSeeThrough());
        int[] c = OverlayRenderType.decomposeColor(color);

        poseStack.pushPose();
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        Matrix4f mat = poseStack.last().pose();

        // Slightly outside the see-through quad so the outline is not hidden behind it.
        float o = 0.004f;

        float nx = face.getStepX();
        float ny = face.getStepY();
        float nz = face.getStepZ();

        float[][] corners = switch (face) {
            case DOWN -> new float[][] {{0, -o, 0}, {1, -o, 0}, {1, -o, 1}, {0, -o, 1}};
            case UP -> new float[][] {{0, 1 + o, 0}, {1, 1 + o, 0}, {1, 1 + o, 1}, {0, 1 + o, 1}};
            case NORTH -> new float[][] {{0, 0, -o}, {1, 0, -o}, {1, 1, -o}, {0, 1, -o}};
            case SOUTH -> new float[][] {{0, 0, 1 + o}, {1, 0, 1 + o}, {1, 1, 1 + o}, {0, 1, 1 + o}};
            case WEST -> new float[][] {{-o, 0, 0}, {-o, 1, 0}, {-o, 1, 1}, {-o, 0, 1}};
            case EAST -> new float[][] {{1 + o, 0, 0}, {1 + o, 1, 0}, {1 + o, 1, 1}, {1 + o, 0, 1}};
        };

        for (int i = 0; i < 4; i++) {
            float[] a = corners[i];
            float[] b = corners[(i + 1) % 4];
            vc.vertex(mat, a[0], a[1], a[2]).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
            vc.vertex(mat, b[0], b[1], b[2]).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
        }

        poseStack.popPose();
    }

    /**
     * Render a line from provider center to the center of the connected face.
     * Established connections use the see-through line type so the line stays
     * visible even when it passes behind opaque blocks; preview lines stay on
     * the normal (non-see-through) hilight type since the player is looking
     * directly at the target face.
     */
    private static void renderLine(PoseStack poseStack, MultiBufferSource buffer,
            BlockPos from, BlockPos to, Direction face, int color, boolean seeThrough) {
        VertexConsumer vc = buffer.getBuffer(seeThrough
                ? Ae2ltRenderTypes.getLineSeeThrough()
                : OverlayRenderType.getBlockHilightLine());
        int[] c = OverlayRenderType.decomposeColor(color);

        Matrix4f mat = poseStack.last().pose();

        // Provider center
        float fx = from.getX() + 0.5f;
        float fy = from.getY() + 0.5f;
        float fz = from.getZ() + 0.5f;

        // Target face center
        float tx = to.getX() + 0.5f + face.getStepX() * 0.501f;
        float ty = to.getY() + 0.5f + face.getStepY() * 0.501f;
        float tz = to.getZ() + 0.5f + face.getStepZ() * 0.501f;

        float dx = tx - fx, dy = ty - fy, dz = tz - fz;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f) return;
        float nx = dx / len, ny = dy / len, nz = dz / len;

        vc.vertex(mat, fx, fy, fz).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
        vc.vertex(mat, tx, ty, tz).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz);
    }

    // -- Item NBT helpers (client-side read-only) --

    private static final String TAG_SELECTED = "SelectedProvider";
    private static final String TAG_DIM = "Dim";
    private static final String TAG_POS = "Pos";
    private static final String TAG_HOST_TYPE = "HostType";

    private static <T> Set<BlockPos> collectConnectionsForFace(Iterable<T> connections,
            Level level, Direction face,
            Function<T, ResourceKey<Level>> dimensionGetter,
            Function<T, BlockPos> posGetter,
            Function<T, Direction> faceGetter) {
        // Reuse a single scratch set to keep allocations off the hot render path.
        // Safe because RenderLevelStageEvent is only fired on the client render thread.
        scratchConnectionSet.clear();
        for (var conn : connections) {
            if (dimensionGetter.apply(conn).equals(level.dimension()) && faceGetter.apply(conn) == face) {
                scratchConnectionSet.add(posGetter.apply(conn));
            }
        }
        return scratchConnectionSet;
    }

    /**
     * Refreshes {@link #cachedHostPositions} by walking the chunks in {@link #SCAN_RANGE}
     * around {@code playerPos}. Called at most every {@link #RESCAN_INTERVAL_TICKS} ticks
     * (or immediately on dimension change) to keep the per-frame render path allocation-free.
     */
    private static void rescanHosts(net.minecraft.client.multiplayer.ClientLevel level, BlockPos playerPos) {
        cachedHostPositions.clear();

        int minCX = (playerPos.getX() - SCAN_RANGE) >> 4;
        int maxCX = (playerPos.getX() + SCAN_RANGE) >> 4;
        int minCZ = (playerPos.getZ() - SCAN_RANGE) >> 4;
        int maxCZ = (playerPos.getZ() + SCAN_RANGE) >> 4;

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                var chunk = level.getChunk(cx, cz);
                for (var bePos : chunk.getBlockEntitiesPos()) {
                    var be = chunk.getBlockEntity(bePos);
                    if (be instanceof WirelessPatternProviderHost
                            || be instanceof OverloadedInterfaceBlockEntity
                            || be instanceof OverloadedPowerSupplyBlockEntity) {
                        // Defensive copy: chunk.getBlockEntitiesPos() returns positions
                        // that may share identity with the BE's stored pos; we only need
                        // the immutable coordinates.
                        cachedHostPositions.add(bePos.immutable());
                    }
                }
            }
        }
    }

    /**
     * Returns the wireless-connector stack currently held by the local player, or EMPTY.
     * Shared with {@link WirelessConnectorHostRenderer}.
     */
    public static ItemStack getHeldConnectorStack() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return ItemStack.EMPTY;
        }
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            var held = player.getItemInHand(hand);
            if (held.getItem() instanceof OverloadedWirelessConnectorItem) {
                return held;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * True if the given host position/type is the one selected on the held connector stack.
     * Shared with {@link WirelessConnectorHostRenderer}.
     */
    public static boolean isSelectedHost(ItemStack stack, Level level, BlockPos pos, String hostType) {
        var selected = getSelectedHost(stack);
        return selected != null
                && selected.hostType().equals(hostType)
                && selected.pos().equals(pos)
                && level.dimension().equals(selected.dimension());
    }

    private static SelectedHost getSelectedHost(ItemStack stack) {
        // 1.20.1 stores the selection in the stack NBT instead of a CUSTOM_DATA component.
        var tag = ItemStackTagSupport.getTagCopy(stack);
        if (!tag.contains(TAG_SELECTED, CompoundTag.TAG_COMPOUND)) {
            return null;
        }
        var sel = tag.getCompound(TAG_SELECTED);
        var dimStr = sel.getString(TAG_DIM);
        if (dimStr.isEmpty()) {
            return null;
        }
        return new SelectedHost(
                BlockPos.of(sel.getLong(TAG_POS)),
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(dimStr)),
                sel.contains(TAG_HOST_TYPE, CompoundTag.TAG_STRING)
                        ? sel.getString(TAG_HOST_TYPE)
                        : OverloadedWirelessConnectorItem.HOST_PROVIDER);
    }

    private record SelectedHost(BlockPos pos, ResourceKey<Level> dimension, String hostType) {
    }

    /**
     * Drop cached host positions when the player disconnects. Without this, a
     * subsequent login to a different world would briefly render highlights at
     * the previous world's coordinates on the very first frame (until the next
     * scheduled re-scan replaces them). Cheap and prevents stale-state bleed.
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        cachedHostPositions.clear();
        scratchConnectionSet.clear();
        lastScanTick = -1L;
        lastScanDimension = null;
    }
}
