package org.dynmap.bukkit.helper.v118_2;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.level.WorldServer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The provider used to work with paper libs
 * Because paper libs need java 17 we can't interact with them directly
 */
public class AsyncChunkProvider118_2 {
    private final Thread ioThread;
    private final Method getChunk;
    private final Predicate<NBTTagCompound> ifFailed;
    AsyncChunkProvider118_2 () {
        try {
            Predicate<NBTTagCompound> ifFailed1 = null;
            Method getChunk1 = null;
            Thread ioThread1 = null;
            try {
                Class<?> threadClass = Class.forName("com.destroystokyo.paper.io.PaperFileIOThread");
                Class<?>[] classes = threadClass.getClasses();
                Class<?> holder = Arrays.stream(classes).filter(aClass -> aClass.getSimpleName().equals("Holder")).findAny().orElseThrow(RuntimeException::new);
                ioThread1 = (Thread) holder.getField("INSTANCE").get(null);
                getChunk1 = threadClass.getMethod("loadChunkDataAsync", WorldServer.class, int.class, int.class, int.class, Consumer.class, boolean.class, boolean.class, boolean.class);
                NBTTagCompound failure = (NBTTagCompound) threadClass.getField("FAILURE_VALUE").get(null);
                ifFailed1 = nbtTagCompound -> nbtTagCompound == failure;
            } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException | NoSuchMethodException e) {
                e.printStackTrace();
            }
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
}
