package org.dynmap.utils;

import org.bukkit.block.Biome;

/**
 * Iterator for traversing map chunk cache (base is for non-snapshot)
 */
public interface MapIterator {
    /**
     * Initialize iterator at given coordinates
     * 
     * @param x0
     * @param y0
     * @param z0
     */
    void initialize(int x0, int y0, int z0);
    /**
     * Get block ID at current coordinates
     * 
     * @return block id
     */
    int getBlockTypeID();
    /**
     * Get block data at current coordinates
     * @return block data
     */
    int getBlockData();
    /**
     * Get highest block Y coordinate at current X,Z
     * @return highest block coord 
     */
    int getHighestBlockYAt();
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
     */
    public Biome getBiome();
    /**
     * Get raw temperature data (0.0-1.0)
     */
    public double getRawBiomeTemperature();
    /**
     * Get raw rainfall data (0.0-1.0)
     */
    public double getRawBiomeRainfall();
    /**
     * Increment X of current position
     */
    void incrementX();
    /**
     * Decrement X of current position
     */
    void decrementX();
    /**
     * Increment Y of current position
     */
    void incrementY();
    /**
     * Decrement Y of current position
     */
    void decrementY();
    /**
     * Increment Z of current position
     */
    void incrementZ();
    /**
     * Decrement Y of current position
     */
    void decrementZ();
    /**
     * Set Y coordinate of current position
     * @param y
     */
    void setY(int y);
    /**
     * Get Y coordinate
     */
    int getY();
}
