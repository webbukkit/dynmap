package org.dynmap.modsupport;

/**
 * Volumetric block model - uses standard 6 sides texture indices 
 */
@Deprecated
public interface VolumetricBlockModel extends BlockModel {
    /**
     * Set subblock to be filled
     * @param x - x coordinate within grid (0 to (scale-1))
     * @param y - y coordinate within grid (0 to (scale-1))
     * @param z - z coordinate within grid (0 to (scale-1))
     */
    public void setSubBlockToFilled(int x, int y, int z);
    /**
     * Set subblock to be empty
     * @param x - x coordinate within grid (0 to (scale-1))
     * @param y - y coordinate within grid (0 to (scale-1))
     * @param z - z coordinate within grid (0 to (scale-1))
     */
    public void setSubBlockToEmpty(int x, int y, int z);
}
