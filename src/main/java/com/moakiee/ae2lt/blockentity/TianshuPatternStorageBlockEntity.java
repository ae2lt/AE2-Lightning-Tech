package com.moakiee.ae2lt.blockentity;

import com.moakiee.ae2lt.item.ClosedLoopPatternItem;
import com.moakiee.ae2lt.logic.tianshu.TianshuFunctionProfile;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternPayload;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternRepository;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** One 64-entry physical closed-loop-pattern warehouse. */
public final class TianshuPatternStorageBlockEntity extends BlockEntity {
    private static final String TAG_PATTERNS = "ClosedLoopPatterns";
    private static final String TAG_PORT_POS = "PortPos";
    private final ClosedLoopPatternRepository patterns = new ClosedLoopPatternRepository(
            () -> TianshuFunctionProfile.PATTERNS_PER_CLOSED_LOOP_STORAGE);
    private BlockPos portPos;

    public TianshuPatternStorageBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TIANSHU_PATTERN_STORAGE.get(), pos, state);
    }

    public List<ClosedLoopPatternPayload> patterns() {
        return patterns.patterns();
    }

    public void replacePatterns(List<ClosedLoopPatternPayload> payloads) {
        patterns.replaceAll(payloads);
        setChanged();
    }

    public void bindToPort(BlockPos newPortPos) {
        portPos = newPortPos == null ? null : newPortPos.immutable();
        setChanged();
    }

    public BlockPos getPortPos() {
        return portPos;
    }

    public void dropStoredPatterns(Level level, BlockPos pos) {
        var item = (ClosedLoopPatternItem) ModItems.CLOSED_LOOP_PATTERN.get();
        for (var payload : patterns.patterns()) {
            Block.popResource(level, pos, item.createStack(payload, level.registryAccess()));
        }
        patterns.clear();
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        var patternTag = new CompoundTag();
        patterns.writeTo(patternTag, registries);
        tag.put(TAG_PATTERNS, patternTag);
        if (portPos != null) tag.putLong(TAG_PORT_POS, portPos.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        patterns.readFrom(tag.getCompound(TAG_PATTERNS), registries);
        portPos = tag.contains(TAG_PORT_POS, Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong(TAG_PORT_POS)) : null;
    }
}
