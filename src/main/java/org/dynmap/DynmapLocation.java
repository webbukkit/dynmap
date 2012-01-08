package org.dynmap;

/**
 * Generic block location
 */
public class DynmapLocation {
    public int x, y, z;
    public String world;
    
    public DynmapLocation() {}
    
    public DynmapLocation(String w, int x, int y, int z) {
        world = w;
        this.x = x; this.y = y; this.z = z;
    }
}
