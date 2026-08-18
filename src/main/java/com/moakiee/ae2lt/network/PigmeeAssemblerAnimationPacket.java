package com.moakiee.ae2lt.network;

import java.util.function.Supplier;

import com.moakiee.ae2lt.client.ClientNetworkPacketHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

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
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientNetworkPacketHandlers.handlePigmeeAssemblerAnimation(packet)));
        ctx.setPacketHandled(true);
    }
}
