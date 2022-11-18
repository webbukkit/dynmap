package org.dynmap.bukkit.helper.v116_4;

import net.minecraft.server.v1_16_R3.*;

import org.bukkit.World;
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld;
import org.dynmap.DynmapChunk;
import org.dynmap.bukkit.helper.BukkitWorld;
import org.dynmap.common.BiomeMap;
import org.dynmap.common.chunk.GenericChunk;
import org.dynmap.common.chunk.GenericChunkCache;
import org.dynmap.common.chunk.GenericMapChunkCache;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Container for managing chunks - dependent upon using chunk snapshots, since rendering is off server thread
 */
public class MapChunkCache116_4 extends GenericMapChunkCache {
	private World w;
	/**
	 * Construct empty cache
	 */
	public MapChunkCache116_4(GenericChunkCache cc) {
		super(cc);
	}

	// Load generic chunk from existing and already loaded chunk
	protected GenericChunk getLoadedChunk(DynmapChunk chunk) {
        CraftWorld cw = (CraftWorld) w;
        NBTTagCompound nbt = null;
        GenericChunk gc = null;
        if (cw.isChunkLoaded(chunk.x, chunk.z)) {
            Chunk c = cw.getHandle().getChunkAt(chunk.x,  chunk.z);
            if ((c != null) && c.loaded) {
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
            nbt = cw.getHandle().getChunkProvider().playerChunkMap.read(cc);
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
        Optional<BiomeBase> base = bm.getBiomeObject();
        return BukkitVersionHelperSpigot116_4.getBiomeBaseFoliageMult(base.orElse(null)).orElse(colormap[bm.biomeLookup()]);
    }

    @Override
    public int getGrassColor(BiomeMap bm, int[] colormap, int x, int z) {
        BiomeBase base = bm.<BiomeBase>getBiomeObject().orElse(null);
        if (base == null) return bm.getModifiedGrassMultiplier(colormap[bm.biomeLookup()]);
        int grassMult = BukkitVersionHelperSpigot116_4.getBiomeBaseGrassMult(base).orElse(colormap[bm.biomeLookup()]);
        BiomeFog.GrassColor modifier = BukkitVersionHelperSpigot116_4.getBiomeBaseGrassModifier(base);
        if (modifier == BiomeFog.GrassColor.DARK_FOREST) {
            return ((grassMult & 0xfefefe) + 0x28340a) >> 1;
        } else if (modifier == BiomeFog.GrassColor.SWAMP) {
            double var5 = BiomeBase.f.a(x * 0.0225, z * 0.0225, false);
            return var5 < -0.1 ? 0x4c763c : 0x6a7039;
        } else {
            return grassMult;
        }
    }
}

