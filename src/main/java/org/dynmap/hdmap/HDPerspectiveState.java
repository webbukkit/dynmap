package org.dynmap.hdmap;

import org.dynmap.utils.Vector3D;

public interface HDPerspectiveState {
    /* Represents last step of movement of the ray */
    public enum BlockStep {
        X_PLUS,
        Y_PLUS,
        Z_PLUS,
        X_MINUS,
        Y_MINUS,
        Z_MINUS
    };
    /**
     * Get sky light level - only available if shader requested it
     */
    int getSkyLightLevel();
    /**
     * Get emitted light level - only available if shader requested it
     */
    int getEmittedLightLevel();
    /**
     * Get current block type ID
     */
    int getBlockTypeID();
    /**
     * Get current block data
     */
    int getBlockData();
    /**
     * Get direction of last block step
     */
    BlockStep getLastBlockStep();
    /**
     * Get perspective scale
     */
    double getScale();
    /**
     * Get start of current ray, in world coordinates
     */
    Vector3D getRayStart();
    /**
     * Get end of current ray, in world coordinates
     */
    Vector3D getRayEnd();
    /**
     * Get pixel X coordinate
     */
    int getPixelX();
    /**
     * Get pixel Y coordinate
     */
    int getPixelY();
}
