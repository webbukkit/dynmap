package org.dynmap.fabric_helper;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.dynmap.common.chunk.GenericNBTCompound;

import java.io.IOException;

/**
 * Abstraction interface for version-specific Minecraft logic.
 */
public interface FabricVersionInterface {

    float[] World_getBrightnessTable(World world);

    GenericNBTCompound ThreadedAnvilChunkStorage_getNbt(ThreadedAnvilChunkStorage tacs, ChunkPos chunkPos) throws IOException;

    void ServerPlayerEntity_sendMessage(ServerPlayerEntity player, String message);

    void MinecraftServer_broadcastMessage(MinecraftServer server, String message);

    void ServerPlayerEntity_sendTitleText(ServerPlayerEntity player, String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks);

    String World_getDimensionName(World world);

}
