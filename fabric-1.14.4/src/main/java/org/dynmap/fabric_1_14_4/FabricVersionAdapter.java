package org.dynmap.fabric_1_14_4;

import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.dynmap.common.chunk.GenericNBTCompound;
import org.dynmap.fabric_helper.FabricVersionInterface;

import java.io.IOException;

public class FabricVersionAdapter implements FabricVersionInterface {

    @Override
    public float[] World_getBrightnessTable(World world) {
        return world.getDimension().getLightLevelToBrightness();
    }

    @Override
    public GenericNBTCompound ThreadedAnvilChunkStorage_getNbt(ThreadedAnvilChunkStorage tacs, ChunkPos chunkPos) throws IOException {
        return new NBT.NBTCompound(tacs.getTagAt(chunkPos));
    }

    @Override
    public void ServerPlayerEntity_sendMessage(ServerPlayerEntity player, String message) {
        player.sendMessage(new LiteralText(message));
    }

    @Override
    public void MinecraftServer_broadcastMessage(MinecraftServer server, String message) {
        server.getPlayerManager().broadcastChatMessage(new LiteralText(message), true);
    }

    @Override
    public void ServerPlayerEntity_sendTitleText(ServerPlayerEntity player, String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        player.networkHandler.sendPacket(new TitleS2CPacket(fadeInTicks, stayTicks, fadeOutTicks));
        if (title != null) {
            player.networkHandler.sendPacket(new TitleS2CPacket(TitleS2CPacket.Action.TITLE, new LiteralText(title)));
        }
        if (subtitle != null) {
            player.networkHandler.sendPacket(new TitleS2CPacket(TitleS2CPacket.Action.SUBTITLE, new LiteralText(subtitle)));
        }
    }

}
