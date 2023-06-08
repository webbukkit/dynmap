package org.dynmap.bukkit.helper.v120;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.level.chunk.Chunk;
import net.minecraft.world.level.chunk.IChunkAccess;
import net.minecraft.world.level.chunk.storage.ChunkRegionLoader;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_20_R1.CraftServer;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;
import org.dynmap.MapManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * The provider used to work with paper libs
 * Because paper libs need java 17 we can't interact with them directly
 */
@SuppressWarnings({"JavaReflectionMemberAccess"}) //java don't know about paper
public class AsyncChunkProvider120 {
    private final Method getChunk;
    private final Method getAsyncSaveData;
    private final Method save;
    private final Enum<?> data;
    private final Enum<?> priority;
    private int currTick = MinecraftServer.currentTick;
    private int currChunks = 0;

    AsyncChunkProvider120() {
        try {
            Method getChunk1 = null;
            Method getAsyncSaveData1 = null;
            Method save1 = null;
            Enum<?> priority1 = null;
            Enum<?> data1 = null;
            try {
                Class<?> threadClass = Class.forName("io.papermc.paper.chunk.system.io.RegionFileIOThread");

                Class<?> dataclass = Arrays.stream(threadClass.getDeclaredClasses())
                        .filter(c -> c.getSimpleName().equals("RegionFileType"))
                        .findAny()
                        .orElseThrow(NullPointerException::new);
                data1 = Enum.valueOf(cast(dataclass), "CHUNK_DATA");

                Class<?> priorityClass = Arrays.stream(Class.forName("ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor").getClasses())
                        .filter(c -> c.getSimpleName().equals("Priority"))
                        .findAny()
                        .orElseThrow(NullPointerException::new);
                //Almost lowest priority, but not quite so low as to be considered idle
                //COMPLETING->BLOCKING->HIGHEST->HIGHER->HIGH->NORMAL->LOW->LOWER->LOWEST->IDLE
                priority1 = Enum.valueOf(cast(priorityClass), "LOWEST");

                getAsyncSaveData1 = ChunkRegionLoader.class.getMethod("getAsyncSaveData", WorldServer.class, IChunkAccess.class);
                save1 = ChunkRegionLoader.class.getMethod("saveChunk", WorldServer.class, IChunkAccess.class, getAsyncSaveData1.getReturnType());
                getChunk1 = threadClass.getMethod("loadDataAsync", WorldServer.class, int.class, int.class, data1.getClass(), BiConsumer.class, boolean.class, priority1.getClass());
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                e.printStackTrace();
            }
            getAsyncSaveData = Objects.requireNonNull(getAsyncSaveData1);
            save = Objects.requireNonNull(save1);
            getChunk = Objects.requireNonNull(getChunk1);
            data = Objects.requireNonNull(data1);
            priority = Objects.requireNonNull(priority1);
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T cast(Object o) {
        return (T) o;
    }
    public CompletableFuture<NBTTagCompound> getChunk(WorldServer world, int x, int y) throws InvocationTargetException, IllegalAccessException {
        CompletableFuture<NBTTagCompound> future = new CompletableFuture<>();
        getChunk.invoke(null, world, x, y, data, (BiConsumer<NBTTagCompound, Throwable>) (nbt, exception) -> future.complete(nbt), true, priority);
        return future;
    }

    public synchronized Supplier<NBTTagCompound> getLoadedChunk(CraftWorld world, int x, int z) {
        if (!world.isChunkLoaded(x, z)) return () -> null;
        Chunk c = world.getHandle().getChunkIfLoaded(x, z); //already safe async on vanilla
        if ((c == null) || !c.q) return () -> null;    // c.loaded
        if (currTick != MinecraftServer.currentTick) {
            currTick = MinecraftServer.currentTick;
            currChunks = 0;
        }
        //prepare data synchronously
        CompletableFuture<?> future = CompletableFuture.supplyAsync(() -> {
            //Null will mean that we save with spigot methods, which may be risky on async
            //Since we're not in main thread, it now refuses new tasks because of shutdown, the risk is lower
            if (!Bukkit.isPrimaryThread()) return null;
            try {
                return getAsyncSaveData.invoke(null, world.getHandle(), c);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }, ((CraftServer) Bukkit.getServer()).getServer());
        //we shouldn't stress main thread
        if (++currChunks > MapManager.mapman.getMaxChunkLoadsPerTick()) {
            try {
                Thread.sleep(25); //hold the lock so other threads also won't stress main thread
            } catch (InterruptedException ignored) {}
        }
        //save data asynchronously
        return () -> {
            Object o = null;
            try {
                o = future.get();
                return (NBTTagCompound) save.invoke(null, world.getHandle(), c, o);
            } catch (InterruptedException e) {
                return null;
            } catch (InvocationTargetException e) {
                //We tried to use simple spigot methods at shutdown and failed, hopes for reading from disk
                if (o == null) return null;
                throw new RuntimeException(e);
            } catch (ReflectiveOperationException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        };
    }
}
