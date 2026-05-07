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
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.util.SavedDataCodecs;

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

    private static final SavedDataType<EjectModeSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(AE2LightningTech.MODID, DATA_NAME),
            level -> new EjectModeSavedData(),
            SavedDataCodecs.codecFactory(EjectModeSavedData::load, EjectModeSavedData::saveTag));

    public EjectModeSavedData() {
        super();
    }

    public static EjectModeSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
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

    private CompoundTag saveTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
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
        var list = tag.getListOrEmpty(TAG_ENTRIES);
        for (int i = 0; i < list.size(); i++) {
            var ct = list.getCompoundOrEmpty(i);
            var iDim = ResourceKey.create(Registries.DIMENSION,
                    Identifier.parse(ct.getStringOr(TAG_I_DIM, "minecraft:overworld")));
            var iPos = BlockPos.of(ct.getLongOr(TAG_I_POS, 0L));
            var iFace = Direction.from3DDataValue(ct.getIntOr(TAG_I_FACE, 0));
            var pDim = ResourceKey.create(Registries.DIMENSION,
                    Identifier.parse(ct.getStringOr(TAG_P_DIM, "minecraft:overworld")));
            var pPos = BlockPos.of(ct.getLongOr(TAG_P_POS, 0L));
            data.entries.add(new PersistentReg(iDim, iPos, iFace, pDim, pPos));
        }
        return data;
    }
}
