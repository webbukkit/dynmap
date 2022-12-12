package org.dynmap.bukkit.helper.v118_2;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.BiomeBase;
import net.minecraft.world.level.biome.BiomeFog;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_18_R2.CraftServer;
import org.bukkit.craftbukkit.v1_18_R2.CraftWorld;
import org.dynmap.DynmapChunk;
import org.dynmap.MapManager;
import org.dynmap.bukkit.helper.BukkitVersionHelper;
import org.dynmap.bukkit.helper.BukkitWorld;
import org.dynmap.common.BiomeMap;
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
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Container for managing chunks - dependent upon using chunk snapshots, since rendering is off server thread
 */
public class MapChunkCache118_2 extends GenericMapChunkCache {
    private static final AsyncChunkProvider118_2 provider = BukkitVersionHelper.helper.isUnsafeAsync() ? null : new AsyncChunkProvider118_2();
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
        if (!cw.isChunkLoaded(chunk.x, chunk.z)) return null;
        Chunk c = cw.getHandle().getChunkIfLoaded(chunk.x, chunk.z); //already safe async on vanilla
        if ((c == null) || !c.o) return null;    // c.loaded
        NBTTagCompound nbt = ChunkRegionLoader.a(cw.getHandle(), c);
        return nbt != null ? parseChunkFromNBT(new NBT.NBTCompound(nbt)) : null;
    }
    @Override
    protected Supplier<GenericChunk> getLoadedChunkAsync(DynmapChunk ch) {
        Supplier<NBTTagCompound> nbtSupplier = provider.getLoadedChunk((CraftWorld) w, ch.x, ch.z);
        return () -> {
            NBTTagCompound nbt = nbtSupplier.get();
            return nbt == null ? null : parseChunkFromNBT(new NBT.NBTCompound(nbt));
        };
    }

    @Override
    protected Supplier<GenericChunk> loadChunkAsync(DynmapChunk chunk){
        try {
            CompletableFuture<NBTTagCompound> nbt = provider.getChunk(((CraftWorld) w).getHandle(), chunk.x, chunk.z);
            return () -> {
                NBTTagCompound compound;
                try {
                    compound = nbt.get();
                } catch (InterruptedException e) {
                    return null;
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
                return compound == null ? null : parseChunkFromNBT(new NBT.NBTCompound(compound));
            };
        } catch (InvocationTargetException | IllegalAccessException ignored) {
            return () -> null;
        }
    }

    // Load generic chunk from unloaded chunk
    protected GenericChunk loadChunk(DynmapChunk chunk) {
        CraftWorld cw = (CraftWorld) w;
        NBTTagCompound nbt = null;
        ChunkCoordIntPair cc = new ChunkCoordIntPair(chunk.x, chunk.z);
        GenericChunk gc = null;
        try {
            nbt = cw.getHandle().k().a.f(cc);	// playerChunkMap
        } catch (IOException iox) {
        }
        if (nbt != null) {
            gc = parseChunkFromNBT(new NBT.NBTCompound(nbt));
        }
        return gc;
    }

    public void setChunks(BukkitWorld dw, List<DynmapChunk> chunks) {
        this.w = dw.getWorld();
        super.setChunks(dw, chunks);
    }

    @Override
    public int getFoliageColor(BiomeMap bm, int[] colormap, int x, int z) {
        return bm.<BiomeBase>getBiomeObject().map(BiomeBase::j).flatMap(BiomeFog::e).orElse(colormap[bm.biomeLookup()]);
    }

    @Override
    public int getGrassColor(BiomeMap bm, int[] colormap, int x, int z) {
        BiomeFog fog = bm.<BiomeBase>getBiomeObject().map(BiomeBase::j).orElse(null);
        if (fog == null) return colormap[bm.biomeLookup()];
        return fog.g().a(x, z, fog.f().orElse(colormap[bm.biomeLookup()]));
    }
}
