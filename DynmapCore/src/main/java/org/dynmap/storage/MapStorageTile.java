package org.dynmap.storage;

import java.awt.image.BufferedImage;

import org.dynmap.DynmapWorld;
import org.dynmap.MapType;
import org.dynmap.utils.BufferInputStream;
import org.dynmap.utils.BufferOutputStream;
import org.dynmap.utils.ImageIOManager;

/**
 * Abstract class for instance of a stored map tile
 */
public abstract class MapStorageTile {
    public final DynmapWorld world;
    public final MapType map;
    public final int x, y;
    public final int zoom;
    public final MapType.ImageVariant var;
    
    public static class TileRead {
        public BufferInputStream image;    // Image bytes
        public MapType.ImageEncoding format; // Image format
        public long hashCode;              // Image hashcode (-1 = unknown)
        public long lastModified;          // Last modified timestamp (-1 = unknown)
    }

    protected MapStorageTile(DynmapWorld world, MapType map, int x, int y, int zoom, MapType.ImageVariant var) {
        this.world = world;
        this.map = map;
        this.x = x;
        this.y = y;
        this.zoom = zoom;
        this.var = var;
    }
    /**
     * Test if given tile exists in the tile storage
     * @return true if tile exists, false if not
     */
    public abstract boolean exists();
    /**
     * Test if tile exists and matches given hash code
     * @param hash - hash code to test against tile's content
     * @return true if tile exists and matches given hash code, false if not
     */
    public abstract boolean matchesHashCode(long hash);
    /**
     * Read tile
     *
     * @return loaded Tile, or null if not read
     */
    public abstract TileRead read();
    /**
     * Write tile
     *
     * @param hash - hash code of uncompressed image
     * @param encImage - output stream for encoded image
     * @return true if write succeeded
     */
    public abstract boolean write(long hash, BufferOutputStream encImage);
    /**
     * Write tile from image
     * 
     * @param hash - hash code of uncompressed image
     * @param image - image to be encoded
     * @return true if write succeeded
     */
    public boolean write(long hash, BufferedImage image) {
        BufferOutputStream bos = ImageIOManager.imageIOEncode(image, map.getImageFormat());
        if (bos != null) {
            return write(hash, bos);
        }
        return false;
    }
    /**
     * Delete tile
     *
     * @return true if write succeeded
     */
    public boolean delete() {
        return write(-1, (BufferOutputStream) null);
    }
    /**
     * Get write lock on tile
     * @return true if locked
     */
    public abstract boolean getWriteLock();
    /**
     * Release write lock on tile
     */
    public abstract void releaseWriteLock();
    /**
     * Get read lock on tile
     * @param timeout - timeout, in msec (-1 = never)
     * @return true if lock acquired, false if not (timeout)
     */
    public abstract boolean getReadLock(long timeout);
    /**
     * Get read lock on tile (indefinite timeout)
     * @return true if lock acquired, false if not (timeout)
     */
    public boolean getReadLock() {
        return getReadLock(-1L);
    }
    /**
     * Release read lock on tile
     */
    public abstract void releaseReadLock();
    /**
     * Cleanup
     */
    public abstract void cleanup();
    /**
     * Get URI for tile (for web interface)
     * @return URI for tile
     */
    public abstract String getURI();
    /**
     * Enqueue zoom out update for tile
     */
    public abstract void enqueueZoomOutUpdate();
    /**
     * Get zoom out tile for this tile (next zoom leveL)
     * @return zoom out tile
     */
    public abstract MapStorageTile getZoomOutTile();
    /**
     * Equals
     */
    @Override
    public abstract boolean equals(Object o);
    /**
     * Hashcode
     */
    @Override
    public abstract int hashCode();
}
