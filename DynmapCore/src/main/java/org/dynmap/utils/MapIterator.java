package org.dynmap.utils;

import org.dynmap.common.BiomeMap;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.renderer.MapDataContext;

/**
 * Iterator for traversing map chunk cache (base is for non-snapshot)
 */
public interface MapIterator extends MapDataContext {
    /**
     * Initialize iterator at given coordinates
     * 
     * @param x0 - X coord
     * @param y0 - Y coord
     * @param z0 - Z coord
     */
    void initialize(int x0, int y0, int z0);
    /**
     * Get block sky light level at current coordinate
     * @return sky light level
     */
    int getBlockSkyLight();
    /**
     * Get emitted light level at current coordinate
     * @return emitted light level
     */
    int getBlockEmittedLight();
    /**
     * Get biome at coordinates
     * @return biome
     */
    public BiomeMap getBiome();
    /**
     * Get smoothed grass color multiplier
     * @param colormap - color map
     * @return smoothed multiplier
     */
    public int getSmoothGrassColorMultiplier(int[] colormap);
    /**
     * Get smoothed foliage color multiplier
     * @param colormap - color map
     * @return smoothed multiplier
     */
    public int getSmoothFoliageColorMultiplier(int[] colormap);
    /**
     * get smoothed water color multiplier
     * @return smoothed multiplier
     */
    public int getSmoothWaterColorMultiplier();
    /**
     * get smoothed water color multiplier
     * @param colormap - color map
     * @return smoothed multiplier
     */
    public int getSmoothWaterColorMultiplier(int[] colormap);
    /**
     * Get smoothed color multiplier, given normal and swamp color map
     * @param colormap - color map
     * @param swampcolormap - swamp-specific color map
     * @return smoothed multiplier
     */
    public int getSmoothColorMultiplier(int[] colormap, int[] swampcolormap);
    /**
     * Step current position in given direction
     * @param step - direction to step
     */
    void stepPosition(BlockStep step);
    /**
     * Step current position in opposite of given direction
     * @param step - direction to unstep
     */
    void unstepPosition(BlockStep step);
    /**
     * Unstep current position to previous position
     * @return step to take to return
     */
    BlockStep unstepPosition();
    /**
     * Set Y coordinate of current position
     * @param y - y coord
     */
    void setY(int y);
    /**
     * Get block ID at 1 step in given direction
     * @param s - direction to step
     * @return block id
     */
    DynmapBlockState getBlockTypeAt(BlockStep s);
    /**
     * Get last step taken
     * @return last step
     */
    BlockStep getLastStep();
    /**
     * Get world height
     * @return height
     */
    int getWorldHeight();
    /**
     * Get block key for current position (unique ID for block within cache being iterated)
     * @return block key
     */
    long getBlockKey();
    /**
     * Get inhabited ticks for current position
     * @return ticks inhabited
     */
    long getInhabitedTicks();
    /**
     * Get chunk dataVersion 
     */
    default int getDataVersion() { return 0; }
    /**
     * Get chunk status
     */
    default String getChunkStatus() { return null; }
}
