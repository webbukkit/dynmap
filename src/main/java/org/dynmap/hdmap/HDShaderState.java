package org.dynmap.hdmap;

import org.dynmap.Color;
import org.dynmap.utils.Vector3D;

/**
 * This interface is used to define the operational state of a renderer during raytracing
 * All method should be considered performance critical
 */
public interface HDShaderState {
    /**
     * Get our shader
     */
    HDShader getShader();
    /**
     * Get our lighting
     */
    HDLighting getLighting();
    /**
     * Get our map
     */
    HDMap getMap();
    /**
     * Reset renderer state for new ray - passes in pixel coordinate for ray
     */
    void reset(HDPerspectiveState ps);
    /**
     * Process next ray step - called for each block on route
     * @return true if ray is done, false if ray needs to continue
     */
    boolean processBlock(HDPerspectiveState ps);
    /**
     * Ray ended - used to report that ray has exited map (called if renderer has not reported complete)
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
}
