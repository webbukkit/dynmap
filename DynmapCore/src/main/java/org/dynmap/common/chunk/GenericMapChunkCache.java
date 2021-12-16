package org.dynmap.common.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.dynmap.DynmapChunk;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.common.BiomeMap;
import org.dynmap.common.chunk.GenericChunkCache.ChunkCacheRec;
import org.dynmap.hdmap.HDBlockModels;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.renderer.RenderPatchFactory;
import org.dynmap.utils.DynIntHashMap;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.DataBitsPacked;
import org.dynmap.utils.VisibilityLimit;

/**
 * Abstract container for handling map cache and map iterator, using DynmapChunks
 */
public abstract class GenericMapChunkCache extends MapChunkCache {
	private static boolean init = false;
	protected DynmapWorld dw;
	private int nsect;
	private int sectoff;	// Offset for sake of negative section indexes
	private List<DynmapChunk> chunks;
	private ListIterator<DynmapChunk> iterator;
	private int x_min, x_max, z_min, z_max;
	private int x_dim;
	private HiddenChunkStyle hidestyle = HiddenChunkStyle.FILL_AIR;
	private List<VisibilityLimit> visible_limits = null;
	private List<VisibilityLimit> hidden_limits = null;
	private boolean isempty = true;
	private int snapcnt;
	private GenericChunk[] snaparray; /* Index = (x-x_min) + ((z-z_min)*x_dim) */
	private boolean[][] isSectionNotEmpty; /* Indexed by snapshot index, then by section index */

	private static final BlockStep unstep[] = { BlockStep.X_MINUS, BlockStep.Y_MINUS, BlockStep.Z_MINUS,
			BlockStep.X_PLUS, BlockStep.Y_PLUS, BlockStep.Z_PLUS };

	/**
	 * Iterator for traversing map chunk cache (base is for non-snapshot)
	 */
	public class OurMapIterator implements MapIterator {
		private int x, y, z, chunkindex, bx, bz;
		private GenericChunk snap;
		private BlockStep laststep;
		private DynmapBlockState blk;
		private final int worldheight;
		private final int ymin;

		OurMapIterator(int x0, int y0, int z0) {
			initialize(x0, y0, z0);
			worldheight = dw.worldheight;
			ymin = dw.minY;
		}

		@Override
		public final void initialize(int x0, int y0, int z0) {
			this.x = x0;
			this.y = y0;
			this.z = z0;
			this.chunkindex = ((x >> 4) - x_min) + (((z >> 4) - z_min) * x_dim);
			this.bx = x & 0xF;
			this.bz = z & 0xF;

			if ((chunkindex >= snapcnt) || (chunkindex < 0)) {
				snap = GenericChunk.EMPTY;
			} else {
				snap = snaparray[chunkindex];
			}

			laststep = BlockStep.Y_MINUS;

			if ((y >= ymin) && (y < worldheight)) {
				blk = null;
			} else {
				blk = DynmapBlockState.AIR;
			}
		}

		@Override
		public int getBlockSkyLight() {
			try {
				return snap.getBlockSkyLight(bx, y, bz);
			} catch (ArrayIndexOutOfBoundsException aioobx) {
				return 15;
			}
		}

		@Override
		public final int getBlockEmittedLight() {
			try {
				return snap.getBlockEmittedLight(bx, y, bz);
			} catch (ArrayIndexOutOfBoundsException aioobx) {
				return 0;
			}
		}

		@Override
		public final BiomeMap getBiome() {
			try {
				return snap.getBiome(bx, y, bz);
			} catch (Exception ex) {
				return BiomeMap.NULL;
			}
		}

		private final BiomeMap getBiomeRel(int dx, int dz) {
			int nx = x + dx;
			int nz = z + dz;
			int nchunkindex = ((nx >> 4) - x_min) + (((nz >> 4) - z_min) * x_dim);
			if ((nchunkindex >= snapcnt) || (nchunkindex < 0)) {
				return BiomeMap.NULL;
			} else {
				return snaparray[nchunkindex].getBiome(nx, y, nz);
			}
		}
		
		@Override
		public final int getSmoothGrassColorMultiplier(int[] colormap) {
			int mult = 0xFFFFFF;

			try {
				int raccum = 0;
				int gaccum = 0;
				int baccum = 0;
				int cnt = 0;
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						BiomeMap bm = getBiomeRel(dx, dz);
						if (bm == BiomeMap.NULL) continue; 
						int rmult = bm.getModifiedGrassMultiplier(colormap[bm.biomeLookup()]);
						raccum += (rmult >> 16) & 0xFF;
						gaccum += (rmult >> 8) & 0xFF;
						baccum += rmult & 0xFF;
						cnt++;
					}
				}
				cnt = (cnt > 0) ? cnt : 1;
				mult = ((raccum / cnt) << 16) | ((gaccum / cnt) << 8) | (baccum / cnt);
			} catch (Exception x) {
				//Log.info("getSmoothGrassColorMultiplier() error: " + x);
				mult = 0xFFFFFF;
			}

			//Log.info(String.format("getSmoothGrassColorMultiplier() at %d, %d = %X", x, z, mult));
			return mult;
		}

		@Override
		public final int getSmoothFoliageColorMultiplier(int[] colormap) {
			int mult = 0xFFFFFF;

			try {
				int raccum = 0;
				int gaccum = 0;
				int baccum = 0;
				int cnt = 0;
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						BiomeMap bm = getBiomeRel(dx, dz);
						if (bm == BiomeMap.NULL) continue; 
						int rmult = bm.getModifiedFoliageMultiplier(colormap[bm.biomeLookup()]);
						raccum += (rmult >> 16) & 0xFF;
						gaccum += (rmult >> 8) & 0xFF;
						baccum += rmult & 0xFF;
						cnt++;
					}
				}
				cnt = (cnt > 0) ? cnt : 1;
				mult = ((raccum / cnt) << 16) | ((gaccum / cnt) << 8) | (baccum / cnt);
			} catch (Exception x) {
				//Log.info("getSmoothFoliageColorMultiplier() error: " + x);
			}
			//Log.info(String.format("getSmoothFoliageColorMultiplier() at %d, %d = %X", x, z, mult));

			return mult;
		}

		@Override
		public final int getSmoothColorMultiplier(int[] colormap, int[] swampmap) {
			int mult = 0xFFFFFF;

			try {
				int raccum = 0;
				int gaccum = 0;
				int baccum = 0;
				int cnt = 0;
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						BiomeMap bm = getBiomeRel(dx, dz);
						if (bm == BiomeMap.NULL) continue; 
						int rmult;
						if (bm == BiomeMap.SWAMPLAND) {
							rmult = swampmap[bm.biomeLookup()];
						} else {
							rmult = colormap[bm.biomeLookup()];
						}
						raccum += (rmult >> 16) & 0xFF;
						gaccum += (rmult >> 8) & 0xFF;
						baccum += rmult & 0xFF;
						cnt++;
					}
				}
				cnt = (cnt > 0) ? cnt : 1;
				mult = ((raccum / cnt) << 16) | ((gaccum / cnt) << 8) | (baccum / cnt);
			} catch (Exception x) {
				//Log.info("getSmoothColorMultiplier() error: " + x);
			}
			//Log.info(String.format("getSmoothColorMultiplier() at %d, %d = %X", x, z, mult));

			return mult;
		}

		@Override
		public final int getSmoothWaterColorMultiplier() {
			int multv = 0xFFFFFF;
			try {
				int raccum = 0;
				int gaccum = 0;
				int baccum = 0;
				int cnt = 0;
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						BiomeMap bm = getBiomeRel(dx, dz);
						if (bm == BiomeMap.NULL) continue; 
						int rmult = bm.getWaterColorMult();
						raccum += (rmult >> 16) & 0xFF;
						gaccum += (rmult >> 8) & 0xFF;
						baccum += rmult & 0xFF;
						cnt++;
					}
				}
				cnt = (cnt > 0) ? cnt : 1;
				multv = ((raccum / cnt) << 16) | ((gaccum / cnt) << 8) | (baccum / cnt);
			} catch (Exception x) {
				//Log.info("getSmoothWaterColorMultiplier(nomap) error: " + x);
			}
			//Log.info(String.format("getSmoothWaterColorMultiplier(nomap) at %d, %d = %X", x, z, multv));

			return multv;
		}

		@Override
		public final int getSmoothWaterColorMultiplier(int[] colormap) {
			int mult = 0xFFFFFF;

			try {
				int raccum = 0;
				int gaccum = 0;
				int baccum = 0;
				int cnt = 0;
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						BiomeMap bm = getBiomeRel(dx, dz);
						if (bm == BiomeMap.NULL) continue; 
						int rmult = colormap[bm.biomeLookup()];
						raccum += (rmult >> 16) & 0xFF;
						gaccum += (rmult >> 8) & 0xFF;
						baccum += rmult & 0xFF;
						cnt++;
					}
				}
				cnt = (cnt > 0) ? cnt : 1;
				mult = ((raccum / cnt) << 16) | ((gaccum / cnt) << 8) | (baccum / cnt);
			} catch (Exception x) {
				//Log.info("getSmoothWaterColorMultiplier() error: " + x);
			}
			//Log.info(String.format("getSmoothWaterColorMultiplier() at %d, %d = %X", x, z, mult));

			return mult;
		}

		/**
		 * Step current position in given direction
		 */
		@Override
		public final void stepPosition(BlockStep step) {
			blk = null;

			switch (step.ordinal()) {
			case 0:
				x++;
				bx++;

				if (bx == 16) /* Next chunk? */
				{
					bx = 0;
					chunkindex++;
					if ((chunkindex >= snapcnt) || (chunkindex < 0)) {
						snap = GenericChunk.EMPTY;
					} else {
						snap = snaparray[chunkindex];
					}
				}

				break;

			case 1:
				y++;

				if (y >= worldheight) {
					blk = DynmapBlockState.AIR;
				}

				break;

			case 2:
				z++;
				bz++;

				if (bz == 16) /* Next chunk? */
				{
					bz = 0;
					chunkindex += x_dim;
					if ((chunkindex >= snapcnt) || (chunkindex < 0)) {
						snap = GenericChunk.EMPTY;
					} else {
						snap = snaparray[chunkindex];
					}
				}
				break;

			case 3:
				x--;
				bx--;

				if (bx == -1) /* Next chunk? */
				{
					bx = 15;
					chunkindex--;
					if ((chunkindex >= snapcnt) || (chunkindex < 0)) {
						snap = GenericChunk.EMPTY;
					} else {
						snap = snaparray[chunkindex];
					}
				}

				break;

			case 4:
				y--;

				if (y < ymin) {
					blk = DynmapBlockState.AIR;
				}

				break;

			case 5:
				z--;
				bz--;

				if (bz == -1) /* Next chunk? */
				{
					bz = 15;
					chunkindex -= x_dim;
					if ((chunkindex >= snapcnt) || (chunkindex < 0)) {
						snap = GenericChunk.EMPTY;
					} else {
						snap = snaparray[chunkindex];
					}
				}
				break;
			}

			laststep = step;
		}

		/**
		 * Unstep current position to previous position
		 */
		@Override
		public final BlockStep unstepPosition() {
			BlockStep ls = laststep;
			stepPosition(unstep[ls.ordinal()]);
			return ls;
		}

		/**
		 * Unstep current position in oppisite director of given step
		 */
		@Override
		public final void unstepPosition(BlockStep s) {
			stepPosition(unstep[s.ordinal()]);
		}

		@Override
		public final void setY(int y) {
			if (y > this.y) {
				laststep = BlockStep.Y_PLUS;
			} else {
				laststep = BlockStep.Y_MINUS;
			}

			this.y = y;

			if ((y < ymin) || (y >= worldheight)) {
				blk = DynmapBlockState.AIR;
			} else {
				blk = null;
			}
		}

		@Override
		public final int getX() {
			return x;
		}

		@Override
		public final int getY() {
			return y;
		}

		@Override
		public final int getZ() {
			return z;
		}

		@Override
		public final DynmapBlockState getBlockTypeAt(BlockStep s) {
			if (s == BlockStep.Y_MINUS) {
				if (y > ymin) {
					return snap.getBlockType(bx, y - 1, bz);
				}
			} else if (s == BlockStep.Y_PLUS) {
				if (y < (worldheight - 1)) {
					return snap.getBlockType(bx, y + 1, bz);
				}
			} else {
				BlockStep ls = laststep;
				stepPosition(s);
				DynmapBlockState tid = snap.getBlockType(bx, y, bz);
				unstepPosition();
				laststep = ls;
				return tid;
			}

			return DynmapBlockState.AIR;
		}

		@Override
		public final BlockStep getLastStep() {
			return laststep;
		}

		@Override
		public final int getWorldHeight() {
			return worldheight;
		}

		@Override
		public final long getBlockKey() {
			return (((chunkindex * (worldheight - ymin)) + (y - ymin)) << 8) | (bx << 4) | bz;
		}

		@Override
		public final boolean isEmptySection() {
	    	boolean[] flags = isSectionNotEmpty[chunkindex];
	        if(flags == null) {
				initSectionData(chunkindex);
	            flags = isSectionNotEmpty[chunkindex];
	        }
	        return !flags[(y >> 4) + sectoff];
		}

		@Override
		public final RenderPatchFactory getPatchFactory() {
			return HDBlockModels.getPatchDefinitionFactory();
		}

		@Override
		public final Object getBlockTileEntityField(String fieldId) {
			// TODO: handle tile entities here
			return null;
		}

		@Override
		public final DynmapBlockState getBlockTypeAt(int xoff, int yoff, int zoff) {
			int xx = this.x + xoff;
			int yy = this.y + yoff;
			int zz = this.z + zoff;
			int idx = ((xx >> 4) - x_min) + (((zz >> 4) - z_min) * x_dim);
			try {
				return snaparray[idx].getBlockType(xx & 0xF, yy, zz & 0xF);
			} catch (Exception x) {
				return DynmapBlockState.AIR;
			}
		}

		@Override
		public final Object getBlockTileEntityFieldAt(String fieldId, int xoff, int yoff, int zoff) {
			return null;
		}

		@Override
		public final long getInhabitedTicks() {
			try {
				return snap.getInhabitedTicks();
			} catch (Exception x) {
				return 0;
			}
		}

		@Override
		public final DynmapBlockState getBlockType() {
			if (blk == null) {
				blk = snap.getBlockType(bx, y, bz);
			}
			return blk;
		}
	}

	private class OurEndMapIterator extends OurMapIterator {
		OurEndMapIterator(int x0, int y0, int z0) {
			super(x0, y0, z0);
		}

		@Override
		public final int getBlockSkyLight() {
			return 15;
		}
	}

	private static final GenericChunkSection STONESECTION = (new GenericChunkSection.Builder()).singleBlockState(DynmapBlockState.getBaseStateByName(DynmapBlockState.STONE_BLOCK)).build();
	private static final GenericChunkSection WATERSECTION = (new GenericChunkSection.Builder()).singleBlockState(DynmapBlockState.getBaseStateByName(DynmapBlockState.WATER_BLOCK)).build();

	// Generate generic chunks for STONE and OCEAN hidden areas
	private static final GenericChunk STONE = (new GenericChunk.Builder(-64, 319))
			.addSection(0, STONESECTION).addSection(1, STONESECTION).addSection(2, STONESECTION).addSection(0, STONESECTION).build();
	private static final GenericChunk OCEAN = (new GenericChunk.Builder(-64, 319))
			.addSection(0, WATERSECTION).addSection(1, WATERSECTION).addSection(2, WATERSECTION).addSection(0, WATERSECTION).build();

	public static void init() {
		if (!init) {
			init = true;
		}
	}

	private GenericChunkCache cache;
	/**
	 * Construct empty cache
	 */
	public GenericMapChunkCache(GenericChunkCache c) {
		cache = c;	// Save reference to cache
		
		init();
	}

	public void setChunks(DynmapWorld dw, List<DynmapChunk> chunks) {
		this.dw = dw;
		nsect = (dw.worldheight - dw.minY) >> 4;
		sectoff = (-dw.minY) >> 4;
		this.chunks = chunks;

		/* Compute range */
		if (chunks.size() == 0) {
			this.x_min = 0;
			this.x_max = 0;
			this.z_min = 0;
			this.z_max = 0;
			x_dim = 1;
		}
		else {
			x_min = x_max = chunks.get(0).x;
			z_min = z_max = chunks.get(0).z;

			for (DynmapChunk c : chunks) {
				if (c.x > x_max) {
					x_max = c.x;
				}

				if (c.x < x_min) {
					x_min = c.x;
				}

				if (c.z > z_max) {
					z_max = c.z;
				}

				if (c.z < z_min) {
					z_min = c.z;
				}
			}

			x_dim = x_max - x_min + 1;
		}

		snapcnt = x_dim * (z_max - z_min + 1);
		snaparray = new GenericChunk[snapcnt];
		isSectionNotEmpty = new boolean[snapcnt][];

	}

	private boolean isChunkVisible(DynmapChunk chunk) {
		boolean vis = true;
		if (visible_limits != null) {
			vis = false;
			for (VisibilityLimit limit : visible_limits) {
				if (limit.doIntersectChunk(chunk.x, chunk.z)) {
					vis = true;
					break;
				}
			}
		}
		if (vis && (hidden_limits != null)) {
			for (VisibilityLimit limit : hidden_limits) {
				if (limit.doIntersectChunk(chunk.x, chunk.z)) {
					vis = false;
					break;
				}
			}
		}
		return vis;
	}

	private boolean tryChunkCache(DynmapChunk chunk, boolean vis) {
		/* Check if cached chunk snapshot found */
		GenericChunk ss = null;
		ChunkCacheRec ssr = cache.getSnapshot(dw.getName(), chunk.x, chunk.z);
		if (ssr != null) {
			ss = ssr.ss;
			if (!vis) {
				if (hidestyle == HiddenChunkStyle.FILL_STONE_PLAIN) {
					ss = STONE;
				} else if (hidestyle == HiddenChunkStyle.FILL_OCEAN) {
					ss = OCEAN;
				} else {
					ss = GenericChunk.EMPTY;
				}
			}
			int idx = (chunk.x - x_min) + (chunk.z - z_min) * x_dim;
			snaparray[idx] = ss;
		}
		return (ssr != null);
	}

	// Prep snapshot and add to cache
	private void prepChunkSnapshot(DynmapChunk chunk, GenericChunk ss) {
		DynIntHashMap tileData = new DynIntHashMap();

		ChunkCacheRec ssr = new ChunkCacheRec();
		ssr.ss = ss;
		ssr.tileData = tileData;
		
		cache.putSnapshot(dw.getName(), chunk.x, chunk.z, ssr);
	}

	// Load generic chunk from existing and already loaded chunk
	protected abstract GenericChunk getLoadedChunk(DynmapChunk ch);
	// Load generic chunk from unloaded chunk
	protected abstract GenericChunk loadChunk(DynmapChunk ch);
	
	/**
	 * Read NBT data from loaded chunks - needs to be called from server/world
	 * thread to be safe
	 * 
	 * @returns number loaded
	 */
	public int getLoadedChunks() {
		int cnt = 0;
		if (!dw.isLoaded()) {
			isempty = true;
			unloadChunks();
			return 0;
		}
		ListIterator<DynmapChunk> iter = chunks.listIterator();
		while (iter.hasNext()) {
			long startTime = System.nanoTime();
			DynmapChunk chunk = iter.next();
			int chunkindex = (chunk.x - x_min) + (chunk.z - z_min) * x_dim;
			if (snaparray[chunkindex] != null)
				continue; // Skip if already processed

			boolean vis = isChunkVisible(chunk);

			/* Check if cached chunk snapshot found */
			if (tryChunkCache(chunk, vis)) {
				endChunkLoad(startTime, ChunkStats.CACHED_SNAPSHOT_HIT);
				cnt++;
			}
			// If chunk is loaded and not being unloaded, we're grabbing its NBT data
			else {
				// Get generic chunk from already loaded chunk, if we can
				GenericChunk ss = getLoadedChunk(chunk);
				if (ss != null) {
					if (vis) { // If visible
						prepChunkSnapshot(chunk, ss);
					}
					else {
						if (hidestyle == HiddenChunkStyle.FILL_STONE_PLAIN) {
							ss = STONE;
						}
						else if (hidestyle == HiddenChunkStyle.FILL_OCEAN) {
							ss = OCEAN;
						}
						else {
							ss = GenericChunk.EMPTY;
						}
					}
					snaparray[chunkindex] = ss;
					endChunkLoad(startTime, ChunkStats.LOADED_CHUNKS);
					cnt++;
				}
			}
		}
		return cnt;
	}

	@Override
	public int loadChunks(int max_to_load) {
		return getLoadedChunks() + readChunks(max_to_load);

	}

	public int readChunks(int max_to_load) {
		if (!dw.isLoaded()) {
			isempty = true;
			unloadChunks();
			return 0;
		}

		int cnt = 0;

		if (iterator == null) {
			iterator = chunks.listIterator();
		}

		DynmapCore.setIgnoreChunkLoads(true);

		// Load the required chunks.
		while ((cnt < max_to_load) && iterator.hasNext()) {
			long startTime = System.nanoTime();

			DynmapChunk chunk = iterator.next();

			int chunkindex = (chunk.x - x_min) + (chunk.z - z_min) * x_dim;

			if (snaparray[chunkindex] != null)
				continue; // Skip if already processed

			boolean vis = isChunkVisible(chunk);

			/* Check if cached chunk snapshot found */
			if (tryChunkCache(chunk, vis)) {
				endChunkLoad(startTime, ChunkStats.CACHED_SNAPSHOT_HIT);
			}
			else {
				GenericChunk ss = loadChunk(chunk);
				// If read was good
				if (ss != null) {
					// If hidden
					if (!vis) {
						if (hidestyle == HiddenChunkStyle.FILL_STONE_PLAIN) {
							ss = STONE;
						}
						else if (hidestyle == HiddenChunkStyle.FILL_OCEAN) {
							ss = OCEAN;
						}
						else {
							ss = GenericChunk.EMPTY;
						}
					}
					else {
						// Prep snapshot
						prepChunkSnapshot(chunk, ss);
					}
					snaparray[chunkindex] = ss;
					endChunkLoad(startTime, ChunkStats.UNLOADED_CHUNKS);
				}
				else {
					endChunkLoad(startTime, ChunkStats.UNGENERATED_CHUNKS);
				}
			}
			cnt++;
		}

		DynmapCore.setIgnoreChunkLoads(false);

		if (iterator.hasNext() == false) { /* If we're done */
			isempty = true;

			/* Fill missing chunks with empty dummy chunk */
			for (int i = 0; i < snaparray.length; i++) {
				if (snaparray[i] == null) {
					snaparray[i] = GenericChunk.EMPTY;
				}
				else if (snaparray[i] != GenericChunk.EMPTY) {
					isempty = false;
				}
			}
		}
		return cnt;
	}

	/**
	 * Test if done loading
	 */
	public boolean isDoneLoading() {
		if (!dw.isLoaded()) {
			return true;
		}
		if (iterator != null) {
			return !iterator.hasNext();
		}

		return false;
	}

	/**
	 * Test if all empty blocks
	 */
	public boolean isEmpty() {
		return isempty;
	}

	/**
	 * Unload chunks
	 */
	public void unloadChunks() {
		if (snaparray != null) {
			for (int i = 0; i < snaparray.length; i++) {
				snaparray[i] = null;
			}

			snaparray = null;
		}
	}

	private void initSectionData(int idx) {
		isSectionNotEmpty[idx] = new boolean[nsect + 1];

		if (snaparray[idx] != GenericChunk.EMPTY) {
			for (int i = 0; i < nsect; i++) {
				if (snaparray[idx].isSectionEmpty(i - sectoff) == false) {
					isSectionNotEmpty[idx][i] = true;
				}
			}
		}
	}

    public boolean isEmptySection(int sx, int sy, int sz) {
        int idx = (sx - x_min) + (sz - z_min) * x_dim;
    	boolean[] flags = isSectionNotEmpty[idx];
        if(flags == null) {
            initSectionData(idx);
            flags = isSectionNotEmpty[idx];
        }
        return !flags[sy + sectoff];
    }

	/**
	 * Get cache iterator
	 */
	public MapIterator getIterator(int x, int y, int z) {
		if (dw.getEnvironment().equals("the_end")) {
			return new OurEndMapIterator(x, y, z);
		}
		return new OurMapIterator(x, y, z);
	}

	/**
	 * Set hidden chunk style (default is FILL_AIR)
	 */
	public void setHiddenFillStyle(HiddenChunkStyle style) {
		this.hidestyle = style;
	}

	/**
	 * Add visible area limit - can be called more than once Needs to be set before
	 * chunks are loaded Coordinates are block coordinates
	 */
	public void setVisibleRange(VisibilityLimit lim) {
		if (visible_limits == null)
			visible_limits = new ArrayList<VisibilityLimit>();
		visible_limits.add(lim);
	}

	/**
	 * Add hidden area limit - can be called more than once Needs to be set before
	 * chunks are loaded Coordinates are block coordinates
	 */
	public void setHiddenRange(VisibilityLimit lim) {
		if (hidden_limits == null)
			hidden_limits = new ArrayList<VisibilityLimit>();
		hidden_limits.add(lim);
	}

	@Override
	public DynmapWorld getWorld() {
		return dw;
	}
	
	@Override
    public boolean setChunkDataTypes(boolean blockdata, boolean biome, boolean highestblocky, boolean rawbiome) {
		return true;
	}

	private static final String litStates[] = { "light", "spawn", "heightmaps", "full" };
	
	public GenericChunk parseChunkFromNBT(GenericNBTCompound nbt) {
		if ((nbt != null) && nbt.contains("Level")) {
			nbt = nbt.getCompound("Level");
		}
		if (nbt == null) return null;
		String status = nbt.getString("Status");
		int version = nbt.getInt("DataVersion");

		boolean hasLitState = false;
		if (status != null) {
			for (int i = 0; i < litStates.length; i++) {
				if (status.equals(litStates[i])) { hasLitState = true; }
			}
		}
		boolean hasLight = hasLitState;	// Assume good light in a lit state
		
		// Start generic chunk builder
		GenericChunk.Builder bld = new GenericChunk.Builder(dw.minY,  dw.worldheight);
		int x = nbt.getInt("xPos");
		int z = nbt.getInt("zPos");

		bld.coords(x, z);
        if (nbt.contains("InhabitedTime")) {
        	bld.inhabitedTicks(nbt.getLong("InhabitedTime"));
        }
        // Check for 2D or old 3D biome data from chunk level: need these when we build old sections
        List<BiomeMap[]> old3d = null;	// By section, then YZX list
        BiomeMap[] old2d = null;
        if (nbt.contains("Biomes")) {
            int[] bb = nbt.getIntArray("Biomes");
        	if (bb != null) {
        		// If v1.15+ format
        		if (bb.length > 256) {
        			old3d = new ArrayList<BiomeMap[]>();
        			// Get 4 x 4 x 4 list for each section
        			for (int sect = 0; sect < (bb.length / 64); sect++) {
        				BiomeMap smap[] = new BiomeMap[64];
        				for (int i = 0; i < 64; i++) {
        					smap[i] = BiomeMap.byBiomeID(bb[sect*64 + i]);
        				}
        				old3d.add(smap);
        			}
        	    }
        	    else { // Else, older chunks
        	    	old2d = new BiomeMap[256];
        	        for (int i = 0; i < bb.length; i++) {
    					old2d[i] = BiomeMap.byBiomeID(bb[i]);
        	        }
        	    }
            }
        }
        // Start section builder
        GenericChunkSection.Builder sbld = new GenericChunkSection.Builder();
        /* Get sections */
        GenericNBTList sect = nbt.contains("sections") ? nbt.getList("sections", 10) : nbt.getList("Sections", 10);
        // Prescan sections to see if lit
        for (int i = 0; i < sect.size(); i++) {
            GenericNBTCompound sec = sect.getCompound(i);
            if (sec.contains("SkyLight")) { // Only consider skylight for now, since that is what we generate if needed
            	hasLight = true;
            }
        }
        // And process sections
        for (int i = 0; i < sect.size(); i++) {
            GenericNBTCompound sec = sect.getCompound(i);
            int secnum = sec.getByte("Y");
            
            DynmapBlockState[] palette = null;
            // If we've got palette and block states list, process non-empty section
            if (sec.contains("Palette", 9) && sec.contains("BlockStates", 12)) {
                GenericNBTList plist = sec.getList("Palette", 10);
                long[] statelist = sec.getLongArray("BlockStates");
                palette = new DynmapBlockState[plist.size()];
                for (int pi = 0; pi < plist.size(); pi++) {
                    GenericNBTCompound tc = plist.getCompound(pi);
                    String pname = tc.getString("Name");
                    if (tc.contains("Properties")) {
                        StringBuilder statestr = new StringBuilder();
                        GenericNBTCompound prop = tc.getCompound("Properties");
                        for (String pid : prop.getAllKeys()) {
                            if (statestr.length() > 0) statestr.append(',');
                            statestr.append(pid).append('=').append(prop.getAsString(pid));
                        }
                        palette[pi] = DynmapBlockState.getStateByNameAndState(pname, statestr.toString());
                    }
                    if (palette[pi] == null) {
                        palette[pi] = DynmapBlockState.getBaseStateByName(pname);
                    }
                    if (palette[pi] == null) {
                        palette[pi] = DynmapBlockState.AIR;
                    }
                }
            	int recsperblock = (4096 + statelist.length - 1) / statelist.length;
            	int bitsperblock = 64 / recsperblock;
            	GenericBitStorage db = null;
            	DataBitsPacked dbp = null;
            	try {
            		db = nbt.makeBitStorage(bitsperblock, 4096, statelist);
            	} catch (Exception ex) {	// Handle legacy encoded
	            	bitsperblock = (statelist.length * 64) / 4096;
            		dbp = new DataBitsPacked(bitsperblock, 4096, statelist);
            	}
                if (bitsperblock > 8) {	// Not palette
                    for (int j = 0; j < 4096; j++) {
                    	int v = (dbp != null) ? dbp.getAt(j) : db.get(j);
                    	sbld.xyzBlockState(j & 0xF, (j & 0xF00) >> 8, (j & 0xF0) >> 4, DynmapBlockState.getStateByGlobalIndex(v));
                    }
                }
                else {
                    for (int j = 0; j < 4096; j++) {
                    	int v = (dbp != null) ? dbp.getAt(j) : db.get(j);
                        DynmapBlockState bs = (v < palette.length) ? palette[v] : DynmapBlockState.AIR;
                    	sbld.xyzBlockState(j & 0xF, (j & 0xF00) >> 8, (j & 0xF0) >> 4, bs);
                    }
                }
            }
            else if (sec.contains("block_states", GenericNBTCompound.TAG_COMPOUND)) {	// 1.18
            	GenericNBTCompound block_states = sec.getCompound("block_states");
            	// If we've got palette, process non-empty section
            	if (block_states.contains("palette", GenericNBTCompound.TAG_LIST)) {
            		long[] statelist = block_states.contains("data", GenericNBTCompound.TAG_LONG_ARRAY) ? block_states.getLongArray("data") : new long[4096 / 64]; // Handle zero bit palette (all same)
            		GenericNBTList plist = block_states.getList("palette", GenericNBTCompound.TAG_COMPOUND);
            		palette = new DynmapBlockState[plist.size()];
            		for (int pi = 0; pi < plist.size(); pi++) {
            			GenericNBTCompound tc = plist.getCompound(pi);
            			String pname = tc.getString("Name");
            			if (tc.contains("Properties")) {
            				StringBuilder statestr = new StringBuilder();
            				GenericNBTCompound prop = tc.getCompound("Properties");
            				for (String pid : prop.getAllKeys()) {
            					if (statestr.length() > 0) statestr.append(',');
            					statestr.append(pid).append('=').append(prop.getAsString(pid));
            				}
            				palette[pi] = DynmapBlockState.getStateByNameAndState(pname, statestr.toString());
            			}
            			if (palette[pi] == null) {
            				palette[pi] = DynmapBlockState.getBaseStateByName(pname);
            			}
            			if (palette[pi] == null) {
            				palette[pi] = DynmapBlockState.AIR;
            			}
            		}
        			GenericBitStorage db = null;
        			DataBitsPacked dbp = null;

        			int bitsperblock = (statelist.length * 64) / 4096;
        			int expectedStatelistLength = (4096 + (64 / bitsperblock) - 1) / (64 / bitsperblock);
        			if (statelist.length == expectedStatelistLength) {
        				db = nbt.makeBitStorage(bitsperblock, 4096, statelist);
        			}
        			else {
		            	bitsperblock = (statelist.length * 64) / 4096;
	            		dbp = new DataBitsPacked(bitsperblock, 4096, statelist);
        			}
        			if (bitsperblock > 8) {    // Not palette
        				for (int j = 0; j < 4096; j++) {
        					int v = db != null ? db.get(j) : dbp.getAt(j);
                        	sbld.xyzBlockState(j & 0xF, (j & 0xF00) >> 8, (j & 0xF0) >> 4, DynmapBlockState.getStateByGlobalIndex(v));
        				}
        			}
        			else {
        				for (int j = 0; j < 4096; j++) {
        					int v = db != null ? db.get(j) : dbp.getAt(j);
        					DynmapBlockState bs = (v < palette.length) ? palette[v] : DynmapBlockState.AIR;
                        	sbld.xyzBlockState(j & 0xF, (j & 0xF00) >> 8, (j & 0xF0) >> 4, bs);
        				}
        			}
            	}
            }
            if (sec.contains("BlockLight")) {
            	sbld.emittedLight(sec.getByteArray("BlockLight"));
            }
            if (sec.contains("SkyLight")) {
            	sbld.skyLight(sec.getByteArray("SkyLight"));
            }
			// If section biome palette
			if (sec.contains("biomes")) {
                GenericNBTCompound nbtbiomes = sec.getCompound("biomes");
                long[] bdataPacked = nbtbiomes.getLongArray("data");
                GenericNBTList bpalette = nbtbiomes.getList("palette", 8);
                GenericBitStorage bdata = null;
                if (bdataPacked.length > 0)
                    bdata = nbt.makeBitStorage(bdataPacked.length, 64, bdataPacked);
                for (int j = 0; j < 64; j++) {
                    int b = bdata != null ? bdata.get(j) : 0;
                    sbld.xyzBiome(j & 0x3, (j & 0x30) >> 4, (j & 0xC) >> 2, BiomeMap.byBiomeResourceLocation(bpalette.getString(b)));
                }
            }
			else {	// Else, apply legacy biomes
				if (old3d != null) {
					BiomeMap m[] = old3d.get((secnum > 0) ? ((secnum < old3d.size()) ? secnum : old3d.size()-1) : 0);
					if (m != null) {
		                for (int j = 0; j < 64; j++) {
		                    sbld.xyzBiome(j & 0x3, (j & 0x30) >> 4, (j & 0xC) >> 2, m[j]);
		                }
					}
				}
				else if (old2d != null) {
	                for (int j = 0; j < 256; j++) {
	                    sbld.xzBiome(j & 0xF, (j & 0xF0) >> 4, old2d[j]);
	                }					
				}
			}
			// Finish and add section
			bld.addSection(secnum, sbld.build());
			sbld.reset();
        }
        // If pre 1.17, assume unlit state means bad light
		if ((version < 2724) && (!hasLitState)) {
			hasLight = false;
		}
        // If no light, do simple generate
        if (!hasLight) {
        	//Log.info(String.format("generateSky(%d,%d)", x, z));
        	bld.generateSky();
        }
		return bld.build();
	}

}
