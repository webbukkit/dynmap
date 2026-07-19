package org.dynmap;

/**
 * Generic block location
 */
public class DynmapLocation {
    public double x, y, z;
    public String world;
    
    public DynmapLocation() {}
    
    public DynmapLocation(String w, double x, double y, double z) {
        world = w;
        this.x = x; this.y = y; this.z = z;
    }
    public String toString() {
    	return String.format("{%s,%f,%f,%f}", world, x, y, z);
    }
}
