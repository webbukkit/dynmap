package org.dynmap.bukkit.helper.v119;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_19_R1.CraftWorld;
import org.dynmap.DynmapChunk;
import org.dynmap.bukkit.helper.BukkitVersionHelper;
import org.dynmap.bukkit.helper.BukkitWorld;
import org.dynmap.common.chunk.GenericChunk;
import org.dynmap.common.chunk.GenericChunkCache;
import org.dynmap.common.chunk.GenericMapChunkCache;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Container for managing chunks - dependent upon using chunk snapshots, since rendering is off server thread
 */
public class MapChunkCache119 extends GenericMapChunkCache {
    private static final AsyncChunkProvider119 provider = BukkitVersionHelper.helper.isUnsafeAsync() ? null : new AsyncChunkProvider119();
    private World w;
    /**
     * Construct empty cache
     */
    public MapChunkCache119(GenericChunkCache cc) {
        super(cc);
    }

    // Load generic chunk from existing and already loaded chunk
    @Override
    protected Supplier<GenericChunk> getLoadedChunkAsync(DynmapChunk chunk) {
        Supplier<CompoundTag> supplier = provider.getLoadedChunk((CraftWorld) w, chunk.x, chunk.z);
        return () -> {
            try {
                if (Thread.interrupted()) throw new InterruptedException(); //reason to catch it, we would throw it anyway
                CompoundTag nbt = supplier.get();
                return nbt != null ? parseChunkFromNBT(new NBT.NBTCompound(nbt)) : null;
            } catch (InterruptedException e) {
                return null;
            }
        };
    }
    protected GenericChunk getLoadedChunk(DynmapChunk chunk) {
        CraftWorld cw = (CraftWorld) w;
        if (!cw.isChunkLoaded(chunk.x, chunk.z)) return null;
        LevelChunk c = cw.getHandle().getChunkIfLoaded(chunk.x, chunk.z);
        if (c == null || !c.loaded) return null;
        CompoundTag nbt = ChunkSerializer.write(cw.getHandle(), c);
        return parseChunkFromNBT(new NBT.NBTCompound(nbt));
    }

    // Load generic chunk from unloaded chunk
    @Override
    protected Supplier<GenericChunk> loadChunkAsync(DynmapChunk chunk){
        try {
            CompletableFuture<CompoundTag> nbt = provider.getChunk(((CraftWorld) w).getHandle(), chunk.x, chunk.z);
            return () -> {
                try {
                    if (Thread.interrupted()) throw new InterruptedException(); //reason to catch it, we would throw it anyway
                    return nbt.join() == null ? null : parseChunkFromNBT(new NBT.NBTCompound(nbt.join()));
                } catch (InterruptedException e) {
                    return null;
                }
            };
        } catch (InvocationTargetException | IllegalAccessException ignored) {
            return () -> null;
        }
    }

    protected GenericChunk loadChunk(DynmapChunk chunk) {
        CraftWorld cw = (CraftWorld) w;
        CompoundTag nbt = null;
        ChunkPos cc = new ChunkPos(chunk.x, chunk.z);
        GenericChunk gc = null;
        try {	// BUGBUG - convert this all to asyn properly, since now native async
            nbt = cw.getHandle().getChunkSource().chunkMap.read(cc).join().orElse(null);	// playerChunkMap
        } catch (CancellationException ignored) {}
        if (nbt != null) {
            gc = parseChunkFromNBT(new NBT.NBTCompound(nbt));
        }
        return gc;
    }

    public void setChunks(BukkitWorld dw, List<DynmapChunk> chunks) {
        this.w = dw.getWorld();
        super.setChunks(dw, chunks);
    }
}
