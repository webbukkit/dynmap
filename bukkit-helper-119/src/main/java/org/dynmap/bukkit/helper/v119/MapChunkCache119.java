package org.dynmap.bukkit.helper.v119;

import net.minecraft.core.IRegistry;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.world.level.biome.BiomeBase;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_19_R1.CraftWorld;
import org.dynmap.DynmapChunk;
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
import org.dynmap.utils.MapIterator;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Container for managing chunks - dependent upon using chunk snapshots, since rendering is off server thread
 */
public class MapChunkCache119 extends GenericMapChunkCache {
    private static final AsyncChunkProvider119 provider = BukkitVersionHelper.helper.isUnsafeAsync() ? null : new AsyncChunkProvider119();
    private CraftWorld w;
    /**
     * Construct empty cache
     */
    public MapChunkCache119(GenericChunkCache cc) {
        super(cc);
    }

    // Load generic chunk from existing and already loaded chunk
    @Override
    protected Supplier<GenericChunk> getLoadedChunkAsync(DynmapChunk chunk) {
        Supplier<NBTTagCompound> supplier = provider.getLoadedChunk(w, chunk.x, chunk.z);
        return () -> {
            NBTTagCompound nbt = supplier.get();
            return nbt != null ? parseChunkFromNBT(new NBT.NBTCompound(nbt)) : null;
        };
    }
    protected GenericChunk getLoadedChunk(DynmapChunk chunk) {
        CraftWorld cw = w;
        if (!cw.isChunkLoaded(chunk.x, chunk.z)) return null;
        Chunk c = cw.getHandle().getChunkIfLoaded(chunk.x, chunk.z);
        if (c == null || !c.o) return null;    // c.loaded
        NBTTagCompound nbt = ChunkRegionLoader.a(cw.getHandle(), c);
        return nbt != null ? parseChunkFromNBT(new NBT.NBTCompound(nbt)) : null;
    }

    // Load generic chunk from unloaded chunk
    @Override
    protected Supplier<GenericChunk> loadChunkAsync(DynmapChunk chunk){
        try {
            CompletableFuture<NBTTagCompound> nbt = provider.getChunk(w.getHandle(), chunk.x, chunk.z);
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

    protected GenericChunk loadChunk(DynmapChunk chunk) {
        CraftWorld cw = w;
        NBTTagCompound nbt = null;
        ChunkCoordIntPair cc = new ChunkCoordIntPair(chunk.x, chunk.z);
        GenericChunk gc = null;
        try {	// BUGBUG - convert this all to asyn properly, since now native async
            nbt = cw.getHandle().k().a.f(cc).join().get();	// playerChunkMap
        } catch (CancellationException cx) {
        } catch (NoSuchElementException snex) {
        }
        if (nbt != null) {
            gc = parseChunkFromNBT(new NBT.NBTCompound(nbt));
        }
        return gc;
    }

    public void setChunks(BukkitWorld dw, List<DynmapChunk> chunks) {
        this.w = (CraftWorld) dw.getWorld();
        super.setChunks(dw, chunks);
    }

    private class MapIterator119 extends OurMapIterator {
        int light;
        MapIterator119(int x, int y, int z) {
            super(x, y, z);
            light = dw.getEnvironment().equals("the_end") ? 15 : -1;
        }

        @Override
        public int getBlockSkyLight() {
            return light == -1 ? super.getBlockSkyLight() : light;
        }

        @Override
        public int getSmoothGrassColorMultiplier(int[] colormap) {
            int r = 0;
            int g = 0;
            int b = 0;
            int cnt = 0;
            IRegistry<BiomeBase> reg  = BukkitVersionHelperSpigot119.getBiomeReg();
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    BiomeMap map = this.getBiomeRel(x, z);
                    if (map.getResourcelocation() == null) continue;
                    BiomeBase base = reg.a(MinecraftKey.a(map.getResourcelocation()));
                    int rgb = 0;
                    if (base != null) {
                        rgb = base.j().f().orElse(colormap[map.biomeLookup()]);
                        rgb = base.j().g().a(x + getX(), z + getZ(), rgb);
                    }
                    if (rgb == 0) rgb = colormap[map.biomeLookup()];
                    b += rgb & 0xFF;
                    rgb >>= 8;
                    g += rgb & 0xFF;
                    rgb >>= 8;
                    r += rgb & 0xFF;
                    cnt++;
                }
            }
            if (cnt < 1) return 0;
            r /= cnt;
            g /= cnt;
            b /= cnt;
            return r << 16 | g << 8 | b;
        }

        @Override
        public int getSmoothFoliageColorMultiplier(int[] colormap) {
            int r = 0;
            int g = 0;
            int b = 0;
            int cnt = 0;
            IRegistry<BiomeBase> reg  = BukkitVersionHelperSpigot119.getBiomeReg();
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    BiomeMap map = this.getBiomeRel(x, z);
                    if (map.getResourcelocation() == null) continue;
                    BiomeBase base = reg.a(MinecraftKey.a(map.getResourcelocation()));
                    int rgb = base == null ? colormap[map.biomeLookup()] : base.j().e().orElse(colormap[map.biomeLookup()]);
                    b += rgb & 0xFF;
                    rgb >>= 8;
                    g += rgb & 0xFF;
                    rgb >>= 8;
                    r += rgb & 0xFF;
                    cnt++;
                }
            }
            if (cnt < 1) return 0;
            r /= cnt;
            g /= cnt;
            b /= cnt;
            return r << 16 | g << 8 | b;
        }
    }

    @Override
    public MapIterator getIterator(int x, int y, int z) {
        return new MapIterator119(x, y, z);
    }
}
