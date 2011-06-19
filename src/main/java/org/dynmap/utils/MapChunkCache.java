package org.dynmap.utils;
import org.bukkit.World;
import java.util.List;
import org.dynmap.DynmapChunk;

public interface MapChunkCache {
    public enum HiddenChunkStyle {
        FILL_AIR,
        FILL_STONE_PLAIN,
        FILL_OCEAN
    };
    public static class VisibilityLimit {
        public int x0, x1, z0, z1;
    }
    /**
     * Set chunks to load, and world to load from
     */
    void setChunks(World w, List<DynmapChunk> chunks);
    /**
     * Load chunks into cache
     * @param maxToLoad - maximum number to load at once
     * @return number loaded
     */
    int loadChunks(int maxToLoad);
    /**
     * Test if done loading
     */
    boolean isDoneLoading();
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
    /**
     * Set hidden chunk style (default is FILL_AIR)
     */
    public void setHiddenFillStyle(HiddenChunkStyle style);
    /**
     * Add visible area limit - can be called more than once 
     * Needs to be set before chunks are loaded
     * Coordinates are block coordinates
     */
    public void setVisibleRange(VisibilityLimit limit);
}
