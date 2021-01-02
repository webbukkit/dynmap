package org.dynmap.forge_1_13_2;

import java.util.Arrays;

import org.dynmap.Log;
import org.dynmap.renderer.DynmapBlockState;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.BitArray;

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
        this.section = new Section[this.sectionCnt+1];
        /* Fill with empty data */
        for (int i = 0; i <= this.sectionCnt; i++) {
            this.section[i] = empty_section;
        }
        /* Get sections */
        NBTTagList sect = nbt.getList("Sections", 10);
        for (int i = 0; i < sect.size(); i++) {
            NBTTagCompound sec = sect.getCompound(i);
            int secnum = sec.getByte("Y");
            if (secnum >= this.sectionCnt) {
                //Log.info("Section " + (int) secnum + " above world height " + worldheight);
                continue;
            }
            if (secnum < 0)
                continue;
            //System.out.println("section(" + secnum + ")=" + sec.asString());
            // Create normal section to initialize
            StdSection cursect = new StdSection();
            this.section[secnum] = cursect;
            DynmapBlockState[] states = cursect.states;
            DynmapBlockState[] palette = null;
            // If we've got palette and block states list, process non-empty section
            if (sec.contains("Palette", 9) && sec.contains("BlockStates", 12)) {
                NBTTagList plist = sec.getList("Palette", 10);
                long[] statelist = sec.getLongArray("BlockStates");
                palette = new DynmapBlockState[plist.size()];
                for (int pi = 0; pi < plist.size(); pi++) {
                    NBTTagCompound tc = plist.getCompound(pi);
                    String pname = tc.getString("Name");
                    if (tc.contains("Properties")) {
                        StringBuilder statestr = new StringBuilder();
                        NBTTagCompound prop = tc.getCompound("Properties");
                        for (String pid : prop.keySet()) {
                            if (statestr.length() > 0) statestr.append(',');
                            statestr.append(pid).append('=').append(prop.get(pid).getString());
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
                int bitsperblock = (statelist.length * 64) / 4096;
                BitArray db = new BitArray(bitsperblock, 4096, statelist);
                if (bitsperblock > 8) {	// Not palette
                    for (int j = 0; j < 4096; j++) {
                        states[j] = DynmapBlockState.getStateByGlobalIndex(db.getAt(j));
                    }
                }
                else {
                    for (int j = 0; j < 4096; j++) {
                        int v = db.getAt(j);
                        states[j] = (v < palette.length) ? palette[v] : DynmapBlockState.AIR;
                    }
                }
            }
            if (sec.contains("BlockLight")) {
                cursect.emitlight = sec.getByteArray("BlockLight");
            }
            if (sec.contains("SkyLight")) {
                cursect.skylight = sec.getByteArray("SkyLight");
            }
        }
        /* Get biome data */
        this.biome = new int[COLUMNS_PER_CHUNK];
        if (nbt.contains("Biomes")) {
            int[] bb = nbt.getIntArray("Biomes");
            if (bb != null) {
                for (int i = 0; i < bb.length; i++) {
                    int bv = bb[i];
                    this.biome[i] = (bv < 0) ? 0 : bv;
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
