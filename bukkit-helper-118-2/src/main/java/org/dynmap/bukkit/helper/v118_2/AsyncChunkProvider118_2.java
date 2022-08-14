package org.dynmap.bukkit.helper.v118_2;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.level.chunk.Chunk;
import net.minecraft.world.level.chunk.IChunkAccess;
import net.minecraft.world.level.chunk.storage.ChunkRegionLoader;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_18_R2.CraftServer;
import org.bukkit.craftbukkit.v1_18_R2.CraftWorld;
import org.dynmap.MapManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The provider used to work with paper libs
 * Because paper libs need java 17 we can't interact with them directly
 */
@SuppressWarnings({"JavaReflectionMemberAccess"}) //java don't know about paper
public class AsyncChunkProvider118_2 {
    private final Thread ioThread;
    private final Method getChunk;
    private final Predicate<NBTTagCompound> ifFailed;
    private final Method getAsyncSaveData;
    private final Method save;
    private int currTick = MinecraftServer.currentTick;
    private int currChunks = 0;

    AsyncChunkProvider118_2 () {
        try {
            Predicate<NBTTagCompound> ifFailed1 = null;
            Method getChunk1 = null, getAsyncSaveData1 = null, save1 = null;
            Thread ioThread1 = null;
            try {
                Class<?> threadClass = Class.forName("com.destroystokyo.paper.io.PaperFileIOThread");
                Class<?> asyncChunkData = Arrays.stream(ChunkRegionLoader.class.getClasses())
                        .filter(c -> c.getSimpleName().equals("AsyncSaveData"))
                        .findFirst()
                        .orElseThrow(RuntimeException::new);
                getAsyncSaveData1 = ChunkRegionLoader.class.getMethod("getAsyncSaveData", WorldServer.class, IChunkAccess.class);
                save1 = ChunkRegionLoader.class.getMethod("saveChunk", WorldServer.class, IChunkAccess.class, asyncChunkData);
                Class<?>[] classes = threadClass.getClasses();
                Class<?> holder = Arrays.stream(classes).filter(aClass -> aClass.getSimpleName().equals("Holder")).findAny().orElseThrow(RuntimeException::new);
                ioThread1 = (Thread) holder.getField("INSTANCE").get(null);
                getChunk1 = threadClass.getMethod("loadChunkDataAsync", WorldServer.class, int.class, int.class, int.class, Consumer.class, boolean.class, boolean.class, boolean.class);
                NBTTagCompound failure = (NBTTagCompound) threadClass.getField("FAILURE_VALUE").get(null);
                ifFailed1 = nbtTagCompound -> nbtTagCompound == failure;
            } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException | NoSuchMethodException e) {
                e.printStackTrace();
            }
            getAsyncSaveData = Objects.requireNonNull(getAsyncSaveData1);
            save = Objects.requireNonNull(save1);
            ifFailed = Objects.requireNonNull(ifFailed1);
            getChunk = Objects.requireNonNull(getChunk1);
            ioThread = Objects.requireNonNull(ioThread1);
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
    public CompletableFuture<NBTTagCompound> getChunk(WorldServer world, int x, int y) throws InvocationTargetException, IllegalAccessException {
        CompletableFuture<Object> future = new CompletableFuture<>();
        getChunk.invoke(ioThread,world,x,y,5,(Consumer<Object>) future::complete, false, true, true);
        return future.thenApply((resultFuture) -> {
            if (resultFuture == null) return null;
            try {
                NBTTagCompound compound =  (NBTTagCompound) resultFuture.getClass().getField("chunkData").get(resultFuture);
                return ifFailed.test(compound) ? null : compound;
            } catch (IllegalAccessException | NoSuchFieldException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    public synchronized Supplier<NBTTagCompound> getLoadedChunk(CraftWorld world, int x, int z) {
        if (!world.isChunkLoaded(x, z)) return () -> null;
        Chunk c = world.getHandle().getChunkIfLoaded(x, z); //already safe async on vanilla
        if ((c == null) || !c.o) return () -> null;    // c.loaded
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
            } catch (IllegalAccessException | InvocationTargetException e) {
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
