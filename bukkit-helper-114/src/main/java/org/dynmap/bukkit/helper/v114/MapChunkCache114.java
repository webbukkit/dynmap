package org.dynmap.bukkit.helper.v114;

import org.bukkit.block.Biome;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;

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
		for (int xx = x-3; xx <= x+3; xx++) {
			for (int zz = z-3; zz <= z+3; zz++) {
				if (isChunkGenerated(w, xx, zz) == false) {
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
	
	private static class CacheRec {
		long timestamp;
		long[] blockmap; // ( bit = 1 at blockmap[z] & (1 << x))
	}
	private static HashMap<String, CacheRec> regioncache = new HashMap<String, CacheRec>();
	private static long CACHE_TIMEOUT = 15000L;	// 15 second cache
	
	private static boolean isChunkGenerated(World w, int x, int z) {
		String fn = String.format("%s/region/r.%d.%d.mca", w.getWorldFolder().getPath(), (x >> 5), (z >> 5));
		CacheRec rec = regioncache.get(fn);
		long ts = System.currentTimeMillis();
		if ((rec == null) || (rec.timestamp < ts)) {
			if (rec == null) { 
				rec = new CacheRec();
				rec.blockmap = new long[32];
			}
			rec.timestamp = ts + CACHE_TIMEOUT;
			RandomAccessFile raf = null;
			byte[] dat = new byte[4096];
			try {
				raf = new RandomAccessFile(fn, "r");
				raf.seek(0);
				raf.read(dat);
			} catch (IOException iox) {
			} finally {
				if (raf != null) {
					try {
						raf.close();
					} catch (IOException iox) {
					}
				}
			}
			// Build cache map
			for (int zz = 0; zz < 32; zz++) {
				long val = 0;
				for (int xx = 0; xx < 32; xx++) {
					int off = 4*((zz << 5) + xx);
					int v = dat[off] | dat[off+1] | dat[off+2] | dat[off+3];
					if (v != 0)
						val |= (1L << xx);
				}
				rec.blockmap[zz] = val;
			}
			regioncache.put(fn, rec);
		}
		return (rec.blockmap[z & 31] & (1L << (x & 31))) != 0;
	}
}
