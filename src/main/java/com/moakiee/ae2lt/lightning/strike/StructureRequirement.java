package com.moakiee.ae2lt.lightning.strike;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;

public record StructureRequirement(BlockPos offset, Block block, boolean consume) {
    public static StructureRequirement fromJson(JsonObject json) {
        JsonArray offsetArray = GsonHelper.getAsJsonArray(json, "offset");
        if (offsetArray.size() != 3) {
            throw new JsonSyntaxException("Structure requirement offset must contain exactly 3 integers");
        }

        Block block = BuiltInRegistries.BLOCK.getOptional(new ResourceLocation(GsonHelper.getAsString(json, "block")))
                .orElseThrow(() -> new JsonSyntaxException("Unknown block id: " + GsonHelper.getAsString(json, "block")));

        return new StructureRequirement(
                new BlockPos(
                        GsonHelper.convertToInt(offsetArray.get(0), "offset[0]"),
                        GsonHelper.convertToInt(offsetArray.get(1), "offset[1]"),
                        GsonHelper.convertToInt(offsetArray.get(2), "offset[2]")),
                block,
                GsonHelper.getAsBoolean(json, "consume", false));
    }

    public static StructureRequirement fromNetwork(FriendlyByteBuf buffer) {
        Block block = BuiltInRegistries.BLOCK.getOptional(buffer.readResourceLocation())
                .orElseThrow(() -> new IllegalStateException("Received unknown block id in lightning strike recipe"));
        return new StructureRequirement(buffer.readBlockPos(), block, buffer.readBoolean());
    }

    public void toNetwork(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(offset);
        buffer.writeResourceLocation(BuiltInRegistries.BLOCK.getKey(block));
        buffer.writeBoolean(consume);
    }
}
