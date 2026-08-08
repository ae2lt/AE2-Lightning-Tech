package com.moakiee.ae2lt.network;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraftforge.network.NetworkEvent;

import appeng.client.render.crafting.AssemblerAnimationStatus;
import com.moakiee.ae2lt.blockentity.PigmeeMolecularAssemblerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public record PigmeeAssemblerAnimationPacket(
        BlockPos pos,
        byte speed,
        ItemStack output) {
public static PigmeeAssemblerAnimationPacket decode(FriendlyByteBuf buffer) {
        return new PigmeeAssemblerAnimationPacket(
                buffer.readBlockPos(),
                buffer.readByte(),
                buffer.readItem());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeByte(speed);
        buffer.writeItem(output);
    }

    public static void handle(
            PigmeeAssemblerAnimationPacket packet,
            Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            var blockEntity = Minecraft.getInstance().player.level().getBlockEntity(packet.pos);
            if (blockEntity instanceof PigmeeMolecularAssemblerBlockEntity assembler) {
                assembler.setAnimationStatus(
                        new AssemblerAnimationStatus(packet.speed, packet.output));
            }
        });
        ctx.setPacketHandled(true);
    }
}
