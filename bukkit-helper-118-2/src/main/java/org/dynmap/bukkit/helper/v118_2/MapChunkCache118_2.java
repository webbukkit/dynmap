package org.dynmap.bukkit.helper.v118_2;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_18_R2.CraftServer;
import org.bukkit.craftbukkit.v1_18_R2.CraftWorld;
import org.dynmap.DynmapChunk;
import org.dynmap.bukkit.helper.BukkitVersionHelper;
import org.dynmap.bukkit.helper.BukkitWorld;
import org.dynmap.common.chunk.GenericChunk;
import org.dynmap.common.chunk.GenericChunkCache;
import org.dynmap.common.chunk.GenericMapChunkCache;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.chunk.storage.ChunkRegionLoader;
import net.minecraft.world.level.chunk.Chunk;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Container for managing chunks - dependent upon using chunk snapshots, since rendering is off server thread
 */
public class MapChunkCache118_2 extends GenericMapChunkCache {
    private final AsyncChunkProvider118_2 provider = BukkitVersionHelper.helper.isUnsafeAsync() ? null : new AsyncChunkProvider118_2();
    private World w;
    /**
     * Construct empty cache
     */
    public MapChunkCache118_2(GenericChunkCache cc) {
        super(cc);
    }

    // Load generic chunk from existing and already loaded chunk
    protected GenericChunk getLoadedChunk(DynmapChunk chunk) {
        CraftWorld cw = (CraftWorld) w;
        NBTTagCompound nbt = null;
        GenericChunk gc = null;
        if (cw.isChunkLoaded(chunk.x, chunk.z)) {
            Chunk c = cw.getHandle().getChunkIfLoaded(chunk.x, chunk.z); //already safe async on vanilla
            if ((c != null) && c.o) {    // c.loaded
                if (provider == null) { //idk why, but paper uses this only sync, so I won't be smarter
                    nbt = ChunkRegionLoader.a(cw.getHandle(), c);
                } else {
                    nbt = CompletableFuture.supplyAsync(() -> ChunkRegionLoader.a(cw.getHandle(), c), ((CraftServer) Bukkit.getServer()).getServer()).join();
                }
            }
            if (nbt != null) {
                gc = parseChunkFromNBT(new NBT.NBTCompound(nbt));
            }
        }
        return gc;
    }
    // Load generic chunk from unloaded chunk
    protected GenericChunk loadChunk(DynmapChunk chunk) {
        CraftWorld cw = (CraftWorld) w;
        NBTTagCompound nbt = null;
        ChunkCoordIntPair cc = new ChunkCoordIntPair(chunk.x, chunk.z);
        GenericChunk gc = null;
        try {
            if (provider == null){
                nbt = cw.getHandle().k().a.f(cc);	// playerChunkMap
            } else {
                nbt = provider.getChunk(cw.getHandle(),chunk.x, chunk.z);
            }
        } catch (IOException | InvocationTargetException | IllegalAccessException | NoSuchFieldException ignored) {}
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
