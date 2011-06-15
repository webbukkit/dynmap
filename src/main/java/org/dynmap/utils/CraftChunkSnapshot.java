package org.dynmap.utils;


/**
 * Represents a static, thread-safe snapshot of chunk of blocks
 * Purpose is to allow clean, efficient copy of a chunk data to be made, and then handed off for processing in another thread (e.g. map rendering)
 */
public class CraftChunkSnapshot implements LegacyChunkSnapshot {
	private final int x, z;
	private final byte[] buf;	/* Flat buffer in uncompressed chunk file format */
	private final byte[] hmap; /* Highest Y map */
	private static final int BLOCKDATA_OFF = 32768;
	private static final int BLOCKLIGHT_OFF = BLOCKDATA_OFF + 16384;
	private static final int SKYLIGHT_OFF = BLOCKLIGHT_OFF + 16384;
	
	/**
	 * Constructor
	 */
	CraftChunkSnapshot(int x, int z, byte[] buf, byte[] hmap) {
		this.x = x;
		this.z = z;
		this.buf = buf;
		this.hmap = hmap;
	}
	
	/**
     * Gets the X-coordinate of this chunk
     *
     * @return X-coordinate
     */
    public int getX() {
    	return x;
    }

    /**
     * Gets the Z-coordinate of this chunk
     *
     * @return Z-coordinate
     */
    public int getZ() {
    	return z;
    }

    /**
     * Get block type for block at corresponding coordinate in the chunk
     * 
     * @param x 0-15
     * @param y 0-127
     * @param z 0-15
     * @return 0-255
     */
    public int getBlockTypeId(int x, int y, int z) {
    	return buf[x << 11 | z << 7 | y] & 255;
    }

    /**
     * Get block data for block at corresponding coordinate in the chunk
     * 
     * @param x 0-15
     * @param y 0-127
     * @param z 0-15
     * @return 0-15
     */
    public int getBlockData(int x, int y, int z) {
    	int off = ((x << 10) | (z << 6) | (y >> 1)) + BLOCKDATA_OFF;
 
        return ((y & 1) == 0) ? (buf[off] & 0xF) : ((buf[off] >> 4) & 0xF);
    }

    /**
     * Get sky light level for block at corresponding coordinate in the chunk
     * 
     * @param x 0-15
     * @param y 0-127
     * @param z 0-15
     * @return 0-15
     */
    public int getBlockSkyLight(int x, int y, int z) {
    	int off = ((x << 10) | (z << 6) | (y >> 1)) + SKYLIGHT_OFF;
    	 
        return ((y & 1) == 0) ? (buf[off] & 0xF) : ((buf[off] >> 4) & 0xF);
    }

    /**
     * Get light level emitted by block at corresponding coordinate in the chunk
     * 
     * @param x 0-15
     * @param y 0-127
     * @param z 0-15
     * @return 0-15
     */
    public int getBlockEmittedLight(int x, int y, int z) {
    	int off = ((x << 10) | (z << 6) | (y >> 1)) + BLOCKLIGHT_OFF;
    	 
        return ((y & 1) == 0) ? (buf[off] & 0xF) : ((buf[off] >> 4) & 0xF);
    }
    
    public int getHighestBlockYAt(int x, int z) {
        return hmap[z << 4 | x] & 255;
    }
}
