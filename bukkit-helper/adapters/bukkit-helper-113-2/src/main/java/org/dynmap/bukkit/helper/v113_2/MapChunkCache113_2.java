package org.dynmap.bukkit.helper.v113_2;

import org.bukkit.block.Biome;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.dynmap.DynmapCore;
import org.dynmap.bukkit.helper.AbstractMapChunkCache;
import org.dynmap.bukkit.helper.BukkitVersionHelper;
import org.dynmap.renderer.DynmapBlockState;

import net.minecraft.server.v1_13_R2.DataPaletteBlock;
import net.minecraft.server.v1_13_R2.Chunk;

/**
 * Container for managing chunks - dependent upon using chunk snapshots, since rendering is off server thread
 */
public class MapChunkCache113_2 extends AbstractMapChunkCache {

    public static class WrappedSnapshot implements Snapshot {
    	private final ChunkSnapshot ss;
    	private final DataPaletteBlock[] blockids;
    	private final int sectionmask;
		public WrappedSnapshot(ChunkSnapshot ss) {
    		this.ss = ss;
    		blockids = (DataPaletteBlock[]) BukkitVersionHelper.helper.getBlockIDFieldFromSnapshot(ss);
    		int mask = 0;
    		for (int i = 0; i < blockids.length; i++) {
    			if (ss.isSectionEmpty(i))
    				mask |= (1 << i);
    		}
    		sectionmask = mask;
    	}
		@Override
    	public final DynmapBlockState getBlockType(int x, int y, int z) {
    		if ((sectionmask & (1 << (y >> 4))) != 0)
    			return DynmapBlockState.AIR;
    		return BukkitVersionHelperSpigot113_2.dataToState.getOrDefault(blockids[y >> 4].a(x & 0xF, y & 0xF, z & 0xF), DynmapBlockState.AIR);
    	}
		@Override
        public final int getBlockSkyLight(int x, int y, int z) {
        	return ss.getBlockSkyLight(x, y, z);
        }
		@Override
        public final int getBlockEmittedLight(int x, int y, int z) {
        	return ss.getBlockEmittedLight(x, y, z);
        }
		@Override
        public final int getHighestBlockYAt(int x, int z) {
        	return ss.getHighestBlockYAt(x, z);
        }
		@Override
        public final Biome getBiome(int x, int z) {
        	return ss.getBiome(x, z);
        }
		@Override
        public final boolean isSectionEmpty(int sy) {
        	return (sectionmask & (1 << sy)) != 0;
        }
		@Override
        public final Object[] getBiomeBaseFromSnapshot() {
        	return BukkitVersionHelper.helper.getBiomeBaseFromSnapshot(ss);
        }
    }

	@Override
	public Snapshot wrapChunkSnapshot(ChunkSnapshot css) {
		return new WrappedSnapshot(css);
	}
	@Override
    public boolean loadChunkNoGenerate(World w, int x, int z) {
		boolean rslt = w.loadChunk(x,  z, false);
		// Workaround for Spigot 1.13.2 bug - check if generated and do load-with-generate if so to drive migration of old chunks
		if ((!rslt) && DynmapCore.migrateChunks()) {
			boolean generated = true;
			// Check one in each direction: see if all are generated
			for (int xx = x-3; xx <= x+3; xx++) {
				for (int zz = z-3; zz <= z+3; zz++) {
					if (w.isChunkGenerated(xx, zz) == false) {
						generated = false;
						break;
					}
				}
			}
			if (generated) {
				rslt = w.loadChunk(x, z, true);
			}
		}
		return rslt;
    }
}
