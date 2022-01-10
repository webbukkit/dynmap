package org.dynmap.fabric_common;

import com.mojang.authlib.GameProfile;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import org.dynmap.common.chunk.GenericNBTCompound;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Abstraction interface for version-specific Minecraft logic.
 */
public interface FabricVersionInterface {

    float[] World_getBrightnessTable(World world);

    GenericNBTCompound ThreadedAnvilChunkStorage_getGenericNbt(ThreadedAnvilChunkStorage tacs, ChunkPos chunkPos) throws IOException;

    /* Ugly "Object"-style passing because the type was renamed in Yarn 1.16.5. */
    GenericNBTCompound NbtCompound_getGenericNbt(Object nbtCompound);

    void ServerPlayerEntity_sendMessage(ServerPlayerEntity player, String message);

    void MinecraftServer_broadcastMessage(MinecraftServer server, String message);

    void ServerPlayerEntity_sendTitleText(ServerPlayerEntity player, String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks);

    String World_getDimensionName(World world);

    int BlockState_getRawId(BlockState blockState);

    boolean World_isNether(World world);

    boolean World_isEnd(World world);

    String World_getDefaultTitle(World world);

    int World_getMinimumY(World world);

    /* FIXME: Pull this from somewhere in vanilla server? */
    int maxWorldHeight();

    /* FIXME: Pull this from somewhere in vanilla server? */
    boolean BlockState_isOpaqueFullCube(BlockState blockState);

    Optional<GameProfile> MinecraftServer_getProfileByName(MinecraftServer server, String username);

    boolean MinecraftServer_isSinglePlayer(MinecraftServer server);

    String MinecraftServer_getSinglePlayerName(MinecraftServer server);

    Registry<Biome> MinecraftServer_getBiomeRegistry(MinecraftServer server);

    float Biome_getPrecipitation(Biome biome);

    int Biome_getWaterColor(Biome biome);

    CompletableFuture<Chunk> ChunkHolder_getSavingFuture(ChunkHolder chunk);

}
