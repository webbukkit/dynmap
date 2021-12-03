package org.dynmap.forge_1_18;

import java.util.Arrays;
import java.util.LinkedList;

import org.dynmap.common.BiomeMap;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.DataBitsPacked;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;


/**
 * Represents a static, thread-safe snapshot of chunk of blocks
 * Purpose is to allow clean, efficient copy of a chunk data to be made, and then handed off for processing in another thread (e.g. map rendering)
 */
public class ChunkSnapshot
{
    private static interface Section {
        public DynmapBlockState getBlockType(int x, int y, int z);
        public int getBlockSkyLight(int x, int y, int z);
        public int getBlockEmittedLight(int x, int y, int z);
        public boolean isEmpty();
        public int getBiome(int x, int y, int z);
    }

    private final int x, z;
    private final Section[] section;	// Section, indexed by (Y/16) + sectionOffset (to handle negatives)
    private final int sectionOffset;	// Offset - section[N] = section for Y = N-sectionOffset
    private final int[] hmap; // Height map
    private final int[] biome;
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
        int[] biomes;
        byte[] skylight;
        byte[] emitlight;

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
            return BiomeMap.PLAINS.getBiomeID();
        }
    }
    /**
     * Construct empty chunk snapshot
     *
     * @param x
     * @param z
     */
    public ChunkSnapshot(int worldheight, int x, int z, long captime, long inhabitedTime)
    {
        this.x = x;
        this.z = z;
        this.captureFulltime = captime;
        this.biome = new int[COLUMNS_PER_CHUNK];
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

    public ChunkSnapshot(CompoundTag nbt, int worldheight) {
        this.x = nbt.getInt("xPos");
        this.z = nbt.getInt("zPos");
        this.captureFulltime = 0;
        this.hmap = nbt.getIntArray("HeightMap");
        this.sectionCnt = worldheight / 16;
        if (nbt.contains("InhabitedTime")) {
            this.inhabitedTicks = nbt.getLong("InhabitedTime");
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
        ListTag sect = nbt.contains("sections") ? nbt.getList("sections", 10) : nbt.getList("Sections", 10);
        for (int i = 0; i < sect.size(); i++) {
            CompoundTag sec = sect.getCompound(i);
            int secnum = sec.getByte("Y");
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
            //System.out.println("section(" + secnum + ")=" + sec.asString());
            // Create normal section to initialize
            StdSection cursect = new StdSection();
            sections.set(secnum + sectoff, cursect);
            DynmapBlockState[] states = cursect.states;
            DynmapBlockState[] palette = null;
            // If we've got palette and block states list, process non-empty section
            if (sec.contains("Palette", 9) && sec.contains("BlockStates", 12)) {
                ListTag plist = sec.getList("Palette", 10);
                long[] statelist = sec.getLongArray("BlockStates");
                palette = new DynmapBlockState[plist.size()];
                for (int pi = 0; pi < plist.size(); pi++) {
                    CompoundTag tc = plist.getCompound(pi);
                    String pname = tc.getString("Name");
                    if (tc.contains("Properties")) {
                        StringBuilder statestr = new StringBuilder();
                        CompoundTag prop = tc.getCompound("Properties");
                        for (String pid : prop.getAllKeys()) {
                            if (statestr.length() > 0) statestr.append(',');
                            statestr.append(pid).append('=').append(prop.get(pid).getAsString());
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
            	BitStorage db = null;
            	DataBitsPacked dbp = null;
            	try {
            		db = new SimpleBitStorage(bitsperblock, 4096, statelist);
            	} catch (Exception x) {	// Handle legacy encoded
	            	bitsperblock = (statelist.length * 64) / 4096;
            		dbp = new DataBitsPacked(bitsperblock, 4096, statelist);
            	}
                if (bitsperblock > 8) {	// Not palette
                    for (int j = 0; j < 4096; j++) {
                    	int v = (dbp != null) ? dbp.getAt(j) : db.get(j);
                        states[j] = DynmapBlockState.getStateByGlobalIndex(v);
                    }
                }
                else {
                    for (int j = 0; j < 4096; j++) {
                    	int v = (dbp != null) ? dbp.getAt(j) : db.get(j);
                        states[j] = (v < palette.length) ? palette[v] : DynmapBlockState.AIR;
                    }
                }
            }
            else if (sec.contains("block_states")) {	// 1.18
            	CompoundTag block_states = sec.getCompound("block_states");
            	// If we've block state data, process non-empty section
            	if (block_states.contains("data", 12)) {
            		long[] statelist = block_states.getLongArray("data");
            		ListTag plist = block_states.getList("palette", 10);
            		palette = new DynmapBlockState[plist.size()];
            		for (int pi = 0; pi < plist.size(); pi++) {
            			CompoundTag tc = plist.getCompound(pi);
            			String pname = tc.getString("Name");
            			if (tc.contains("Properties")) {
            				StringBuilder statestr = new StringBuilder();
            				CompoundTag prop = tc.getCompound("Properties");
            				for (String pid : prop.getAllKeys()) {
            					if (statestr.length() > 0) statestr.append(',');
            					statestr.append(pid).append('=').append(prop.get(pid).getAsString());
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
        					int v = db != null ? db.get(j) : dbp.getAt(j);
        					states[j] = DynmapBlockState.getStateByGlobalIndex(v);
        				}
        			}
        			else {
        				for (int j = 0; j < 4096; j++) {
        					int v = db != null ? db.get(j) : dbp.getAt(j);
        					states[j] = (v < palette.length) ? palette[v] : DynmapBlockState.AIR;
        				}
        			}
            	}
            }
            
            if (sec.contains("BlockLight")) {
                cursect.emitlight = sec.getByteArray("BlockLight");
            }
            if (sec.contains("SkyLight")) {
                cursect.skylight = sec.getByteArray("SkyLight");
            }
			// If section biome palette
			if (sec.contains("biomes")) {
                CompoundTag nbtbiomes = sec.getCompound("biomes");
                long[] bdataPacked = nbtbiomes.getLongArray("data");
                ListTag bpalette = nbtbiomes.getList("palette", 8);
                SimpleBitStorage bdata = null;
                if (bdataPacked.length > 0)
                    bdata = new SimpleBitStorage(bdataPacked.length, 64, bdataPacked);
                for (int j = 0; j < 64; j++) {
                    int b = bdata != null ? bdata.get(j) : 0;
                    cursect.biomes[j] = b < bpalette.size() ? BiomeMap.byBiomeResourceLocation(bpalette.getString(b)).getBiomeID() : -1;
                }
                // Favor the Y=64 version
                if ((secnum == 4) || (lastsectwithbiome == null)) {
                	lastsectwithbiome = cursect;
                }
            }
        }
        /* Get biome data */
        this.biome = new int[COLUMNS_PER_CHUNK];
        if (nbt.contains("Biomes")) {
            int[] bb = nbt.getIntArray("Biomes");
        	if (bb != null) {
        		// If v1.15+ format
        		if (bb.length > COLUMNS_PER_CHUNK) {
        	        // For now, just pad the grid with the first 16
                    for (int i = 0; i < COLUMNS_PER_CHUNK; i++) {
                        int off = ((i >> 4) & 0xC) + ((i >> 2) & 0x3);
                        int bv = bb[off + 64];   // Offset to y=64
                        if (bv < 0) bv = 0;
                        this.biome[i] = bv;
                    }
        	    }
        	    else { // Else, older chunks
        	        for (int i = 0; i < bb.length; i++) {
        	            int bv = bb[i];
        	            if (bv < 0) bv = 0;
        	            this.biome[i] = bv;
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

    public int getBiome(int x, int z)
    {
        return biome[z << 4 | x];
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
}
