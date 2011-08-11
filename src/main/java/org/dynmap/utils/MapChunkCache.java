package org.dynmap.utils;
import org.bukkit.World;
import org.bukkit.block.Biome;

import java.util.List;
import org.dynmap.DynmapChunk;
import org.dynmap.DynmapWorld;

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
     * Set chunk data type needed
     * @param blockdata - need block type and data for chunk
     * @param biome - need biome data
     * @param highestblocky - need highest-block-y data
     * @param rawbiome - need raw biome temp/rain data
     * @return true if all data types can be retrieved, false if not
     */
    boolean setChunkDataTypes(boolean blockdata, boolean biome, boolean highestblocky, boolean rawbiome);
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
     * Test if all empty blocks
     */
    boolean isEmpty();
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
     * Get biome at coordinates
     */
    public Biome getBiome(int x, int z);
    /**
     * Get raw temperature data (0.0-1.0)
     */
    public double getRawBiomeTemperature(int x, int z);
    /**
     * Get raw rainfall data (0.0-1.0)
     */
    public double getRawBiomeRainfall(int x, int z);
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
    /**
     * Add hidden area limit - can be called more than once 
     * Needs to be set before chunks are loaded
     * Coordinates are block coordinates
     */
    public void setHiddenRange(VisibilityLimit limit);
    /**
     * Set autogenerate - must be done after at least one visible range has been set
     */
    public void setAutoGenerateVisbileRanges(DynmapWorld.AutoGenerateOption do_generate);
    /**
     * Get world
     */
    public World getWorld();
}
