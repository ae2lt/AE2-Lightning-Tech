package com.moakiee.ae2lt.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Overworld-level SavedData that persists all active EJECT-mode capability
 * interception registrations. This allows the registry to be rebuilt after
 * a server restart (before any pattern-provider BlockEntity has loaded).
 */
public class EjectModeSavedData extends SavedData {

    private static final String DATA_NAME = "ae2lt_eject_registrations";
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_I_DIM = "IDim";
    private static final String TAG_I_POS = "IPos";
    private static final String TAG_I_FACE = "IFace";
    private static final String TAG_P_DIM = "PDim";
    private static final String TAG_P_POS = "PPos";

    public record PersistentReg(
            ResourceKey<Level> interceptDim,
            BlockPos interceptPos,
            Direction interceptFace,
            ResourceKey<Level> hostDim,
            BlockPos hostPos
    ) {}

    private final List<PersistentReg> entries = new ArrayList<>();

    public static final Factory<EjectModeSavedData> FACTORY = new Factory<>(
            EjectModeSavedData::new,
            EjectModeSavedData::load,
            null
    );

    public EjectModeSavedData() {
        super();
    }

    public static EjectModeSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public List<PersistentReg> getAll() {
        return Collections.unmodifiableList(entries);
    }

    public void add(PersistentReg reg) {
        entries.add(reg);
        setDirty();
    }

    public void removeByIntercept(ResourceKey<Level> dim, BlockPos pos, Direction face) {
        long posL = pos.asLong();
        boolean changed = entries.removeIf(e ->
                e.interceptDim().equals(dim)
                        && e.interceptPos().asLong() == posL
                        && e.interceptFace() == face);
        if (changed) setDirty();
    }

    public void removeByHost(ResourceKey<Level> hostDim, BlockPos hostPos) {
        boolean changed = entries.removeIf(e ->
                e.hostDim().equals(hostDim)
                        && e.hostPos().equals(hostPos));
        if (changed) setDirty();
    }

    // ---- Persistence -------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var e : entries) {
            var ct = new CompoundTag();
            ct.putString(TAG_I_DIM, e.interceptDim().location().toString());
            ct.putLong(TAG_I_POS, e.interceptPos().asLong());
            ct.putInt(TAG_I_FACE, e.interceptFace().get3DDataValue());
            ct.putString(TAG_P_DIM, e.hostDim().location().toString());
            ct.putLong(TAG_P_POS, e.hostPos().asLong());
            list.add(ct);
        }
        tag.put(TAG_ENTRIES, list);
        return tag;
    }

    private static EjectModeSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new EjectModeSavedData();
        if (tag.contains(TAG_ENTRIES, Tag.TAG_LIST)) {
            var list = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                var ct = list.getCompound(i);
                var iDim = ResourceKey.create(Registries.DIMENSION,
                        ResourceLocation.parse(ct.getString(TAG_I_DIM)));
                var iPos = BlockPos.of(ct.getLong(TAG_I_POS));
                var iFace = Direction.from3DDataValue(ct.getInt(TAG_I_FACE));
                var pDim = ResourceKey.create(Registries.DIMENSION,
                        ResourceLocation.parse(ct.getString(TAG_P_DIM)));
                var pPos = BlockPos.of(ct.getLong(TAG_P_POS));
                data.entries.add(new PersistentReg(iDim, iPos, iFace, pDim, pPos));
            }
        }
        return data;
    }
}
