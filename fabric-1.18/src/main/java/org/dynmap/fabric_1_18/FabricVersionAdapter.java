package org.dynmap.fabric_1_18;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.network.MessageType;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import org.dynmap.common.chunk.GenericNBTCompound;
import org.dynmap.fabric_helper.FabricVersionInterface;

import java.io.IOException;

public class FabricVersionAdapter implements FabricVersionInterface {

    @Override
    public float[] World_getBrightnessTable(World world) {
        float brightnessTable[] = new float[16];
        for (int i=0; i<16; i++) {
            brightnessTable[i] = world.getDimension().getBrightness(i);
        }
        return brightnessTable;
    }

    @Override
    public GenericNBTCompound ThreadedAnvilChunkStorage_getNbt(ThreadedAnvilChunkStorage tacs, ChunkPos chunkPos) throws IOException {
        return new NBT.NBTCompound(tacs.getNbt(chunkPos));
    }

    @Override
    public void ServerPlayerEntity_sendMessage(ServerPlayerEntity player, String message) {
        player.sendSystemMessage(new LiteralText(message), Util.NIL_UUID);
    }

    @Override
    public void MinecraftServer_broadcastMessage(MinecraftServer server, String message) {
        server.getPlayerManager().broadcast(new LiteralText(message), MessageType.SYSTEM, Util.NIL_UUID);
    }

    @Override
    public void ServerPlayerEntity_sendTitleText(ServerPlayerEntity player, String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(fadeInTicks, stayTicks, fadeOutTicks));
        if (title != null) {
            player.networkHandler.sendPacket(new TitleS2CPacket(new LiteralText(title)));
        }
        if (subtitle != null) {
            player.networkHandler.sendPacket(new SubtitleS2CPacket(new LiteralText(subtitle)));
        }
    }

    @Override
    public String World_getDimensionName(World world) {
        RegistryKey<World> registryKey = world.getRegistryKey();
        if (registryKey == World.OVERWORLD) {
            return world.getServer().getSaveProperties().getLevelName();
        } else if (registryKey == World.END) {
            return "DIM1";
        } else if (registryKey == World.NETHER) {
            return "DIM-1";
        } else {
            return registryKey.getValue().getNamespace() + "_" + registryKey.getValue().getPath();
        }
    }

    @Override
    public int BlockState_getRawId(BlockState blockState) {
        return Block.STATE_IDS.getRawId(blockState);
    }

    @Override
    public boolean World_isNether(World world) {
        return world.getRegistryKey() == World.NETHER;
    }

    @Override
    public boolean World_isEnd(World world) {
        return world.getRegistryKey() == World.END;
    }

    @Override
    public String World_getDefaultTitle(World world) {
        return world.getRegistryKey().getValue().getPath();
    }

    @Override
    public int maxWorldHeight() {
        return 320;
    }

}
