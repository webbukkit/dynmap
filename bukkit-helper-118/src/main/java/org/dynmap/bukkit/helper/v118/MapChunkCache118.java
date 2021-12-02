package org.dynmap.bukkit.helper.v118;

import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.v1_18_R1.CraftWorld;
import org.dynmap.DynmapChunk;
import org.dynmap.DynmapCore;
import org.dynmap.bukkit.helper.AbstractMapChunkCache;
import org.dynmap.bukkit.helper.BukkitVersionHelper;
import org.dynmap.bukkit.helper.SnapshotCache;
import org.dynmap.bukkit.helper.SnapshotCache.SnapshotRec;
import org.dynmap.common.BiomeMap;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.DataBitsPacked;
import org.dynmap.utils.DynIntHashMap;
import org.dynmap.utils.VisibilityLimit;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.DataBits;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkRegionLoader;
import net.minecraft.world.level.chunk.Chunk;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;

/**
 * Container for managing chunks - dependent upon using chunk snapshots, since rendering is off server thread
 */
public class MapChunkCache118 extends AbstractMapChunkCache {

	public static class NBTSnapshot implements Snapshot {
	    private static interface Section {
	        public DynmapBlockState getBlockType(int x, int y, int z);
	        public int getBlockSkyLight(int x, int y, int z);
	        public int getBlockEmittedLight(int x, int y, int z);
	        public boolean isEmpty();
	        public int getBiome(int x, int y, int z);
	    }
	    private final int x, z;
	    private final Section[] section;
	    private final int sectionOffset;
	    private final int[] hmap; // Height map
	    private final int[] biome;
	    private final Object[] biomebase;
	    private final long captureFulltime;
	    private final int sectionCnt;
	    private final long inhabitedTicks;

	    private static final int BLOCKS_PER_SECTION = 16 * 16 * 16;
	    private static final int BIOMES_PER_SECTION = 4 * 4 * 4;
	    private static final int COLUMNS_PER_CHUNK = 16 * 16;
	    private static final byte[] emptyData = new byte[BLOCKS_PER_SECTION / 2];
	    private static final byte[] fullData = new byte[BLOCKS_PER_SECTION / 2];

	    static
	    {
	        Arrays.fill(fullData, (byte)0xFF);
	    }
	    
	    private static byte[] dataCopy(byte[] v) {
	    	if (Arrays.equals(v, emptyData))
	    		return emptyData;
	    	else if (Arrays.equals(v, fullData))
	    		return fullData;
	    	else
	    		return v.clone();
	    }

	    private static class EmptySection implements Section {
	        @Override
	        public DynmapBlockState getBlockType(int x, int y, int z) {
	            return DynmapBlockState.AIR;
	        }
	        @Override
	        public int getBlockSkyLight(int x, int y, int z) {
	            return 15;
	        }
	        @Override
	        public int getBlockEmittedLight(int x, int y, int z) {
	            return 0;
	        }
	        @Override
	        public boolean isEmpty() {
	            return true;
	        }
	        @Override
	        public int getBiome(int x, int y, int z) {
	        	return BiomeMap.PLAINS.getBiomeID();
	        }
	    }
	    
	    private static final EmptySection empty_section = new EmptySection();
	    
	    private static class StdSection implements Section {
	        DynmapBlockState[] states;
	        byte[] skylight;
	        byte[] emitlight;
	        int[] biomes;
	        
	        public StdSection() {
	            states = new DynmapBlockState[BLOCKS_PER_SECTION];
	            Arrays.fill(states,  DynmapBlockState.AIR);
	            biomes = new int[BIOMES_PER_SECTION];
	            skylight = emptyData;
	            emitlight = emptyData;
	        }
	        @Override
	        public DynmapBlockState getBlockType(int x, int y, int z) {
	            return states[((y & 0xF) << 8) | (z << 4) | x];
	        }
	        @Override
	        public int getBlockSkyLight(int x, int y, int z) {
	            int off = ((y & 0xF) << 7) | (z << 3) | (x >> 1);
	            return (skylight[off] >> (4 * (x & 1))) & 0xF;
	        }
	        @Override
	        public int getBlockEmittedLight(int x, int y, int z)
	        {
	            int off = ((y & 0xF) << 7) | (z << 3) | (x >> 1);
	            return (emitlight[off] >> (4 * (x & 1))) & 0xF;
	        }
	        @Override
	        public boolean isEmpty() {
	            return false;
	        }
	        @Override
	        public int getBiome(int x, int y, int z) {
	            int off = (((y & 0xF) >> 2) << 4) | ((z >> 2) << 2) | (x >> 2);
	            return biomes[off];
	        }	        
	    }
	    /**
	     * Construct empty chunk snapshot
	     *
	     * @param x
	     * @param z
	     */
	    public NBTSnapshot(int worldheight, int x, int z, long captime, long inhabitedTime)
	    {
	        this.x = x;
	        this.z = z;
	        this.captureFulltime = captime;
	        this.biome = new int[COLUMNS_PER_CHUNK];
	        this.biomebase = new Object[COLUMNS_PER_CHUNK];
	        this.sectionCnt = worldheight / 16;
	        /* Allocate arrays indexed by section */
	        this.section = new Section[this.sectionCnt+1];
	        this.sectionOffset = 0;
	        /* Fill with empty data */
	        for (int i = 0; i <= this.sectionCnt; i++) {
	            this.section[i] = empty_section;
	        }

	        /* Create empty height map */
	        this.hmap = new int[16 * 16];
	        
	        this.inhabitedTicks = inhabitedTime;
	    }

	    public NBTSnapshot(NBTTagCompound nbt, int worldheight) {
	        this.x = nbt.h("xPos");
	        this.z = nbt.h("zPos");
	        this.captureFulltime = 0;
	        this.hmap = nbt.n("HeightMap");
	        this.sectionCnt = worldheight / 16;
	        if (nbt.e("InhabitedTime")) {
	            this.inhabitedTicks = nbt.i("InhabitedTime");
	        }
	        else {
	            this.inhabitedTicks = 0;
	        }
	        /* Allocate arrays indexed by section */
	        LinkedList<Section> sections = new LinkedList<Section>();
	        int sectoff = 0;	// Default to zero
	        int sectcnt = 0;
	        /* Fill with empty data */
	        for (int i = 0; i <= this.sectionCnt; i++) {
	            sections.add(empty_section);
	            sectcnt++;
	        }
            //System.out.println("nbt.keys()=" + nbt.d().toString());
	        StdSection lastsectwithbiome = null;
	        /* Get sections */
	        
	        NBTTagList sect = nbt.e("sections") ? nbt.c("sections", 10) : nbt.c("Sections", 10);
	        for (int i = 0; i < sect.size(); i++) {
	            NBTTagCompound sec = sect.a(i);
	            int secnum = sec.h("Y");
	            // Beyond end - extend up
	            while (secnum >= (sectcnt - sectoff)) {
	        		sections.addLast(empty_section);	// Pad with empty
	        		sectcnt++;
	            }
	            // Negative - see if we need to extend sectionOffset
	        	while ((secnum + sectoff) < 0) {
	        		sections.addFirst(empty_section);	// Pad with empty
	        		sectoff++;
	        		sectcnt++;
	        	}
	            //System.out.println("section(" + secnum + ")=" + sec.toString());
	            // Create normal section to initialize
	            StdSection cursect = new StdSection();
	            sections.set(secnum + sectoff, cursect);
	            DynmapBlockState[] states = cursect.states;
	            DynmapBlockState[] palette = null;
	            // If we've got palette and block states list, process non-empty section
	            if (sec.b("Palette", 9) && sec.b("BlockStates", 12)) {
	            	NBTTagList plist = sec.c("Palette", 10);
	            	long[] statelist = sec.o("BlockStates");
	            	palette = new DynmapBlockState[plist.size()];
	            	for (int pi = 0; pi < plist.size(); pi++) {
	            		NBTTagCompound tc = plist.a(pi);
	            		String pname = tc.l("Name");
                        if (tc.e("Properties")) {
                            StringBuilder statestr = new StringBuilder();
                            NBTTagCompound prop = tc.p("Properties");
                            for (String pid : prop.d()) {
                                if (statestr.length() > 0) statestr.append(',');
                                statestr.append(pid).append('=').append(prop.c(pid).e_());
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
	            	DataBits db = null;
	            	DataBitsPacked dbp = null;
	            	try {
	            		db = new SimpleBitStorage(bitsperblock, 4096, statelist);
	            	} catch (Exception x) {	// Handle legacy encoded
		            	bitsperblock = (statelist.length * 64) / 4096;
	            		dbp = new DataBitsPacked(bitsperblock, 4096, statelist);
	            	}
	                if (bitsperblock > 8) {	// Not palette
	                    for (int j = 0; j < 4096; j++) {
	                    	int v = (dbp != null) ? dbp.getAt(j) : db.a(j);
	                        states[j] = DynmapBlockState.getStateByGlobalIndex(v);
	                    }
	                }
	                else {
	                    for (int j = 0; j < 4096; j++) {
	                    	int v = (dbp != null) ? dbp.getAt(j) : db.a(j);
	                        states[j] = (v < palette.length) ? palette[v] : DynmapBlockState.AIR;
	                    }
	                }
				}
	            else if (sec.e("block_states")) {	// 1.18
	            	NBTTagCompound block_states = sec.p("block_states");
	            	// If we've block state data, process non-empty section
	            	if (block_states.b("data", 12)) {
	            		long[] statelist = block_states.o("data");
	            		NBTTagList plist = block_states.c("palette", 10);
	            		palette = new DynmapBlockState[plist.size()];
	            		for (int pi = 0; pi < plist.size(); pi++) {
	            			NBTTagCompound tc = plist.a(pi);
	            			String pname = tc.l("Name");
	            			if (tc.e("Properties")) {
	            				StringBuilder statestr = new StringBuilder();
	            				NBTTagCompound prop = tc.p("Properties");
	            				for (String pid : prop.d()) {
	            					if (statestr.length() > 0) statestr.append(',');
	            					statestr.append(pid).append('=').append(prop.c(pid).e_());
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
            			SimpleBitStorage db = null;
            			DataBitsPacked dbp = null;

            			int bitsperblock = (statelist.length * 64) / 4096;
            			int expectedStatelistLength = (4096 + (64 / bitsperblock) - 1) / (64 / bitsperblock);
            			if (statelist.length == expectedStatelistLength) {
            				db = new SimpleBitStorage(bitsperblock, 4096, statelist);
            			}
            			else {
    		            	bitsperblock = (statelist.length * 64) / 4096;
    	            		dbp = new DataBitsPacked(bitsperblock, 4096, statelist);
            			}
            			if (bitsperblock > 8) {    // Not palette
            				for (int j = 0; j < 4096; j++) {
            					int v = db != null ? db.a(j) : dbp.getAt(j);
            					states[j] = DynmapBlockState.getStateByGlobalIndex(v);
            				}
            			}
            			else {
            				for (int j = 0; j < 4096; j++) {
            					int v = db != null ? db.a(j) : dbp.getAt(j);
            					states[j] = (v < palette.length) ? palette[v] : DynmapBlockState.AIR;
            				}
            			}
	            	}
	            }

	            if (sec.e("BlockLight")) {
					cursect.emitlight = dataCopy(sec.m("BlockLight"));
	            }
				if (sec.e("SkyLight")) {
					cursect.skylight = dataCopy(sec.m("SkyLight"));
				}
				// If section biome palette
				if (sec.e("biomes")) {
	                NBTTagCompound nbtbiomes = sec.p("biomes");
	                long[] bdataPacked = nbtbiomes.o("data");
	                NBTTagList bpalette = nbtbiomes.c("palette", 8);
	                SimpleBitStorage bdata = null;
	                if (bdataPacked.length > 0)
	                    bdata = new SimpleBitStorage(bdataPacked.length, 64, bdataPacked);
	                for (int j = 0; j < 64; j++) {
	                    int b = bdata != null ? bdata.a(j) : 0;
	                    cursect.biomes[j] = b < bpalette.size() ? BiomeMap.byBiomeName(bpalette.j(b)).getBiomeID() : -1;
	                }
	                // Favor the Y=64 version
	                if ((secnum == 4) || (lastsectwithbiome == null)) {
	                	lastsectwithbiome = cursect;
	                }
	            }
	        }
	        /* Get biome data */
	        this.biome = new int[COLUMNS_PER_CHUNK];
	        this.biomebase = new Object[COLUMNS_PER_CHUNK];
	        Object[] bbl = BukkitVersionHelper.helper.getBiomeBaseList();
	        if (nbt.e("Biomes")) {
            	int[] bb = nbt.n("Biomes");
            	if (bb != null) {
            	    // If v1.15+ format
            	    if (bb.length > COLUMNS_PER_CHUNK) {
            	        // For now, just pad the grid with the first 16
                        for (int i = 0; i < COLUMNS_PER_CHUNK; i++) {
                            int off = ((i >> 4) & 0xC) + ((i >> 2) & 0x3);
                            int bv = bb[off + 64];   // Offset to y=64
                            if (bv < 0) bv = 0;
                            this.biome[i] = bv;
                            this.biomebase[i] = bbl[bv];
                        }
            	    }
            	    else { // Else, older chunks
            	        for (int i = 0; i < bb.length; i++) {
            	            int bv = bb[i];
            	            if (bv < 0) bv = 0;
            	            this.biome[i] = bv;
            	            this.biomebase[i] = bbl[bv];
            	        }
            	    }
	            }
	        }
	        else {	// Make up 2D version for now
	        	if (lastsectwithbiome != null) {
        	        // For now, just pad the grid with the first 16
                    for (int i = 0; i < COLUMNS_PER_CHUNK; i++) {
                        int off = ((i >> 4) & 0xC) + ((i >> 2) & 0x3);
                        int bv = lastsectwithbiome.biomes[off];   // Offset to y=64
                        if (bv < 0) bv = 0;
                        this.biome[i] = bv;
                        this.biomebase[i] = bbl[bv];
                    }
	        	}
	        }
	        // Finalize sections array
	        this.section = sections.toArray(new Section[sections.size()]);
	        this.sectionOffset = sectoff;
	    }
	    
	    public int getX()
	    {
	        return x;
	    }

	    public int getZ()
	    {
	        return z;
	    }
	    
	    public DynmapBlockState getBlockType(int x, int y, int z)
	    {    	
	    	int idx = (y >> 4) + sectionOffset;
	    	if ((idx < 0) || (idx >= section.length)) return DynmapBlockState.AIR;
	        return section[idx].getBlockType(x, y, z);
	    }

	    public int getBlockSkyLight(int x, int y, int z)
	    {
	    	int idx = (y >> 4) + sectionOffset;
	    	if ((idx < 0) || (idx >= section.length)) return 15;
	        return section[idx].getBlockSkyLight(x, y, z);
	    }

	    public int getBlockEmittedLight(int x, int y, int z)
	    {
	    	int idx = (y >> 4) + sectionOffset;
	    	if ((idx < 0) || (idx >= section.length)) return 0;
	        return section[idx].getBlockEmittedLight(x, y, z);
	    }

	    public int getHighestBlockYAt(int x, int z)
	    {
	        return hmap[z << 4 | x];
	    }

	    public final long getCaptureFullTime()
	    {
	        return captureFulltime;
	    }

	    public boolean isSectionEmpty(int sy)
	    {
	    	int idx = sy + sectionOffset;
	    	if ((idx < 0) || (idx >= section.length)) return true;
	        return section[idx].isEmpty();
	    }
	    
	    public long getInhabitedTicks() {
	        return inhabitedTicks;
	    }

		@Override
		public Biome getBiome(int x, int z) {
	        return AbstractMapChunkCache.getBiomeByID(biome[z << 4 | x]);
		}

		@Override
		public Object[] getBiomeBaseFromSnapshot() {
			return this.biomebase;
		}
	}
	
    private NBTTagCompound fetchLoadedChunkNBT(World w, int x, int z) {
        CraftWorld cw = (CraftWorld) w;
        NBTTagCompound nbt = null;
        if (cw.isChunkLoaded(x, z)) {
            Chunk c = cw.getHandle().getChunkIfLoaded(x,  z);
            if ((c != null) && c.o) {	// c.loaded
				nbt = ChunkRegionLoader.a(cw.getHandle(), c);
            }
        }
        if (nbt != null) {
        	if (nbt.e("Level")) {
        		nbt = nbt.p("Level");
        	}
            if (nbt != null) {
                String stat = nbt.l("Status");
				ChunkStatus cs = ChunkStatus.a(stat);
                if ((stat == null) || (!cs.b(ChunkStatus.l))) {	// ChunkStatus.LIGHT
                    nbt = null;
                }
            }
        }
        return nbt;
    }
    
    private NBTTagCompound loadChunkNBT(World w, int x, int z) {
    	//.info("loadChunkNBT(" + w.getName() + "," + x + "," + z);
        CraftWorld cw = (CraftWorld) w;
        NBTTagCompound nbt = null;
        ChunkCoordIntPair cc = new ChunkCoordIntPair(x, z);
        try {
            nbt = cw.getHandle().k().a.f(cc);	// playerChunkMap
        } catch (IOException iox) {
        }
        if (nbt != null) {
        	// See if we have Level - unwrap this if so
        	if (nbt.e("Level")) {
        		nbt = nbt.p("Level");
        	}
            if (nbt != null) {
            	String stat = nbt.l("Status");
            	if ((stat == null) || (stat.equals("full") == false)) {
                    nbt = null;
                    if ((stat == null) || stat.equals("") && DynmapCore.migrateChunks()) {
                        Chunk c = cw.getHandle().getChunkIfLoaded(x, z);
                        if (c != null) {
                            nbt = fetchLoadedChunkNBT(w, x, z);
                            cw.getHandle().a(c);
                        }
                    }
            	}
            }
        }
        return nbt;
    }   
    
    @Override
    public Snapshot wrapChunkSnapshot(ChunkSnapshot css) {
        // TODO Auto-generated method stub
        return null;
    }
    
    // Load chunk snapshots
    @Override
    public int loadChunks(int max_to_load) {
        if(dw.isLoaded() == false)
            return 0;        
        int cnt = 0;
        if(iterator == null)
            iterator = chunks.listIterator();

        DynmapCore.setIgnoreChunkLoads(true);
        // Load the required chunks.
        while((cnt < max_to_load) && iterator.hasNext()) {
            long startTime = System.nanoTime();
            DynmapChunk chunk = iterator.next();
            boolean vis = true;
            if(visible_limits != null) {
                vis = false;
                for(VisibilityLimit limit : visible_limits) {
                    if (limit.doIntersectChunk(chunk.x, chunk.z)) {
                        vis = true;
                        break;
                    }
                }
            }
            if(vis && (hidden_limits != null)) {
                for(VisibilityLimit limit : hidden_limits) {
                    if (limit.doIntersectChunk(chunk.x, chunk.z)) {
                        vis = false;
                        break;
                    }
                }
            }
            /* Check if cached chunk snapshot found */
            Snapshot ss = null;
            long inhabited_ticks = 0;
            DynIntHashMap tileData = null;
            int idx = (chunk.x-x_min) + (chunk.z - z_min)*x_dim;
            SnapshotRec ssr = SnapshotCache.sscache.getSnapshot(dw.getName(), chunk.x, chunk.z, blockdata, biome, biomeraw, highesty); 
            if(ssr != null) {
                inhabited_ticks = ssr.inhabitedTicks;
                if(!vis) {
                    if(hidestyle == HiddenChunkStyle.FILL_STONE_PLAIN)
                        ss = STONE;
                    else if(hidestyle == HiddenChunkStyle.FILL_OCEAN)
                        ss = OCEAN;
                    else
                        ss = EMPTY;
                }
                else {
                    ss = ssr.ss;
                }
                snaparray[idx] = ss;
                snaptile[idx] = ssr.tileData;
                inhabitedTicks[idx] = inhabited_ticks;
                
                endChunkLoad(startTime, ChunkStats.CACHED_SNAPSHOT_HIT);
                continue;
            }
            // Fetch NTB for chunk if loaded
            NBTTagCompound nbt = fetchLoadedChunkNBT(w, chunk.x, chunk.z); 
            boolean did_load = false;
            if (nbt == null) {
                // Load NTB for chunk, if it exists
                nbt = loadChunkNBT(w, chunk.x, chunk.z);
                did_load = true;
            }
            if (nbt != null) {
                NBTSnapshot nss = new NBTSnapshot(nbt, w.getMaxHeight());
                ss = nss;
                inhabited_ticks = nss.getInhabitedTicks();
                if(!vis) {
                    if(hidestyle == HiddenChunkStyle.FILL_STONE_PLAIN)
                        ss = STONE;
                    else if(hidestyle == HiddenChunkStyle.FILL_OCEAN)
                        ss = OCEAN;
                    else
                        ss = EMPTY;
                }
            }
            else {
                ss = EMPTY;
            }
            ssr = new SnapshotRec();
            ssr.ss = ss;
            ssr.inhabitedTicks = inhabited_ticks;
            ssr.tileData = tileData;
            SnapshotCache.sscache.putSnapshot(dw.getName(), chunk.x, chunk.z, ssr, blockdata, biome, biomeraw, highesty);
            snaparray[idx] = ss;
            snaptile[idx] = ssr.tileData;
            inhabitedTicks[idx] = inhabited_ticks;
            if (nbt == null)
                endChunkLoad(startTime, ChunkStats.UNGENERATED_CHUNKS);
            else if (did_load)
                endChunkLoad(startTime, ChunkStats.UNLOADED_CHUNKS);
            else
                endChunkLoad(startTime, ChunkStats.LOADED_CHUNKS);
            cnt++;
        }
        DynmapCore.setIgnoreChunkLoads(false);

        if(iterator.hasNext() == false) {   /* If we're done */
            isempty = true;
            /* Fill missing chunks with empty dummy chunk */
            for(int i = 0; i < snaparray.length; i++) {
                if(snaparray[i] == null)
                    snaparray[i] = EMPTY;
                else if(snaparray[i] != EMPTY)
                    isempty = false;
            }
        }

        return cnt;
    }
}
