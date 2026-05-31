package com.moakiee.ae2lt.client;

import appeng.client.render.AERenderTypes;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.block.OverloadedInterfaceBlock;
import com.moakiee.ae2lt.block.OverloadedPatternProviderBlock;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.item.OverloadedWirelessConnectorItem;
import com.moakiee.ae2lt.logic.WirelessConnectorTargetHelper;
import com.moakiee.ae2lt.network.WirelessConnectorUsePacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class WirelessConnectorRenderer {
    private static final int COLOR_PREVIEW = 0x60FFFF00;
    private static final int COLOR_CONNECTED = 0x600080FF;
    private static final int COLOR_PREVIEW_LINE = 0xC0FFFF00;
    private static final int COLOR_HOST = 0x800080FF;
    private static final int COLOR_HOST_SELECTED = 0x80FFFF00;
    private static final int COLOR_LINE = 0xC00080FF;

    private static final int SCAN_RANGE = 64;
    private static final int RESCAN_INTERVAL_TICKS = 4;

    private static final List<BlockPos> cachedHostPositions = new ArrayList<>();
    private static final Set<BlockPos> scratchConnectionSet = new HashSet<>();
    private static long lastScanTick = -1L;
    private static ResourceKey<Level> lastScanDimension = null;

    private static final String TAG_SELECTED = "SelectedProvider";
    private static final String TAG_DIM = "Dim";
    private static final String TAG_POS = "Pos";
    private static final String TAG_HOST_TYPE = "HostType";

    private WirelessConnectorRenderer() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !event.getLevel().isClientSide()) {
            return;
        }

        var stack = event.getEntity().getItemInHand(event.getHand());
        if (!(stack.getItem() instanceof OverloadedWirelessConnectorItem)) {
            return;
        }

        var level = event.getLevel();
        var hit = event.getHitVec();
        var pos = hit.getBlockPos();
        var state = level.getBlockState(pos);
        boolean isHost = state.getBlock() instanceof OverloadedPatternProviderBlock
                || state.getBlock() instanceof OverloadedInterfaceBlock;
        if (!isHost && level.getBlockEntity(pos) == null) {
            return;
        }

        ClientPacketDistributor.sendToServer(new WirelessConnectorUsePacket(
                event.getHand(),
                pos,
                hit.getDirection(),
                Minecraft.getInstance().hasControlDown()));
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent.AfterWeather event) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        ItemStack stack = ItemStack.EMPTY;
        for (var hand : InteractionHand.values()) {
            var held = player.getItemInHand(hand);
            if (held.getItem() instanceof OverloadedWirelessConnectorItem) {
                stack = held;
                break;
            }
        }
        if (stack.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        poseStack.pushPose();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().position();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

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

        long gameTime = mc.level.getGameTime();
        var currentDim = mc.level.dimension();
        if (lastScanTick < 0L
                || lastScanDimension == null
                || !lastScanDimension.equals(currentDim)
                || gameTime - lastScanTick >= RESCAN_INTERVAL_TICKS) {
            rescanHosts(mc.level, player.blockPosition());
            lastScanTick = gameTime;
            lastScanDimension = currentDim;
        }

        for (var bePos : cachedHostPositions) {
            if (!mc.level.isLoaded(bePos)) {
                continue;
            }
            var be = mc.level.getBlockEntity(bePos);
            if (be instanceof OverloadedPatternProviderBlockEntity provider) {
                if (provider.getProviderMode() != OverloadedPatternProviderBlockEntity.ProviderMode.WIRELESS) {
                    continue;
                }
                boolean selected = hasSelection
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
                renderProviderHost(poseStack, buffer, mc.level, bePos, provider, selected);
                selectedRendered |= selected;
            } else if (be instanceof OverloadedInterfaceBlockEntity iface) {
                if (iface.getInterfaceMode() != OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS) {
                    continue;
                }
                boolean selected = hasSelection
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
                renderInterfaceHost(poseStack, buffer, mc.level, bePos, iface, selected);
                selectedRendered |= selected;
            }
        }

        if (selectionInCurrentDimension && !selectedRendered && mc.level.isLoaded(selectedPos)) {
            var selectedBe = mc.level.getBlockEntity(selectedPos);
            if (OverloadedWirelessConnectorItem.HOST_PROVIDER.equals(selectedHostType)
                    && selectedBe instanceof OverloadedPatternProviderBlockEntity provider
                    && provider.getProviderMode() == OverloadedPatternProviderBlockEntity.ProviderMode.WIRELESS) {
                renderProviderHost(poseStack, buffer, mc.level, selectedPos, provider, true);
            } else if (OverloadedWirelessConnectorItem.HOST_INTERFACE.equals(selectedHostType)
                    && selectedBe instanceof OverloadedInterfaceBlockEntity iface
                    && iface.getInterfaceMode() == OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS) {
                renderInterfaceHost(poseStack, buffer, mc.level, selectedPos, iface, true);
            }
        }

        if (selectionInCurrentDimension) {
            renderPreview(poseStack, buffer, mc, selectedPos, selectedHostType);
        }

        poseStack.popPose();

        buffer.endBatch(Ae2ltRenderTypes.getFaceSeeThrough());
        buffer.endBatch(AERenderTypes.AREA_OVERLAY_LINE_OCCLUDED);
        buffer.endBatch(AERenderTypes.AREA_OVERLAY_FACE);
        buffer.endBatch(AERenderTypes.AREA_OVERLAY_LINE);
    }

    private static void renderPreview(
            PoseStack poseStack,
            MultiBufferSource buffer,
            Minecraft mc,
            BlockPos selectedPos,
            String selectedHostType) {
        if (!(mc.hitResult instanceof BlockHitResult bhr)
                || bhr.getType() != HitResult.Type.BLOCK
                || bhr.getBlockPos().equals(selectedPos)
                || mc.level.getBlockEntity(bhr.getBlockPos()) == null) {
            return;
        }

        var selectedBe = mc.level.getBlockEntity(selectedPos);
        var lookFace = bhr.getDirection();
        var previewTargets = WirelessConnectorTargetHelper.collectTargets(
                mc.level,
                bhr.getBlockPos(),
                mc.hasControlDown());

        if (OverloadedWirelessConnectorItem.HOST_PROVIDER.equals(selectedHostType)
                && selectedBe instanceof OverloadedPatternProviderBlockEntity selectedProvider) {
            var existingConnections = collectConnectionsForFace(
                    selectedProvider.getConnections(),
                    mc.level,
                    lookFace,
                    OverloadedPatternProviderBlockEntity.WirelessConnection::dimension,
                    OverloadedPatternProviderBlockEntity.WirelessConnection::pos,
                    OverloadedPatternProviderBlockEntity.WirelessConnection::boundFace);
            renderPreviewTargets(poseStack, buffer, selectedPos, lookFace, previewTargets, existingConnections);
        } else if (OverloadedWirelessConnectorItem.HOST_INTERFACE.equals(selectedHostType)
                && selectedBe instanceof OverloadedInterfaceBlockEntity selectedInterface) {
            var existingConnections = collectConnectionsForFace(
                    selectedInterface.getConnections(),
                    mc.level,
                    lookFace,
                    OverloadedInterfaceBlockEntity.WirelessConnection::dimension,
                    OverloadedInterfaceBlockEntity.WirelessConnection::pos,
                    OverloadedInterfaceBlockEntity.WirelessConnection::boundFace);
            renderPreviewTargets(poseStack, buffer, selectedPos, lookFace, previewTargets, existingConnections);
        }
    }

    private static void renderPreviewTargets(
            PoseStack poseStack,
            MultiBufferSource buffer,
            BlockPos selectedPos,
            Direction lookFace,
            Iterable<BlockPos> previewTargets,
            Set<BlockPos> existingConnections) {
        for (var lookPos : previewTargets) {
            if (!existingConnections.contains(lookPos)) {
                renderFaceOverlay(poseStack, buffer, lookPos, lookFace, COLOR_PREVIEW);
                renderLine(poseStack, buffer, selectedPos, lookPos, lookFace, COLOR_PREVIEW_LINE);
            }
        }
    }

    private static void renderProviderHost(
            PoseStack poseStack,
            MultiBufferSource buffer,
            Level level,
            BlockPos hostPos,
            OverloadedPatternProviderBlockEntity provider,
            boolean selected) {
        renderInnerCube(poseStack, buffer, hostPos, selected ? COLOR_HOST_SELECTED : COLOR_HOST);

        for (var conn : provider.getConnections()) {
            if (!conn.dimension().equals(level.dimension())) {
                continue;
            }
            renderFaceOverlay(poseStack, buffer, conn.pos(), conn.boundFace(), COLOR_CONNECTED);
            renderLine(poseStack, buffer, hostPos, conn.pos(), conn.boundFace(), COLOR_LINE);
        }
    }

    private static void renderInterfaceHost(
            PoseStack poseStack,
            MultiBufferSource buffer,
            Level level,
            BlockPos hostPos,
            OverloadedInterfaceBlockEntity iface,
            boolean selected) {
        renderInnerCube(poseStack, buffer, hostPos, selected ? COLOR_HOST_SELECTED : COLOR_HOST);

        for (var conn : iface.getConnections()) {
            if (!conn.dimension().equals(level.dimension())) {
                continue;
            }
            renderFaceOverlay(poseStack, buffer, conn.pos(), conn.boundFace(), COLOR_CONNECTED);
            renderLine(poseStack, buffer, hostPos, conn.pos(), conn.boundFace(), COLOR_LINE);
        }
    }

    private static void renderInnerCube(PoseStack poseStack, MultiBufferSource buffer, BlockPos pos, int color) {
        VertexConsumer vc = buffer.getBuffer(Ae2ltRenderTypes.getFaceSeeThrough());
        int[] c = decomposeColor(color);

        poseStack.pushPose();
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        Matrix4f mat = poseStack.last().pose();

        float lo = 0.25F;
        float hi = 0.75F;
        quad(vc, mat, c, lo, lo, lo, hi, lo, lo, hi, lo, hi, lo, lo, hi, 0, -1, 0);
        quad(vc, mat, c, lo, hi, hi, hi, hi, hi, hi, hi, lo, lo, hi, lo, 0, 1, 0);
        quad(vc, mat, c, lo, lo, lo, lo, hi, lo, hi, hi, lo, hi, lo, lo, 0, 0, -1);
        quad(vc, mat, c, hi, lo, hi, hi, hi, hi, lo, hi, hi, lo, lo, hi, 0, 0, 1);
        quad(vc, mat, c, lo, lo, hi, lo, hi, hi, lo, hi, lo, lo, lo, lo, -1, 0, 0);
        quad(vc, mat, c, hi, lo, lo, hi, hi, lo, hi, hi, hi, hi, lo, hi, 1, 0, 0);

        poseStack.popPose();
    }

    private static void renderFaceOverlay(
            PoseStack poseStack,
            MultiBufferSource buffer,
            BlockPos pos,
            Direction face,
            int color) {
        VertexConsumer vc = buffer.getBuffer(AERenderTypes.AREA_OVERLAY_FACE);
        int[] c = decomposeColor(color);

        poseStack.pushPose();
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        Matrix4f mat = poseStack.last().pose();

        float offset = 0.001F;
        float nx = face.getStepX();
        float ny = face.getStepY();
        float nz = face.getStepZ();

        switch (face) {
            case DOWN -> {
                vertex(vc, mat, c, 0, -offset, 0, nx, ny, nz);
                vertex(vc, mat, c, 1, -offset, 0, nx, ny, nz);
                vertex(vc, mat, c, 1, -offset, 1, nx, ny, nz);
                vertex(vc, mat, c, 0, -offset, 1, nx, ny, nz);
            }
            case UP -> {
                vertex(vc, mat, c, 0, 1 + offset, 1, nx, ny, nz);
                vertex(vc, mat, c, 1, 1 + offset, 1, nx, ny, nz);
                vertex(vc, mat, c, 1, 1 + offset, 0, nx, ny, nz);
                vertex(vc, mat, c, 0, 1 + offset, 0, nx, ny, nz);
            }
            case NORTH -> {
                vertex(vc, mat, c, 0, 0, -offset, nx, ny, nz);
                vertex(vc, mat, c, 0, 1, -offset, nx, ny, nz);
                vertex(vc, mat, c, 1, 1, -offset, nx, ny, nz);
                vertex(vc, mat, c, 1, 0, -offset, nx, ny, nz);
            }
            case SOUTH -> {
                vertex(vc, mat, c, 1, 0, 1 + offset, nx, ny, nz);
                vertex(vc, mat, c, 1, 1, 1 + offset, nx, ny, nz);
                vertex(vc, mat, c, 0, 1, 1 + offset, nx, ny, nz);
                vertex(vc, mat, c, 0, 0, 1 + offset, nx, ny, nz);
            }
            case WEST -> {
                vertex(vc, mat, c, -offset, 0, 1, nx, ny, nz);
                vertex(vc, mat, c, -offset, 1, 1, nx, ny, nz);
                vertex(vc, mat, c, -offset, 1, 0, nx, ny, nz);
                vertex(vc, mat, c, -offset, 0, 0, nx, ny, nz);
            }
            case EAST -> {
                vertex(vc, mat, c, 1 + offset, 0, 0, nx, ny, nz);
                vertex(vc, mat, c, 1 + offset, 1, 0, nx, ny, nz);
                vertex(vc, mat, c, 1 + offset, 1, 1, nx, ny, nz);
                vertex(vc, mat, c, 1 + offset, 0, 1, nx, ny, nz);
            }
        }

        poseStack.popPose();
    }

    private static void renderLine(
            PoseStack poseStack,
            MultiBufferSource buffer,
            BlockPos from,
            BlockPos to,
            Direction face,
            int color) {
        int[] c = decomposeColor(color);
        Matrix4f mat = poseStack.last().pose();

        float fx = from.getX() + 0.5F;
        float fy = from.getY() + 0.5F;
        float fz = from.getZ() + 0.5F;
        float tx = to.getX() + 0.5F + face.getStepX() * 0.501F;
        float ty = to.getY() + 0.5F + face.getStepY() * 0.501F;
        float tz = to.getZ() + 0.5F + face.getStepZ() * 0.501F;

        float dx = tx - fx;
        float dy = ty - fy;
        float dz = tz - fz;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6F) {
            return;
        }

        float nx = dx / len;
        float ny = dy / len;
        float nz = dz / len;
        VertexConsumer visible = buffer.getBuffer(AERenderTypes.AREA_OVERLAY_LINE);
        lineVertex(visible, mat, c, fx, fy, fz, nx, ny, nz);
        lineVertex(visible, mat, c, tx, ty, tz, nx, ny, nz);

        VertexConsumer occluded = buffer.getBuffer(AERenderTypes.AREA_OVERLAY_LINE_OCCLUDED);
        lineVertex(occluded, mat, c, fx, fy, fz, nx, ny, nz);
        lineVertex(occluded, mat, c, tx, ty, tz, nx, ny, nz);
    }

    private static void quad(
            VertexConsumer vc,
            Matrix4f mat,
            int[] c,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float x3,
            float y3,
            float z3,
            float x4,
            float y4,
            float z4,
            float nx,
            float ny,
            float nz) {
        vertex(vc, mat, c, x1, y1, z1, nx, ny, nz);
        vertex(vc, mat, c, x2, y2, z2, nx, ny, nz);
        vertex(vc, mat, c, x3, y3, z3, nx, ny, nz);
        vertex(vc, mat, c, x4, y4, z4, nx, ny, nz);
    }

    private static void vertex(
            VertexConsumer vc,
            Matrix4f mat,
            int[] c,
            float x,
            float y,
            float z,
            float nx,
            float ny,
            float nz) {
        vc.addVertex(mat, x, y, z).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
    }

    private static void lineVertex(
            VertexConsumer vc,
            Matrix4f mat,
            int[] c,
            float x,
            float y,
            float z,
            float nx,
            float ny,
            float nz) {
        vc.addVertex(mat, x, y, z)
                .setColor(c[1], c[2], c[3], c[0])
                .setNormal(nx, ny, nz)
                .setLineWidth(2.5F);
    }

    private static int[] decomposeColor(int color) {
        return new int[] {
                color >> 24 & 0xFF,
                color >> 16 & 0xFF,
                color >> 8 & 0xFF,
                color & 0xFF
        };
    }

    private static <T> Set<BlockPos> collectConnectionsForFace(
            Iterable<T> connections,
            Level level,
            Direction face,
            Function<T, ResourceKey<Level>> dimensionGetter,
            Function<T, BlockPos> posGetter,
            Function<T, Direction> faceGetter) {
        scratchConnectionSet.clear();
        for (var conn : connections) {
            if (dimensionGetter.apply(conn).equals(level.dimension()) && faceGetter.apply(conn) == face) {
                scratchConnectionSet.add(posGetter.apply(conn));
            }
        }
        return scratchConnectionSet;
    }

    private static void rescanHosts(net.minecraft.client.multiplayer.ClientLevel level, BlockPos playerPos) {
        cachedHostPositions.clear();

        int minCX = (playerPos.getX() - SCAN_RANGE) >> 4;
        int maxCX = (playerPos.getX() + SCAN_RANGE) >> 4;
        int minCZ = (playerPos.getZ() - SCAN_RANGE) >> 4;
        int maxCZ = (playerPos.getZ() + SCAN_RANGE) >> 4;

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                var chunk = level.getChunk(cx, cz);
                for (var bePos : chunk.getBlockEntitiesPos()) {
                    var be = chunk.getBlockEntity(bePos);
                    if (be instanceof OverloadedPatternProviderBlockEntity
                            || be instanceof OverloadedInterfaceBlockEntity) {
                        cachedHostPositions.add(bePos.immutable());
                    }
                }
            }
        }
    }

    private static SelectedHost getSelectedHost(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        var sel = tag.getCompound(TAG_SELECTED).orElse(null);
        if (sel == null) {
            return null;
        }

        var dimStr = sel.getStringOr(TAG_DIM, "");
        if (dimStr.isBlank()) {
            return null;
        }

        try {
            return new SelectedHost(
                    BlockPos.of(sel.getLongOr(TAG_POS, 0L)),
                    ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimStr)),
                    sel.getStringOr(TAG_HOST_TYPE, OverloadedWirelessConnectorItem.HOST_PROVIDER));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        cachedHostPositions.clear();
        scratchConnectionSet.clear();
        lastScanTick = -1L;
        lastScanDimension = null;
    }

    private record SelectedHost(BlockPos pos, ResourceKey<Level> dimension, String hostType) {
    }
}
