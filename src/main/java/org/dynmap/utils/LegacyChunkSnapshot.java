package org.dynmap.utils;

/**
 * Represents a static, thread-safe snapshot of chunk of blocks
 * Purpose is to allow clean, efficient copy of a chunk data to be made, and then handed off for processing in another thread (e.g. map rendering)
 */
public interface LegacyChunkSnapshot {
    /**
     * Get block type for block at corresponding coordinate in the chunk
     * 
     * @param x 0-15
     * @param y 0-127
     * @param z 0-15
     * @return 0-255
     */
    public int getBlockTypeId(int x, int y, int z);
    /**
     * Get block data for block at corresponding coordinate in the chunk
     * 
     * @param x 0-15
     * @param y 0-127
     * @param z 0-15
     * @return 0-15
     */
    public int getBlockData(int x, int y, int z);
    /**
     * Get sky light level for block at corresponding coordinate in the chunk
     * 
     * @param x 0-15
     * @param y 0-127
     * @param z 0-15
     * @return 0-15
     */
    public int getBlockSkyLight(int x, int y, int z);
    /**
     * Get light level emitted by block at corresponding coordinate in the chunk
     * 
     * @param x 0-15
     * @param y 0-127
     * @param z 0-15
     * @return 0-15
     */
    public int getBlockEmittedLight(int x, int y, int z);

    public int getHighestBlockYAt(int x, int z);
}
