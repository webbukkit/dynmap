package org.dynmap.forge_1_12_2;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.dynmap.Log;
import org.dynmap.renderer.DynmapBlockState;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.chunk.NibbleArray;
import scala.actors.threadpool.Arrays;

/**
 * Represents a static, thread-safe snapshot of chunk of blocks
 * Purpose is to allow clean, efficient copy of a chunk data to be made, and then handed off for processing in another thread (e.g. map rendering)
 */
public class ChunkSnapshot
{
    private final int x, z;
    private final short[][] blockids; /* Block IDs, by section */
    private final byte[][] blockdata;
    private final byte[][] skylight;
    private final byte[][] emitlight;
    private final boolean[] empty;
    private final int[] hmap; // Height map
    private final byte[] biome;
    private final long captureFulltime;
    private final int sectionCnt;
    private final long inhabitedTicks;

    private static final int BLOCKS_PER_SECTION = 16 * 16 * 16;
    private static final int COLUMNS_PER_CHUNK = 16 * 16;
    private static final short[] emptyIDs = new short[BLOCKS_PER_SECTION];
    private static final byte[] emptyData = new byte[BLOCKS_PER_SECTION / 2];
    private static final byte[] fullData = new byte[BLOCKS_PER_SECTION / 2];
    private static Method getvalarray = null;

    static
    {
        for (int i = 0; i < fullData.length; i++)
        {
            fullData[i] = (byte)0xFF;
        }
        try {
            Method[] m = NibbleArray.class.getDeclaredMethods();
            for (Method mm : m) {
                if (mm.getName().equals("getValueArray")) {
                    getvalarray = mm;
                    break;
                }
            }
        } catch (Exception x) {
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
        this.biome = new byte[COLUMNS_PER_CHUNK];
        this.sectionCnt = worldheight / 16;
        /* Allocate arrays indexed by section */
        this.blockids = new short[this.sectionCnt][];
        this.blockdata = new byte[this.sectionCnt][];
        this.skylight = new byte[this.sectionCnt][];
        this.emitlight = new byte[this.sectionCnt][];
        this.empty = new boolean[this.sectionCnt];

        /* Fill with empty data */
        for (int i = 0; i < this.sectionCnt; i++)
        {
            this.empty[i] = true;
            this.blockids[i] = emptyIDs;
            this.blockdata[i] = emptyData;
            this.emitlight[i] = emptyData;
            this.skylight[i] = fullData;
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
        this.blockids = new short[this.sectionCnt][];
        this.blockdata = new byte[this.sectionCnt][];
        this.skylight = new byte[this.sectionCnt][];
        this.emitlight = new byte[this.sectionCnt][];
        this.empty = new boolean[this.sectionCnt];
        /* Fill with empty data */
        for (int i = 0; i < this.sectionCnt; i++) {
            this.empty[i] = true;
            this.blockids[i] = emptyIDs;
            this.blockdata[i] = emptyData;
            this.emitlight[i] = emptyData;
            this.skylight[i] = fullData;
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
            byte[] lsb_bytes = sec.getByteArray("Blocks");
            short[] blkids = new short[BLOCKS_PER_SECTION];
            this.blockids[secnum] = blkids;
            int len = BLOCKS_PER_SECTION;
            if(len > lsb_bytes.length) len = lsb_bytes.length;
            for(int j = 0; j < len; j++) {
                blkids[j] = (short)(0xFF & lsb_bytes[j]); 
            }
            if (sec.hasKey("Add")) {    /* If additional data, add it */
                byte[] msb = sec.getByteArray("Add");
                len = BLOCKS_PER_SECTION / 2;
                if(len > msb.length) len = msb.length;
                for (int j = 0; j < len; j++) {
                    short b = (short)(msb[j] & 0xFF);
                    if (b == 0) {
                        continue;
                    }
                    blkids[j << 1] |= (b & 0x0F) << 8;
                    blkids[(j << 1) + 1] |= (b & 0xF0) << 4;
                }
            }
            this.blockdata[secnum] = sec.getByteArray("Data");
            this.emitlight[secnum] = sec.getByteArray("BlockLight");
            if (sec.hasKey("SkyLight")) {
                this.skylight[secnum] = sec.getByteArray("SkyLight");
            }
            this.empty[secnum] = false;
        }
        /* Get biome data */
        if (nbt.hasKey("Biomes")) {
            byte[] b = nbt.getByteArray("Biomes");
            if (b.length < COLUMNS_PER_CHUNK) {
            	b = Arrays.copyOf(b, COLUMNS_PER_CHUNK);
            }
            this.biome = b;
        }
        else {
            this.biome = new byte[COLUMNS_PER_CHUNK];
        }
    }
    
    private static byte[] getValueArray(NibbleArray na) {
        if(getvalarray != null) {
            try {
                return (byte[])getvalarray.invoke(na);
            } catch (IllegalArgumentException e) {
            } catch (IllegalAccessException e) {
            } catch (InvocationTargetException e) {
            }
        }
        return na.getData();
    }

    public int getX()
    {
        return x;
    }

    public int getZ()
    {
        return z;
    }

    public int getBlockTypeId(int x, int y, int z)
    {
        return blockids[y >> 4][((y & 0xF) << 8) | (z << 4) | x];
    }

    public int getBlockData(int x, int y, int z)
    {
        int off = ((y & 0xF) << 7) | (z << 3) | (x >> 1);
        return (blockdata[y >> 4][off] >> ((x & 1) << 2)) & 0xF;
    }
    
    public DynmapBlockState getBlockType(int x, int y, int z)
    {
        int id = blockids[y >> 4][((y & 0xF) << 8) | (z << 4) | x];
        int off = ((y & 0xF) << 7) | (z << 3) | (x >> 1);
        int dat = (blockdata[y >> 4][off] >> ((x & 1) << 2)) & 0xF;
    	return DynmapPlugin.stateByID[(id << 4) + dat];
    }

    public int getBlockSkyLight(int x, int y, int z)
    {
        int off = ((y & 0xF) << 7) | (z << 3) | (x >> 1);
        return (skylight[y >> 4][off] >> ((x & 1) << 2)) & 0xF;
    }

    public int getBlockEmittedLight(int x, int y, int z)
    {
        int off = ((y & 0xF) << 7) | (z << 3) | (x >> 1);
        return (emitlight[y >> 4][off] >> ((x & 1) << 2)) & 0xF;
    }

    public int getHighestBlockYAt(int x, int z)
    {
        return hmap[z << 4 | x];
    }

    public int getBiome(int x, int z)
    {
        return 255 & biome[z << 4 | x];
    }

    public final long getCaptureFullTime()
    {
        return captureFulltime;
    }

    public boolean isSectionEmpty(int sy)
    {
        return empty[sy];
    }
    
    public long getInhabitedTicks() {
        return inhabitedTicks;
    }
}
