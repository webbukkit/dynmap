package org.dynmap.utils;
import org.bukkit.World;
import org.dynmap.DynmapChunk;

public interface MapChunkCache {
    /**
     * Load chunks into cache
     * @param w - world
     * @param chunks - chunks to be loaded
     */
    void loadChunks(World w, DynmapChunk[] chunks);
    /**
     * Unload chunks
     */
    void unloadChunks();
    /**
     * Get block ID at coordinates
     */
    int getBlockTypeID(int x, int y, int z);
    /**
     * Get block data at coordiates
     */
    byte getBlockData(int x, int y, int z);
    /**
     *  Get highest block Y
     */
    int getHighestBlockYAt(int x, int z);
    /**
     *  Get sky light level
     */
    int getBlockSkyLight(int x, int y, int z);
    /**
     * Get emitted light level
     */
    int getBlockEmittedLight(int x, int y, int z);
    /**
     * Get cache iterator
     */
    public MapIterator getIterator(int x, int y, int z);
}
