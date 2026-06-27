package com.moakiee.ae2lt.worldgen;

import com.moakiee.ae2lt.registry.ModStructureTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class FirmamentStarshipStructure extends Structure {
    public static final MapCodec<FirmamentStarshipStructure> CODEC =
            RecordCodecBuilder.<FirmamentStarshipStructure>mapCodec(instance -> instance.group(
                            settingsCodec(instance),
                            ResourceLocation.CODEC.fieldOf("template").forGetter(structure -> structure.template),
                            Codec.intRange(16, 256).fieldOf("search_radius").forGetter(structure -> structure.searchRadius),
                            Codec.intRange(4, 64).fieldOf("sample_step").forGetter(structure -> structure.sampleStep),
                            Codec.intRange(1, 256).fieldOf("min_anchor_height").forGetter(structure -> structure.minAnchorHeight),
                            Codec.intRange(0, 256).fieldOf("horizontal_offset").forGetter(structure -> structure.horizontalOffset),
                            Codec.intRange(0, 256).fieldOf("min_y_offset").forGetter(structure -> structure.minYOffset),
                            Codec.intRange(0, 256).fieldOf("max_y_offset").forGetter(structure -> structure.maxYOffset))
                    .apply(instance, FirmamentStarshipStructure::new))
                    .validate(FirmamentStarshipStructure::validate);

    private final ResourceLocation template;
    private final int searchRadius;
    private final int sampleStep;
    private final int minAnchorHeight;
    private final int horizontalOffset;
    private final int minYOffset;
    private final int maxYOffset;

    public FirmamentStarshipStructure(
            Structure.StructureSettings settings,
            ResourceLocation template,
            int searchRadius,
            int sampleStep,
            int minAnchorHeight,
            int horizontalOffset,
            int minYOffset,
            int maxYOffset) {
        super(settings);
        this.template = template;
        this.searchRadius = searchRadius;
        this.sampleStep = sampleStep;
        this.minAnchorHeight = minAnchorHeight;
        this.horizontalOffset = horizontalOffset;
        this.minYOffset = minYOffset;
        this.maxYOffset = maxYOffset;
    }

    private static DataResult<FirmamentStarshipStructure> validate(FirmamentStarshipStructure structure) {
        if (structure.sampleStep > structure.searchRadius) {
            return DataResult.error(() -> "sample_step must not be greater than search_radius");
        }
        if (structure.minYOffset > structure.maxYOffset) {
            return DataResult.error(() -> "min_y_offset must not be greater than max_y_offset");
        }
        return DataResult.success(structure);
    }

    @Override
    protected Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        return findBestAnchor(context)
                .map(anchor -> createGenerationStub(context, anchor));
    }

    private Structure.GenerationStub createGenerationStub(Structure.GenerationContext context, Anchor anchor) {
        RandomSource random = context.random();
        int yOffset = this.minYOffset + random.nextInt(this.maxYOffset - this.minYOffset + 1);
        ChunkPos chunkPos = context.chunkPos();
        StructureTemplate structureTemplate = context.structureTemplateManager().getOrCreate(this.template);
        FirmamentStarshipPlacement.Position center = FirmamentStarshipPlacement.offsetFromStartChunk(
                chunkPos.getMiddleBlockX(), anchor.y(), chunkPos.getMiddleBlockZ(), this.horizontalOffset, yOffset);
        FirmamentStarshipPlacement.Position origin = FirmamentStarshipPlacement.originFromCenter(
                center,
                structureTemplate.getSize().getX(),
                structureTemplate.getSize().getZ());
        BlockPos startPos = new BlockPos(origin.x(), origin.y(), origin.z());
        BlockPos rotationPivot = new BlockPos(
                structureTemplate.getSize().getX() / 2,
                0,
                structureTemplate.getSize().getZ() / 2);
        Rotation rotation = Rotation.getRandom(random);
        return new Structure.GenerationStub(startPos, builder -> builder.addPiece(new FirmamentStarshipPiece(
                context.structureTemplateManager(), this.template, startPos, rotation, rotationPivot)));
    }

    private Optional<Anchor> findBestAnchor(Structure.GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();
        int centerX = chunkPos.getMiddleBlockX();
        int centerZ = chunkPos.getMiddleBlockZ();
        Anchor best = null;

        for (int dx = -this.searchRadius; dx <= this.searchRadius; dx += this.sampleStep) {
            for (int dz = -this.searchRadius; dz <= this.searchRadius; dz += this.sampleStep) {
                int x = centerX + dx;
                int z = centerZ + dz;
                int y = context.chunkGenerator().getFirstOccupiedHeight(
                        x,
                        z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        context.heightAccessor(),
                        context.randomState());
                if (y >= this.minAnchorHeight && (best == null || y > best.y())) {
                    best = new Anchor(x, y, z);
                }
            }
        }

        return Optional.ofNullable(best);
    }

    @Override
    public StructureType<?> type() {
        return ModStructureTypes.FIRMAMENT_STARSHIP.get();
    }

    private record Anchor(int x, int y, int z) {
    }
}
