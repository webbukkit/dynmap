package org.dynmap.hdmap;

import org.dynmap.Color;
import org.dynmap.utils.Vector3D;

/**
 * This interface is used to define the operational state of a renderer during raytracing
 * All method should be considered performance critical
 */
public interface HDShaderState {
    /**
     * Reset renderer state for new ray - passes in pixel coordinate for ray
     */
    void reset(int x, int y, Vector3D raystart, double scale);
    /**
     * Process next ray step - called for each block on route
     * @param blocktype - block type of current block
     * @param blockdata - data nibble of current block
     * @param skylightlevel - sky light level of previous block (surface on current block)
     * @param emittedlightlevel - emitted light level of previous block (surface on current block)
     * @param laststep - direction of last step
     * @return true if ray is done, false if ray needs to continue
     */
    boolean processBlock(int blocktype, int blockdata, int skylightlevel, int emittedlightlevel, HDMap.BlockStep laststep);
    /**
     * Ray ended - used to report that ray has exited map (called if renderer has not reported complete)
     */
    void rayFinished();
    /**
     * Get result color - get resulting color for ray
     * @param c - object to store color value in
     * @param index - index of color to request (renderer specific - 0=default, 1=day for night/day renderer
     */
    void getRayColor(Color c, int index);
    /**
     * Clean up state object - called after last ray completed
     */
    void cleanup();
}
