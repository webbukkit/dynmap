package org.dynmap.hdmap;

import org.dynmap.utils.MapIterator;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.Vector3D;
import org.dynmap.utils.LightLevels;

public interface HDPerspectiveState {
    /**
     * Get light levels - only available if shader requested it
     * @param ll - light levels (filled in when returned)
     */
    void getLightLevels(LightLevels ll);
    /**
     * Get sky light level - only available if shader requested it
     * @param step - last step
     * @param ll - light levels (filled in when returned)
     */
    void getLightLevelsAtStep(BlockStep step, LightLevels ll);
    /**
     * Get current block state
     * @return block 
     */
    DynmapBlockState getBlockState();
    /**
     * Get direction of last block step
     * @return last step direction
     */
    BlockStep getLastBlockStep();
    /**
     * Get perspective scale
     * @return scale
     */
    double getScale();
    /**
     * Get start of current ray, in world coordinates
     * @return start of ray
     */
    Vector3D getRayStart();
    /**
     * Get end of current ray, in world coordinates
     * @return end of ray
     */
    Vector3D getRayEnd();
    /**
     * Get pixel X coordinate
     * @return x coordinate
     */
    int getPixelX();
    /**
     * Get pixel Y coordinate
     * @return y coordinate
     */
    int getPixelY();
    /**
     * Get current patch shade setting (false = no shadows)
     */
    boolean getShade();
    /**
     * Return submodel alpha value (-1 if no submodel rendered)
     * @return alpha value
     */
    int getSubmodelAlpha();
    /**
     * Return subblock coordinates of current ray position
     * @return coordinates of ray
     */
    int[] getSubblockCoord();
    /**
     * Check if point is on face
     */
    boolean isOnFace();
    /**
     * Get map iterator
     * @return iterator
     */
    MapIterator getMapIterator();
    /**
     * Get current texture index
     * @return texture index
     */
    int getTextureIndex();
    /**
     * Get current U of patch intercept
     * @return U in patch
     */
    double getPatchU();
    /**
     * Get current V of patch intercept
     * @return V in patch
     */
    double getPatchV();
    /**
     * Light level cache
     * @param idx - index of light level (0-3)
     * @return light level
     */
    LightLevels getCachedLightLevels(int idx);
}
