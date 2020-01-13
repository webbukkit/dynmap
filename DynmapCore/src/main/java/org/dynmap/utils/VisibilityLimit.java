package org.dynmap.utils;

public interface VisibilityLimit {
    /**
     * Test if chunk intersects with visibility limit.
     * Coordinates are chunk coordinates.
     * @param chunk_x - x-coordinate of chunk
     * @param chunk_z - z-coordinate of chunk
     * @return true if chunk intersects visibility limit, false if not
     */
    /* Test if chunk is contained in visibility limit */
    public boolean doIntersectChunk(int chunk_x, int chunk_z);

    /* Returns x-coordinate of central block of visibility limit */
    public int xCenter();

    /* Returns z-coordinate of central block of visibility limit */
    public int zCenter();
}
