package org.dynmap.utils;
import org.bukkit.World;
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
