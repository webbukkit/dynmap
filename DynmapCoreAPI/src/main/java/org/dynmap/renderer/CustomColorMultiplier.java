package org.dynmap.renderer;

/**
 * Base class for custom color multiplier
 */
public abstract class CustomColorMultiplier {
    /**
     * Default constructor - required
     */
    protected CustomColorMultiplier() {
    }
    /**
     * Cleanup custom color multiplier
     * 
     * If overridden, super.cleanupColorMultiplier() should be called
     */
    public void cleanupColorMultiplier() {
    }
    /**
     * Compute color multiplier for current block, given context
     * @param mapDataCtx - Map data context: can be used to read any data available for map.
     * @return color multiplier (0xRRGGBB)
     */
    public abstract int getColorMultiplier(MapDataContext mapDataCtx);
}
