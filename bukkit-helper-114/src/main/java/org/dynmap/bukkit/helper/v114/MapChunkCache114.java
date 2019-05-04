package org.dynmap.bukkit.helper.v114;

import org.bukkit.block.Biome;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.dynmap.bukkit.helper.AbstractMapChunkCache;
import org.dynmap.bukkit.helper.BukkitVersionHelper;
import org.dynmap.renderer.DynmapBlockState;

import net.minecraft.server.v1_14_R1.DataPaletteBlock;

/**
 * Container for managing chunks - dependent upon using chunk snapshots, since rendering is off server thread
 */
public class MapChunkCache114 extends AbstractMapChunkCache {

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
    		return BukkitVersionHelperSpigot114.dataToState.getOrDefault(blockids[y >> 4].a(x & 0xF, y & 0xF, z & 0xF), DynmapBlockState.AIR);
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
		boolean generated = true;
		// Check one in each direction: see if all are generated
		for (int xx = x-4; xx <= x+4; xx++) {
			for (int zz = z-4; zz <= z+4; zz++) {
				if (w.isChunkGenerated(xx, zz) == false) {
					generated = false;
					break;
				}
			}
		}
		boolean rslt = false;
		if (generated) {
			rslt = w.loadChunk(x, z, true);
		}
		return rslt;
    }
}
