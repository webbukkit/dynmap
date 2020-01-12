package org.dynmap.renderer;

/**
 * Interface allowing a custom renderer to access the available map data needed to generate the render patch
 * list for the requested block data.
 * The context assures availability of the block data for the block requested, all blocks within the chunk
 * containing the block, and all the blocks adjacent to the block, at a minimum.
 */
public interface MapDataContext {
    /**
     * Get render patch factory - for allocating patches
     *
     * @return render patch factory
     */
    RenderPatchFactory getPatchFactory();

    /**
     * Get block type ID of requested block
     */
    DynmapBlockState getBlockType();

    /**
     * Get Tile Entity field value for requested block
     *
     * @param fieldId - field ID
     * @return value, or null of not found or available
     */
    Object getBlockTileEntityField(String fieldId);

    /**
     * Get block type ID of block at relative offset from requested block
     *
     * @param xoff - offset on X axis
     * @param yoff - offset on Y axis
     * @param zoff - offset on Z axis
     */
    DynmapBlockState getBlockTypeAt(int xoff, int yoff, int zoff);

    /**
     * Get Tile Entity field value of block at relative offset from requested block
     *
     * @param fieldId - field ID
     * @param xoff    - offset on X axis
     * @param yoff    - offset on Y axis
     * @param zoff    - offset on Z axis
     * @return value, or null of not found or available
     */
    Object getBlockTileEntityFieldAt(String fieldId, int xoff, int yoff, int zoff);

    /**
     * Get current X coordinate
     */
    int getX();

    /**
     * Get current Y coordinate
     */
    int getY();

    /**
     * Get current Z coordinate
     */
    int getZ();
}
