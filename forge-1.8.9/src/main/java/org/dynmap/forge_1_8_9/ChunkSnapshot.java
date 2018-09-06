package org.dynmap.forge_1_8_9;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.dynmap.Log;
import org.dynmap.renderer.DynmapBlockState;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import scala.actors.threadpool.Arrays;

/**
 * Represents a static, thread-safe snapshot of chunk of blocks
 * Purpose is to allow clean, efficient copy of a chunk data to be made, and then handed off for processing in another thread (e.g. map rendering)
 */
public class ChunkSnapshot
{
    private final int x, z;
    private final int[][] blockidx; /* Block state index, by section */
    private final byte[][] skylight;
    private final byte[][] emitlight;
    private final boolean[] empty;
    private final int[] hmap; // Height map
    private final int[] biome;
    private final long captureFulltime;
    private final int sectionCnt;
    private final long inhabitedTicks;

    private static final int BLOCKS_PER_SECTION = 16 * 16 * 16;
    private static final int COLUMNS_PER_CHUNK = 16 * 16;
    private static final int[] emptyIdx = new int[BLOCKS_PER_SECTION];
    private static final byte[] emptyData = new byte[BLOCKS_PER_SECTION / 2];
    private static final byte[] fullData = new byte[BLOCKS_PER_SECTION / 2];

    static
    {
        for (int i = 0; i < fullData.length; i++)
        {
            fullData[i] = (byte)0xFF;
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
        this.blockidx = new int[this.sectionCnt][];
        this.skylight = new byte[this.sectionCnt][];
        this.emitlight = new byte[this.sectionCnt][];
        this.empty = new boolean[this.sectionCnt];

        /* Fill with empty data */
        for (int i = 0; i < this.sectionCnt; i++)
        {
            this.empty[i] = true;
            this.blockidx[i] = emptyIdx;
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
        this.blockidx = new int[this.sectionCnt][];
        this.skylight = new byte[this.sectionCnt][];
        this.emitlight = new byte[this.sectionCnt][];
        this.empty = new boolean[this.sectionCnt];
        /* Fill with empty data */
        for (int i = 0; i < this.sectionCnt; i++) {
            this.empty[i] = true;
            this.blockidx[i] = emptyIdx;
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
            int[] blkidxs = new int[BLOCKS_PER_SECTION];
            this.blockidx[secnum] = blkidxs;
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
        			blkidxs[2*j] = (idx < p.length) ? p[idx] : 0;
        			// Get odd block id
        			blkidxs[2*j+1] = (idx2 < p.length) ? p[idx2] : 0;
                }
            }
            else {
            	byte[] lsb_bytes = sec.getByteArray("Blocks");
            	int len = BLOCKS_PER_SECTION;
            	if(len > lsb_bytes.length) len = lsb_bytes.length;
            	for(int j = 0; j < len; j++) {
            		blkidxs[j] = (0xFF & lsb_bytes[j]) << 4; 
            	}
            	if (sec.hasKey("Add", 7)) {    /* If additional data, add it */
            		byte[] msb = sec.getByteArray("Add");
            		len = BLOCKS_PER_SECTION / 2;
            		if(len > msb.length) len = msb.length;
            		for (int j = 0; j < len; j++) {
            			short b = (short)(msb[j] & 0xFF);
            			if (b == 0) {
            				continue;
            			}
            			blkidxs[j << 1] |= (b & 0x0F) << 12;
            			blkidxs[(j << 1) + 1] |= (b & 0xF0) << 8;
            		}
            	}
            	if (sec.hasKey("Add2", 7)) {    /* If additional data (NEID), add it */
            		byte[] msb = sec.getByteArray("Add2");
            		len = BLOCKS_PER_SECTION / 2;
            		if(len > msb.length) len = msb.length;
            		for (int j = 0; j < len; j++) {
            			short b = (short)(msb[j] & 0xFF);
            			if (b == 0) {
            				continue;
            			}
            			blkidxs[j << 1] |= (b & 0x0F) << 16;
            			blkidxs[(j << 1) + 1] |= (b & 0xF0) << 12;
            		}
            	}
            	byte[] bd = sec.getByteArray("Data");
            	for (int j = 0; j < bd.length; j++) {
        			int b = bd[j] & 0xFF;
        			if (b == 0) {
        				continue;
        			}
        			blkidxs[j << 1] |= b & 0x0F;
        			blkidxs[(j << 1) + 1] |= (b & 0xF0) >> 4;
            	}
            }
            this.emitlight[secnum] = sec.getByteArray("BlockLight");
            if (sec.hasKey("SkyLight")) {
                this.skylight[secnum] = sec.getByteArray("SkyLight");
            }
            this.empty[secnum] = false;
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

    public int getBlockTypeId(int x, int y, int z)
    {
        return blockidx[y >> 4][((y & 0xF) << 8) | (z << 4) | x] >> 4;
    }

    public int getBlockData(int x, int y, int z)
    {
        return blockidx[y >> 4][((y & 0xF) << 8) | (z << 4) | x] & 0xF;
    }
    
    public DynmapBlockState getBlockType(int x, int y, int z)
    {
        int id = blockidx[y >> 4][((y & 0xF) << 8) | (z << 4) | x];
    	return DynmapPlugin.stateByID[id];
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
        return biome[z << 4 | x];
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
