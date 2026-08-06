package com.moakiee.ae2lt.network;

import appeng.client.render.crafting.AssemblerAnimationStatus;
import com.moakiee.ae2lt.blockentity.PigmeeMolecularAssemblerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PigmeeAssemblerAnimationPacket(
        BlockPos pos,
        byte speed,
        ItemStack output) implements CustomPacketPayload {
    public static final Type<PigmeeAssemblerAnimationPacket> TYPE =
            new Type<>(NetworkInit.id("pigmee_assembler_animation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PigmeeAssemblerAnimationPacket>
            STREAM_CODEC = StreamCodec.ofMember(
                    PigmeeAssemblerAnimationPacket::write,
                    PigmeeAssemblerAnimationPacket::decode);

    public static PigmeeAssemblerAnimationPacket decode(RegistryFriendlyByteBuf buffer) {
        return new PigmeeAssemblerAnimationPacket(
                buffer.readBlockPos(),
                buffer.readByte(),
                ItemStack.STREAM_CODEC.decode(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeByte(speed);
        ItemStack.STREAM_CODEC.encode(buffer, output);
    }

    public static void handle(
            PigmeeAssemblerAnimationPacket packet,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            var blockEntity = context.player().level().getBlockEntity(packet.pos);
            if (blockEntity instanceof PigmeeMolecularAssemblerBlockEntity assembler) {
                assembler.setAnimationStatus(
                        new AssemblerAnimationStatus(packet.speed, packet.output));
            }
        });
    }

    @Override
    public Type<PigmeeAssemblerAnimationPacket> type() {
        return TYPE;
    }
}
