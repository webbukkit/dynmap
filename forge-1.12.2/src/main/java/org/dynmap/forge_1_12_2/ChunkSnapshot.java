package org.dynmap.forge_1_12_2;

import java.util.Arrays;

import org.dynmap.Log;
import org.dynmap.renderer.DynmapBlockState;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

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
    }

    private final int x, z;
    private final Section[] section;
    private final int[] hmap; // Height map
    private final int[] biome;
    private final long captureFulltime;
    private final int sectionCnt;
    private final long inhabitedTicks;

    private static final int BLOCKS_PER_SECTION = 16 * 16 * 16;
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
    }
    
    private static final EmptySection empty_section = new EmptySection();
    
    private static class StdSection implements Section {
        DynmapBlockState[] states;
        byte[] skylight;
        byte[] emitlight;

        public StdSection() {
            states = new DynmapBlockState[BLOCKS_PER_SECTION];
            Arrays.fill(states,  DynmapBlockState.AIR);
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

        /* Fill with empty data */
        for (int i = 0; i <= this.sectionCnt; i++) {
            this.section[i] = empty_section;
        }

        /* Create empty height map */
        this.hmap = new int[16 * 16];
        
        this.inhabitedTicks = inhabitedTime;
    }

    public ChunkSnapshot(NBTTagCompound nbt, int worldheight) {
        this.x = nbt.getInteger("xPos");
        this.z = nbt.getInteger("zPos");
        this.captureFulltime = 0;
        this.hmap = nbt.getIntArray("HeightMap");
        this.sectionCnt = worldheight / 16;
        if (nbt.hasKey("InhabitedTime")) {
            this.inhabitedTicks = nbt.getLong("InhabitedTime");
        }
        else {
            this.inhabitedTicks = 0;
        }
        /* Allocate arrays indexed by section */
        this.section = new Section[this.sectionCnt+1];
        /* Fill with empty data */
        for (int i = 0; i <= this.sectionCnt; i++) {
            this.section[i] = empty_section;
        }
        /* Get sections */
        NBTTagList sect = nbt.getTagList("Sections", 10);
        for (int i = 0; i < sect.tagCount(); i++) {
            NBTTagCompound sec = sect.getCompoundTagAt(i);
            byte secnum = sec.getByte("Y");
            if (secnum >= this.sectionCnt) {
                Log.info("Section " + (int) secnum + " above world height " + worldheight);
                continue;
            }
            // Create normal section to initialize
            StdSection cursect = new StdSection();
            this.section[secnum] = cursect;
            DynmapBlockState[] states = cursect.states;
            // JEI format
            if (sec.hasKey("Palette", 11)) {
            	int[] p = sec.getIntArray("Palette");
                // Palette is list of state values, where Blocks=bit 11-4 of index, Data=bit 3-0
            	byte[] msb_bytes = sec.getByteArray("Blocks");
            	int mlen = msb_bytes.length / 2;
            	byte[] lsb_bytes = sec.getByteArray("Data");
            	int llen = BLOCKS_PER_SECTION / 2;
            	if (llen > lsb_bytes.length) llen = lsb_bytes.length;
                for(int j = 0; j < llen; j++) {
                	int idx = lsb_bytes[j] & 0xF;
                	int idx2 = (lsb_bytes[j] & 0xF0) >>> 4;
        			if (j < mlen) {
        				idx += (255 & msb_bytes[2*j]) << 4;
        				idx2 += (255 & msb_bytes[2*j+1]) << 4;
        			}
        			// Get even block id
        			states[2*j] = DynmapPlugin.stateByID[(idx < p.length) ? p[idx] : 0];
        			// Get odd block id
        			states[2*j+1] = DynmapPlugin.stateByID[(idx2 < p.length) ? p[idx2] : 0];
                }
            }
            else {
                // Get block IDs
            	byte[] lsb_bytes = sec.getByteArray("Blocks");
            	if (lsb_bytes.length < BLOCKS_PER_SECTION) {
            	    lsb_bytes = Arrays.copyOf(lsb_bytes, BLOCKS_PER_SECTION);
            	}
            	// Get any additional ID data
            	byte[] addid = null;
                if (sec.hasKey("Add", 7)) {    /* If additional data, add it */
                    addid = sec.getByteArray("Add");
                    if (addid.length < (BLOCKS_PER_SECTION / 2)) {
                        addid = Arrays.copyOf(addid, (BLOCKS_PER_SECTION / 2));
                    }
                }
                // Check for NEID additional additional ID data
                byte[] addid2 = null;
                if (sec.hasKey("Add2", 7)) {    /* If additional data (NEID), add it */
                    addid2 = sec.getByteArray("Add2");
                    if (addid2.length < (BLOCKS_PER_SECTION / 2)) {
                        addid2 = Arrays.copyOf(addid2, (BLOCKS_PER_SECTION / 2));
                    }
                }
                // Get meta nibble data
                byte[] bd = null;
                if (sec.hasKey("Data", 7)) {
                    bd = sec.getByteArray("Data");
                    if (bd.length < (BLOCKS_PER_SECTION / 2)) {
                        bd = Arrays.copyOf(bd, (BLOCKS_PER_SECTION / 2));
                    }
                }
                // Traverse section
            	for(int j = 0; j < BLOCKS_PER_SECTION; j += 2) {
            	    // Start with block ID
            	    int id = (0xFF & lsb_bytes[j]) << 4;
                    int id2 = (0xFF & lsb_bytes[j+1]) << 4;
            	    // Add in additional parts
                    if (addid != null) {
                        byte b = addid[j >> 1];
                        id += (0xF & b) << 12;
                        id2 += (0xF0 & b) << 8;
                    }
                    // Add in additional additional parts
                    if (addid2 != null) {
                        byte b = addid2[j >> 1];
                        id += (0xF & b) << 16;
                        id2 += (0xF0 & b) << 12;
                    }
                    // Add in metadata
                    if (bd != null) {
                        byte b = bd[j >> 1];
                        id += (0xF & b);
                        id2 += (0xF0 & b) >> 4;
                    }
                    // Compute states
                    states[j] = DynmapPlugin.stateByID[id];
                    states[j+1] = DynmapPlugin.stateByID[id2];
            	}
            }
            cursect.emitlight = sec.getByteArray("BlockLight");
            if (sec.hasKey("SkyLight")) {
                cursect.skylight = sec.getByteArray("SkyLight");
            }
        }
        /* Get biome data */
        this.biome = new int[COLUMNS_PER_CHUNK];
        if (nbt.hasKey("Biomes")) {
            byte[] b = nbt.getByteArray("Biomes");
            if (b != null) {
            	for (int i = 0; i < b.length; i++) {
            		int bv = 255 & b[i];
            		this.biome[i] = (bv == 255) ? 0 : bv;
            	}
            }
            else {	// Check JEI biomes
            	int[] bb = nbt.getIntArray("Biomes");
            	if (bb != null) {
                	for (int i = 0; i < bb.length; i++) {
                		int bv = bb[i];
                		this.biome[i] = (bv < 0) ? 0 : bv;
                	}
            	}
            }
        }
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
        return section[y >> 4].getBlockType(x, y, z);
    }

    public int getBlockSkyLight(int x, int y, int z)
    {
        return section[y >> 4].getBlockSkyLight(x, y, z);
    }

    public int getBlockEmittedLight(int x, int y, int z)
    {
        return section[y >> 4].getBlockEmittedLight(x, y, z);
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
        return section[sy].isEmpty();
    }
    
    public long getInhabitedTicks() {
        return inhabitedTicks;
    }
}
