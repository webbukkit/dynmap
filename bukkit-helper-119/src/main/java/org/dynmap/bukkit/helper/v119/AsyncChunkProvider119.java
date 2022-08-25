package org.dynmap.bukkit.helper.v119;

import com.destroystokyo.paper.io.PaperFileIOThread;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.ChunkSerializer.AsyncSaveData;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_19_R1.CraftServer;
import org.bukkit.craftbukkit.v1_19_R1.CraftWorld;
import org.dynmap.MapManager;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Isolation of paper specific methods
 */
public class AsyncChunkProvider119 {
    private int currTick = MinecraftServer.currentTick;
    private int currChunks = 0;

    public CompletableFuture<CompoundTag> getChunk(ServerLevel world, int x, int y) throws InvocationTargetException, IllegalAccessException {
        return PaperFileIOThread.Holder.INSTANCE.loadChunkDataAsyncFuture(world, x, y, 5, false, true, true)
                .thenApply(resultFuture -> resultFuture == null ? null : PaperFileIOThread.FAILURE_VALUE == resultFuture.chunkData ? null : resultFuture.chunkData);
    }

    public synchronized Supplier<CompoundTag> getLoadedChunk(CraftWorld world, int x, int z) {
        if (!world.isChunkLoaded(x, z)) return () -> null;
        LevelChunk c = world.getHandle().getChunkIfLoaded(x, z);
        if ((c == null) || !c.loaded) return () -> null;
        if (currTick != MinecraftServer.currentTick) {
            currTick = MinecraftServer.currentTick;
            currChunks = 0;
        }
        //prepare data synchronously
        CompletableFuture<AsyncSaveData> future = CompletableFuture.supplyAsync(() -> {
            //Null will mean that we save with spigot methods, which may be risky on async
            //Since we're not in main thread, it now refuses new tasks because of shutdown, the risk is lower
            return Bukkit.isPrimaryThread() ? ChunkSerializer.getAsyncSaveData(world.getHandle(), c) : null;
        }, ((CraftServer) Bukkit.getServer()).getServer());
        //we shouldn't stress main thread
        if (++currChunks > MapManager.mapman.getMaxChunkLoadsPerTick()) {
            try {
                Thread.sleep(25); //hold the lock so other threads also won't stress main thread
            } catch (InterruptedException ignored) {}
        }
        //save data asynchronously
        return () -> {
            AsyncSaveData o;
            o = future.join();
            if (o == null) {
                try {
                    return ChunkSerializer.saveChunk(world.getHandle(), c, null);
                } catch (Exception e) {
                    return null; //Server stopping, Be more cautions
                }
            } else return ChunkSerializer.saveChunk(world.getHandle(), c, o);
        };
    }
}
