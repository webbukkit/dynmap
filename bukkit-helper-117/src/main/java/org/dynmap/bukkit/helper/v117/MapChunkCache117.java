package org.dynmap.bukkit.helper.v117;

import net.minecraft.world.level.biome.BiomeBase;
import net.minecraft.world.level.biome.BiomeFog;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.dynmap.DynmapChunk;
import org.dynmap.bukkit.helper.BukkitWorld;
import org.dynmap.common.BiomeMap;
import org.dynmap.common.chunk.GenericChunk;
import org.dynmap.common.chunk.GenericChunkCache;
import org.dynmap.common.chunk.GenericMapChunkCache;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.chunk.Chunk;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkRegionLoader;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Container for managing chunks - dependent upon using chunk snapshots, since rendering is off server thread
 */
public class MapChunkCache117 extends GenericMapChunkCache {
	private World w;
	/**
	 * Construct empty cache
	 */
	public MapChunkCache117(GenericChunkCache cc) {
		super(cc);
	}

	// Load generic chunk from existing and already loaded chunk
	protected GenericChunk getLoadedChunk(DynmapChunk chunk) {
        CraftWorld cw = (CraftWorld) w;
        NBTTagCompound nbt = null;
        GenericChunk gc = null;
        if (cw.isChunkLoaded(chunk.x, chunk.z)) {
            Chunk c = cw.getHandle().getChunkAt(chunk.x,  chunk.z);
            if ((c != null) && c.h) {	// c.loaded
                nbt = ChunkRegionLoader.saveChunk(cw.getHandle(), c);
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
        ChunkCoordIntPair cc = new ChunkCoordIntPair(chunk.x,  chunk.z);
        GenericChunk gc = null;
        try {
            nbt = cw.getHandle().getChunkProvider().a.read(cc);	// playerChunkMap
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
        return bm.<BiomeBase>getBiomeObject().map(BiomeBase::l).flatMap(BiomeFog::e).orElse(colormap[bm.biomeLookup()]);
    }

    @Override
    public int getGrassColor(BiomeMap bm, int[] colormap, int x, int z) {
        BiomeFog fog = bm.<BiomeBase>getBiomeObject().map(BiomeBase::l).orElse(null);
        if (fog == null) return colormap[bm.biomeLookup()];
        return fog.g().a(x, z, fog.f().orElse(colormap[bm.biomeLookup()]));
    }
}
