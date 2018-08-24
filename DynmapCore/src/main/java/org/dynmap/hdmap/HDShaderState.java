package org.dynmap.hdmap;

import org.dynmap.Color;
import org.dynmap.utils.DynLongHashMap;

/**
 * This interface is used to define the operational state of a renderer during raytracing
 * All method should be considered performance critical
 */
public interface HDShaderState {
    /**
     * Get our shader
     * @return shader
     */
    HDShader getShader();
    /**
     * Get our lighting
     * @return lighting
     */
    HDLighting getLighting();
    /**
     * Get our map
     * @return map
     */
    HDMap getMap();
    /**
     * Reset renderer state for new ray - passes in pixel coordinate for ray
     * @param ps - perspective state
     */
    void reset(HDPerspectiveState ps);
    /**
     * Process next ray step - called for each block on route
     * @param ps - perspective state
     * @return true if ray is done, false if ray needs to continue
     */
    boolean processBlock(HDPerspectiveState ps);
    /**
     * Ray ended - used to report that ray has exited map (called if renderer has not reported complete)
     * @param ps - perspective state
     */
    void rayFinished(HDPerspectiveState ps);
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
    /**
     * Get CTM texture cache
     * @return texture cache
     */
    DynLongHashMap getCTMTextureCache();
    /**
     * Get lighting table
     * @return array of lighting values
     */
    int[] getLightingTable();
}
