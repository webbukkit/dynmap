package org.dynmap.utils;

import org.bukkit.block.Biome;

/**
 * Iterator for traversing map chunk cache (base is for non-snapshot)
 */
public interface MapIterator {
    /* Represents last step of movement of the ray (don't alter order here - ordinal sensitive) */
    public enum BlockStep {
        X_PLUS,
        Y_PLUS,
        Z_PLUS,
        X_MINUS,
        Y_MINUS,
        Z_MINUS;
    };
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
     * Step current position in given direction
     */
    void stepPosition(BlockStep step);
    /**
     * Step current position in opposite of given direction
     */
    void unstepPosition(BlockStep step);
    /**
     * Unstep current position to previous position : return step to take to return
     */
    BlockStep unstepPosition();
    /**
     * Set Y coordinate of current position
     * @param y
     */
    void setY(int y);
    /**
     * Get X coordinate
     */
    int getX();
    /**
     * Get Y coordinate
     */
    int getY();
    /**
     * Get Z coordinate
     */
    int getZ();
    /**
     * Get block ID at 1 step in given direction
     * 
     * @return block id
     */
    int getBlockTypeIDAt(BlockStep s);
    /**
     * Get last step taken
     */
    BlockStep getLastStep();
}
