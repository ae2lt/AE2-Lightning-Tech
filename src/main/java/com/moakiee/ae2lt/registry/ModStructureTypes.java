package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.worldgen.FirmamentStarshipPiece;
import com.moakiee.ae2lt.worldgen.FirmamentStarshipStructure;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModStructureTypes {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(BuiltInRegistries.STRUCTURE_TYPE, AE2LightningTech.MODID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
            DeferredRegister.create(BuiltInRegistries.STRUCTURE_PIECE, AE2LightningTech.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<FirmamentStarshipStructure>>
            FIRMAMENT_STARSHIP =
                    STRUCTURE_TYPES.register("firmament_starship", () -> () -> FirmamentStarshipStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> FIRMAMENT_STARSHIP_PIECE =
            STRUCTURE_PIECES.register(
                    "firmament_starship_piece",
                    () -> (context, tag) -> new FirmamentStarshipPiece(context.structureTemplateManager(), tag));

    private ModStructureTypes() {
    }
}
